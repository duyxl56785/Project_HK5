package com.projecthk5.orderservice.catalog;

import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight.*;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.domain.InventoryOperationIntent.Line;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.InventoryOperationWire;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Order-local fixtures only, not an authenticated Product provider or HTTP contract test. */
class CatalogReservationPlanTest {
    private static final UUID ATTEMPT = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final Expected A = new Expected("product-A", "store-A", "seller-A", new BigDecimal("10.00"), "VND");
    private static final Expected B = new Expected("product-B", "store-B", "seller-B", new BigDecimal("20.00"), "VND");

    private static CartPort.Item cart(Expected e, int quantity) {
        return new CartPort.Item(e.productId(), e.storeId(), e.sellerId(), quantity, e.unitPrice());
    }
    private static ItemResult ready(Expected e, String sku) {
        return new ItemResult(e.productId(), "READY", new VerifiedProduct(e.productId(), sku,
                e.storeId(), e.sellerId(), e.unitPrice(), e.currency(), true, true, true, false));
    }
    private static BatchResponse batch(ItemResult... rows) {
        return new BatchResponse(CatalogCheckoutBatchPreflight.CONTRACT_VERSION, true, List.of(rows));
    }
    private static InventoryOperationIntent plan(List<CartPort.Item> cart, List<Expected> expected, BatchResponse reply) {
        return CatalogReservationPlan.reserveIntent(ATTEMPT, "reserve-step-1", cart, expected, reply);
    }

    @Test void mapsProviderSkuAndCartQuantityByProductIdentityNotListPosition() {
        var intent = plan(List.of(cart(B, 7), cart(A, 2)), List.of(A, B),
                batch(ready(A, "z-Catalog-SKU"), ready(B, "a-Catalog-SKU")));
        assertEquals(List.of(new Line("a-Catalog-SKU", 7), new Line("z-Catalog-SKU", 2)), intent.lines());
        assertEquals(ATTEMPT, intent.checkoutAttemptId());
        assertEquals("reserve-step-1", intent.operationId());
        assertEquals(InventoryOperationIntent.Type.RESERVE, intent.type());
        var wire = InventoryOperationWire.from(intent);
        assertEquals(List.of(new InventoryOperationWire.Item("a-Catalog-SKU", 7),
                new InventoryOperationWire.Item("z-Catalog-SKU", 2)), wire.items());
        assertFalse(wire.items().stream().anyMatch(i -> i.sku().equals(A.productId()) || i.sku().equals(B.productId())));
    }

    @Test void preservesExactSkuCaseCompositionAndInteriorUnicodeInFingerprint() {
        String sku = "SKU-e\u0301\u00a0inside";
        var intent = plan(List.of(cart(A, 1000)), List.of(A), batch(ready(A, sku)));
        assertEquals(List.of(new Line(sku, 1000)), intent.lines());
        assertEquals(InventoryOperationIntent.prepare(ATTEMPT, "reserve-step-1",
                InventoryOperationIntent.Type.RESERVE, List.of(new Line(sku, 1000))).fingerprint(), intent.fingerprint());
    }

    @Test void rejectsMissingSkuInsteadOfFallingBackToProductId() {
        assertThrows(IllegalStateException.class, () -> plan(List.of(cart(A, 1)), List.of(A), batch(ready(A, null))));
    }

    @Test void requiresExplicitCurrencyRatherThanInventingItForCartV1() {
        var missing = new Expected(A.productId(), A.storeId(), A.sellerId(), A.unitPrice(), null);
        assertThrows(IllegalStateException.class, () -> plan(List.of(cart(A, 1)), List.of(missing), batch(ready(A, "sku-A"))));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1001, Integer.MAX_VALUE})
    void rejectsInvalidCartQuantitiesBeforeAnyIntent(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(cart(A, quantity)), List.of(A), batch(ready(A, "sku-A"))));
    }

    @Test void rejectsDuplicateOrIncompleteCartRatherThanAggregatingOrDroppingProducts() {
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(cart(A, 1), cart(A, 2)), List.of(A, B), batch(ready(A, "a"), ready(B, "b"))));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(cart(A, 1)), List.of(A, B), batch(ready(A, "a"), ready(B, "b"))));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(), List.of(), batch()));
    }

    @Test void rejectsExpectationsForDifferentProductStoreSellerOrPriceThanCart() {
        for (Expected forged : List.of(
                new Expected("other-product", A.storeId(), A.sellerId(), A.unitPrice(), A.currency()),
                new Expected(A.productId(), "other-store", A.sellerId(), A.unitPrice(), A.currency()),
                new Expected(A.productId(), A.storeId(), "other-seller", A.unitPrice(), A.currency()),
                new Expected(A.productId(), A.storeId(), A.sellerId(), new BigDecimal("11.00"), A.currency()))) {
            assertThrows(IllegalStateException.class, () -> plan(List.of(cart(A, 1)), List.of(forged), batch(ready(forged, "a"))));
        }
    }

    @Test void rejectsLateNonReadyRowAndDuplicateProviderSkuForEntireBatch() {
        var cart = List.of(cart(A, 2), cart(B, 7));
        assertThrows(IllegalStateException.class, () -> plan(cart, List.of(A, B), batch(ready(A, "a"),
                new ItemResult(B.productId(), "PRODUCT_NOT_FOUND", null))));
        assertThrows(IllegalStateException.class, () -> plan(cart, List.of(A, B), batch(ready(A, "same"), ready(B, "same"))));
    }

    @Test void rejectsWrongVersionOrReorderedCatalogRowsWhileAllowingCartReordering() {
        var cart = List.of(cart(B, 7), cart(A, 2));
        assertThrows(IllegalStateException.class, () -> plan(cart, List.of(A, B), batch(ready(B, "b"), ready(A, "a"))));
        assertThrows(IllegalStateException.class, () -> plan(cart, List.of(A, B),
                new BatchResponse("wrong-version", true, List.of(ready(A, "a"), ready(B, "b")))));
    }

    @Test void returnedIntentCannotChangeWithLaterMutationOfInputLists() {
        var cart = new ArrayList<>(List.of(cart(A, 2)));
        var expected = new ArrayList<>(List.of(A));
        var rows = new ArrayList<>(List.of(ready(A, "sku-A")));
        var intent = plan(cart, expected, new BatchResponse(CatalogCheckoutBatchPreflight.CONTRACT_VERSION, true, rows));
        String fingerprint = intent.fingerprint();
        cart.clear(); expected.clear(); rows.clear();
        assertEquals(List.of(new Line("sku-A", 2)), intent.lines());
        assertEquals(fingerprint, intent.fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> intent.lines().add(new Line("other", 1)));
    }
}
