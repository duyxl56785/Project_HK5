package com.projecthk5.sellerservice.store;

import java.util.UUID;

/** Internal immutable model; neither a database schema nor a shared HTTP DTO. */
public record StoreProfile(UUID id, UUID sellerId, String ownerReference, String name, String slug, boolean open) {
    public StoreProfile {
        if (id == null || sellerId == null || ownerReference == null || ownerReference.isBlank()
                || ownerReference.codePointCount(0, ownerReference.length()) > 128) {
            throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Invalid store details");
        }
        var details = new StoreDetails(name, slug);
        name = details.name();
        slug = details.slug();
    }
}
