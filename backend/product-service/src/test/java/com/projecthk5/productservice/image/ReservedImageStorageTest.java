package com.projecthk5.productservice.image;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.image.ProductImageValidator.ImageInput;
import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReservedImageStorageTest {
    @TempDir Path root;

    @Test void sameReservationAndContentSurviveAdapterRestartWithoutAnotherObject() throws Exception {
        UUID id = UUID.randomUUID();
        var image = image(1);
        var first = store(new LocalFileSystemImageStorage(root), id, image);
        var restarted = new LocalFileSystemImageStorage(root);
        assertEquals(id.toString(), first.key());
        assertEquals(first, store(restarted, id, image));
        assertArrayEquals(image.content(), restarted.read(first.key()));
        assertEquals(1, Files.list(root).count());
    }

    @Test void anotherContentCannotReplaceTheSameReservation() throws Exception {
        var storage = new LocalFileSystemImageStorage(root);
        UUID id = UUID.randomUUID();
        var first = image(1);
        store(storage, id, first);
        assertThrows(ImageStorageException.class, () -> store(storage, id, image(2)));
        assertArrayEquals(first.content(), storage.read(id.toString()));
    }

    @Test void partialWriteWithVerifiedBindingCanBeRecovered() throws Exception {
        var storage = new LocalFileSystemImageStorage(root);
        UUID id = UUID.randomUUID();
        var image = image(1);
        store(storage, id, image);
        Files.delete(root.resolve(id.toString()).resolve("content"));
        Files.write(root.resolve(id.toString()).resolve(".pending"), new byte[]{1, 2});
        assertEquals(id.toString(), store(storage, id, image).key());
        assertArrayEquals(image.content(), storage.read(id.toString()));
        assertFalse(Files.exists(root.resolve(id.toString()).resolve(".pending")));
    }

    @Test void cleanupRequiresCorrectBindingAndRetainsTerminalTombstone() throws Exception {
        var storage = new LocalFileSystemImageStorage(root);
        UUID id = UUID.randomUUID();
        var image = image(1);
        store(storage, id, image);
        assertThrows(ImageStorageException.class, () -> remove(storage, id, "0".repeat(64)));
        assertArrayEquals(image.content(), storage.read(id.toString()));
        remove(storage, id, digest(image.content()));
        remove(new LocalFileSystemImageStorage(root), id, digest(image.content()));
        assertEquals(ImageStorageException.Code.NOT_FOUND,
                assertThrows(ImageStorageException.class, () -> storage.read(id.toString())).getCode());
        assertThrows(ImageStorageException.class, () -> store(storage, id, image));
    }

    @Test void unboundDirectoryAndUnknownFilesAreNeverDeleted() throws Exception {
        var storage = new LocalFileSystemImageStorage(root);
        UUID unknown = UUID.randomUUID();
        Path unrelated = Files.createDirectory(root.resolve(unknown.toString()));
        Files.writeString(unrelated.resolve("content"), "unowned");
        assertThrows(ImageStorageException.class, () -> remove(storage, unknown, "0".repeat(64)));
        assertEquals("unowned", Files.readString(unrelated.resolve("content")));
        UUID managed = UUID.randomUUID();
        var image = image(3);
        store(storage, managed, image);
        Path extra = root.resolve(managed.toString()).resolve("operator-note");
        Files.writeString(extra, "keep");
        assertThrows(ImageStorageException.class, () -> remove(storage, managed, digest(image.content())));
        assertArrayEquals(image.content(), storage.read(managed.toString()));
        assertEquals("keep", Files.readString(extra));
    }

    @Test void malformedReservationIdCannotReachFilesystem() throws Exception {
        var storage = new LocalFileSystemImageStorage(root);
        assertThrows(ImageStorageException.class, () -> store(storage, new UUID(0, 0), image(1)));
        assertEquals(0, Files.list(root).count());
    }

    static ValidatedImage image(int color) throws Exception {
        var picture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        picture.setRGB(0, 0, color);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", bytes);
        return new ProductImageValidator().validate(new ImageInput("image.png", "image/png",
                new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static ImageStorage.StoredImage store(ImageStorage storage, UUID id, ValidatedImage image) throws Exception {
        return (ImageStorage.StoredImage) invoke(storage, "storeReserved",
                new Class<?>[]{UUID.class, ValidatedImage.class}, id, image);
    }

    private static void remove(ImageStorage storage, UUID id, String digest) throws Exception {
        invoke(storage, "deleteReserved", new Class<?>[]{UUID.class, String.class}, id, digest);
    }

    private static Object invoke(ImageStorage storage, String method, Class<?>[] types, Object... args) throws Exception {
        try { return ImageStorage.class.getMethod(method, types).invoke(storage, args); }
        catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException failure) throw failure;
            throw wrapped;
        }
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

