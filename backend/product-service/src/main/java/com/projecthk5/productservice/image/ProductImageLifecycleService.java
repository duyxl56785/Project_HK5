package com.projecthk5.productservice.image;

import com.projecthk5.productservice.image.ProductImageMetadataService.Reservation;
import com.projecthk5.productservice.image.ProductImageValidator.ImageInput;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ProductImageLifecycleService {
    private final ProductImageMetadataService metadata;
    private final ImageStorage storage;
    private final ProductImageValidator validator;

    public ProductImageLifecycleService(ProductImageMetadataService metadata, ImageStorage storage,
                                        ProductImageValidator validator) {
        this.metadata = Objects.requireNonNull(metadata);
        this.storage = Objects.requireNonNull(storage);
        this.validator = Objects.requireNonNull(validator);
    }

    public Reservation upload(String actor, UUID productId, long expectedProductVersion,
                              UUID operationId, ImageInput input) {
        outsideTransaction();
        metadata.list(actor, productId);
        var image = validator.validate(input);
        var pending = metadata.reserve(actor, productId, expectedProductVersion, operationId, image);
        if ("CANCELLED".equals(pending.state())) return pending;
        return metadata.completeStorage(actor, productId, pending.productVersion(), pending.id(),
                pending.version(), image, storage);
    }

    public byte[] read(String actor, UUID productId, UUID imageId) {
        outsideTransaction();
        return metadata.readStored(actor, productId, imageId, storage);
    }

    public Reservation cancel(String actor, UUID productId, long expectedProductVersion,
                              UUID imageId, long expectedImageVersion) {
        outsideTransaction();
        var cancelled = metadata.cancel(actor, productId, expectedProductVersion, imageId, expectedImageVersion);
        metadata.cleanupCancelled(actor, productId, imageId, storage);
        return cancelled;
    }

    private static void outsideTransaction() {
        // REQUIRED stages must commit before filesystem work; an ambient transaction breaks recovery.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ProductImageMetadataException(ProductImageMetadataException.Code.INVALID_INPUT,
                    "Image lifecycle must run outside an ambient transaction");
        }
    }
}
