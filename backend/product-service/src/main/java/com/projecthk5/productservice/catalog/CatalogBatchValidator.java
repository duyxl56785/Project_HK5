package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.catalog.CatalogSnapshotSource.ResolvedProduct;
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
        public Result { items = List.copyOf(items); }
        public boolean valid() { return items.stream().allMatch(item -> item.code() == ItemCode.READY); }
    }

    public Result validate(List<ExpectedItem> expected) {
        List<ExpectedItem> items = checkedItems(expected);
        Set<UUID> ids = new LinkedHashSet<>();
        items.forEach(item -> ids.add(item.productId()));
        Map<UUID, ResolvedProduct> products = load(ids);
        List<ItemResult> results = new ArrayList<>();
        for (ExpectedItem item : items) {
            ResolvedProduct facts = products.get(item.productId());
            ItemCode code = outcome(item, facts);
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

    private Map<UUID, ResolvedProduct> load(Set<UUID> ids) {
        List<ResolvedProduct> loaded;
        try {
            loaded = source.load(Set.copyOf(ids));
        } catch (RuntimeException unavailable) {
            throw unavailable();
        }
        if (loaded == null) throw unavailable();
        Map<UUID, ResolvedProduct> products = new HashMap<>();
        Set<String> skus = new HashSet<>();
        for (ResolvedProduct facts : loaded) {
            if (facts == null || facts.product() == null || facts.store() == null || facts.currency() == null
                    || facts.currency().isBlank() || !ids.contains(facts.product().id())
                    || !facts.product().storeId().equals(facts.store().storeId())
                    || products.putIfAbsent(facts.product().id(), facts) != null
                    || !skus.add(facts.product().sku())) throw unavailable();
        }
        return products;
    }

    private ItemCode outcome(ExpectedItem item, ResolvedProduct facts) {
        if (facts == null) return ItemCode.PRODUCT_NOT_FOUND;
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
