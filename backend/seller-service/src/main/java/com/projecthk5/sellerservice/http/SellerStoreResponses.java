package com.projecthk5.sellerservice.http;

import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.StorePersistenceService.PersistedStore;
import java.time.Instant;
import java.util.UUID;

/** Mapping only: call after the existing service has authorized the trusted principal and resource. */
public final class SellerStoreResponses {
    private SellerStoreResponses() { }
    public record SellerPrivate(UUID id, String version, SellerStatus status, String reviewReason) {
        public SellerPrivate {
            if (id == null || status == null) throw invalidSource();
            CanonicalVersion.parse(version);
        }
    }
    public record StoreOwner(UUID id, UUID sellerId, String name, String slug, boolean open,
                             String version, Instant createdAt, Instant updatedAt) {
        public StoreOwner {
            if (id == null || sellerId == null || name == null || slug == null
                    || createdAt == null || updatedAt == null) throw invalidSource();
            CanonicalVersion.parse(version);
        }
    }

    public static SellerPrivate sellerPrivate(PersistedSeller source) {
        if (source == null || source.application() == null) throw invalidSource();
        var application = source.application();
        return new SellerPrivate(source.id(), CanonicalVersion.format(source.version()),
                application.status(), application.reviewReason());
    }

    public static StoreOwner storeOwner(PersistedStore source) {
        if (source == null || source.profile() == null) throw invalidSource();
        var store = source.profile();
        return new StoreOwner(store.id(), store.sellerId(), store.name(), store.slug(), store.open(),
                CanonicalVersion.format(source.version()), source.createdAt(), source.updatedAt());
    }

    private static IllegalArgumentException invalidSource() {
        return new IllegalArgumentException("A complete authorized Seller/Store result is required");
    }
}
