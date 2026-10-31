package com.projecthk5.productservice.image;

import static com.projecthk5.productservice.image.ProductImageMetadataException.Code.*;

import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import com.projecthk5.productservice.image.persistence.ProductImageMetadataEntity;
import com.projecthk5.productservice.image.persistence.ProductImageMetadataRepository;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductEntity;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable, owner-scoped reservations only. No file is stored or publicly readable by this service. */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class ProductImageMetadataService {
    private final ProductImageMetadataRepository images;
    private final EntityManager entityManager;
    private final ObjectProvider<ProductAccessPolicy> policies;

    public ProductImageMetadataService(ProductImageMetadataRepository images, EntityManager entityManager,
                                       ObjectProvider<ProductAccessPolicy> policies) {
        this.images = images;
        this.entityManager = entityManager;
        this.policies = policies;
    }

    /** Internal projection: neither private storage keys nor owner references leave this boundary. */
    public record Reservation(UUID id, UUID productId, UUID operationId, String mediaType, int sizeBytes,
                              int width, int height, Integer displayOrder, String state, Instant createdAt,
                              Instant cancelledAt, long version, long productVersion) { }

    public Reservation reserve(String actor, UUID productId, long expectedProductVersion,
                               UUID operationId, ValidatedImage image) {
        if (operationId == null || image == null || expectedProductVersion < 0) {
            throw failure(INVALID_INPUT, "Operation, validated image and nonnegative Product version are required");
        }
        // ValidatedImage can only be constructed by the existing content validator.
        String digest = digest(image.content());
        return database(() -> {
            ProductEntity product = ownedProduct(actor, productId, true);
            var previous = images.findByProductIdAndOperationId(productId, operationId);
            if (previous.isPresent()) {
                var existing = previous.get();
                entityManager.refresh(existing);
                if (!digest.equals(existing.getContentSha256()) || !image.format().contentType().equals(existing.getMediaType())
                        || image.size() != existing.getSizeBytes() || image.width() != existing.getWidth()
                        || image.height() != existing.getHeight()) {
                    throw failure(OPERATION_CONFLICT, "Operation was already used for another image");
                }
                // A cancelled operation remains cancelled; retry never creates a new upload.
                return projection(existing, product);
            }
            mutable(product, expectedProductVersion);
            if (images.findByProductIdAndContentSha256AndState(productId, digest, "PENDING_STORAGE").isPresent()) {
                throw failure(DUPLICATE_IMAGE, "Image already has a pending reservation");
            }
            boolean[] occupied = new boolean[ProductImageValidator.MAX_IMAGES + 1];
            images.findAllByProductIdAndStateOrderByDisplayOrderAsc(productId, "PENDING_STORAGE").stream()
                    .forEach(row -> occupied[row.getDisplayOrder()] = true);
            int slot = 1;
            while (slot <= ProductImageValidator.MAX_IMAGES && occupied[slot]) slot++;
            if (slot > ProductImageValidator.MAX_IMAGES) throw failure(LIMIT_EXCEEDED, "Product has five image reservations");
            var row = images.saveAndFlush(ProductImageMetadataEntity.pending(UUID.randomUUID(), productId,
                    operationId, digest, image.format().contentType(), image.size(), image.width(), image.height(), slot, Instant.now()));
            changed(product);
            return projection(row, product);
        });
    }

    /** Cancels metadata only; it must never be presented as deletion of a stored file. */
    public Reservation cancel(String actor, UUID productId, long expectedProductVersion,
                              UUID imageId, long expectedImageVersion) {
        if (imageId == null || expectedProductVersion < 0 || expectedImageVersion < 0) {
            throw failure(INVALID_INPUT, "Image and nonnegative versions are required");
        }
        return database(() -> {
            ProductEntity product = ownedProduct(actor, productId, true);
            var row = images.findById(imageId).filter(value -> productId.equals(value.getProductId()))
                    .orElseThrow(() -> failure(NOT_FOUND, "Image reservation was not found"));
            entityManager.refresh(row);
            if ("CANCELLED".equals(row.getState())) return projection(row, product);
            mutable(product, expectedProductVersion);
            if (row.getVersion() != expectedImageVersion) throw failure(STALE_VERSION, "Image reservation changed");
            row.cancel(Instant.now());
            images.flush();
            changed(product);
            return projection(row, product);
        });
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<Reservation> list(String actor, UUID productId) {
        return database(() -> {
            ProductEntity product = ownedProduct(actor, productId, false);
            // Filter in SQL: an outer REQUIRED persistence context may still cache a cancelled row as pending.
            return images.findAllByProductIdAndStateOrderByDisplayOrderAsc(productId, "PENDING_STORAGE").stream()
                    .map(row -> projection(row, product)).toList();
        });
    }

    private ProductEntity ownedProduct(String actor, UUID productId, boolean lock) {
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 128) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Actor is required");
        }
        ProductAccessPolicy policy = policies.getIfUnique();
        if (policy == null) throw unavailableAuthority();
        UUID store = policy.currentManagementStore(actor);
        if (store == null) throw unavailableAuthority();
        var facts = policy.requireSellerManagement(actor, store);
        if (facts == null || !store.equals(facts.storeId())) throw unavailableAuthority();
        if (!actor.equals(facts.ownerReference()) || !facts.sellerApproved() || !facts.storeOpen()) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Store is not manageable by actor");
        }
        if (productId == null) throw failure(INVALID_INPUT, "Product is required");
        // Acquire the Product lock BEFORE reading reservations. Bind verified scope in SQL, retaining foreign/missing parity.
        var ids = entityManager.createNativeQuery("SELECT id FROM products WHERE id=:id AND store_id=:store"
                        + (lock ? " FOR UPDATE" : ""), UUID.class)
                .setParameter("id", productId).setParameter("store", store).getResultList();
        if (ids.isEmpty()) throw new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND, "Product was not found");
        ProductEntity product = entityManager.find(ProductEntity.class, productId);
        entityManager.refresh(product, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        return product;
    }

    private void changed(ProductEntity product) {
        product.backToDraftIfInactive();
        // Invalidate stale Product edit/submit snapshots as well as concurrent image commands.
        entityManager.lock(product, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
    }

    private static void mutable(ProductEntity product, long expectedVersion) {
        if (product.getVersion() != expectedVersion) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.STALE_VERSION, "Product changed since it was read");
        }
        if (product.getStatus() != ProductStatus.DRAFT && product.getStatus() != ProductStatus.REJECTED
                && product.getStatus() != ProductStatus.INACTIVE) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_STATE, "Product images cannot change in this state");
        }
    }

    private static Reservation projection(ProductImageMetadataEntity row, ProductEntity product) {
        return new Reservation(row.getId(), row.getProductId(), row.getOperationId(), row.getMediaType(),
                row.getSizeBytes(), row.getWidth(), row.getHeight(), row.getDisplayOrder(), row.getState(),
                row.getCreatedAt(), row.getCancelledAt(), row.getVersion(), product.getVersion());
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required by Java", impossible); }
    }

    private static ProductRuleException unavailableAuthority() {
        return new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Verified current Product authority is unavailable");
    }

    private static ProductImageMetadataException failure(ProductImageMetadataException.Code code, String message) {
        return new ProductImageMetadataException(code, message);
    }

    private static <T> T database(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DataAccessException cause) { throw translate(cause); }
        catch (PersistenceException cause) {
            DataAccessException translated = EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(cause);
            if (translated != null) throw translate(translated);
            throw new ProductImageMetadataException(UNAVAILABLE, "Image reservation persistence is unavailable", cause);
        }
    }

    private static ProductImageMetadataException translate(DataAccessException cause) {
        if (cause instanceof OptimisticLockingFailureException) return new ProductImageMetadataException(STALE_VERSION, "Image reservation changed", cause);
        if (cause instanceof DataIntegrityViolationException) return new ProductImageMetadataException(CONSTRAINT_VIOLATION, "Image reservation violates a constraint", cause);
        return new ProductImageMetadataException(UNAVAILABLE, "Image reservation persistence is unavailable", cause);
    }
}
