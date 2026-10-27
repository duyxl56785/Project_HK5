package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutResult;
import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutRequest;
import com.projecthk5.orderservice.api.CheckoutDtos.ExpectedItem;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.CatalogPort;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.CheckoutService;
import com.projecthk5.orderservice.service.IdempotencyCoordinator;
import com.projecthk5.orderservice.service.OrderWriteService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Order DB durability; remote ports are mocks. No settlement, COD, JWT or Store API acceptance. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class FinanceDurabilityPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String KEY = randomKey();

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
        properties.add("app.security.issuer", () -> "https://tv5-durability.example.invalid");
        properties.add("app.security.audience", () -> "tv5-durability-test");
        properties.add("app.security.current-kid", () -> "ephemeral-test-key");
        properties.add("app.security.current-key-b64", () -> KEY);
    }

    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;

    @Test void postgresOutboxFaultAfterTwoStoreFlushRollsBackEveryMoneyRowAndAllowsCleanLocalRetry() throws Exception {
        var pending = pending("late-outbox", "100.00", "200.00");
        try (var fault = rejectOutboxAfterFinancialRowsExist(pending.order().getId())) {
            var failure = assertThrows(DataIntegrityViolationException.class,
                    () -> writer.savePlaced(pending.order(), pending.attempt().getId(), payload()));
            var databaseFailure = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
            assertEquals("23514", databaseFailure.getSQLState());
            assertTrue(databaseFailure.getMessage().contains("TV5_DURABILITY_OUTBOX"));
            assertNothingPlaced(pending);
        }
        // Retry a known-rolled-back LOCAL writer only; no claim about remote UNKNOWN compensation.
        writer.savePlaced(pending.order(), pending.attempt().getId(), payload());
        assertPlaced(pending, "300.00");
    }

    @Test void outerRollbackRevertsAlreadyFlushedTwoStoreMoneyOutboxAndSuccessfulAttemptTogether() {
        var pending = pending("outer-rollback", "110.00", "220.00");
        var failure = assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    writer.savePlaced(pending.order(), pending.attempt().getId(), payload());
                    assertPlaced(pending, "330.00"); // Prove the full aggregate is visible inside REQUIRED.
                    throw new IllegalStateException("TV5_OUTER_ROLLBACK");
                }));
        assertEquals("TV5_OUTER_ROLLBACK", failure.getMessage());
        assertNothingPlaced(pending);
    }

    @Test void concurrentSuccessfulReplaysKeepEveryStoredMoneySnapshotWhileFinancePolicyIsClosed() throws Exception {
        var pending = pending("concurrent-replay", "100.00", "200.00");
        writer.savePlaced(pending.order(), pending.attempt().getId(), payload());
        var before = snapshots(pending.order().getId());
        CartPort cart = mock(CartPort.class);
        CatalogPort catalog = mock(CatalogPort.class);
        InventoryPort inventory = mock(InventoryPort.class);
        var caller = replayCaller(cart, catalog, inventory);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<CheckoutResult>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return caller.checkout(pending.attempt().getCustomerId(), "test-only-bearer",
                        pending.attempt().getIdempotencyKey(), FinanceTwoStoreFixture.request());
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (var future : results) assertReplay(future.get(20, TimeUnit.SECONDS), pending, "300.00");
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(before, snapshots(pending.order().getId()));
        assertPlaced(pending, "300.00");
        verify(cart, times(4)).finalizeCart("test-only-bearer", pending.attempt().getIdempotencyKey());
        verifyNoMoreInteractions(cart);
        verifyNoInteractions(catalog, inventory);
    }

    @Test void identicalIdempotencyKeysAcrossCustomersReplayOnlyTheirOwnTwoStoreFinancialAggregate() throws Exception {
        String sameKey = "shared-key-" + UUID.randomUUID();
        var first = pendingWithKey("customer-a-" + UUID.randomUUID(), sameKey, "100.00", "200.00");
        var second = pendingWithKey("customer-b-" + UUID.randomUUID(), sameKey, "300.00", "400.00");
        writer.savePlaced(first.order(), first.attempt().getId(), payload());
        writer.savePlaced(second.order(), second.attempt().getId(), payload());
        CartPort cart = mock(CartPort.class);
        CatalogPort catalog = mock(CatalogPort.class);
        InventoryPort inventory = mock(InventoryPort.class);
        var caller = replayCaller(cart, catalog, inventory);
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return caller.checkout(first.attempt().getCustomerId(), "customer-a-fixture", sameKey,
                        FinanceTwoStoreFixture.request());
            });
            var b = pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return caller.checkout(second.attempt().getCustomerId(), "customer-b-fixture", sameKey,
                        FinanceTwoStoreFixture.request());
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertReplay(a.get(20, TimeUnit.SECONDS), first, "300.00");
            assertReplay(b.get(20, TimeUnit.SECONDS), second, "700.00");
        } finally {
            start.countDown(); pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertNotEquals(first.order().getId(), second.order().getId());
        assertPlaced(first, "300.00"); assertPlaced(second, "700.00");
        assertEquals(2, sql.queryForObject("select count(*) from checkout_attempts where idempotency_key=?",
                Integer.class, sameKey));
        verify(cart).finalizeCart("customer-a-fixture", sameKey);
        verify(cart).finalizeCart("customer-b-fixture", sameKey);
        verifyNoMoreInteractions(cart);
        verifyNoInteractions(catalog, inventory);
    }

    private CheckoutService replayCaller(CartPort cart, CatalogPort catalog, InventoryPort inventory) {
        return new CheckoutService(cart, catalog, inventory, keys, writer, orders, json,
                new MoneyRules(false, "", "", "")); // No synthetic approved Finance policy, even in this caller.
    }

    @Test void conflictingReplayCannotMutateTheFinancialAggregateWhileAValidReplayCompletes() throws Exception {
        var pending = pending("mixed-replay", "100.00", "200.00");
        writer.savePlaced(pending.order(), pending.attempt().getId(), payload());
        var moneyBefore = snapshots(pending.order().getId());
        var outboxBefore = sql.queryForList("select id,topic,aggregate_id,payload,created_at from outbox_events where aggregate_id=?",
                pending.order().getId());
        String fingerprintBefore = sql.queryForObject("select fingerprint from checkout_attempts where id=?",
                String.class, pending.attempt().getId());
        var changed = new CheckoutRequest(List.of(new ExpectedItem(FinanceTwoStoreFixture.PRODUCT_A, 2),
                new ExpectedItem(FinanceTwoStoreFixture.PRODUCT_B, 1)), null);
        CartPort cart = mock(CartPort.class);
        CatalogPort catalog = mock(CatalogPort.class);
        InventoryPort inventory = mock(InventoryPort.class);
        var caller = replayCaller(cart, catalog, inventory);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var valid = pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return caller.checkout(pending.attempt().getCustomerId(), "valid-fixture", pending.attempt().getIdempotencyKey(),
                        FinanceTwoStoreFixture.request());
            });
            var invalid = pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return assertThrows(ResponseStatusException.class, () -> caller.checkout(pending.attempt().getCustomerId(),
                        "changed-fixture", pending.attempt().getIdempotencyKey(), changed));
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertReplay(valid.get(20, TimeUnit.SECONDS), pending, "300.00");
            var conflict = invalid.get(20, TimeUnit.SECONDS);
            assertEquals(409, conflict.getStatusCode().value());
            assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", conflict.getReason());
        } finally {
            start.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(moneyBefore, snapshots(pending.order().getId()));
        assertEquals(outboxBefore, sql.queryForList("select id,topic,aggregate_id,payload,created_at from outbox_events where aggregate_id=?",
                pending.order().getId()));
        assertEquals(fingerprintBefore, sql.queryForObject("select fingerprint from checkout_attempts where id=?",
                String.class, pending.attempt().getId()));
        assertPlaced(pending, "300.00");
        verify(cart).finalizeCart("valid-fixture", pending.attempt().getIdempotencyKey());
        verifyNoMoreInteractions(cart); verifyNoInteractions(catalog, inventory);
    }

    private Pending pending(String prefix, String first, String second) {
        return pendingWithKey(prefix + "-" + UUID.randomUUID(), "key-" + UUID.randomUUID(), first, second);
    }

    private Pending pendingWithKey(String customer, String key, String first, String second) {
        var attempt = keys.begin(customer, key, RequestFingerprint.compute(FinanceTwoStoreFixture.request())).attempt();
        return new Pending(attempt, FinanceTwoStoreFixture.order(attempt, first, second));
    }

    private record Pending(CheckoutAttempt attempt, Order order) { }

    private void assertReplay(CheckoutResult result, Pending pending, String total) {
        assertEquals(pending.order().getId(), result.orderId());
        assertEquals(new BigDecimal(total), result.total());
        assertEquals(2, result.sellerOrderCount());
        assertEquals("PLACED", result.status());
    }

    private void assertPlaced(Pending pending, String total) {
        UUID id = pending.order().getId();
        assertEquals(new BigDecimal(total), sql.queryForObject("select total from orders where id=?", BigDecimal.class, id));
        assertEquals(pending.attempt().getCustomerId(), sql.queryForObject("select customer_id from orders where id=?", String.class, id));
        assertEquals(2, sql.queryForObject("select count(*) from seller_orders where order_id=?", Integer.class, id));
        assertEquals(2, sql.queryForObject("select count(*) from order_items i join seller_orders s on s.id=i.seller_order_id where s.order_id=?", Integer.class, id));
        assertEquals(1, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, id));
        assertEquals("SUCCEEDED", sql.queryForObject("select state from checkout_attempts where id=?", String.class, id));
        assertEquals(id, sql.queryForObject("select order_id from checkout_attempts where id=?", UUID.class, id));
    }

    private void assertNothingPlaced(Pending pending) {
        UUID id = pending.order().getId();
        assertEquals(0, sql.queryForObject("select count(*) from orders where id=?", Integer.class, id));
        assertEquals(0, sql.queryForObject("select count(*) from seller_orders where order_id=?", Integer.class, id));
        assertEquals(0, sql.queryForObject("select count(*) from order_items i join seller_orders s on s.id=i.seller_order_id where s.order_id=?", Integer.class, id));
        assertEquals(0, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, id));
        assertEquals("PROCESSING", sql.queryForObject("select state from checkout_attempts where id=?", String.class, id));
        assertNull(sql.queryForObject("select order_id from checkout_attempts where id=?", UUID.class, id));
    }

    private List<Map<String, Object>> snapshots(UUID orderId) {
        return sql.queryForList("select s.store_id,s.seller_id,s.gross,s.platform_fee,i.product_id,i.quantity,"
                + "i.unit_price_snapshot,i.line_total from seller_orders s join order_items i on i.seller_order_id=s.id "
                + "where s.order_id=? order by s.store_id,i.product_id", orderId);
    }

    private AutoCloseable rejectOutboxAfterFinancialRowsExist(UUID orderId) {
        String name = "tv5_fault_" + UUID.randomUUID().toString().replace("-", "");
        // DDL touches only this new Testcontainer DB. The trigger itself proves the late-failure precondition.
        sql.execute("""
                create function %s() returns trigger language plpgsql as $$
                begin
                  if new.aggregate_id = '%s'::uuid then
                    if (select count(*) from seller_orders where order_id=new.aggregate_id) <> 2
                       or (select count(*) from order_items i join seller_orders s on s.id=i.seller_order_id
                           where s.order_id=new.aggregate_id) <> 2 then
                      raise exception 'TV5_PRECONDITION_NOT_TWO_STORES' using errcode='P0001';
                    end if;
                    raise exception 'TV5_DURABILITY_OUTBOX' using errcode='23514', constraint='tv5_durability_fault';
                  end if;
                  return new;
                end $$
                """.formatted(name, orderId));
        try {
            sql.execute("create trigger " + name + " before insert on outbox_events for each row execute function " + name + "()");
        } catch (RuntimeException failure) {
            sql.execute("drop function " + name + "()");
            throw failure;
        }
        return () -> {
            sql.execute("drop trigger " + name + " on outbox_events");
            sql.execute("drop function " + name + "()");
        };
    }

    private static String payload() { return "{\"eventId\":\"" + UUID.randomUUID() + "\"}"; }
    private static String randomKey() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
