package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.api.InitializeInventoryRequest;
import com.projecthk5.inventoryservice.api.InventoryController;
import com.projecthk5.inventoryservice.api.InventoryOperationType;
import com.projecthk5.inventoryservice.candidate.InventoryReservationLedgerCandidateService;
import com.projecthk5.inventoryservice.candidate.ReservationCandidateException;
import com.projecthk5.inventoryservice.candidate.ReservationOperationCandidateRequest;
import com.projecthk5.inventoryservice.candidate.ReservationOperationCandidateResponse;
import com.projecthk5.inventoryservice.model.InsufficientStockException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test void concurrentSameReserveOperationIdReplaysWinnerWithoutDuplicatingOwnerOrStock() throws Exception {
        String sku = initialize("same-reserve-race", 10);
        var reserve = request(id("same-reserve-key"), id("same-reservation"), id("same-store"), id("same-seller"),
                id("same-order"), InventoryOperationType.RESERVE, line(sku, 6));
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationOperationCandidateResponse> first = workers.submit(() ->
                    new TransactionTemplate(transactions).execute(tx -> {
                        var response = ledger.apply(reserve);
                        firstApplied.countDown();
                        await(releaseFirst);
                        return response;
                    }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<Object> retry = workers.submit(() -> {
                try {
                    return ledger.apply(reserve);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            waitForDatabaseLock(sql, "inventory_reservation");
            releaseFirst.countDown();

            assertFalse(first.get(20, TimeUnit.SECONDS).replayed());
            var replay = assertInstanceOf(ReservationOperationCandidateResponse.class, retry.get(20, TimeUnit.SECONDS));
            assertTrue(replay.replayed());
            assertEquals(1, sql.queryForObject(
                    "SELECT count(*) FROM inventory_operation WHERE operation_id = ?",
                    Integer.class, reserve.operationId()));
            assertEquals(1, sql.queryForObject(
                    "SELECT count(*) FROM inventory_reservation WHERE reservation_id = ?",
                    Integer.class, reserve.reservationId()));
            assertEquals(4, inventory.getBalance(sku).availableQuantity());
            assertEquals(6, inventory.getBalance(sku).reservedQuantity());
            assertEquals(6L, held(reserve.reservationId(), sku));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void concurrentSameReleaseOperationIdReplaysWithoutReturningStockTwice() throws Exception {
        String sku = initialize("same-release-race", 10);
        var reserve = request(id("same-release-reserve"), id("same-release-reservation"), id("same-release-store"),
                id("same-release-seller"), id("same-release-order"), InventoryOperationType.RESERVE, line(sku, 7));
        ledger.apply(reserve);
        var release = request(id("same-release-key"), reserve.reservationId(), reserve.storeId(), reserve.sellerId(),
                reserve.sellerOrderId(), InventoryOperationType.RELEASE, line(sku, 4));
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationOperationCandidateResponse> first = workers.submit(() ->
                    new TransactionTemplate(transactions).execute(tx -> {
                        var response = ledger.apply(release);
                        firstApplied.countDown();
                        await(releaseFirst);
                        return response;
                    }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<Object> retry = workers.submit(() -> {
                try {
                    return ledger.apply(release);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            waitForDatabaseLock(sql, "inventory_reservation");
            releaseFirst.countDown();

            assertFalse(first.get(20, TimeUnit.SECONDS).replayed());
            var replay = assertInstanceOf(ReservationOperationCandidateResponse.class, retry.get(20, TimeUnit.SECONDS));
            assertTrue(replay.replayed());
            assertEquals(1, sql.queryForObject(
                    "SELECT count(*) FROM inventory_operation WHERE operation_id = ?",
                    Integer.class, release.operationId()));
            assertEquals(1, sql.queryForObject("""
                    SELECT count(*) FROM inventory_reservation_operation_line
                    WHERE operation_id = ? AND sku = ? AND quantity = 4
                    """, Integer.class, release.operationId(), sku));
            assertEquals(7, inventory.getBalance(sku).availableQuantity());
            assertEquals(3, inventory.getBalance(sku).reservedQuantity());
            assertEquals(3L, held(reserve.reservationId(), sku));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void concurrentReleaseRetryWithChangedPayloadConflictsAfterWinnerCommits() throws Exception {
        String sku = initialize("release-mismatch-race", 10);
        var reserve = request(id("release-mismatch-reserve"), id("release-mismatch-reservation"),
                id("release-mismatch-store"), id("release-mismatch-seller"), id("release-mismatch-order"),
                InventoryOperationType.RESERVE, line(sku, 7));
        ledger.apply(reserve);
        String operationId = id("release-mismatch-key");
        var original = request(operationId, reserve.reservationId(), reserve.storeId(), reserve.sellerId(),
                reserve.sellerOrderId(), InventoryOperationType.RELEASE, line(sku, 4));
        var changed = request(operationId, reserve.reservationId(), reserve.storeId(), reserve.sellerId(),
                reserve.sellerOrderId(), InventoryOperationType.RELEASE, line(sku, 3));
        CountDownLatch firstApplied = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationOperationCandidateResponse> first = workers.submit(() ->
                    new TransactionTemplate(transactions).execute(tx -> {
                        var response = ledger.apply(original);
                        firstApplied.countDown();
                        await(releaseFirst);
                        return response;
                    }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<Object> retry = workers.submit(() -> {
                try {
                    return ledger.apply(changed);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            waitForDatabaseLock(sql, "inventory_reservation");
            releaseFirst.countDown();

            assertFalse(first.get(20, TimeUnit.SECONDS).replayed());
            ReservationCandidateException conflict = assertInstanceOf(ReservationCandidateException.class,
                    retry.get(20, TimeUnit.SECONDS));
            assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.getCode());
            assertEquals(1, sql.queryForObject(
                    "SELECT count(*) FROM inventory_operation WHERE operation_id = ?",
                    Integer.class, operationId));
            assertEquals(1, sql.queryForObject("""
                    SELECT count(*) FROM inventory_reservation_operation_line
                    WHERE operation_id = ? AND sku = ? AND quantity = 4
                    """, Integer.class, operationId, sku));
            assertEquals(7, inventory.getBalance(sku).availableQuantity());
            assertEquals(3, inventory.getBalance(sku).reservedQuantity());
            assertEquals(3L, held(reserve.reservationId(), sku));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void reservationOwnerCannotChangeInitialSkuLinesWithANewOperationId() {
        String a = initialize("immutable-owner-a", 10);
        String z = initialize("immutable-owner-z", 10);
        var reserve = request(id("immutable-owner-reserve"), id("immutable-owner-reservation"),
                id("immutable-owner-store"), id("immutable-owner-seller"), id("immutable-owner-order"),
                InventoryOperationType.RESERVE, line(a, 5), line(z, 2));
        ledger.apply(reserve);
        var beforeA = inventory.getBalance(a);
        var beforeZ = inventory.getBalance(z);

        var changedLines = request(id("immutable-owner-changed-lines"), reserve.reservationId(), reserve.storeId(),
                reserve.sellerId(), reserve.sellerOrderId(), InventoryOperationType.RESERVE, line(a, 4), line(z, 2));
        ReservationCandidateException conflict = assertThrows(ReservationCandidateException.class,
                () -> ledger.apply(changedLines));

        assertEquals("RESERVATION_FINGERPRINT_MISMATCH", conflict.getCode());
        assertEquals(beforeA, inventory.getBalance(a));
        assertEquals(beforeZ, inventory.getBalance(z));
        assertEquals(5L, held(reserve.reservationId(), a));
        assertEquals(2L, held(reserve.reservationId(), z));
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                Boolean.class, changedLines.operationId()));
        assertEquals(1, sql.queryForObject("""
                SELECT count(*) FROM inventory_operation
                WHERE reservation_id = ? AND operation_type = 'RESERVE'
                """, Integer.class, reserve.reservationId()));
    }

    @Test void reservationCandidateRejectsStockAdjustmentTypesBeforeCreatingLedgerRows() {
        for (InventoryOperationType unsupported : List.of(InventoryOperationType.INCREASE, InventoryOperationType.DECREASE)) {
            String operationId = id("unsupported-operation");
            String reservationId = id("unsupported-reservation");
            ReservationCandidateException failure = assertThrows(ReservationCandidateException.class, () ->
                    ledger.apply(request(operationId, reservationId, id("unsupported-store"), id("unsupported-seller"),
                            id("unsupported-order"), unsupported, line(id("unsupported-sku"), 1))));
            assertEquals("INVALID_RESERVATION_OPERATION", failure.getCode());
            assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                    Boolean.class, operationId));
            assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_reservation WHERE reservation_id = ?)",
                    Boolean.class, reservationId));
        }
        String nullTypeOperation = id("null-operation-type");
        ReservationCandidateException nullType = assertThrows(ReservationCandidateException.class, () ->
                ledger.apply(request(nullTypeOperation, id("null-type-reservation"), id("null-type-store"),
                        id("null-type-seller"), id("null-type-order"), null,
                        line(id("null-type-sku"), 1))));
        assertEquals("INVALID_RESERVATION_OPERATION", nullType.getCode());
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                Boolean.class, nullTypeOperation));
    }

    @Test void nullLineIsRejectedBeforeReservationOrInventoryMutation() {
        String sku = initialize("null-line-sku", 3);
        String operationId = id("null-line-operation");
        String reservationId = id("null-line-reservation");
        var malformed = new ReservationOperationCandidateRequest(operationId, reservationId,
                id("null-line-store"), id("null-line-seller"), id("null-line-order"),
                InventoryOperationType.RESERVE, java.util.Arrays.asList(line(sku, 1), null));

        ReservationCandidateException failure = assertThrows(ReservationCandidateException.class,
                () -> ledger.apply(malformed));

        assertEquals("INVALID_RESERVATION_LINES", failure.getCode());
        assertEquals(3, inventory.getBalance(sku).availableQuantity());
        assertEquals(0, inventory.getBalance(sku).reservedQuantity());
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_operation WHERE operation_id = ?)",
                Boolean.class, operationId));
        assertFalse(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_reservation WHERE reservation_id = ?)",
                Boolean.class, reservationId));
    }

    @Test void maximumQuantityReserveNeverCreatesNegativeOrOverflowedStock() {
        String sku = initialize("maximum-quantity", Long.MAX_VALUE);
        var reserve = request("maximum-quantity-reserve", "maximum-quantity-reservation",
                "maximum-quantity-store", "maximum-quantity-seller", "maximum-quantity-order",
                InventoryOperationType.RESERVE, line(sku, Long.MAX_VALUE));

        ledger.apply(reserve);

        assertEquals(0, inventory.getBalance(sku).availableQuantity());
        assertEquals(Long.MAX_VALUE, inventory.getBalance(sku).reservedQuantity());
        InsufficientStockException secondReserve = assertThrows(InsufficientStockException.class,
                () -> ledger.apply(request("maximum-quantity-second", "maximum-quantity-second-reservation",
                        "maximum-quantity-store-2", "maximum-quantity-seller-2", "maximum-quantity-order-2",
                        InventoryOperationType.RESERVE, line(sku, 1))));
        assertNotNull(secondReserve);
        assertEquals(0, inventory.getBalance(sku).availableQuantity());
        assertEquals(Long.MAX_VALUE, inventory.getBalance(sku).reservedQuantity());
    }

    @ParameterizedTest
    @ValueSource(strings = {" SKU-A ", "\u2003SKU-A", "SKU-A\u2003"})
    void rejectsPaddedSkuWithoutRewritingItToAnExistingInventoryKey(String nonCanonicalSku) {
        String sku = initialize("canonical-sku", 10);
        String operationId = id("padded-sku-operation");
        String reservationId = id("padded-sku-reservation");
        var canonicalRequest = request(operationId, reservationId, id("padded-sku-store"),
                id("padded-sku-seller"), id("padded-sku-order"), InventoryOperationType.RESERVE, line(sku, 2));
        assertFalse(ledger.apply(canonicalRequest).replayed());
        var afterCanonicalReserve = inventory.getBalance(sku);
        assertTrue(ledger.apply(canonicalRequest).replayed());

        ReservationCandidateException failure = assertThrows(ReservationCandidateException.class, () ->
                ledger.apply(request(operationId, reservationId, canonicalRequest.storeId(), canonicalRequest.sellerId(),
                        canonicalRequest.sellerOrderId(), InventoryOperationType.RESERVE, line(nonCanonicalSku, 2))));

        assertEquals("INVALID_RESERVATION_LINES", failure.getCode());
        assertEquals(afterCanonicalReserve, inventory.getBalance(sku));
        assertEquals(1, sql.queryForObject("SELECT count(*) FROM inventory_operation WHERE operation_id = ?",
                Integer.class, operationId));
        assertEquals(1, sql.queryForObject("SELECT count(*) FROM inventory_reservation WHERE reservation_id = ?",
                Integer.class, reservationId));
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
