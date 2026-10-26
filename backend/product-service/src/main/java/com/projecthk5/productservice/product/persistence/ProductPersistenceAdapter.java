package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductRuleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal data adapter. Callers remain responsible for verified authorization and lifecycle policy. */
@Repository
@Transactional(propagation = Propagation.REQUIRED)
public class ProductPersistenceAdapter {
    private final ProductRepository repository;
    private final EntityManager entityManager;

    public ProductPersistenceAdapter(ProductRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public record StoredProduct(ProductListing listing, long version) { }

    public StoredProduct create(ProductListing listing) {
        require(listing != null, "Product is required");
        try {
            // A null @Version makes this a persist, even with an application-assigned UUID.
            // A reused ID must fail, rather than merging over an existing Product.
            return stored(repository.saveAndFlush(new ProductEntity(listing)));
        } catch (DataAccessException failure) {
            throw translate(failure);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<StoredProduct> findById(UUID id) {
        require(id != null, "Product ID is required");
        try {
            return repository.findById(id).map(ProductPersistenceAdapter::stored);
        } catch (DataAccessException failure) {
            throw translate(failure);
        }
    }

    /** Lifecycle decisions require the current row even if an outer REQUIRED caller cached an older entity. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<StoredProduct> findFreshById(UUID id) {
        require(id != null, "Product ID is required");
        try {
            Optional<ProductEntity> entity = repository.findById(id);
            entity.ifPresent(entityManager::refresh);
            return entity.map(ProductPersistenceAdapter::stored);
        } catch (EntityNotFoundException deleted) {
            return Optional.empty();
        } catch (PersistenceException failure) {
            DataAccessException translated = EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(failure);
            if (translated != null) throw translate(translated);
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Product persistence is unavailable", failure);
        } catch (DataAccessException failure) {
            throw translate(failure);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<StoredProduct> findBySku(String sku) {
        require(sku != null, "Inventory SKU is required");
        try {
            return repository.findByInventorySku(sku).map(ProductPersistenceAdapter::stored);
        } catch (DataAccessException failure) {
            throw translate(failure);
        }
    }

    public StoredProduct update(ProductListing listing, long expectedVersion) {
        require(listing != null && expectedVersion >= 0, "Product and nonnegative version are required");
        try {
            ProductEntity entity = repository.findById(listing.id()).orElseThrow(() ->
                    new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND, "Product was not found"));
            // A REQUIRED caller may have cached an older entity. Refresh before comparing even for no-op edits.
            entityManager.refresh(entity);
            if (entity.getVersion() == null || entity.getVersion() != expectedVersion) {
                throw new ProductPersistenceException(ProductPersistenceException.Code.STALE_VERSION,
                        "Product changed since it was read");
            }
            entity.apply(listing);
            // Flush inside REQUIRED so constraints/version failures roll back the caller's transaction too.
            return stored(repository.saveAndFlush(entity));
        } catch (EntityNotFoundException failure) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND,
                    "Product was not found", failure);
        } catch (PersistenceException failure) {
            DataAccessException translated = EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(failure);
            if (translated != null) throw translate(translated);
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Product persistence is unavailable", failure);
        } catch (DataAccessException failure) {
            throw translate(failure);
        }
    }

    private static StoredProduct stored(ProductEntity entity) {
        return new StoredProduct(entity.toDomain(), entity.getVersion());
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, message);
    }

    private static ProductPersistenceException translate(DataAccessException failure) {
        if (failure instanceof OptimisticLockingFailureException) {
            return new ProductPersistenceException(ProductPersistenceException.Code.STALE_VERSION,
                    "Product changed since it was read", failure);
        }
        if (failure instanceof DataIntegrityViolationException) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint) {
                    String name = constraint.getConstraintName();
                    if ("products_inventory_sku_uk".equals(name)) {
                        return new ProductPersistenceException(ProductPersistenceException.Code.DUPLICATE_SKU,
                                "Inventory SKU is already assigned", failure);
                    }
                    if ("products_category_fk".equals(name)) {
                        return new ProductPersistenceException(ProductPersistenceException.Code.CATEGORY_NOT_FOUND,
                                "Product Category was not found", failure);
                    }
                    if ("products_sku_immutable".equals(name)) {
                        return new ProductPersistenceException(ProductPersistenceException.Code.IMMUTABLE_IDENTITY,
                                "Inventory SKU cannot change", failure);
                    }
                }
            }
            return new ProductPersistenceException(ProductPersistenceException.Code.CONSTRAINT_VIOLATION,
                    "Product violates a persistence constraint", failure);
        }
        return new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                "Product persistence is unavailable", failure);
    }
}
