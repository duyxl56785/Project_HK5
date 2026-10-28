package com.projecthk5.sellerservice.http;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.store.StorePersistenceService;
import java.util.Objects;
import java.util.UUID;

public final class SellerStoreManagementHandler {
    private final SellerStoreReadAuthority authority;
    private final SellerPersistenceService sellers;
    private final StorePersistenceService stores;
    private final SellerStoreJson json;

    public SellerStoreManagementHandler(SellerStoreReadAuthority authority, SellerPersistenceService sellers,
                                        StorePersistenceService stores, SellerStoreJson json) {
        this.authority = Objects.requireNonNull(authority);
        this.sellers = Objects.requireNonNull(sellers);
        this.stores = Objects.requireNonNull(stores);
        this.json = Objects.requireNonNull(json);
    }

    public SellerStoreResponses.SellerPrivate register(String payload) {
        var actor = account();
        json.registration(payload);
        return SellerStoreResponses.sellerPrivate(sellers.register(actor));
    }

    public SellerStoreResponses.SellerPrivate resubmit(String payload) {
        var actor = account();
        var input = json.version(payload);
        var own = sellers.findOwn(actor);
        return SellerStoreResponses.sellerPrivate(sellers.resubmit(own.id(), input, actor));
    }

    public SellerStoreResponses.StoreOwner createStore(String payload) {
        var actor = seller();
        var input = json.createStore(payload);
        return SellerStoreResponses.storeOwner(stores.create(actor, input));
    }

    public SellerStoreResponses.StoreOwner updateStore(String payload) {
        var actor = seller();
        var input = json.updateStore(payload);
        var own = stores.findOwn(actor);
        return SellerStoreResponses.storeOwner(stores.revise(own.profile().id(), actor, input));
    }

    public SellerStoreResponses.StoreOwner setStoreOpen(String payload) {
        var actor = seller();
        var input = json.setOpen(payload);
        var own = stores.findOwn(actor);
        return SellerStoreResponses.storeOwner(stores.setOpen(own.profile().id(), actor, input));
    }

    public SellerStoreResponses.SellerPrivate sellerForModeration(UUID id) {
        return SellerStoreResponses.sellerPrivate(sellers.findForModeration(id, account()));
    }

    public SellerStoreResponses.SellerPrivate approve(UUID id, String payload) {
        var actor = account();
        return SellerStoreResponses.sellerPrivate(sellers.approve(id, json.version(payload), actor));
    }

    public SellerStoreResponses.SellerPrivate reject(UUID id, String payload) {
        var actor = account();
        return SellerStoreResponses.sellerPrivate(sellers.reject(id, json.moderation(payload), actor));
    }

    public SellerStoreResponses.SellerPrivate suspend(UUID id, String payload) {
        var actor = account();
        return SellerStoreResponses.sellerPrivate(sellers.suspend(id, json.moderation(payload), actor));
    }

    public SellerStoreResponses.SellerPrivate reinstate(UUID id, String payload) {
        var actor = account();
        return SellerStoreResponses.sellerPrivate(sellers.reinstate(id, json.version(payload), actor));
    }

    private SellerIdentity account() {
        return SellerStoreReadHandler.requireIdentity(authority.requireCurrentAccount());
    }

    private SellerIdentity seller() {
        return SellerStoreReadHandler.requireIdentity(authority.requireCurrentSeller());
    }
}
