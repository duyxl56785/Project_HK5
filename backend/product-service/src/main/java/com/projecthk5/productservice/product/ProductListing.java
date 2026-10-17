package com.projecthk5.productservice.product;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable internal model. Store boundary, persistence and wire contracts still require review. */
public record ProductListing(UUID id, UUID storeId, UUID categoryId, String sku, String name,
                             BigDecimal unitPrice, ProductStatus status, String reviewReason) {
    public ProductListing {
        if (id == null || storeId == null || categoryId == null || status == null
                || sku == null || sku.isBlank() || sku.length() > 128 || !sku.equals(sku.strip())
                || name == null || name.isBlank() || name.strip().length() > 255
                || unitPrice == null || unitPrice.signum() <= 0) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid product details");
        }
        name = name.strip();
        boolean needsReason = status == ProductStatus.REJECTED || status == ProductStatus.SUSPENDED;
        if (needsReason && (reviewReason == null || reviewReason.isBlank()
                || reviewReason.codePointCount(0, reviewReason.length()) > 500)
                || !needsReason && reviewReason != null) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid moderation reason");
        }
        reviewReason = reviewReason == null ? null : reviewReason.strip();
    }

    ProductListing withState(ProductStatus next, String reason) {
        return new ProductListing(id, storeId, categoryId, sku, name, unitPrice, next, reason);
    }
}
