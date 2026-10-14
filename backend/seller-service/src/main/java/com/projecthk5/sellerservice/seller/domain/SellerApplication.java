package com.projecthk5.sellerservice.seller.domain;

// Immutable domain snapshot, not a JPA entity, request DTO or approved persistence schema.
public record SellerApplication(SellerIdentity owner, SellerStatus status, String reviewReason) {
    public SellerApplication {
        if (owner == null || status == null) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT,
                    "Seller owner and status must be present");
        }
        if ((status == SellerStatus.REJECTED || status == SellerStatus.SUSPENDED)
                && (reviewReason == null || reviewReason.isBlank())) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT,
                    "Rejected or suspended seller requires a review reason");
        }
        reviewReason = reviewReason == null ? null : reviewReason.strip();
    }
}
