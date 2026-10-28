package com.projecthk5.productservice.image;

import com.projecthk5.productservice.image.ProductImageValidator.Format;
import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import java.util.UUID;

/** Internal byte storage only. A key is not ownership, authorization or a public URL. */
public interface ImageStorage {
    record StoredImage(String key, Format format, int size, int width, int height) { }
    StoredImage store(ValidatedImage image);
    byte[] read(String key);
    default StoredImage storeReserved(UUID imageId, ValidatedImage image) {
        throw new ImageStorageException(ImageStorageException.Code.UNSAFE_STORAGE);
    }
    default void deleteReserved(UUID imageId, String contentSha256) {
        throw new ImageStorageException(ImageStorageException.Code.UNSAFE_STORAGE);
    }
}
