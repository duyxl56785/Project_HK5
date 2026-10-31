package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // Store belongs to seller-service; this is a logical reference, never a cross-service join.
    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "inventory_sku", nullable = false, updatable = false, columnDefinition = "text")
    private String inventorySku;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    // PostgreSQL NUMERIC has no typmod: preserve the supplied BigDecimal before CHECK evaluation.
    @Column(name = "unit_price", nullable = false, columnDefinition = "numeric")
    private BigDecimal unitPrice;

    @Column(nullable = false, columnDefinition = "text")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ProductStatus status;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProductEntity() { }

    public ProductEntity(ProductListing listing) {
        requireListing(listing);
        id = listing.id();
        storeId = listing.storeId();
        inventorySku = listing.sku();
        apply(listing);
    }

    public void apply(ProductListing listing) {
        requireListing(listing);
        if (!listing.id().equals(id) || !listing.storeId().equals(storeId) || !listing.sku().equals(inventorySku)) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.IMMUTABLE_IDENTITY,
                    "Product identity, Store and inventory SKU cannot change");
        }
        categoryId = listing.categoryId();
        name = listing.name();
        unitPrice = listing.unitPrice();
        currency = listing.currency();
        status = listing.status();
        reviewReason = listing.reviewReason();
    }

    /** New images on an INACTIVE Product are unreviewed content, so activation must not be able to skip review. */
    public void backToDraftIfInactive() {
        if (status == ProductStatus.INACTIVE) status = ProductStatus.DRAFT;
    }

    public ProductListing toDomain() {
        return new ProductListing(id, storeId, categoryId, inventorySku, name, unitPrice, currency, status, reviewReason);
    }

    private static void requireListing(ProductListing listing) {
        if (listing == null) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Product is required");
        }
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getCategoryId() { return categoryId; }
    public String getInventorySku() { return inventorySku; }
    public String getName() { return name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public ProductStatus getStatus() { return status; }
    public String getReviewReason() { return reviewReason; }
    public Long getVersion() { return version; }
}
