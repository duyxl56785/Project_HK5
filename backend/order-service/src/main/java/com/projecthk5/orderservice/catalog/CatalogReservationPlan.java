package com.projecthk5.orderservice.catalog;

import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight.BatchResponse;
import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight.Expected;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.ports.CartPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Order-local Cart -> validated Catalog -> immutable SKU intent mapping.
 * No Spring registration, HTTP, journal write or Inventory dispatch occurs here.
 * The candidate C01/E01 batch must come from an approved authenticated adapter
 * before this can be connected to live Checkout. A READY record is not authority.
 */
public final class CatalogReservationPlan {
    private CatalogReservationPlan() {}

    /**
     * Quantities come only from the claimed Cart, SKUs only from the fully checked
     * Catalog response. Expectations must describe that same Cart and carry an
     * explicit currency; current Cart V1 cannot supply it and must not default it.
     * Persist the returned intent with Journal.prepare before any future send.
     * On retry reload that journal entry, never rebuild from a new Catalog read.
     */
    public static InventoryOperationIntent reserveIntent(UUID checkoutAttemptId, String operationId,
            List<CartPort.Item> cartItems, List<Expected> expectations, BatchResponse response) {
        if (cartItems == null || expectations == null || cartItems.isEmpty()
                || cartItems.size() > CatalogCheckoutBatchPreflight.MAX_BATCH_SIZE
                || expectations.size() != cartItems.size()) {
            throw new IllegalArgumentException("Complete Cart and Catalog expectations required");
        }

        Map<String, CartPort.Item> cartByProduct = new HashMap<>();
        for (CartPort.Item item : cartItems) {
            if (item == null || item.productId() == null || item.productId().isBlank()
                    || item.quantity() < 1 || item.quantity() > 1000
                    || cartByProduct.putIfAbsent(item.productId(), item) != null) {
                throw new IllegalArgumentException("Invalid or duplicate Cart product/quantity");
            }
        }
        for (Expected expected : expectations) {
            CartPort.Item cart = expected == null ? null : cartByProduct.get(expected.productId());
            if (cart == null || !Objects.equals(cart.storeId(), expected.storeId())
                    || !Objects.equals(cart.sellerId(), expected.sellerId())
                    || cart.unitPriceSnapshot() == null || expected.unitPrice() == null
                    || cart.unitPriceSnapshot().compareTo(expected.unitPrice()) != 0) {
                throw new IllegalStateException("Catalog expectations do not describe the claimed Cart");
            }
        }

        // Validate the ENTIRE batch before constructing any line. No READY subset,
        // positional Cart quantity association, Product-ID fallback or SKU trimming.
        var products = CatalogCheckoutBatchPreflight.requireFullyReady(expectations, response);
        var lines = products.stream().map(product -> new InventoryOperationIntent.Line(
                product.inventorySku(), cartByProduct.get(product.productId()).quantity())).toList();
        return InventoryOperationIntent.prepare(checkoutAttemptId, operationId,
                InventoryOperationIntent.Type.RESERVE, lines);
    }
}
