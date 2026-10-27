package com.projecthk5.productservice.product;

import java.util.UUID;

/** Trusted adapter boundary; no production implementation or default allow policy is provided. */
public interface ProductAccessPolicy {
    /** Resolve from verified current account/roles and Seller ownership, never a requested Product or client Store ID. */
    default UUID currentManagementStore(String actorReference) {
        throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                "Verified current management Store is not configured");
    }

    // Bind actor to verified principal and role, then load current authoritative Store/Seller facts.
    StoreAccess requireSellerManagement(String actorReference, UUID storeId);
    StoreAccess requireModeration(String actorReference, UUID storeId);
    StoreAccess readStore(UUID storeId);

    record StoreAccess(UUID storeId, UUID sellerId, String ownerReference,
                       boolean sellerApproved, boolean storeOpen, boolean houseStore) {
        public StoreAccess {
            if (storeId == null || sellerId == null || ownerReference == null || ownerReference.isBlank()) {
                throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                        "Authoritative store identity is required");
            }
        }
    }
}
