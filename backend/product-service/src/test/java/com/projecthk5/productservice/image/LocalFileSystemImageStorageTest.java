package com.projecthk5.productservice.image;

import static com.projecthk5.productservice.image.ImageStorageException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import com.projecthk5.productservice.image.ProductImageValidator.ImageInput;
import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileSystemImageStorageTest {
    @TempDir Path sandbox;
    private static final UUID FIXED = UUID.fromString("f1736435-4321-4123-9123-001122334455");
    private final ProductImageValidator validator = new ProductImageValidator();
    private final List<Path> redirects = new ArrayList<>();

    @AfterEach
    void removeOnlyOwnedRedirectEntriesBeforeJUnitDirectoryCleanup() throws IOException {
        for (Path link : redirects.reversed()) {
            assertTrue(link.normalize().startsWith(sandbox));
            var attributes = Files.readAttributes(link, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            assertTrue(attributes.isSymbolicLink() || attributes.isOther());
            Files.delete(link); // Non-recursive: remove only our link/junction, never its target.
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sample.jpg", "sample.png", "sample.webp"})
    void storesValidatedBytesAndReadsThemAfterAdapterRestart(String name) throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var checked = image(name);
        var storage = new LocalFileSystemImageStorage(root);
        var stored = storage.store(checked);
        assertEquals(checked.format(), stored.format());
        assertEquals(16, stored.width());
        assertEquals(12, stored.height());
        assertEquals(checked.size(), stored.size());
        assertTrue(stored.key().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        assertArrayEquals(fixture(name), storage.read(stored.key()));
        byte[] firstRead = storage.read(stored.key());
        firstRead[0] ^= 1;
        checked.content()[0] ^= 1;
        assertArrayEquals(fixture(name), new LocalFileSystemImageStorage(root).read(stored.key()));
        assertFalse(stored.toString().contains(root.toString()));
        assertEquals(1, entries(root));
    }

    @Test
    void ignoresClientFilenameAndDoesNotReopenValidatedInput() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        byte[] expected = fixture("sample.png");
        var input = new ByteArrayInputStream(expected) {
            boolean consumed;
            @Override public byte[] readNBytes(int length) throws IOException {
                if (consumed) throw new AssertionError("Untrusted stream reopened");
                consumed = true;
                return super.readNBytes(length);
            }
        };
        var checked = validator.validate(new ImageInput("../../outside-private.png", "image/png", input));
        input.close();
        var storage = new LocalFileSystemImageStorage(root);
        var result = storage.store(checked);
        assertArrayEquals(expected, storage.read(result.key()));
        assertFalse(Files.exists(sandbox.resolve("outside-private.png")));
        assertFalse(result.key().contains("private"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../outside", "..", ".", "/etc/passwd", "C:\\private\\image", "\\\\server\\share",
            "%2e%2e%2fsecret", "f1736435-4321-4123-9123-001122334455/../secret",
            "F1736435-4321-4123-9123-001122334455", "f1736435-4321-4123-9123-001122334455.png", " key ", "x\u0000y"})
    void rejectsUntrustedKeysBeforeAnyPathLookup(String key) throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        assertCode(INVALID_KEY, () -> storage.read(key));
        assertEquals(0, entries(root));
    }

    @Test
    void missingObjectHasSanitizedErrorAndNoSideEffect() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        assertCode(NOT_FOUND, () -> storage.read(FIXED.toString()));
        assertEquals(0, entries(root));
    }

    @Test
    void nullImageDoesNotReserveStorageSpace() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        assertCode(INVALID_INPUT, () -> storage.store(null));
        assertEquals(0, entries(root));
    }

    @Test
    void requiresExplicitExistingDirectoryInsteadOfCreatingDefaultOrParentPaths() throws Exception {
        assertCode(INVALID_INPUT, () -> new LocalFileSystemImageStorage(null));
        assertCode(UNSAFE_STORAGE, () -> new LocalFileSystemImageStorage(Path.of("relative-storage")));
        assertCode(UNSAFE_STORAGE, () -> new LocalFileSystemImageStorage(sandbox.resolve("missing")));
        var regular = Files.writeString(sandbox.resolve("not-directory"), "private");
        assertCode(UNSAFE_STORAGE, () -> new LocalFileSystemImageStorage(regular));
        assertFalse(Files.exists(sandbox.resolve("missing")));
    }

    @Test
    void duplicateServerKeyNeverOverwritesOrDeletesPublishedImage() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var png = image("sample.png");
        var jpeg = image("sample.jpg");
        try (var keys = mockStatic(UUID.class, call -> call.getMethod().getName().equals("randomUUID")
                ? FIXED : call.callRealMethod())) {
            storage.store(png);
            assertCode(KEY_CONFLICT, () -> new LocalFileSystemImageStorage(root).store(jpeg));
        }
        assertArrayEquals(png.content(), storage.read(FIXED.toString()));
        assertEquals(1, entries(root));
        assertEquals(1, entries(root.resolve(FIXED.toString())));
    }

    @Test
    void concurrentAdaptersWriteUniqueCompleteObjects() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var checked = image("sample.png");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var writes = new ArrayList<Future<String>>();
            for (int i = 0; i < 24; i++) writes.add(pool.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out");
                var storage = new LocalFileSystemImageStorage(root);
                var stored = storage.store(checked);
                assertArrayEquals(checked.content(), storage.read(stored.key()));
                return stored.key();
            }));
            start.countDown();
            var keys = new HashSet<String>();
            for (var write : writes) assertTrue(keys.add(write.get(20, TimeUnit.SECONDS)));
            assertEquals(24, entries(root));
        }
    }

    @Test
    void atomicPublishExposesNoPartialObjectAndLeavesNoTemporaryFile() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        var moves = new AtomicInteger();
        try (var io = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("move")) {
                moves.incrementAndGet();
                Path temporary = call.getArgument(0);
                String key = temporary.getParent().getFileName().toString();
                assertArrayEquals(checked.content(), Files.readAllBytes(temporary));
                assertCode(NOT_FOUND, () -> storage.read(key));
                assertArrayEquals(new CopyOption[]{StandardCopyOption.ATOMIC_MOVE},
                        (CopyOption[]) call.getRawArguments()[2]);
            }
            return call.callRealMethod();
        })) {
            var stored = storage.store(checked);
            assertArrayEquals(checked.content(), storage.read(stored.key()));
            assertEquals(1, entries(root.resolve(stored.key())));
        }
        assertEquals(1, moves.get());
    }

    @Test
    void unsupportedAtomicMoveFallsBackWithoutReplaceExisting() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        var moves = new AtomicInteger();
        try (var io = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("move")) {
                CopyOption[] options = (CopyOption[]) call.getRawArguments()[2];
                if (moves.getAndIncrement() == 0) {
                    assertArrayEquals(new CopyOption[]{StandardCopyOption.ATOMIC_MOVE}, options);
                    throw new AtomicMoveNotSupportedException("private-source", "private-target", "test only");
                }
                assertEquals(0, options.length);
            }
            return call.callRealMethod();
        })) {
            var stored = storage.store(checked);
            assertArrayEquals(checked.content(), storage.read(stored.key()));
            assertEquals(1, entries(root.resolve(stored.key())));
        }
        assertEquals(2, moves.get());
    }

    @Test
    void writeFailureCleansReservedDirectoryAndSanitizesFilesystemException() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        try (var io = mockStatic(FileChannel.class, call -> {
            if (call.getMethod().getName().equals("open"))
                throw new IOException("Disk full at " + root);
            return call.callRealMethod();
        })) {
            assertCode(IO_FAILURE, () -> storage.store(checked));
        }
        assertEquals(0, entries(root));
    }

    @Test
    void publishFailureCleansWrittenTemporaryBytesAndReservedDirectory() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        try (var io = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("move")) throw new IOException("Private path: " + root);
            return call.callRealMethod();
        })) {
            assertCode(IO_FAILURE, () -> storage.store(checked));
        }
        assertEquals(0, entries(root));
    }

    @Test
    void rejectsRedirectedRootAndAncestorWithoutReadingOrWritingOutside() throws Exception {
        var outside = Files.createDirectory(sandbox.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "unchanged");
        var link = sandbox.resolve("redirect");
        redirect(link, outside);
        assertCode(UNSAFE_STORAGE, () -> new LocalFileSystemImageStorage(link));
        var child = Files.createDirectory(outside.resolve("child"));
        assertCode(UNSAFE_STORAGE, () -> new LocalFileSystemImageStorage(link.resolve("child")));
        assertEquals("unchanged", Files.readString(outside.resolve("sentinel")));
        assertEquals(0, entries(child));
    }

    @Test
    void rejectsKeyDirectoryRedirectAndDoesNotRemoveItsOutsideTargetOnCollision() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var outside = Files.createDirectory(sandbox.resolve("outside"));
        Files.writeString(outside.resolve("content"), "private outside bytes");
        redirect(root.resolve(FIXED.toString()), outside);
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        assertCode(UNSAFE_STORAGE, () -> storage.read(FIXED.toString()));
        try (var keys = mockStatic(UUID.class, call -> call.getMethod().getName().equals("randomUUID")
                ? FIXED : call.callRealMethod())) {
            assertCode(KEY_CONFLICT, () -> storage.store(checked));
        }
        assertEquals("private outside bytes", Files.readString(outside.resolve("content")));
    }

    @Test
    void rootReplacementAfterConstructionIsRejected() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var outside = Files.createDirectory(sandbox.resolve("outside"));
        var storage = new LocalFileSystemImageStorage(root);
        var checked = image("sample.png");
        Files.move(root, sandbox.resolve("original-store"));
        redirect(root, outside);
        assertCode(UNSAFE_STORAGE, () -> storage.store(checked));
        assertCode(UNSAFE_STORAGE, () -> storage.read(FIXED.toString()));
        assertEquals(0, entries(outside));
    }

    @Test
    void rejectsOversizedOrNonRegularStoredContentWithoutUnboundedRead() throws Exception {
        var root = Files.createDirectory(sandbox.resolve("store"));
        var storage = new LocalFileSystemImageStorage(root);
        var stored = storage.store(image("sample.png"));
        var file = root.resolve(stored.key()).resolve("content");
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.position(ProductImageValidator.MAX_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{1}));
        }
        assertCode(IO_FAILURE, () -> storage.read(stored.key()));
        Files.delete(file);
        Files.createDirectory(file);
        assertCode(UNSAFE_STORAGE, () -> storage.read(stored.key()));
    }

    private ValidatedImage image(String name) throws IOException {
        String mime = name.endsWith(".jpg") ? "image/jpeg" : name.endsWith(".png") ? "image/png" : "image/webp";
        return validator.validate(new ImageInput(name, mime, new ByteArrayInputStream(fixture(name))));
    }

    private static byte[] fixture(String name) throws IOException {
        try (var stream = Objects.requireNonNull(LocalFileSystemImageStorageTest.class.getResourceAsStream("/images/" + name))) {
            return stream.readAllBytes();
        }
    }

    private static long entries(Path directory) throws IOException {
        try (var files = Files.list(directory)) { return files.count(); }
    }

    private void assertCode(ImageStorageException.Code code, Runnable action) {
        var failure = assertThrows(ImageStorageException.class, action::run);
        assertEquals(code, failure.getCode());
        assertFalse(failure.toString().contains(sandbox.toString()));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    private void redirect(Path link, Path target) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createSymbolicLink(link, target);
            redirects.add(link);
            return;
        }
        // Windows junctions exercise real reparse-point escape without requiring symlink privileges.
        String command = "New-Item -ItemType Junction -Path '" + link.toString().replace("'", "''")
                + "' -Target '" + target.toString().replace("'", "''") + "' | Out-Null";
        var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Junction creation timed out");
        assertEquals(0, process.exitValue(), () -> "Junction creation failed");
        redirects.add(link);
    }
}
