package com.projecthk5.sellerservice.seller.domain;

// Internal opaque reference; the Auth adapter must define its verified source with TV1.
public record SellerIdentity(String reference) {
    public SellerIdentity {
        if (reference == null || reference.isBlank()) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT,
                    "Seller identity must be present");
        }
    }
}
