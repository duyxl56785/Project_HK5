package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.api.*;
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

    private String initialize(String prefix, long quantity) {
        String sku = id(prefix);
        controller.initialize(new InitializeInventoryRequest(sku, quantity));
        return sku;
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
