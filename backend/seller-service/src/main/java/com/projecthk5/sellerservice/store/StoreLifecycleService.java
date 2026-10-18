package com.projecthk5.sellerservice.store;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import java.util.Objects;
import java.util.UUID;

/** Internal domain operations only; not registered as a production bean without an approved adapter. */
public class StoreLifecycleService {
    private final StoreAccessPolicy policy;

    public StoreLifecycleService(StoreAccessPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public StoreProfile create(SellerIdentity actor, String name, String slug) {
        PersistedSeller seller = seller(actor);
        return new StoreProfile(UUID.randomUUID(), seller.id(), seller.application().owner().reference(),
                name, slug, true);
    }

    public StoreProfile revise(StoreProfile current, SellerIdentity actor, String name, String slug) {
        requireOwner(current, actor);
        return new StoreProfile(current.id(), current.sellerId(), current.ownerReference(), name, slug, current.open());
    }

    public StoreProfile setOpen(StoreProfile current, SellerIdentity actor, boolean open) {
        requireOwner(current, actor);
        return new StoreProfile(current.id(), current.sellerId(), current.ownerReference(), current.name(),
                current.slug(), open);
    }

    private void requireOwner(StoreProfile current, SellerIdentity actor) {
        PersistedSeller seller = seller(actor);
        if (current == null || !current.sellerId().equals(seller.id())
                || !current.ownerReference().equals(actor.reference())) {
            throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Store belongs to another seller");
        }
    }

    private PersistedSeller seller(SellerIdentity actor) {
        if (actor == null) {
            throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Actor is required");
        }
        PersistedSeller seller = policy.requireSellerManagement(actor);
        if (seller == null || seller.id() == null || seller.application() == null) {
            throw new StoreRuleException(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Verified seller could not be resolved");
        }
        if (!seller.application().owner().equals(actor)) {
            throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Seller identity mismatch");
        }
        if (seller.application().status() != SellerStatus.APPROVED) {
            throw new StoreRuleException(StoreRuleException.Code.SELLER_NOT_APPROVED, "Seller must be approved");
        }
        return seller;
    }
}
