package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.api.*;
import com.projecthk5.inventoryservice.model.InsufficientReservationException;
import com.projecthk5.inventoryservice.model.InsufficientStockException;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(InventoryOperationPostgresIT.LookupScheduling.class)
class InventoryOperationPostgresIT extends InventoryPostgresSupport {
    @Autowired InventoryController controller;
    @Autowired InventoryOperationRepository operations;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;
    private static final AtomicReference<LookupGate> GATE = new AtomicReference<>();

    @Test void disjointSkuSameIdCannotReplaceWinnerAfterEarlierEmptyLedgerRead() throws Exception {
        String a = initialize("race-a", 10), b = initialize("race-b", 10), key = id("same-key");
        var requestA = request(key, InventoryOperationType.DECREASE, a, 1);
        var requestB = request(key, InventoryOperationType.DECREASE, b, 2);
        LookupGate gate = new LookupGate(key, new CountDownLatch(1), new CountDownLatch(1));
        GATE.set(gate);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "tv5-inventory-first"));
        try {
            Future<Object> first = worker.submit(() -> {
                try { return controller.operate(requestA); }
                catch (RuntimeException failure) { return failure; }
            });
            assertTrue(gate.emptyRead().await(15, TimeUnit.SECONDS));
            assertFalse(controller.operate(requestB).replayed()); // real separate transaction commits B
            String winningHash = operations.findById(key).orElseThrow().getRequestHash();
            gate.release().countDown();
            Object outcome = first.get(20, TimeUnit.SECONDS);
            assertEquals(10, controller.getBalance(a).availableQuantity(), "loser must not mutate stock");
            assertEquals(8, controller.getBalance(b).availableQuantity());
            assertEquals(winningHash, operations.findById(key).orElseThrow().getRequestHash());
            assertEquals(1, sql.queryForObject("select count(*) from inventory_operation where operation_id=?",
                Integer.class, key));
            var conflict = assertInstanceOf(InventoryOperationServiceException.class, outcome);
            assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.getCode());
            assertTrue(controller.operate(requestB).replayed());
            assertThrows(InventoryOperationServiceException.class, () -> controller.operate(requestA));
            assertEquals(8, controller.getBalance(b).availableQuantity());
        } finally {
            gate.release().countDown();
            GATE.set(null);
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sharedSkuWaitsForWriterThenReplaysSamePayloadOrRejectsDifferentPayload(boolean samePayload) throws Exception {
        String sku = initialize("shared", 10), key = id("shared-key");
        CountDownLatch flushed = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                controller.operate(request(key, InventoryOperationType.DECREASE, sku, 1));
                flushed.countDown();
                await(release);
            }));
            assertTrue(flushed.await(15, TimeUnit.SECONDS));
            Future<Object> second = workers.submit(() -> {
                try { return controller.operate(request(key, InventoryOperationType.DECREASE, sku, samePayload ? 1 : 2)); }
                catch (InventoryOperationServiceException failure) { return failure; }
            });
            waitForDatabaseLock(sql, "inventory_item");
            release.countDown();
            first.get(20, TimeUnit.SECONDS);
            Object outcome = second.get(20, TimeUnit.SECONDS);
            if (samePayload) assertTrue(assertInstanceOf(InventoryOperationResponse.class, outcome).replayed());
            else assertEquals("IDEMPOTENCY_KEY_REUSED",
                assertInstanceOf(InventoryOperationServiceException.class, outcome).getCode());
            assertEquals(9, controller.getBalance(sku).availableQuantity());
            assertEquals(1, sql.queryForObject("select count(*) from inventory_operation where operation_id=?",
                Integer.class, key));
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void failureOnLaterSkuRollsBackEarlierStockMutationAndClaimedLedger() {
        String a = initialize("A-first", 5), z = initialize("Z-last", 0), key = id("rollback");
        assertThrows(InsufficientStockException.class, () -> controller.operate(new InventoryOperationRequest(
            key, InventoryOperationType.RESERVE, List.of(
                new InventoryOperationRequest.ItemQuantity(a, 2), new InventoryOperationRequest.ItemQuantity(z, 1)))));
        assertEquals(5, controller.getBalance(a).availableQuantity());
        assertEquals(0, controller.getBalance(a).reservedQuantity());
        assertEquals(0, controller.getBalance(z).availableQuantity());
        assertFalse(operations.existsById(key));
    }

    @Test void concurrentDifferentIdsCannotOversellSharedSkuAndLosingClaimRollsBack() throws Exception {
        String sku = initialize("oversell", 10), keyA = id("reserve-a"), keyB = id("reserve-b");
        CountDownLatch flushed = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                controller.operate(request(keyA, InventoryOperationType.RESERVE, sku, 7));
                flushed.countDown(); await(release);
            }));
            assertTrue(flushed.await(15, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> controller.operate(request(keyB, InventoryOperationType.RESERVE, sku, 7)));
            waitForDatabaseLock(sql, "inventory_item");
            release.countDown();
            first.get(20, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(20, TimeUnit.SECONDS));
            assertInstanceOf(InsufficientStockException.class, failure.getCause());
            assertEquals(3, controller.getBalance(sku).availableQuantity());
            assertEquals(7, controller.getBalance(sku).reservedQuantity());
            assertTrue(operations.existsById(keyA));
            assertFalse(operations.existsById(keyB));
        } finally {
            release.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void reserveConsumeAndReleaseRetriesDoNotApplyStockTwice() {
        String sku = initialize("lifecycle-retry", 10);
        String reserveId = id("lifecycle-reserve");
        String consumeId = id("lifecycle-consume");
        String releaseId = id("lifecycle-release");

        var reserve = request(reserveId, InventoryOperationType.RESERVE, sku, 4);
        assertFalse(controller.operate(reserve).replayed());
        var afterReserve = controller.getBalance(sku);
        var reserveReplay = controller.operate(reserve);
        assertTrue(reserveReplay.replayed());
        assertEquals(afterReserve, controller.getBalance(sku));
        assertEquals(6, afterReserve.availableQuantity());
        assertEquals(4, afterReserve.reservedQuantity());

        var consume = request(consumeId, InventoryOperationType.CONSUME, sku, 3);
        assertFalse(controller.operate(consume).replayed());
        var afterConsume = controller.getBalance(sku);
        assertTrue(controller.operate(consume).replayed());
        assertEquals(afterConsume, controller.getBalance(sku));
        assertEquals(6, afterConsume.availableQuantity());
        assertEquals(1, afterConsume.reservedQuantity());

        var release = request(releaseId, InventoryOperationType.RELEASE, sku, 1);
        assertFalse(controller.operate(release).replayed());
        var afterRelease = controller.getBalance(sku);
        assertTrue(controller.operate(release).replayed());
        assertEquals(afterRelease, controller.getBalance(sku));
        assertEquals(7, afterRelease.availableQuantity());
        assertEquals(0, afterRelease.reservedQuantity());

        var mismatch = request(releaseId, InventoryOperationType.RELEASE, sku, 2);
        var conflict = assertThrows(InventoryOperationServiceException.class,
                () -> controller.operate(mismatch));
        assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.getCode());
        assertEquals(afterRelease, controller.getBalance(sku));
    }

    @Test void multiSkuReserveAndReleaseReplayCanonicalizeLineOrderAndRejectPayloadReuse() {
        String a = initialize("multi-release-a", 10), b = initialize("multi-release-b", 20), c = initialize("multi-release-c", 30);
        String reserveId = id("multi-reserve"), releaseId = id("multi-release");

        var reserve = request(reserveId, InventoryOperationType.RESERVE, line(c, 2), line(a, 3), line(b, 4));
        assertFalse(controller.operate(reserve).replayed());
        var aReserved = controller.getBalance(a);
        var bReserved = controller.getBalance(b);
        var cReserved = controller.getBalance(c);
        assertEquals(7, aReserved.availableQuantity());
        assertEquals(3, aReserved.reservedQuantity());
        assertEquals(16, bReserved.availableQuantity());
        assertEquals(4, bReserved.reservedQuantity());
        assertEquals(28, cReserved.availableQuantity());
        assertEquals(2, cReserved.reservedQuantity());

        assertTrue(controller.operate(request(reserveId, InventoryOperationType.RESERVE,
                line(b, 4), line(a, 3), line(c, 2))).replayed());
        assertEquals(aReserved, controller.getBalance(a));
        assertEquals(bReserved, controller.getBalance(b));
        assertEquals(cReserved, controller.getBalance(c));

        var release = request(releaseId, InventoryOperationType.RELEASE, line(b, 4), line(c, 2), line(a, 3));
        assertFalse(controller.operate(release).replayed());
        var aReleased = controller.getBalance(a);
        var bReleased = controller.getBalance(b);
        var cReleased = controller.getBalance(c);
        assertEquals(10, aReleased.availableQuantity());
        assertEquals(0, aReleased.reservedQuantity());
        assertEquals(20, bReleased.availableQuantity());
        assertEquals(0, bReleased.reservedQuantity());
        assertEquals(30, cReleased.availableQuantity());
        assertEquals(0, cReleased.reservedQuantity());

        assertTrue(controller.operate(request(releaseId, InventoryOperationType.RELEASE,
                line(a, 3), line(c, 2), line(b, 4))).replayed());
        assertEquals(aReleased, controller.getBalance(a));
        assertEquals(bReleased, controller.getBalance(b));
        assertEquals(cReleased, controller.getBalance(c));

        var changedQuantity = assertThrows(InventoryOperationServiceException.class,
                () -> controller.operate(request(releaseId, InventoryOperationType.RELEASE,
                        line(a, 2), line(b, 4), line(c, 2))));
        assertEquals("IDEMPOTENCY_KEY_REUSED", changedQuantity.getCode());
        var changedType = assertThrows(InventoryOperationServiceException.class,
                () -> controller.operate(request(releaseId, InventoryOperationType.CONSUME,
                        line(a, 3), line(b, 4), line(c, 2))));
        assertEquals("IDEMPOTENCY_KEY_REUSED", changedType.getCode());
        assertEquals(aReleased, controller.getBalance(a));
        assertEquals(bReleased, controller.getBalance(b));
        assertEquals(cReleased, controller.getBalance(c));
    }

    @Test void multiSkuReleaseAfterConsumeRejectsExcessHeldQuantityAndRollsBackWholeBatch() {
        String a = initialize("partial-release-a", 10), b = initialize("partial-release-b", 10), c = initialize("partial-release-c", 10);
        String reserveId = id("partial-reserve"), consumeId = id("partial-consume");
        String rejectedReleaseId = id("partial-release-rejected"), releaseId = id("partial-release-valid");

        assertFalse(controller.operate(request(reserveId, InventoryOperationType.RESERVE,
                line(c, 4), line(a, 4), line(b, 4))).replayed());
        assertFalse(controller.operate(request(consumeId, InventoryOperationType.CONSUME,
                line(b, 1), line(a, 3))).replayed());

        var aBefore = controller.getBalance(a);
        var bBefore = controller.getBalance(b);
        var cBefore = controller.getBalance(c);
        assertEquals(1, aBefore.reservedQuantity());
        assertEquals(3, bBefore.reservedQuantity());
        assertEquals(4, cBefore.reservedQuantity());

        var tooMuch = request(rejectedReleaseId, InventoryOperationType.RELEASE,
                line(a, 1), line(b, 2), line(c, 5));
        var failure = assertThrows(InsufficientReservationException.class, () -> controller.operate(tooMuch));
        assertEquals(c, failure.getSku());
        assertEquals(aBefore, controller.getBalance(a));
        assertEquals(bBefore, controller.getBalance(b));
        assertEquals(cBefore, controller.getBalance(c));
        assertFalse(operations.existsById(rejectedReleaseId), "failed multi-SKU release must roll back its ledger claim");

        var validPartialRelease = request(releaseId, InventoryOperationType.RELEASE,
                line(c, 4), line(a, 1), line(b, 2));
        assertFalse(controller.operate(validPartialRelease).replayed());
        assertEquals(7, controller.getBalance(a).availableQuantity());
        assertEquals(0, controller.getBalance(a).reservedQuantity());
        assertEquals(8, controller.getBalance(b).availableQuantity());
        assertEquals(1, controller.getBalance(b).reservedQuantity());
        assertEquals(10, controller.getBalance(c).availableQuantity());
        assertEquals(0, controller.getBalance(c).reservedQuantity());
        assertTrue(controller.operate(validPartialRelease).replayed());
        assertEquals(8, controller.getBalance(b).availableQuantity());
        assertEquals(1, controller.getBalance(b).reservedQuantity());
    }

    @Test void concurrentDuplicateMultiSkuReleaseReplaysAfterFirstCommitWithoutDoubleReturn() throws Exception {
        String a = initialize("duplicate-release-a", 10), b = initialize("duplicate-release-b", 10), c = initialize("duplicate-release-c", 10);
        String reserveId = id("duplicate-reserve"), releaseId = id("duplicate-release");
        assertFalse(controller.operate(request(reserveId, InventoryOperationType.RESERVE,
                line(c, 3), line(a, 3), line(b, 3))).replayed());
        var release = request(releaseId, InventoryOperationType.RELEASE,
                line(b, 3), line(c, 3), line(a, 3));

        CountDownLatch firstApplied = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<InventoryOperationResponse> first = workers.submit(() -> new TransactionTemplate(transactions)
                    .execute(status -> {
                        InventoryOperationResponse result = controller.operate(release);
                        firstApplied.countDown();
                        await(releaseFirst);
                        return result;
                    }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<InventoryOperationResponse> retry = workers.submit(() -> controller.operate(
                    request(releaseId, InventoryOperationType.RELEASE, line(a, 3), line(b, 3), line(c, 3))));
            waitForDatabaseLock(sql, "inventory_item");
            releaseFirst.countDown();

            assertFalse(first.get(20, TimeUnit.SECONDS).replayed());
            assertTrue(retry.get(20, TimeUnit.SECONDS).replayed());
            assertEquals(10, controller.getBalance(a).availableQuantity());
            assertEquals(0, controller.getBalance(a).reservedQuantity());
            assertEquals(10, controller.getBalance(b).availableQuantity());
            assertEquals(0, controller.getBalance(b).reservedQuantity());
            assertEquals(10, controller.getBalance(c).availableQuantity());
            assertEquals(0, controller.getBalance(c).reservedQuantity());
            assertEquals(1, sql.queryForObject("select count(*) from inventory_operation where operation_id=?",
                    Integer.class, releaseId));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void competingMultiSkuReleaseIdsCannotOverdrawReservedBalances() throws Exception {
        String a = initialize("competing-release-a", 10), b = initialize("competing-release-b", 10), c = initialize("competing-release-c", 10);
        String reserveId = id("competing-reserve"), firstReleaseId = id("competing-release-1");
        String secondReleaseId = id("competing-release-2");
        assertFalse(controller.operate(request(reserveId, InventoryOperationType.RESERVE,
                line(c, 4), line(a, 4), line(b, 4))).replayed());

        var firstRelease = request(firstReleaseId, InventoryOperationType.RELEASE,
                line(a, 3), line(b, 3), line(c, 3));
        var secondRelease = request(secondReleaseId, InventoryOperationType.RELEASE,
                line(c, 3), line(a, 3), line(b, 3));
        CountDownLatch firstApplied = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<InventoryOperationResponse> first = workers.submit(() -> new TransactionTemplate(transactions)
                    .execute(status -> {
                        InventoryOperationResponse result = controller.operate(firstRelease);
                        firstApplied.countDown();
                        await(releaseFirst);
                        return result;
                    }));
            assertTrue(firstApplied.await(15, TimeUnit.SECONDS));
            Future<Object> competing = workers.submit(() -> {
                try { return controller.operate(secondRelease); }
                catch (RuntimeException failure) { return failure; }
            });
            waitForDatabaseLock(sql, "inventory_item");
            releaseFirst.countDown();

            assertFalse(first.get(20, TimeUnit.SECONDS).replayed());
            var rejected = assertInstanceOf(InsufficientReservationException.class, competing.get(20, TimeUnit.SECONDS));
            assertEquals(a, rejected.getSku());
            assertEquals(9, controller.getBalance(a).availableQuantity());
            assertEquals(1, controller.getBalance(a).reservedQuantity());
            assertEquals(9, controller.getBalance(b).availableQuantity());
            assertEquals(1, controller.getBalance(b).reservedQuantity());
            assertEquals(9, controller.getBalance(c).availableQuantity());
            assertEquals(1, controller.getBalance(c).reservedQuantity());
            assertEquals(1, sql.queryForObject("select count(*) from inventory_operation where operation_id in (?, ?)",
                    Integer.class, firstReleaseId, secondReleaseId));
            assertFalse(operations.existsById(secondReleaseId), "losing release must roll back its idempotency claim");
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }
    private String initialize(String prefix, long quantity) {
        String sku = id(prefix);
        controller.initialize(new InitializeInventoryRequest(sku, quantity));
        return sku;
    }
    private static InventoryOperationRequest.ItemQuantity line(String sku, long quantity) { return new InventoryOperationRequest.ItemQuantity(sku, quantity); }
    private static InventoryOperationRequest request(String key, InventoryOperationType type, InventoryOperationRequest.ItemQuantity... items) {
        return new InventoryOperationRequest(key, type, List.of(items));
    }
    private static InventoryOperationRequest request(String key, InventoryOperationType type, String sku, long quantity) {
        return new InventoryOperationRequest(key, type, List.of(new InventoryOperationRequest.ItemQuantity(sku, quantity)));
    }
    record LookupGate(String operationId, CountDownLatch emptyRead, CountDownLatch release) {}

    /** Schedules after a real SELECT, never substitutes repository data or transaction behavior. */
    @TestConfiguration(proxyBeanMethods = false)
    static class LookupScheduling {
        @Bean static BeanPostProcessor scheduleFirstEmptyLookup() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof InventoryOperationRepository)) return bean;
                    ProxyFactory factory = new ProxyFactory(bean);
                    factory.addAdvice((MethodInterceptor) call -> {
                        Object result = call.proceed();
                        LookupGate gate = GATE.get();
                        if (gate != null && call.getMethod().getName().equals("findById")
                            && gate.operationId().equals(call.getArguments()[0])
                            && Thread.currentThread().getName().equals("tv5-inventory-first")
                            && result instanceof Optional<?> prior && prior.isEmpty()) {
                            gate.emptyRead().countDown(); await(gate.release());
                        }
                        return result;
                    });
                    return factory.getProxy();
                }
            };
        }
    }
}
