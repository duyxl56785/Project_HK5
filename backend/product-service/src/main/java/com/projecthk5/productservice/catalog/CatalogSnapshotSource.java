package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.product.ProductListing;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Trusted data boundary only; no production adapter, fixture fallback or HTTP contract. */
public interface CatalogSnapshotSource {
    List<ResolvedProduct> load(Set<UUID> productIds);

    // Currency/status/ownership facts must come from verified provider storage/dependencies.
    record ResolvedProduct(ProductListing product, StoreAccess store, String currency, boolean categoryExists) { }
}
