package com.projecthk5.sellerservice.store;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;

/** Must bind actor to verified SELLER principal and load its current persisted seller. No default policy. */
public interface StoreAccessPolicy {
    PersistedSeller requireSellerManagement(SellerIdentity actor);
}
