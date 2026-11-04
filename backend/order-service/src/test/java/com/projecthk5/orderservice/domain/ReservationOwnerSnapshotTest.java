package com.projecthk5.orderservice.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Candidate contract tests only: no Order/Inventory HTTP call or V3 runtime activation. */
class ReservationOwnerSnapshotTest {
    private static final UUID RES_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RES_B = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SO_A = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private CheckoutDraft.Line line(String product, String sku, int quantity, String price) {
        BigDecimal unit = new BigDecimal(price);
        return new CheckoutDraft.Line(UUID.randomUUID(), product, sku, product, quantity,
                unit, unit.multiply(BigDecimal.valueOf(quantity)));
    }

    private CheckoutDraft.Store store(UUID id, String seller, String store, List<CheckoutDraft.Line> lines) {
        BigDecimal gross = lines.stream().map(CheckoutDraft.Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CheckoutDraft.Store(id, seller, store, false, "PLACED", "PENDING",
                Instant.parse("2026-11-04T00:00:00Z"), gross, BigDecimal.ZERO, gross, lines);
    }

    private CheckoutDraft draft(CheckoutDraft.Store... stores) {
        BigDecimal gross = java.util.Arrays.stream(stores).map(CheckoutDraft.Store::gross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CheckoutDraft(1, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "customer-a", "a".repeat(64), 7L, 8L, Instant.parse("2026-11-04T00:00:00Z"),
                "COD", null, gross, List.of(stores), "{\"eventId\":\"fixture\"}");
    }

    @Test
    void twoStoreReservationsBindDistinctSellerOrdersWithCanonicalSkuOrder() {
        var a = store(SO_A, "seller-a", "store-a", List.of(
                line("product-z", "SKU-Z", 2, "3.00"), line("product-a", "SKU-A", 1, "4.00")));
        var b = store(UUID.fromString("44444444-4444-4444-4444-444444444444"), "seller-b", "store-b",
                List.of(line("product-b", "SKU-B", 2, "5.00")));
        CheckoutDraft draft = draft(a, b);

        var ownerA = ReservationOwnerSnapshot.fromDraft(draft, a, RES_A);
        var ownerB = ReservationOwnerSnapshot.fromDraft(draft, b, RES_B);

        assertNotEquals(ownerA.reservationId(), ownerB.reservationId());
        assertNotEquals(ownerA.sellerOrderId(), ownerB.sellerOrderId());
        assertEquals(List.of("SKU-A", "SKU-Z"), ownerA.lines().stream()
                .map(ReservationOwnerSnapshot.Line::sku).toList());
        assertEquals(draft.orderId(), ownerA.checkoutAttemptId());
        assertEquals(draft.orderId(), ownerA.rootOrderId());
        assertEquals("091b54233cf527803458dd1601299ed98acf0bc59eecb3d5d6235d1caa2285b4",
                ownerA.providerOwnerFingerprint());
        assertEquals("c12bda61051906250f3633be41f05e6427d57570ec9c5cd99621106163235c84",
                ownerA.providerOperationFingerprint(InventoryOperationIntent.Type.RESERVE, List.of(
                        new InventoryOperationIntent.Line("SKU-Z", 2),
                        new InventoryOperationIntent.Line("SKU-A", 1))));
        assertNotEquals(ownerA.providerOwnerFingerprint(), ownerB.providerOwnerFingerprint());
    }

    @Test
    void immutableFingerprintRejectsDifferentOwnerOrChangedLines() {
        var store = store(SO_A, "seller-a", "store-a",
                List.of(line("product-a", "SKU-A", 1, "4.00")));
        var owner = ReservationOwnerSnapshot.fromDraft(draft(store), store, RES_A);

        assertThrows(IllegalStateException.class, () -> new ReservationOwnerSnapshot(
                owner.reservationId(), owner.checkoutAttemptId(), owner.rootOrderId(), owner.sellerOrderId(),
                owner.customerId(), "store-b", owner.sellerId(), owner.checkoutRequestFingerprint(),
                owner.lines(), owner.providerOwnerFingerprint(), owner.snapshotSha256()));
        assertThrows(IllegalStateException.class, () -> new ReservationOwnerSnapshot(
                owner.reservationId(), owner.checkoutAttemptId(), owner.rootOrderId(), owner.sellerOrderId(),
                owner.customerId(), owner.storeId(), owner.sellerId(), owner.checkoutRequestFingerprint(),
                owner.lines(), owner.providerOwnerFingerprint(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> owner.providerOperationFingerprint(
                InventoryOperationIntent.Type.RELEASE, List.of(
                        new InventoryOperationIntent.Line("SKU-A", 1),
                        new InventoryOperationIntent.Line("SKU-A", 1))));
        assertNotEquals(owner.providerOperationFingerprint(InventoryOperationIntent.Type.RESERVE,
                        List.of(new InventoryOperationIntent.Line("SKU-A", 1))),
                owner.providerOperationFingerprint(InventoryOperationIntent.Type.RELEASE,
                        List.of(new InventoryOperationIntent.Line("SKU-A", 1))));
    }

    @Test
    void malformedSkuAndDuplicateProductAreRejectedBeforeAnyPersistence() {
        var bad = store(SO_A, "seller-a", "store-a",
                List.of(line("product-x", "\u2003SKU-A", 1, "1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOwnerSnapshot.fromDraft(draft(bad), bad, RES_A));

        var duplicate = store(SO_A, "seller-a", "store-a",
                List.of(line("product-x", "SKU-A", 1, "1.00"),
                        line("product-x", "SKU-B", 1, "1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOwnerSnapshot.fromDraft(draft(duplicate), duplicate, RES_A));
    }

    @Test
    void cannotAttachForeignSellerOrderOrMintReplayIdentityFromClientInput() {
        var real = store(SO_A, "seller-a", "store-a", List.of(line("p1", "SKU-1", 1, "1.00")));
        var foreign = store(UUID.randomUUID(), "seller-x", "store-x",
                List.of(line("p2", "SKU-2", 1, "1.00")));
        var saved = draft(real);
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOwnerSnapshot.fromDraft(saved, foreign, RES_B));
        var owner = ReservationOwnerSnapshot.fromDraft(saved, real, RES_A);
        var different = ReservationOwnerSnapshot.fromDraft(saved, real, RES_B);
        assertNotEquals(owner, different);
        assertNotEquals(owner.snapshotSha256(), different.snapshotSha256());
        // Recovery must load RES_A from the committed owner row, not regenerate RES_B.
    }
}
