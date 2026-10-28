package com.projecthk5.sellerservice.http;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.store.StorePersistenceService;
import java.util.Objects;

/** Own-read preparation only: deliberately not a Spring component or HTTP endpoint. */
public final class SellerStoreReadHandler {
    private final SellerStoreReadAuthority authority;
    private final SellerPersistenceService sellers;
    private final StorePersistenceService stores;

    public SellerStoreReadHandler(SellerStoreReadAuthority authority, SellerPersistenceService sellers,
                                  StorePersistenceService stores) {
        this.authority = Objects.requireNonNull(authority);
        this.sellers = Objects.requireNonNull(sellers);
        this.stores = Objects.requireNonNull(stores);
    }

    public SellerStoreResponses.SellerPrivate sellerOwn() {
        SellerIdentity actor = requireIdentity(authority.requireCurrentAccount());
        return SellerStoreResponses.sellerPrivate(sellers.findOwn(actor));
    }

    public SellerStoreResponses.StoreOwner storeOwn() {
        SellerIdentity actor = requireIdentity(authority.requireCurrentSeller());
        return SellerStoreResponses.storeOwner(stores.findOwn(actor));
    }

    static SellerIdentity requireIdentity(SellerIdentity actor) {
        if (actor == null || actor.reference() == null || actor.reference().isBlank()
                || actor.reference().codePointCount(0, actor.reference().length()) > 128) {
            // Invalid trusted output is unavailable authority, never anonymous/empty success.
            throw new IllegalStateException("Current verified Seller/Store authority is unavailable");
        }
        return actor; // Preserve the verified subject exactly; do not parse, trim or normalize it.
    }
}
