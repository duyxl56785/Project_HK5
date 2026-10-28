package com.projecthk5.productservice.image;

import static com.projecthk5.productservice.image.ImageStorageException.Code.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Internal dev storage in an explicit, existing, service-exclusive directory.
 * No Spring bean, HTTP route, public URL or default root. Protect the root/ancestors with OS ACLs:
 * portable Windows NIO does not offer race-free directory handles against same-account attackers.
 */
public final class LocalFileSystemImageStorage implements ImageStorage {
    private static final Pattern KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private final Path root;
    private final DirectoryIdentity rootIdentity;

    public LocalFileSystemImageStorage(Path root) {
        if (root == null) throw failure(INVALID_INPUT);
        if (!root.isAbsolute() || !root.equals(root.normalize()) || root.getParent() == null)
            throw failure(UNSAFE_STORAGE);
        this.root = root;
        try {
            checkAncestors(root);
            this.rootIdentity = directory(root);
        } catch (IOException | SecurityException invalidRoot) {
            throw failure(UNSAFE_STORAGE);
        }
    }

    @Override
    public StoredImage store(ValidatedImage image) {
        if (image == null) throw failure(INVALID_INPUT);
        byte[] bytes = image.content(); // One defensive snapshot; never reopen the supplied stream.
        checkRoot();
        String key = UUID.randomUUID().toString();
        Path directory = root.resolve(key);
        Path temporary = directory.resolve(".pending");
        Path content = directory.resolve("content");
        DirectoryIdentity reserved = null;
        boolean published = false;
        try {
            // Reservation is atomic and never replaces an existing key, including across adapters.
            Files.createDirectory(directory, ownerOnly("rwx------"));
            reserved = directory(directory);
            checkOwnedDirectory(directory, reserved);
            try (var channel = FileChannel.open(temporary, Set.of(CREATE_NEW, WRITE, NOFOLLOW_LINKS),
                    ownerOnly("rw-------"))) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            checkOwnedDirectory(directory, reserved);
            if (Files.exists(content, NOFOLLOW_LINKS)) throw failure(KEY_CONFLICT);
            try {
                Files.move(temporary, content, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, content); // No replace; completed temp is on the same filesystem.
            }
            published = true;
            checkOwnedDirectory(directory, reserved);
            return new StoredImage(key, image.format(), bytes.length, image.width(), image.height());
        } catch (FileAlreadyExistsException collision) {
            throw failure(KEY_CONFLICT);
        } catch (IOException | SecurityException io) {
            throw failure(IO_FAILURE);
        } finally {
            if (!published && reserved != null) cleanup(directory, temporary, reserved);
        }
    }

    @Override
    public byte[] read(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw failure(INVALID_KEY);
        checkRoot();
        Path directory = root.resolve(key);
        Path content = directory.resolve("content");
        try {
            DirectoryIdentity identity = directory(directory);
            checkOwnedDirectory(directory, identity);
            BasicFileAttributes file = Files.readAttributes(content, BasicFileAttributes.class, NOFOLLOW_LINKS);
            if (!file.isRegularFile() || file.isSymbolicLink() || file.isOther()) throw failure(UNSAFE_STORAGE);
            if (file.size() < 1 || file.size() > ProductImageValidator.MAX_BYTES) throw failure(IO_FAILURE);
            byte[] bytes;
            try (var channel = FileChannel.open(content, READ, NOFOLLOW_LINKS)) {
                bytes = Channels.newInputStream(channel).readNBytes(ProductImageValidator.MAX_BYTES + 1);
            }
            checkOwnedDirectory(directory, identity);
            if (bytes.length != file.size() || bytes.length > ProductImageValidator.MAX_BYTES) throw failure(IO_FAILURE);
            return bytes;
        } catch (NoSuchFileException missing) {
            throw failure(NOT_FOUND);
        } catch (IOException | SecurityException io) {
            throw failure(IO_FAILURE);
        }
    }


    @Override
    public StoredImage storeReserved(UUID imageId, ValidatedImage image) {
        String key = reservedKey(imageId);
        if (image == null) throw failure(INVALID_INPUT);
        byte[] bytes = image.content();
        String digest = digest(bytes);
        checkRoot();
        Path folder = root.resolve(key);
        try {
            try {
                Files.createDirectory(folder, ownerOnly("rwx------"));
                writeNew(folder.resolve(".binding"), digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            } catch (FileAlreadyExistsException existing) {
                // An existing directory is usable only with the same durable binding.
            }
            DirectoryIdentity identity = directory(folder);
            checkOwnedDirectory(folder, identity);
            Path binding = folder.resolve(".binding");
            regular(binding);
            try (var channel = FileChannel.open(binding, READ, WRITE, NOFOLLOW_LINKS);
                 var lease = channel.lock()) {
                requireBinding(channel, digest);
                knownChildren(folder);
                if (Files.exists(folder.resolve(".cancelled"), NOFOLLOW_LINKS)) throw failure(KEY_CONFLICT);
                Path content = folder.resolve("content");
                if (Files.exists(content, NOFOLLOW_LINKS)) {
                    if (!digest.equals(digest(read(key)))) throw failure(KEY_CONFLICT);
                } else {
                    Path temporary = folder.resolve(".pending");
                    if (Files.exists(temporary, NOFOLLOW_LINKS)) {
                        regular(temporary);
                        Files.delete(temporary);
                    }
                    writeNew(temporary, bytes);
                    checkOwnedDirectory(folder, identity);
                    if (Files.exists(content, NOFOLLOW_LINKS)) throw failure(KEY_CONFLICT);
                    try { Files.move(temporary, content, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, content); }
                }
                checkOwnedDirectory(folder, identity);
                if (!digest.equals(digest(read(key)))) throw failure(IO_FAILURE);
                return new StoredImage(key, image.format(), bytes.length, image.width(), image.height());
            }
        } catch (NoSuchFileException unbound) { throw failure(KEY_CONFLICT); }
        catch (IOException | SecurityException | java.nio.channels.OverlappingFileLockException unavailable) {
            throw failure(IO_FAILURE);
        }
    }

    @Override
    public void deleteReserved(UUID imageId, String contentSha256) {
        String key = reservedKey(imageId);
        if (contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) throw failure(INVALID_INPUT);
        checkRoot();
        Path folder = root.resolve(key);
        if (!Files.exists(folder, NOFOLLOW_LINKS)) return;
        try {
            DirectoryIdentity identity = directory(folder);
            checkOwnedDirectory(folder, identity);
            Path binding = folder.resolve(".binding");
            regular(binding);
            try (var channel = FileChannel.open(binding, READ, WRITE, NOFOLLOW_LINKS);
                 var lease = channel.lock()) {
                requireBinding(channel, contentSha256);
                knownChildren(folder);
                Path content = folder.resolve("content");
                if (Files.exists(content, NOFOLLOW_LINKS) && !contentSha256.equals(digest(read(key)))) {
                    throw failure(KEY_CONFLICT);
                }
                Path temporary = folder.resolve(".pending");
                if (Files.exists(temporary, NOFOLLOW_LINKS)) regular(temporary);
                Path cancelled = folder.resolve(".cancelled");
                if (!Files.exists(cancelled, NOFOLLOW_LINKS)) {
                    writeNew(cancelled, contentSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                } else {
                    regular(cancelled);
                    try (var marker = FileChannel.open(cancelled, READ, NOFOLLOW_LINKS)) {
                        requireBinding(marker, contentSha256);
                    }
                }
                checkOwnedDirectory(folder, identity);
                Files.deleteIfExists(content);
                Files.deleteIfExists(temporary);
                // Retain binding/cancellation tombstones for safe retries; no recursive deletion.
            }
        } catch (NoSuchFileException unbound) { throw failure(KEY_CONFLICT); }
        catch (IOException | SecurityException | java.nio.channels.OverlappingFileLockException unavailable) {
            throw failure(IO_FAILURE);
        }
    }

    private static String reservedKey(UUID imageId) {
        if (imageId == null || !KEY.matcher(imageId.toString()).matches()) throw failure(INVALID_KEY);
        return imageId.toString();
    }

    private static void regular(Path file) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) throw failure(UNSAFE_STORAGE);
    }

    private static void knownChildren(Path folder) throws IOException {
        try (var entries = Files.list(folder)) {
            if (entries.anyMatch(path -> !Set.of(".binding", ".cancelled", ".pending", "content")
                    .contains(path.getFileName().toString()))) throw failure(KEY_CONFLICT);
        }
    }

    private void writeNew(Path path, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(path, Set.of(CREATE_NEW, WRITE, NOFOLLOW_LINKS), ownerOnly("rw-------"))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void requireBinding(FileChannel channel, String digest) throws IOException {
        channel.position(0);
        byte[] bytes = Channels.newInputStream(channel).readNBytes(65);
        if (!java.util.Arrays.equals(bytes, digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw failure(KEY_CONFLICT);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private void checkRoot() {
        try {
            checkAncestors(root);
            if (!rootIdentity.matches(directory(root))) throw failure(UNSAFE_STORAGE);
        } catch (IOException | SecurityException unsafe) {
            throw failure(UNSAFE_STORAGE);
        }
    }

    private void checkOwnedDirectory(Path directory, DirectoryIdentity expected) throws IOException {
        checkRoot();
        if (!expected.matches(directory(directory))) throw failure(UNSAFE_STORAGE);
    }

    private static void checkAncestors(Path directory) throws IOException {
        for (Path current = directory; current != null; current = current.getParent()) directory(current);
    }

    private static DirectoryIdentity directory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
        // Windows junctions can be directories AND isOther; never rely on isSymbolicLink alone.
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther())
            throw failure(UNSAFE_STORAGE);
        if (!path.toRealPath().equals(path.toRealPath(NOFOLLOW_LINKS))) throw failure(UNSAFE_STORAGE);
        return new DirectoryIdentity(attributes.fileKey(), attributes.creationTime());
    }

    private FileAttribute<?>[] ownerOnly(String permissions) {
        return root.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions))}
                : new FileAttribute<?>[0]; // Windows inherits the explicitly provisioned private root ACL.
    }

    private void cleanup(Path directory, Path temporary, DirectoryIdentity identity) {
        try {
            checkOwnedDirectory(directory, identity);
            Files.deleteIfExists(temporary);
            Files.delete(directory); // Never recursive; never delete a pre-existing/colliding key.
        } catch (IOException | SecurityException | ImageStorageException cleanupFailure) {
            // Keep the original sanitized operation failure. Unsafe/unwritable roots may retain
            // a private orphan; cleanup must never follow a redirect or delete unowned data.
        }
    }

    private record DirectoryIdentity(Object fileKey, FileTime createdAt) {
        boolean matches(DirectoryIdentity current) {
            // Windows fileKey is absent: creation time is a replacement diagnostic, NOT race-proof identity.
            return fileKey != null && current.fileKey != null
                    ? Objects.equals(fileKey, current.fileKey) : createdAt.equals(current.createdAt);
        }
    }

    private static ImageStorageException failure(ImageStorageException.Code code) {
        return new ImageStorageException(code);
    }
}
