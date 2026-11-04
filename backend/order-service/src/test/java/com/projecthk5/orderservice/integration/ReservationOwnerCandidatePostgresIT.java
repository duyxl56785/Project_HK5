package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import com.projecthk5.orderservice.domain.ReservationOwnerSnapshot;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.service.IdempotencyCoordinator;
import com.projecthk5.orderservice.service.ReservationOwnerCandidateJournal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/**
 * Isolated Order PostgreSQL/Flyway candidate rehearsal. Not Checkout→Inventory E2E.
 * V6 candidate is opted into this disposable DB only; normal Order Flyway is V1–V5.
 */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/candidate-migration",
        "app.checkout.reconcile-scan-ms=86400000"
})
class ReservationOwnerCandidatePostgresIT {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("app.security.issuer", () -> "https://fixture.example.invalid");
        registry.add("app.security.audience", () -> "fixture-api");
        registry.add("app.security.current-kid", () -> "fixture-kid");
        registry.add("app.security.current-key-b64",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired IdempotencyCoordinator keys;
    @Autowired ReservationOwnerCandidateJournal owners;
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx() { return new TransactionTemplate(txManager); }

    private record Fixture(CheckoutDraft draft, UUID attemptId) {}

    private Fixture fixture() {
        String customer = "owner-map-" + UUID.randomUUID();
        String fingerprint = "a".repeat(64);
        CheckoutAttempt attempt = keys.begin(customer, "key", fingerprint).attempt();
        CheckoutDraft.Line lineA = new CheckoutDraft.Line(UUID.randomUUID(), "product-a", "SKU-A",
                "Product A", 2, new BigDecimal("4.00"), new BigDecimal("8.00"));
        CheckoutDraft.Line lineB = new CheckoutDraft.Line(UUID.randomUUID(), "product-b", "SKU-B",
                "Product B", 1, new BigDecimal("5.00"), new BigDecimal("5.00"));
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        CheckoutDraft.Store storeA = new CheckoutDraft.Store(UUID.randomUUID(), "seller-a", "store-a",
                false, "PLACED", "PENDING", now, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("8.00"), List.of(lineA));
        CheckoutDraft.Store storeB = new CheckoutDraft.Store(UUID.randomUUID(), "seller-b", "store-b",
                true, "PLACED", "PENDING", now, new BigDecimal("5.00"), BigDecimal.ZERO,
                new BigDecimal("5.00"), List.of(lineB));
        var draft = new CheckoutDraft(1, attempt.getId(), customer, fingerprint, null, 8L,
                now, "COD", null, new BigDecimal("13.00"), List.of(storeA, storeB), "{\"eventId\":\"fixture\"}");
        return new Fixture(draft, attempt.getId());
    }

    /** Insert the draft in THIS test transaction; the normal draft method uses REQUIRES_NEW. */
    private void insertDraftInsideT1(CheckoutDraft draft) {
        try {
            String payload = json.writeValueAsString(draft);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
            db.update("insert into checkout_order_drafts "
                            + "(checkout_attempt_id,snapshot_version,payload,payload_sha256) values (?,?,?,?)",
                    draft.orderId(), draft.version(), payload, hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int count(String table, UUID attemptId) {
        return db.queryForObject("select count(*) from " + table + " where checkout_attempt_id=?",
                Integer.class, attemptId);
    }

    @Test
    void committedT1HasTwoOwnersAndSameAttemptReplayDoesNotRemintReservation() {
        Fixture f = fixture();
        List<ReservationOwnerSnapshot> first = tx().execute(status -> {
            insertDraftInsideT1(f.draft());
            return owners.prepareInsideDraftT1(f.draft());
        });
        assertNotNull(first);
        assertEquals(2, first.size());
        assertEquals(2, count("checkout_reservation_owner_candidate", f.attemptId()));
        assertNotEquals(first.get(0).reservationId(), first.get(1).reservationId());
        assertNotEquals(first.get(0).sellerOrderId(), first.get(1).sellerOrderId());

        List<ReservationOwnerSnapshot> replay = tx().execute(status -> owners.prepareInsideDraftT1(f.draft()));
        assertEquals(first, replay);
        assertEquals(first, owners.loadForAttempt(f.attemptId()));
        assertEquals(2, count("checkout_reservation_owner_candidate", f.attemptId()));
    }

    @Test
    void rollbackAfterDraftAndOwnerInsertLeavesNeitherRow() {
        Fixture f = fixture();
        assertThrows(IllegalStateException.class, () -> tx().execute(status -> {
            insertDraftInsideT1(f.draft());
            owners.prepareInsideDraftT1(f.draft());
            throw new IllegalStateException("simulated process abort before commit");
        }));
        assertEquals(0, count("checkout_order_drafts", f.attemptId()));
        assertEquals(0, count("checkout_reservation_owner_candidate", f.attemptId()));
    }

    @Test
    void changedStoreOwnerFailsClosedWithoutOverwritingCommittedFacts() {
        Fixture f = fixture();
        List<ReservationOwnerSnapshot> first = tx().execute(status -> {
            insertDraftInsideT1(f.draft());
            return owners.prepareInsideDraftT1(f.draft());
        });
        assertNotNull(first);
        var a = f.draft().sellerOrders().get(0);
        var modified = new CheckoutDraft.Store(a.id(), "another-seller", a.storeId(), a.houseStore(),
                a.status(), a.codStatus(), a.updatedAt(), a.gross(), a.platformFee(),
                a.sellerNet(), a.items());
        CheckoutDraft changed = new CheckoutDraft(f.draft().version(), f.draft().orderId(),
                f.draft().customerId(), f.draft().requestFingerprint(), f.draft().expectedRevision(),
                f.draft().claimedCartRevision(), f.draft().createdAt(), f.draft().paymentMethod(),
                f.draft().shipping(), f.draft().total(), List.of(modified, f.draft().sellerOrders().get(1)),
                f.draft().outboxPayload());

        assertThrows(IllegalStateException.class, () ->
                tx().execute(status -> owners.prepareInsideDraftT1(changed)));
        assertEquals(first, owners.loadForAttempt(f.attemptId()));
    }
}
