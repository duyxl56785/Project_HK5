package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.api.InitializeInventoryRequest;
import com.projecthk5.inventoryservice.api.InventoryController;
import com.projecthk5.inventoryservice.api.InventoryOperationType;
import com.projecthk5.inventoryservice.candidate.InventoryReservationLedgerCandidateService;
import com.projecthk5.inventoryservice.candidate.ReservationCandidateException;
import com.projecthk5.inventoryservice.candidate.ReservationOperationCandidateRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL-backed candidate tests; no HTTP route or mock database is involved. */
class InventoryReservationLedgerCandidatePostgresIT extends InventoryPostgresSupport {
    @Autowired InventoryReservationLedgerCandidateService ledger;
    @Autowired InventoryController inventory;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;

    @Test void twoStoresWithSameSkuKeepIndependentOwnerBudgetsAndLookupSurvivesLostResponse() {
        String sku = initialize("shared-store-sku", 12);
        var storeAReserve = request("reserve-a", "reservation-a", "store-a", "seller-a", "seller-order-a",
                InventoryOperationType.RESERVE, line(sku, 5));
        var storeBReserve = request("reserve-b", "reservation-b", "store-b", "seller-b", "seller-order-b",
                InventoryOperationType.RESERVE, line(sku, 4));

        ledger.apply(storeAReserve);
        ledger.apply(storeBReserve);
        assertEquals(3, inventory.getBalance(sku).availableQuantity());
        assertEquals(9, inventory.getBalance(sku).reservedQuantity());

        // The mutation commits; discard the response to model a lost HTTP response.
        ledger.apply(request("consume-a", "reservation-a", "store-a", "seller-a", "seller-order-a",
                InventoryOperationType.CONSUME, line(sku, 2)));
        var recovered = ledger.lookup("consume-a", "reservation-a");
        assertEquals("COMMITTED", recovered.status());
        assertEquals("CONSUME", recovered.operationType());
        assertEquals(List.of(line(sku, 2)), recovered.items());
        assertEquals(64, recovered.requestFingerprint().length());
        assertEquals("RESERVATION_OWNER_MISMATCH", assertThrows(ReservationCandidateException.class,
                () -> ledger.lookup("consume-a", "reservation-b")).getCode());

        var replay = ledger.apply(request("consume-a", "reservation-a", "store-a", "seller-a", "seller-order-a",
                InventoryOperationType.CONSUME, line(sku, 2)));
        assertTrue(replay.replayed());
        assertEquals(3, inventory.getBalance(sku).availableQuantity());
        assertEquals(7, inventory.getBalance(sku).reservedQuantity());

        ledger.apply(request("release-b", "reservation-b", "store-b", "seller-b", "seller-order-b",
                InventoryOperationType.RELEASE, line(sku, 4)));
        assertEquals(7, inventory.getBalance(sku).availableQuantity());
        assertEquals(3, inventory.getBalance(sku).reservedQuantity());
        assertEquals("UNKNOWN", ledger.lookup("not-yet-committed", "reservation-a").status());
        assertEquals(3L, sql.queryForObject("""
                SELECT initial_quantity - consumed_quantity - released_quantity
                FROM inventory_reservation_line WHERE reservation_id = 'reservation-a' AND sku = ?
                """, Long.class, sku));
    }

    @Test void wrongStoreAndSellerOrderCannotUseAnotherReservationsHold() {
        String sku = initialize("owner-mismatch", 10);
        ledger.apply(request("owner-reserve", "owner-reservation", "store-owner", "seller-owner", "seller-order-owner",
                InventoryOperationType.RESERVE, line(sku, 6)));
        var before = inventory.getBalance(sku);

        ReservationCandidateException wrongStore = assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("wrong-store-release", "owner-reservation", "store-other", "seller-owner", "seller-order-owner",
                        InventoryOperationType.RELEASE, line(sku, 6))));
        assertEquals("RESERVATION_OWNER_MISMATCH", wrongStore.getCode());

        ReservationCandidateException wrongSellerOrder = assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("wrong-order-release", "owner-reservation", "store-owner", "seller-owner", "seller-order-other",
                        InventoryOperationType.RELEASE, line(sku, 6))));
        assertEquals("RESERVATION_OWNER_MISMATCH", wrongSellerOrder.getCode());
        assertEquals(before, inventory.getBalance(sku));
        assertFalse(sql.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id IN (?, ?))
                """, Boolean.class, "wrong-store-release", "wrong-order-release"));
    }

    @Test void sameOperationIdWithChangedOwnerTypeOrPayloadConflictsWithoutStockChange() {
        String sku = initialize("idempotency-owner", 10);
        var reserve = request("stable-key", "stable-reservation", "stable-store", "stable-seller", "stable-order",
                InventoryOperationType.RESERVE, line(sku, 5));
        ledger.apply(reserve);
        var afterReserve = inventory.getBalance(sku);

        assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("stable-key", "stable-reservation", "changed-store", "stable-seller", "stable-order",
                        InventoryOperationType.RESERVE, line(sku, 5)))).getCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("stable-key", "stable-reservation", "stable-store", "stable-seller", "stable-order",
                        InventoryOperationType.RESERVE, line(sku, 4)))).getCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("stable-key", "stable-reservation", "stable-store", "stable-seller", "stable-order",
                        InventoryOperationType.RELEASE, line(sku, 5)))).getCode());
        assertEquals("RESERVATION_ALREADY_INITIALIZED", assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("second-reserve-key", "stable-reservation", "stable-store", "stable-seller", "stable-order",
                        InventoryOperationType.RESERVE, line(sku, 5)))).getCode());
        assertEquals("RESERVATION_ALREADY_EXISTS", assertThrows(ReservationCandidateException.class, () -> ledger.apply(
                request("other-owner-key", "other-reservation", "stable-store", "stable-seller", "stable-order",
                        InventoryOperationType.RESERVE, line(sku, 5)))).getCode());
        assertEquals(afterReserve, inventory.getBalance(sku));
        assertEquals(1, sql.queryForObject("""
                SELECT count(*) FROM inventory_operation WHERE reservation_id = ? AND operation_type = 'RESERVE'
                """, Integer.class, "stable-reservation"));
    }

    @Test void multiSkuPartialBudgetFailureRollsBackEarlierLineAndOperationClaim() {
        String a = initialize("partial-a", 10);
        String z = initialize("partial-z", 10);
        ledger.apply(request("reserve-lines", "partial-reservation", "partial-store", "partial-seller", "partial-order",
                InventoryOperationType.RESERVE, line(z, 2), line(a, 5)));
        assertTrue(ledger.apply(request("reserve-lines", "partial-reservation", "partial-store", "partial-seller",
                "partial-order", InventoryOperationType.RESERVE, line(a, 5), line(z, 2))).replayed());
        var beforeA = inventory.getBalance(a);
        var beforeZ = inventory.getBalance(z);

        // SKU a would be released before z fails; both balance and owner ledger must roll back.
        assertThrows(RuntimeException.class, () -> ledger.apply(request("too-large-release", "partial-reservation",
                "partial-store", "partial-seller", "partial-order", InventoryOperationType.RELEASE,
                line(z, 3), line(a, 1))));
        assertEquals(beforeA, inventory.getBalance(a));
        assertEquals(beforeZ, inventory.getBalance(z));
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                Boolean.class, "too-large-release"));
        assertEquals(5L, held("partial-reservation", a));
        assertEquals(2L, held("partial-reservation", z));

        ledger.apply(request("consume-partial", "partial-reservation", "partial-store", "partial-seller", "partial-order",
                InventoryOperationType.CONSUME, line(a, 4), line(z, 1)));
        ledger.apply(request("release-remainder", "partial-reservation", "partial-store", "partial-seller", "partial-order",
                InventoryOperationType.RELEASE, line(a, 1), line(z, 1)));
        assertEquals(0L, held("partial-reservation", a));
        assertEquals(0L, held("partial-reservation", z));
        assertEquals(6, inventory.getBalance(a).availableQuantity());
        assertEquals(0, inventory.getBalance(a).reservedQuantity());
        assertEquals(9, inventory.getBalance(z).availableQuantity());
        assertEquals(0, inventory.getBalance(z).reservedQuantity());
    }

    @Test void failedMultiSkuReserveRollsBackReservationAndOperationLedger() {
        String a = initialize("reserve-rollback-a", 2);
        String z = initialize("reserve-rollback-z", 0);
        assertThrows(RuntimeException.class, () -> ledger.apply(request("reserve-rollback-key", "reserve-rollback-owner",
                "reserve-rollback-store", "reserve-rollback-seller", "reserve-rollback-order",
                InventoryOperationType.RESERVE, line(a, 1), line(z, 1))));
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_reservation WHERE reservation_id = ?)",
                Boolean.class, "reserve-rollback-owner"));
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                Boolean.class, "reserve-rollback-key"));
        assertEquals(2, inventory.getBalance(a).availableQuantity());
        assertEquals(0, inventory.getBalance(a).reservedQuantity());
    }

    @Test void concurrentDistinctReleaseIdsCannotSpendTheSameOwnerBudgetTwice() throws Exception {
        String sku = initialize("owner-race", 10);
        ledger.apply(request("owner-race-reserve", "owner-race-reservation", "owner-race-store", "owner-race-seller",
                "owner-race-order", InventoryOperationType.RESERVE, line(sku, 7)));
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                ledger.apply(request("owner-race-release-a", "owner-race-reservation", "owner-race-store",
                        "owner-race-seller", "owner-race-order", InventoryOperationType.RELEASE, line(sku, 5)));
                firstApplied.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<Object> second = workers.submit(() -> {
                try {
                    return ledger.apply(request("owner-race-release-b", "owner-race-reservation", "owner-race-store",
                            "owner-race-seller", "owner-race-order", InventoryOperationType.RELEASE, line(sku, 5)));
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            waitForDatabaseLock(sql, "inventory_reservation");
            releaseFirst.countDown();
            first.get(20, TimeUnit.SECONDS);
            ReservationCandidateException rejected = assertInstanceOf(ReservationCandidateException.class,
                    second.get(20, TimeUnit.SECONDS));
            assertEquals("INSUFFICIENT_RESERVATION", rejected.getCode());
            assertEquals(8, inventory.getBalance(sku).availableQuantity());
            assertEquals(2, inventory.getBalance(sku).reservedQuantity());
            assertEquals(2L, held("owner-race-reservation", sku));
            assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                    Boolean.class, "owner-race-release-b"));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private String initialize(String prefix, long quantity) {
        String sku = id(prefix);
        inventory.initialize(new InitializeInventoryRequest(sku, quantity));
        return sku;
    }

    private long held(String reservationId, String sku) {
        return sql.queryForObject("""
                SELECT held_quantity FROM inventory_reservation_line WHERE reservation_id = ? AND sku = ?
                """, Long.class, reservationId, sku);
    }

    private static ReservationOperationCandidateRequest.Line line(String sku, long quantity) {
        return new ReservationOperationCandidateRequest.Line(sku, quantity);
    }

    private static ReservationOperationCandidateRequest request(
            String operationId,
            String reservationId,
            String storeId,
            String sellerId,
            String sellerOrderId,
            InventoryOperationType type,
            ReservationOperationCandidateRequest.Line... lines) {
        return new ReservationOperationCandidateRequest(operationId, reservationId, storeId, sellerId,
                sellerOrderId, type, List.of(lines));
    }
}
