package com.projecthk5.sellerservice.store;

import java.util.UUID;

/** Internal immutable model; neither a database schema nor a shared HTTP DTO. */
public record StoreProfile(UUID id, UUID sellerId, String ownerReference, String name, String slug, boolean open) {
    public StoreProfile {
        if (id == null || sellerId == null || ownerReference == null || ownerReference.isBlank()
                || ownerReference.codePointCount(0, ownerReference.length()) > 128 || name == null || name.isBlank()
                || name.strip().length() > 255 || slug == null || slug.length() > 128
                || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Invalid store details");
        }
        name = name.strip();
    }
}
