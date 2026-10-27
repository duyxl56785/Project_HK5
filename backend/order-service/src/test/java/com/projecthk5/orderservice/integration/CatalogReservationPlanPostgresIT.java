package com.projecthk5.orderservice.integration;

import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight;
import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight.*;
import com.projecthk5.orderservice.catalog.CatalogReservationPlan;
import com.projecthk5.orderservice.domain.InventoryOperationIntent.Line;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.InventoryOperationWire;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import com.projecthk5.orderservice.service.InventoryOperationCaller;
import com.projecthk5.orderservice.service.InventoryOperationJournal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Real Order journal DB; Catalog records are fixtures, no Product HTTP/current-actor proof. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CatalogReservationPlanPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://catalog-plan.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired InventoryOperationJournal journal;
    @Autowired InventoryOperationCaller caller;
    @Autowired JdbcTemplate db;

    private static final Expected A = new Expected("product-A", "store-A", "seller-A", new BigDecimal("10.00"), "VND");
    private static final Expected B = new Expected("product-B", "store-B", "seller-B", new BigDecimal("20.00"), "VND");
    private static final List<CartPort.Item> CART = List.of(
            new CartPort.Item(B.productId(), B.storeId(), B.sellerId(), 7, B.unitPrice()),
            new CartPort.Item(A.productId(), A.storeId(), A.sellerId(), 2, A.unitPrice()));

    private CheckoutAttempt attempt() {
        return attempts.saveAndFlush(new CheckoutAttempt("customer-" + UUID.randomUUID(), "key", "fixture"));
    }
    private static ItemResult ready(Expected e, String sku) {
        return new ItemResult(e.productId(), "READY", new VerifiedProduct(e.productId(), sku,
                e.storeId(), e.sellerId(), e.unitPrice(), e.currency(), true, true, true, false));
    }
    private static BatchResponse response(String skuA) {
        return new BatchResponse(CatalogCheckoutBatchPreflight.CONTRACT_VERSION, true,
                List.of(ready(A, skuA), ready(B, "SKU-B")));
    }

    @Test void persistsExactProductToSkuQuantitiesAndRejectsRemappingSameOperationOnRetry() {
        var attempt = attempt();
        String id = "reserve-" + UUID.randomUUID();
        var intent = CatalogReservationPlan.reserveIntent(attempt.getId(), id, CART, List.of(A, B), response("SKU-A"));
        journal.prepare(intent);
        journal.prepare(intent);
        var loaded = journal.load(id);
        assertEquals(List.of(new Line("SKU-A", 2), new Line("SKU-B", 7)), loaded.intent().lines());
        assertEquals(attempt.getId(), loaded.intent().checkoutAttemptId());
        assertEquals(intent.fingerprint(), loaded.intent().fingerprint());
        assertEquals(List.of(new InventoryOperationWire.Item("SKU-A", 2), new InventoryOperationWire.Item("SKU-B", 7)),
                InventoryOperationWire.from(loaded.intent()).items());
        assertEquals(1, db.queryForObject("select count(*) from inventory_operation_journal where operation_id=?", Integer.class, id));
        var changed = CatalogReservationPlan.reserveIntent(attempt.getId(), id, CART, List.of(A, B), response("REMAPPED-A"));
        assertThrows(IllegalArgumentException.class, () -> journal.prepare(changed));
        assertEquals(intent.fingerprint(), journal.load(id).intent().fingerprint());
        assertEquals(InventoryOperationJournal.State.PENDING, journal.load(id).state());
        assertEquals(0, journal.load(id).sends());
    }

    @Test void lateNonReadyProductCannotLeaveAPartialReserveIntent() {
        var attempt = attempt();
        String id = "reserve-" + UUID.randomUUID();
        var bad = new BatchResponse(CatalogCheckoutBatchPreflight.CONTRACT_VERSION, true,
                List.of(ready(A, "SKU-A"), new ItemResult(B.productId(), "PRODUCT_NOT_FOUND", null)));
        assertThrows(IllegalStateException.class, () -> journal.prepare(
                CatalogReservationPlan.reserveIntent(attempt.getId(), id, CART, List.of(A, B), bad)));
        assertEquals(0, db.queryForObject("select count(*) from inventory_operation_journal where operation_id=?", Integer.class, id));
    }

    @Test void actualDisabledTransportStillBlocksDispatchOfMappedJournalIntent() {
        var attempt = attempt();
        String id = "reserve-" + UUID.randomUUID();
        var intent = CatalogReservationPlan.reserveIntent(attempt.getId(), id, CART, List.of(A, B), response("SKU-A"));
        journal.prepare(intent);
        assertEquals(InventoryOperationCaller.Outcome.BLOCKED, caller.dispatchOnce(id));
        assertEquals(InventoryOperationJournal.State.PENDING, journal.load(id).state());
        assertEquals("CALLER_DISABLED", journal.load(id).errorCode());
        assertEquals(intent.lines(), journal.load(id).intent().lines());
        assertEquals(0, db.queryForObject("select count(*) from orders where id=?", Integer.class, attempt.getId()));
        assertEquals(0, db.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, attempt.getId()));
    }
}
