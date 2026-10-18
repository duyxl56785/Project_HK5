package com.projecthk5.sellerservice.seller.service;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;

/** Server-side Auth integration point. Implementations must throw on denied/unverified access. */
public interface SellerAccessPolicy {
    void requireAuthenticated(SellerIdentity actor);

    void requireSellerModerationPermission(SellerIdentity actor);
}
