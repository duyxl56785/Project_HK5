package com.projecthk5.orderservice.catalog;

import com.projecthk5.orderservice.domain.OrderAmountStorageBounds;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TV3 Order-side candidate for TV2's catalog.checkout.v1/v0.2 all-or-nothing
 * consumer preflight. PURE and deliberately NOT wired into CheckoutService:
 * TV2 Product->SKU persistence/provider, TV1 pair/delegation, M01 business
 * approval and freshness/read-to-commit semantics are still pending.
 *
 * Never convert productId into inventorySku. Only use the exact provider-owned
 * SKU after all authoritative Product/Store/Seller/currency fields match.
 * A ready batch is NOT authorization to reserve stock or proof that its
 * snapshot remains fresh through the eventual Order commit.
 */
public final class CatalogCheckoutBatchPreflight {
    public static final String CONTRACT_VERSION = "catalog.checkout.v1";
    public static final int MAX_BATCH_SIZE = 100;

    private CatalogCheckoutBatchPreflight() {}

    /** Cart expectations are for comparison, not authoritative Product facts. */
    public record Expected(String productId, String storeId, String sellerId,
                           BigDecimal unitPrice, String currency) {}

    /** Must be populated by TV2's trustworthy Product/Store source, not request headers. */
    public record VerifiedProduct(
            String productId, String inventorySku, String storeId, String sellerId,
            BigDecimal unitPrice, String currency, boolean available,
            boolean sellerApproved, boolean storeOpen, boolean houseStore) {}

    public record ItemResult(String productId, String code, VerifiedProduct snapshot) {}

    public record BatchResponse(String contractVersion, boolean allReady, List<ItemResult> items) {}

    /**
     * Throws before any Inventory/Order side effect if batch is incomplete,
     * contradictory, non-ready, untrusted, or financially unrepresentable.
     * No automatic partial selection or default currency is permitted.
     */
    public static List<VerifiedProduct> requireFullyReady(List<Expected> expectations,
                                                           BatchResponse response) {
        if (expectations == null || expectations.isEmpty() || expectations.size() > MAX_BATCH_SIZE)
            throw new IllegalArgumentException("Catalog batch must contain 1..100 Product IDs");
        Set<String> productIds = new HashSet<>();
        for (Expected e : expectations) {
            if (e == null) throw blocked("Missing expected Product");
            requireCanonical(e.productId(), "expected Product ID");
            requireCanonical(e.storeId(), "expected Store ID");
            requireCanonical(e.sellerId(), "expected Seller ID");
            requireCurrency(e.currency());
            if (e.unitPrice() == null) throw blocked("Missing expected currency/price");
            OrderAmountStorageBounds.requireExactNonnegative(e.unitPrice());
            if (!productIds.add(e.productId())) throw blocked("Duplicate expected Product ID");
        }

        if (response == null || !CONTRACT_VERSION.equals(response.contractVersion())
                || !response.allReady() || response.items() == null
                || response.items().size() != expectations.size())
            throw blocked("Catalog provider batch unavailable, wrong version or not all READY");

        Set<String> inventorySkus = new HashSet<>();
        java.util.ArrayList<VerifiedProduct> ready = new java.util.ArrayList<>(expectations.size());
        for (int i = 0; i < expectations.size(); i++) {
            Expected wanted = expectations.get(i);
            ItemResult item = response.items().get(i);
            // Explicit order coupling here is a V0.2 candidate. TV2 must confirm
            // response sequencing before this preflight is wired to live Checkout.
            if (item == null || !"READY".equals(item.code())
                    || !wanted.productId().equals(item.productId())
                    || item.snapshot() == null)
                throw blocked("Catalog response row not READY or out of request order");
            VerifiedProduct source = item.snapshot();
            if (!wanted.productId().equals(source.productId())
                    || !wanted.storeId().equals(source.storeId())
                    || !wanted.sellerId().equals(source.sellerId())
                    || source.unitPrice() == null || source.currency() == null
                    || !wanted.currency().equals(source.currency())
                    || wanted.unitPrice().compareTo(source.unitPrice()) != 0
                    || !source.available() || !source.sellerApproved() || !source.storeOpen())
                throw blocked("Authoritative Catalog snapshot changed, missing or ineligible");
            requireCanonical(source.inventorySku(), "authoritative Inventory SKU");
            if (source.inventorySku().length() > 128 || !inventorySkus.add(source.inventorySku()))
                throw blocked("Invalid or duplicate global Inventory SKU in Catalog batch");
            OrderAmountStorageBounds.requireExactNonnegative(source.unitPrice());
            ready.add(source);
        }
        return List.copyOf(ready);
    }

    private static void requireCanonical(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()))
            throw blocked("Missing or noncanonical " + name);
    }

    private static void requireCurrency(String currency) {
        if (currency == null || currency.isBlank() || !currency.equals(currency.trim()))
            throw blocked("Missing/unverified currency");
    }

    private static IllegalStateException blocked(String reason) {
        // Service-level mapping to exact 401/403/503 belongs to TV1+TV2 C01/E01
        // signed wire contract; this helper itself performs NO network calls.
        return new IllegalStateException(reason);
    }
}
