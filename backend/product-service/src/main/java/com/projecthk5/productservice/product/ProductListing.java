package com.projecthk5.productservice.product;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable internal model. Store boundary, persistence and wire contracts still require review. */
public record ProductListing(UUID id, UUID storeId, UUID categoryId, String sku, String name,
                             BigDecimal unitPrice, String currency, ProductStatus status, String reviewReason) {
    public ProductListing {
        if (id == null || storeId == null || categoryId == null || status == null
                || sku == null || sku.isBlank() || sku.length() > 128 || hasBoundaryWhitespace(sku)
                || hasInvalidUnicode(sku) || name == null || name.isBlank() || name.strip().length() > 255
                || hasInvalidUnicode(name)) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid product details");
        }
        ProductPrice.validate(unitPrice, currency);
        name = name.strip();
        boolean needsReason = status == ProductStatus.REJECTED || status == ProductStatus.SUSPENDED;
        if (needsReason && (reviewReason == null || reviewReason.isBlank()
                || reviewReason.codePointCount(0, reviewReason.length()) > 500 || hasInvalidUnicode(reviewReason))
                || !needsReason && reviewReason != null) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid moderation reason");
        }
        reviewReason = reviewReason == null ? null : reviewReason.strip();
    }

    private static boolean hasBoundaryWhitespace(String sku) {
        int first = sku.codePointAt(0), last = sku.codePointBefore(sku.length());
        return Character.isWhitespace(first) || Character.isSpaceChar(first)
                || Character.isWhitespace(last) || Character.isSpaceChar(last);
    }

    private static boolean hasInvalidUnicode(String sku) {
        for (int index = 0; index < sku.length(); index++) {
            char value = sku.charAt(index);
            if (value == 0 || Character.isLowSurrogate(value)) return true;
            if (Character.isHighSurrogate(value)) {
                if (index + 1 == sku.length() || !Character.isLowSurrogate(sku.charAt(index + 1))) return true;
                index++;
            }
        }
        return false;
    }

    ProductListing withState(ProductStatus next, String reason) {
        return new ProductListing(id, storeId, categoryId, sku, name, unitPrice, currency, next, reason);
    }
}
