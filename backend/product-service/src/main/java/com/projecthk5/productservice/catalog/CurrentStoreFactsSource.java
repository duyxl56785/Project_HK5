package com.projecthk5.productservice.catalog;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private trusted dependency, not an HTTP contract or a production authority implementation. */
public interface CurrentStoreFactsSource {
    /**
     * Load current authoritative Store/Seller/House facts for this invocation, without stale-cache fallback.
     * A verified adapter must attest current only after a current read; this is not a lease through Checkout.
     * Missing/unknown House or unavailable/unverified dependencies must not be represented as false.
     */
    List<StoreFacts> loadCurrent(Set<UUID> storeIds);

    // ownerReference is private input required by the existing internal StoreAccess contract, never a consumer field.
    record StoreFacts(UUID storeId, UUID sellerId, String ownerReference, Boolean sellerApproved,
                      Boolean storeOpen, Boolean houseStore, boolean current) { }
}
