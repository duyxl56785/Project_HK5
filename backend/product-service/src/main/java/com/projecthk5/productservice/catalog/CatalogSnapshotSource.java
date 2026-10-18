package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.product.ProductListing;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Trusted data boundary only; no production adapter, fixture fallback or HTTP contract. */
public interface CatalogSnapshotSource {
    // Exactly one result per requested ID. Omission is not authoritative absence.
    List<LookupResult> load(Set<UUID> productIds);

    enum LookupStatus { FOUND, ABSENT, UNAVAILABLE }

    // FOUND requires current verified facts; ABSENT requires a verified Product lookup.
    // Stale, partial, cached without freshness proof, or unverified dependencies use UNAVAILABLE.
    record LookupResult(UUID productId, LookupStatus status, ResolvedProduct facts) { }

    // Currency/status/ownership facts must come from verified provider storage/dependencies.
    record ResolvedProduct(ProductListing product, StoreAccess store, String currency, boolean categoryExists) { }
}
