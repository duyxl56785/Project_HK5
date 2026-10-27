package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.catalog.CurrentStoreFactsSource.StoreFacts;
import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal data source only; deliberately unregistered until service identity/current-authority gates are met. */
public class ProductCatalogSnapshotSource implements CatalogSnapshotSource {
    private final ProductRepository products;
    private final ObjectProvider<CurrentStoreFactsSource> stores;

    public ProductCatalogSnapshotSource(ProductRepository products, ObjectProvider<CurrentStoreFactsSource> stores) {
        this.products = Objects.requireNonNull(products);
        this.stores = Objects.requireNonNull(stores);
    }

    @Override
    public List<LookupResult> load(Set<UUID> productIds) {
        Set<UUID> ids = checkedIds(productIds);
        // An ambient transaction must explicitly prove READ_COMMITTED, never a retained/unknown snapshot.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        TransactionDefinition.ISOLATION_READ_COMMITTED)) return unavailable(ids);

        Map<UUID, LocalProduct> found = new HashMap<>();
        try {
            List<ProductRepository.CatalogRow> rows = products.findCatalogRows(ids);
            verified(rows != null);
            for (ProductRepository.CatalogRow row : rows) {
                verified(row != null && ids.contains(row.getId()) && row.getCategoryExists() != null);
                ProductListing product = new ProductListing(row.getId(), row.getStoreId(), row.getCategoryId(),
                        row.getInventorySku(), row.getName(), row.getUnitPrice(), row.getCurrency(),
                        row.getStatus(), row.getReviewReason());
                verified(found.putIfAbsent(product.id(), new LocalProduct(product, row.getCategoryExists())) == null);
            }
        } catch (RuntimeException unverifiedDatabaseRead) {
            // A failed or inconsistent Product read proves no absences.
            return unavailable(ids);
        }
        if (found.isEmpty()) return ids.stream().map(id -> new LookupResult(id, LookupStatus.ABSENT, null)).toList();

        Map<UUID, StoreAccess> verifiedStores;
        try {
            CurrentStoreFactsSource dependency = stores.getIfUnique();
            verified(dependency != null);
            Set<UUID> storeIds = found.values().stream().map(p -> p.product().storeId()).collect(Collectors.toUnmodifiableSet());
            verifiedStores = currentStores(storeIds, dependency.loadCurrent(storeIds));
        } catch (RuntimeException unverifiedDependencies) {
            // Preserve only absences confirmed by the successful Product query; no partial trusted FOUND subset.
            return ids.stream().map(id -> new LookupResult(id,
                    found.containsKey(id) ? LookupStatus.UNAVAILABLE : LookupStatus.ABSENT, null)).toList();
        }
        return ids.stream().map(id -> {
            LocalProduct row = found.get(id);
            if (row == null) return new LookupResult(id, LookupStatus.ABSENT, null);
            return new LookupResult(id, LookupStatus.FOUND, new ResolvedProduct(row.product(),
                    verifiedStores.get(row.product().storeId()), row.product().currency(), row.categoryExists()));
        }).toList();
    }

    private Map<UUID, StoreAccess> currentStores(Set<UUID> ids, List<StoreFacts> loaded) {
        verified(loaded != null);
        Map<UUID, StoreAccess> result = new HashMap<>();
        Map<UUID, UUID> sellerStores = new HashMap<>();
        for (StoreFacts fact : loaded) {
            verified(fact != null && ids.contains(fact.storeId()) && fact.current()
                    && fact.sellerApproved() != null && fact.storeOpen() != null && fact.houseStore() != null);
            StoreAccess store = new StoreAccess(fact.storeId(), fact.sellerId(), fact.ownerReference(),
                    fact.sellerApproved(), fact.storeOpen(), fact.houseStore());
            verified(result.putIfAbsent(store.storeId(), store) == null);
            UUID previousStore = sellerStores.putIfAbsent(store.sellerId(), store.storeId());
            verified(previousStore == null || previousStore.equals(store.storeId()));
        }
        verified(result.keySet().equals(ids));
        return result;
    }

    private Set<UUID> checkedIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > CatalogBatchValidator.MAX_ITEMS || ids.stream().anyMatch(Objects::isNull)) {
            throw new CatalogBatchException(CatalogBatchException.Code.INVALID_REQUEST,
                    "Batch must contain 1-100 nonnull Product IDs");
        }
        return Set.copyOf(ids);
    }

    private List<LookupResult> unavailable(Set<UUID> ids) {
        return ids.stream().map(id -> new LookupResult(id, LookupStatus.UNAVAILABLE, null)).toList();
    }

    private static void verified(boolean condition) {
        if (!condition) throw new IllegalStateException("Current authoritative Catalog facts are incomplete");
    }

    private record LocalProduct(ProductListing product, boolean categoryExists) { }
}
