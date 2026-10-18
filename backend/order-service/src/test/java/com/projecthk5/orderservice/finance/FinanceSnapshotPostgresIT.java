package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
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
}
