package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.service.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {"app.checkout.reconcile-scan-ms=86400000", "app.checkout.draft-purge-ms=86400000"})
class CheckoutDraftRetentionPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://fixture.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CheckoutDraftRetention retention;
    @Autowired CheckoutService checkout;
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired JdbcTemplate sql;
    @MockBean CartPort carts;
    @MockBean CatalogPort catalog;
    @MockBean InventoryPort inventory;

    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private final CheckoutRequest request = new CheckoutRequest(List.of(new ExpectedItem("p1", 2)), null);

    @Test void expiredSuccessfulDraftIsPurgedAndSameKeyReplayStillReturnsTheOrder() {
        String customer = "purged-" + UUID.randomUUID();
        Order.Shipping shipping = new Order.Shipping("Receiver A", "0901234567", "Address A");
        Placed placed = place(customer, "key", shipping, NOW.minus(Duration.ofDays(8)));
        int outboxBefore = count("select count(*) from outbox_events");

        assertTrue(retention.purgeBefore(NOW.minus(Duration.ofDays(7))) >= 1);
        assertEquals(0, count("select count(*) from checkout_order_drafts where checkout_attempt_id=?", placed.attempt));

        CheckoutResult replay = checkout.checkout(customer, "Bearer fixture", "key", request, shipping);
        assertEquals(placed.order, replay.orderId());
        assertEquals(1, count("select count(*) from orders where customer_id=?", customer));
        assertEquals(1, count("select count(*) from checkout_attempts where id=? and state='SUCCEEDED'", placed.attempt));
        assertEquals(outboxBefore, count("select count(*) from outbox_events"));
        verifyNoInteractions(catalog, inventory);
    }

    @Test void recentSuccessfulDraftIsKept() {
        Placed placed = place("recent-" + UUID.randomUUID(), "key", null, NOW.minus(Duration.ofDays(6)));
        retention.purgeBefore(NOW.minus(Duration.ofDays(7)));
        assertEquals(1, count("select count(*) from checkout_order_drafts where checkout_attempt_id=?", placed.attempt));
    }

    @Test void successfulDraftWithUnsettledInventoryOperationIsKept() {
        for (String state : List.of("PENDING", "IN_FLIGHT", "UNKNOWN")) {
            Placed placed = place("journal-" + UUID.randomUUID(), "key", null, NOW.minus(Duration.ofDays(30)));
            sql.update("insert into inventory_operation_journal (operation_id,checkout_attempt_id,operation_type,"
                + "canonical_lines,payload_fingerprint,state) values (?,?,'RESERVE','[]'::jsonb,?,?)",
                "op-" + UUID.randomUUID(), placed.attempt, "0".repeat(64), state);
            retention.purgeBefore(NOW.minus(Duration.ofDays(7)));
            assertEquals(1, count("select count(*) from checkout_order_drafts where checkout_attempt_id=?", placed.attempt), state);
        }
    }

    @Test void unfinishedOrFailedAttemptDraftIsNeverPurged() {
        for (String state : List.of("PROCESSING", "FAILED", "NEEDS_RECONCILIATION")) {
            String customer = "open-" + UUID.randomUUID();
            CheckoutAttempt attempt = keys.begin(customer, "key", RequestFingerprint.compute(request)).attempt();
            draft(attempt.getId());
            sql.update("update checkout_attempts set state=?, created_at=? where id=?",
                state, Timestamp.from(NOW.minus(Duration.ofDays(60))), attempt.getId());
            retention.purgeBefore(NOW.minus(Duration.ofDays(7)));
            assertEquals(1, count("select count(*) from checkout_order_drafts where checkout_attempt_id=?", attempt.getId()), state);

            // Same state with an Order already linked: only SUCCEEDED makes the draft redundant.
            Placed linked = place("linked-" + UUID.randomUUID(), "key", null, NOW.minus(Duration.ofDays(60)));
            sql.update("update checkout_attempts set state=? where id=?", state, linked.attempt);
            retention.purgeBefore(NOW.minus(Duration.ofDays(7)));
            assertEquals(1, count("select count(*) from checkout_order_drafts where checkout_attempt_id=?", linked.attempt), state);
        }
    }

    @Test void retentionShorterThanOneDayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CheckoutDraftRetention(sql, Duration.ofHours(23), 500));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutDraftRetention(sql, Duration.ofDays(7), 0));
    }

    private record Placed(UUID attempt, UUID order) {}

    private Placed place(String customer, String key, Order.Shipping shipping, Instant orderCreatedAt) {
        CheckoutAttempt attempt = keys.begin(customer, key, RequestFingerprint.compute(request)).attempt();
        draft(attempt.getId());
        Order order = new Order(attempt.getId(), customer);
        order.ship(shipping);
        SellerOrder seller = new SellerOrder(order, "seller1", "store1", new BigDecimal("20000"), BigDecimal.ZERO);
        seller.getItems().add(new OrderItem(seller, "p1", 2, new BigDecimal("10000"), new BigDecimal("20000")));
        order.addSellerOrder(seller);
        writer.savePlaced(order, attempt.getId(), "{\"eventId\":\"retention-fixture\"}");
        sql.update("update orders set created_at=? where id=?", Timestamp.from(orderCreatedAt), order.getId());
        return new Placed(attempt.getId(), order.getId());
    }

    private void draft(UUID attemptId) {
        // sha256("{}"): the purge reads only ids and states, never the payload.
        sql.update("insert into checkout_order_drafts (checkout_attempt_id,snapshot_version,payload,payload_sha256) "
            + "values (?,1,'{}','44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a')", attemptId);
    }

    private int count(String query, Object... args) {
        return sql.queryForObject(query, Integer.class, args);
    }
}
