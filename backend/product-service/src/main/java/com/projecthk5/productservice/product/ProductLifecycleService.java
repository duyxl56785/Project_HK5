package com.projecthk5.productservice.product;

import com.projecthk5.productservice.category.CategoryRepository;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Not a Spring bean: verified policies and Product persistence are deliberately not wired yet. */
public class ProductLifecycleService {
    private final ProductAccessPolicy policy;
    private final CategoryRepository categories;

    public ProductLifecycleService(ProductAccessPolicy policy, CategoryRepository categories) {
        this.policy = Objects.requireNonNull(policy);
        this.categories = Objects.requireNonNull(categories);
    }

    public ProductListing create(String actor, UUID storeId, UUID categoryId, String sku, String name, BigDecimal price) {
        managedStore(actor, storeId);
        requireCategory(categoryId);
        return new ProductListing(UUID.randomUUID(), storeId, categoryId, sku, name, price, ProductStatus.DRAFT, null);
    }

    public ProductListing edit(String actor, ProductListing current, UUID categoryId, String name, BigDecimal price) {
        managedStore(actor, current.storeId());
        requireState(current, ProductStatus.DRAFT, ProductStatus.REJECTED, ProductStatus.INACTIVE);
        requireCategory(categoryId);
        return new ProductListing(current.id(), current.storeId(), categoryId, current.sku(), name, price,
                current.status(), current.reviewReason());
    }

    public ProductListing submit(String actor, ProductListing current) {
        managedStore(actor, current.storeId());
        requireState(current, ProductStatus.DRAFT, ProductStatus.REJECTED, ProductStatus.INACTIVE);
        requireCategory(current.categoryId());
        return current.withState(ProductStatus.PENDING_REVIEW, null);
    }

    public ProductListing approve(String actor, ProductListing current) {
        moderatedStore(actor, current.storeId());
        requireState(current, ProductStatus.PENDING_REVIEW);
        requireCategory(current.categoryId());
        return current.withState(ProductStatus.ACTIVE, null);
    }

    public ProductListing reject(String actor, ProductListing current, String reason) {
        moderatedStore(actor, current.storeId());
        requireState(current, ProductStatus.PENDING_REVIEW);
        return current.withState(ProductStatus.REJECTED, reason);
    }

    public ProductListing suspend(String actor, ProductListing current, String reason) {
        moderatedStore(actor, current.storeId());
        requireState(current, ProductStatus.ACTIVE);
        return current.withState(ProductStatus.SUSPENDED, reason);
    }

    public ProductListing deactivate(String actor, ProductListing current) {
        managedStore(actor, current.storeId());
        requireState(current, ProductStatus.ACTIVE);
        return current.withState(ProductStatus.INACTIVE, null);
    }

    public Optional<CatalogProduct> publicView(ProductListing current) {
        ProductAccessPolicy.StoreAccess store = store(policy.readStore(current.storeId()), current.storeId());
        if (current.status() != ProductStatus.ACTIVE || !store.sellerApproved() || !store.storeOpen()
                || !categories.existsById(current.categoryId())) {
            return Optional.empty();
        }
        return Optional.of(new CatalogProduct(current.id(), store.sellerId(), current.storeId(),
                current.categoryId(), current.sku(), current.name(), current.unitPrice(), store.houseStore()));
    }

    // Internal projection only, not an agreed CatalogPort or public HTTP DTO.
    public record CatalogProduct(UUID productId, UUID sellerId, UUID storeId, UUID categoryId,
                                 String sku, String name, BigDecimal unitPrice, boolean houseStore) { }

    private void managedStore(String actor, UUID storeId) {
        requireActor(actor);
        ProductAccessPolicy.StoreAccess store = store(policy.requireSellerManagement(actor, storeId), storeId);
        if (!actor.equals(store.ownerReference()) || !store.sellerApproved() || !store.storeOpen()) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Store is not manageable by actor");
        }
    }

    private void moderatedStore(String actor, UUID storeId) {
        requireActor(actor);
        ProductAccessPolicy.StoreAccess store = store(policy.requireModeration(actor, storeId), storeId);
        if (actor.equals(store.ownerReference())) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Self-moderation is forbidden");
        }
    }

    private ProductAccessPolicy.StoreAccess store(ProductAccessPolicy.StoreAccess store, UUID expectedId) {
        if (store == null || !store.storeId().equals(expectedId)) {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Authoritative store could not be resolved");
        }
        return store;
    }

    private void requireActor(String actor) {
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 128) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Actor is required");
        }
    }

    private void requireCategory(UUID id) {
        if (id == null) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Category is required");
        }
        if (!categories.existsById(id)) {
            throw new ProductRuleException(ProductRuleException.Code.CATEGORY_NOT_FOUND, "Category does not exist");
        }
    }

    private void requireState(ProductListing current, ProductStatus... allowed) {
        for (ProductStatus state : allowed) {
            if (current.status() == state) return;
        }
        throw new ProductRuleException(ProductRuleException.Code.INVALID_STATE, "Product transition is not allowed");
    }
}
