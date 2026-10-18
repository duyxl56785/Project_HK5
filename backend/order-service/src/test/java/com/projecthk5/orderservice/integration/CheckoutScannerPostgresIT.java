package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.orderservice.service.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CheckoutScannerPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl()
            + (PG.getJdbcUrl().contains("?") ? "&" : "?") + "ApplicationName=tv5-scanner-regression");
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
        r.add("app.security.issuer", () -> "https://fixture.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CheckoutReconciliationScanner scanner;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired OrderRepository orders;
    @Autowired OutboxEventRepository outbox;
    @Autowired OrderWriteService writer;
    @Autowired IdempotencyCoordinator keys;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(strings = {"scanner", "failure-handler"})
    void writerCommitWinsWhileCompetingTransitionIsWaitingOnTheSameRow(String competing) throws Exception {
        CheckoutAttempt attempt = staleAttempt();
        Order order = order(attempt);
        CountDownLatch writerFlushed = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                writer.savePlaced(order, attempt.getId(), "{\"eventId\":\"race-fixture\"}");
                writerFlushed.countDown();
                await(releaseWriter);
            }));
            assertTrue(writerFlushed.await(15, TimeUnit.SECONDS));
            Future<?> scan = workers.submit(() -> {
                if (competing.equals("scanner")) scanner.flagAbandonedAttempts();
                else keys.fail(attempt.getId(), true);
            });
            // Both implementations reach an UPDATE blocked by the real writer row lock.
            // The old managed-entity flush overwrites success after the lock is released.
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!Boolean.TRUE.equals(sql.queryForObject(
                    "select exists(select 1 from pg_stat_activity where datname=current_database() "
                    + "and application_name='tv5-scanner-regression' and wait_event_type='Lock' "
                    + "and lower(query) like '%checkout_attempts%' and pid<>pg_backend_pid())", Boolean.class))) {
                    Thread.sleep(25);
                }
            });
            releaseWriter.countDown();
            write.get(20, TimeUnit.SECONDS);
            scan.get(20, TimeUnit.SECONDS);
            CheckoutAttempt stored = attempts.findById(attempt.getId()).orElseThrow();
            assertEquals(CheckoutAttempt.State.SUCCEEDED, stored.getState());
            assertEquals(order.getId(), stored.getOrderId());
            assertTrue(orders.existsById(order.getId()));
        } finally {
            releaseWriter.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void scanFlagsOnlyStaleProcessingAndIsRepeatable() {
        CheckoutAttempt stale = staleAttempt();
        CheckoutAttempt fresh = attempts.saveAndFlush(new CheckoutAttempt(
            "fresh-" + UUID.randomUUID(), "key", "fingerprint"));
        scanner.flagAbandonedAttempts();
        scanner.flagAbandonedAttempts();
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,
            attempts.findById(stale.getId()).orElseThrow().getState());
        assertEquals(CheckoutAttempt.State.PROCESSING,
            attempts.findById(fresh.getId()).orElseThrow().getState());
    }

    @Test void scannerWinningBeforeWriterKeepsOrderAndOutboxRolledBack() {
        CheckoutAttempt attempt = staleAttempt();
        scanner.flagAbandonedAttempts();
        long outboxBefore = outbox.count();
        assertThrows(IllegalStateException.class,
            () -> writer.savePlaced(order(attempt), attempt.getId(), "{\"eventId\":\"late-fixture\"}"));
        assertFalse(orders.existsById(attempt.getId()));
        assertEquals(outboxBefore, outbox.count());
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,
            attempts.findById(attempt.getId()).orElseThrow().getState());
    }

    private CheckoutAttempt staleAttempt() {
        CheckoutAttempt attempt = attempts.saveAndFlush(new CheckoutAttempt(
            "scanner-" + UUID.randomUUID(), "key", "fingerprint"));
        sql.update("update checkout_attempts set created_at=now()-interval '1 hour' where id=?", attempt.getId());
        return attempt;
    }
    private Order order(CheckoutAttempt attempt) {
        Order order = new Order(attempt.getId(), attempt.getCustomerId());
        SellerOrder seller = new SellerOrder(order, "seller-fixture", "store-fixture",
            new BigDecimal("100"), new BigDecimal("5"));
        seller.getItems().add(new OrderItem(seller, "product-fixture", 1,
            new BigDecimal("100"), new BigDecimal("100")));
        order.addSellerOrder(seller);
        return order;
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Writer gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
