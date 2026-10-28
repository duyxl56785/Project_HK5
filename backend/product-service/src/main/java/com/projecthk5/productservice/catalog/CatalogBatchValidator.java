package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.catalog.CatalogSnapshotSource.ResolvedProduct;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.LookupResult;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.LookupStatus;
import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal batch proposal: deliberately not a Spring bean/controller or an approved shared DTO. */
public class CatalogBatchValidator {
    public static final int MAX_ITEMS = 100;
    private final CatalogSnapshotSource source;

    public CatalogBatchValidator(CatalogSnapshotSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public enum ItemCode { READY, PRODUCT_NOT_FOUND, PRODUCT_NOT_SELLABLE, OWNERSHIP_MISMATCH, PRICE_CHANGED, CURRENCY_MISMATCH }
    public record ExpectedItem(UUID productId, UUID expectedStoreId, UUID expectedSellerId,
                               BigDecimal expectedUnitPrice, String expectedCurrency) { }
    public record Snapshot(UUID productId, String inventorySku, UUID storeId, UUID sellerId,
                           BigDecimal unitPrice, String currency, boolean available,
                           boolean sellerApproved, boolean storeOpen, boolean houseStore) { }
    public record ItemResult(UUID productId, ItemCode code, Snapshot snapshot) { }
    public record Result(List<ItemResult> items) {
        public Result {
            items = List.copyOf(items);
            // A rejected batch must not supply a usable READY subset.
            if (items.stream().anyMatch(item -> item.code() != ItemCode.READY)) {
                items = items.stream().map(item -> new ItemResult(item.productId(), item.code(), null)).toList();
            }
        }
        public boolean valid() { return items.stream().allMatch(item -> item.code() == ItemCode.READY); }
    }

    public Result validate(List<ExpectedItem> expected) {
        List<ExpectedItem> items = checkedItems(expected);
        Set<UUID> ids = new LinkedHashSet<>();
        items.forEach(item -> ids.add(item.productId()));
        Map<UUID, LookupResult> products = load(ids);
        List<ItemResult> results = new ArrayList<>();
        for (ExpectedItem item : items) {
            LookupResult lookup = products.get(item.productId());
            ResolvedProduct facts = lookup.facts();
            ItemCode code = lookup.status() == LookupStatus.ABSENT ? ItemCode.PRODUCT_NOT_FOUND : outcome(item, facts);
            Snapshot snapshot = null;
            if (code == ItemCode.READY) {
                snapshot = new Snapshot(facts.product().id(), facts.product().sku(), facts.store().storeId(),
                        facts.store().sellerId(), facts.product().unitPrice(), facts.currency(),
                        true, true, true, facts.store().houseStore());
            }
            results.add(new ItemResult(item.productId(), code, snapshot));
        }
        return new Result(results);
    }

    private List<ExpectedItem> checkedItems(List<ExpectedItem> expected) {
        if (expected == null || expected.isEmpty() || expected.size() > MAX_ITEMS) throw invalid();
        Set<UUID> seen = new HashSet<>();
        List<ExpectedItem> result = new ArrayList<>();
        for (ExpectedItem item : expected) {
            if (item == null || item.productId() == null || item.expectedStoreId() == null
                    || item.expectedSellerId() == null || item.expectedUnitPrice() == null
                    || item.expectedUnitPrice().signum() <= 0 || item.expectedCurrency() == null
                    || item.expectedCurrency().isBlank() || !seen.add(item.productId())) throw invalid();
            result.add(item);
        }
        return result;
    }

    private Map<UUID, LookupResult> load(Set<UUID> ids) {
        List<LookupResult> loaded;
        try {
            loaded = source.load(Set.copyOf(ids));
        } catch (RuntimeException unavailable) {
            throw unavailable();
        }
        if (loaded == null) throw unavailable();
        Map<UUID, LookupResult> products = new HashMap<>();
        Map<UUID, StoreAccess> stores = new HashMap<>();
        Map<UUID, UUID> sellerStores = new HashMap<>();
        Set<String> skus = new HashSet<>();
        for (LookupResult lookup : loaded) {
            if (lookup == null || lookup.status() == null || lookup.status() == LookupStatus.UNAVAILABLE
                    || !ids.contains(lookup.productId())
                    || products.putIfAbsent(lookup.productId(), lookup) != null) throw unavailable();
            ResolvedProduct facts = lookup.facts();
            if (lookup.status() == LookupStatus.ABSENT) {
                if (facts != null) throw unavailable();
                continue;
            }
            if (facts == null || facts.product() == null || facts.store() == null || facts.currency() == null
                    || facts.currency().isBlank() || !facts.currency().equals(facts.product().currency())
                    || !lookup.productId().equals(facts.product().id())
                    || !facts.product().storeId().equals(facts.store().storeId())
                    || !skus.add(facts.product().sku())) throw unavailable();
            StoreAccess previous = stores.putIfAbsent(facts.store().storeId(), facts.store());
            if (previous != null && !previous.equals(facts.store())) throw unavailable();
            // S01 permits one Store per Seller; contradictory source facts invalidate the batch.
            UUID previousStore = sellerStores.putIfAbsent(facts.store().sellerId(), facts.store().storeId());
            if (previousStore != null && !previousStore.equals(facts.store().storeId())) throw unavailable();
        }
        if (!products.keySet().equals(ids)) throw unavailable();
        return products;
    }

    private ItemCode outcome(ExpectedItem item, ResolvedProduct facts) {
        if (facts.product().status() != ProductStatus.ACTIVE || !facts.store().sellerApproved()
                || !facts.store().storeOpen() || !facts.categoryExists()) return ItemCode.PRODUCT_NOT_SELLABLE;
        if (!facts.store().storeId().equals(item.expectedStoreId())
                || !facts.store().sellerId().equals(item.expectedSellerId())) return ItemCode.OWNERSHIP_MISMATCH;
        if (!facts.currency().equals(item.expectedCurrency())) return ItemCode.CURRENCY_MISMATCH;
        if (facts.product().unitPrice().compareTo(item.expectedUnitPrice()) != 0) return ItemCode.PRICE_CHANGED;
        return ItemCode.READY;
    }

    private CatalogBatchException invalid() {
        return new CatalogBatchException(CatalogBatchException.Code.INVALID_REQUEST,
                "Batch must contain 1-100 distinct products with complete expected snapshots");
    }

    private CatalogBatchException unavailable() {
        return new CatalogBatchException(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE,
                "Authoritative catalog facts could not be verified");
    }
}
