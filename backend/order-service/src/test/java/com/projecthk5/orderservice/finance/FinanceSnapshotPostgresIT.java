package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutRequest;
import com.projecthk5.orderservice.api.CheckoutDtos.ExpectedItem;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.CatalogPort;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.CheckoutService;
import com.projecthk5.orderservice.service.IdempotencyCoordinator;
import com.projecthk5.orderservice.service.OrderWriteService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real current V1 persistence; no settlement, Store authorization or live Catalog claims. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class FinanceSnapshotPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://finance-fixture.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate sql;
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired ObjectMapper json;

    @Test void twoStoreGrossFeeAndLineAmountsSurviveCommitAndFreshRead() {
        var order = new Order(UUID.randomUUID(), "finance-" + UUID.randomUUID());
        for (boolean house : new boolean[] {false, true}) {
            var amount = FinanceRules.snapshot(new BigDecimal("0.10"), house, 2);
            var child = new SellerOrder(order, house ? "seller-house" : "seller-regular",
                house ? "store-house" : "store-regular", amount.subtotal(), amount.commission());
            child.getItems().add(new OrderItem(child, house ? "product-house" : "product-regular",
                1, amount.subtotal(), amount.subtotal()));
            order.addSellerOrder(child);
        }
        orders.saveAndFlush(order); // repository transaction has committed before JDBC reads below
        assertEquals(new BigDecimal("0.20"), sql.queryForObject(
            "select total from orders where id=?", BigDecimal.class, order.getId()));
        for (boolean house : new boolean[] {false, true}) {
            var row = sql.queryForMap("select gross, platform_fee, gross-platform_fee as derived_net "
                + "from seller_orders where order_id=? and store_id=?", order.getId(),
                house ? "store-house" : "store-regular");
            assertEquals(new BigDecimal("0.10"), row.get("gross"));
            assertEquals(new BigDecimal(house ? "0.00" : "0.01"), row.get("platform_fee"));
            assertEquals(new BigDecimal(house ? "0.10" : "0.09"), row.get("derived_net"));
        }
        assertEquals(2, sql.queryForObject("select count(*) from order_items i join seller_orders s "
            + "on s.id=i.seller_order_id where s.order_id=? and i.unit_price_snapshot=0.10 "
            + "and i.line_total=0.10", Integer.class, order.getId()));
        // derived_net is SQL arithmetic, not an immutable seller_net snapshot or Store-scoped API.
    }

    @Test void currentV1ScaleBoundaryIsTwoAndPostgresCastWouldRoundHigherScale() {
        assertEquals(2, sql.queryForObject("select numeric_scale from information_schema.columns "
            + "where table_schema='public' and table_name='seller_orders' and column_name='gross'",
            Integer.class));
        assertEquals(new BigDecimal("1.00"), sql.queryForObject(
            "select cast(cast(? as numeric) as numeric(19,2))", BigDecimal.class, "1.001"));
        assertEquals(new BigDecimal("1.01"), sql.queryForObject(
            "select cast(cast(? as numeric) as numeric(19,2))", BigDecimal.class, "1.005"));
        // Characterization of a confirmed blocker, not an assertion that silent rounding is acceptable.
    }

    @ParameterizedTest
    @CsvSource({"0.00,0.00,0.00", "99999999999999999.99,5000000000000000.00,94999999999999999.99"})
    void exactZeroAndNumeric19Scale2MaximumSurviveActualPersistence(String gross, String fee, String net) {
        var order = new Order(UUID.randomUUID(), "numeric-boundary-" + UUID.randomUUID());
        addChild(order, "regular", new BigDecimal(gross), false);
        orders.saveAndFlush(order);
        var stored = sql.queryForMap("select gross, platform_fee, gross-platform_fee as derived_net "
            + "from seller_orders where order_id=?", order.getId());
        assertEquals(new BigDecimal(gross), stored.get("gross"));
        assertEquals(new BigDecimal(fee), stored.get("platform_fee"));
        assertEquals(new BigDecimal(net), stored.get("derived_net"));
        assertEquals(new BigDecimal(gross), sql.queryForObject(
            "select total from orders where id=?", BigDecimal.class, order.getId()));
        // Current schema bounds, not approval of zero Product prices or a new business upper limit.
    }

    @Test void parentAggregateOverflowRollsBackOrderChildrenAndKeepsAttemptUncommitted() {
        String customer = "overflow-" + UUID.randomUUID();
        var attempt = keys.begin(customer, "overflow-key", "fixture-fingerprint").attempt();
        var order = new Order(attempt.getId(), customer);
        // Both Store subtotals fit individually, but their parent total exceeds NUMERIC(19,2).
        addChild(order, "store-a", new BigDecimal("60000000000000000.00"), false);
        addChild(order, "store-b", new BigDecimal("60000000000000000.00"), false);
        var failure = assertThrows(DataIntegrityViolationException.class,
            () -> writer.savePlaced(order, attempt.getId(), "{\"eventId\":\"overflow-fixture\"}"));
        var databaseError = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals("22003", databaseError.getSQLState());
        assertEquals(0, sql.queryForObject("select count(*) from orders where id=?", Integer.class, order.getId()));
        assertEquals(0, sql.queryForObject("select count(*) from seller_orders where order_id=?", Integer.class, order.getId()));
        assertEquals(0, sql.queryForObject("select count(*) from order_items where product_id like ?",
            Integer.class, order.getId() + "%"));
        assertEquals(0, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?",
            Integer.class, order.getId()));
        assertEquals("PROCESSING", sql.queryForObject("select state from checkout_attempts where id=?",
            String.class, attempt.getId()));
        assertNull(sql.queryForObject("select order_id from checkout_attempts where id=?", UUID.class, attempt.getId()));
        // Rollback is proven. Missing pre-write bound validation remains FIN-06 for owner agreement.
    }

    @Test void successfulReplayCannotRepriceCommittedTwoStoreAmountsOrCreateAnotherPlacementEvent() {
        String customer = "financial-replay-" + UUID.randomUUID();
        var request = new CheckoutRequest(List.of(new ExpectedItem("p-regular", 1),
            new ExpectedItem("p-house", 1)), null);
        var attempt = keys.begin(customer, "same-money-key", RequestFingerprint.compute(request)).attempt();
        var order = new Order(attempt.getId(), customer);
        addChild(order, "regular", new BigDecimal("100.00"), false);
        addChild(order, "house", new BigDecimal("200.00"), true);
        writer.savePlaced(order, attempt.getId(), "{\"eventId\":\"money-replay-fixture\"}");
        var originalRows = sql.queryForList("select store_id,gross,platform_fee from seller_orders "
            + "where order_id=? order by store_id", order.getId());
        CartPort cart = mock(CartPort.class);
        CatalogPort catalog = mock(CatalogPort.class);
        InventoryPort inventory = mock(InventoryPort.class);
        // Deliberately different synthetic policy. A successful replay must not calculate another fee.
        var caller = new CheckoutService(cart, catalog, inventory, keys, writer, orders, json,
            new MoneyRules(true, "1.00", "2", "false"));
        for (int i = 0; i < 2; i++) {
            var result = caller.checkout(customer, "Bearer fixture", "same-money-key", request);
            assertEquals(order.getId(), result.orderId());
            assertEquals(new BigDecimal("300.00"), result.total());
            assertEquals(2, result.sellerOrderCount());
        }
        assertEquals(originalRows, sql.queryForList("select store_id,gross,platform_fee from seller_orders "
            + "where order_id=? order by store_id", order.getId()));
        assertEquals(new BigDecimal("5.00"), sql.queryForObject(
            "select sum(platform_fee) from seller_orders where order_id=?", BigDecimal.class, order.getId()));
        assertEquals(1, sql.queryForObject("select count(*) from orders where customer_id=?", Integer.class, customer));
        assertEquals(2, sql.queryForObject("select count(*) from seller_orders where order_id=?", Integer.class, order.getId()));
        assertEquals(1, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, order.getId()));
        verify(cart, times(2)).finalizeCart("Bearer fixture", "same-money-key");
        verifyNoInteractions(catalog, inventory);
        // No settlement/revenue ledger or live service auth exists in this fixture.
    }

    private void addChild(Order order, String store, BigDecimal subtotal, boolean house) {
        var amounts = FinanceRules.snapshot(subtotal, house, 2);
        var child = new SellerOrder(order, "seller-" + store, store, subtotal, amounts.commission());
        child.getItems().add(new OrderItem(child, order.getId() + "-" + store, 1, subtotal, subtotal));
        order.addSellerOrder(child);
    }
}
