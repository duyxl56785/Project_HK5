package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutRequest;
import com.projecthk5.orderservice.api.CheckoutDtos.ExpectedItem;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executable RED acceptance diagnostics against current DB storage facts, not signed M01 business caps.
 * Real Checkout/transactions/PostgreSQL; mocked remote ports. No live reservation, currency or policy approval.
 * Run via the explicit fin06Regression init script; never disabled or silently included as a green normal suite.
 */
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CheckoutMoneyPreflightRegressionTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://fin06-fixture.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService realWriter;
    @Autowired OrderRepository orders;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @Autowired MoneyRules productionMoney;

    private record Fixture(String customer, String key, CartPort cart, CatalogPort catalog,
                           InventoryPort inventory, OrderWriteService writer,
                           CheckoutService checkout, CheckoutRequest request) { }

    private Fixture fixture(String priceA, String priceB, int scale) {
        String customer = "preflight-" + UUID.randomUUID();
        String key = "money-" + UUID.randomUUID();
        CartPort cart = mock(CartPort.class);
        CatalogPort catalog = mock(CatalogPort.class);
        InventoryPort inventory = mock(InventoryPort.class);
        OrderWriteService writer = mock(OrderWriteService.class, delegatesTo(realWriter));
        when(cart.claim("Bearer fixture", key, null)).thenReturn(new CartPort.CartSnapshot(customer, 1, true,
            List.of(new CartPort.Item("p-a", "store-a", "seller-a", 1, new BigDecimal(priceA)),
                    new CartPort.Item("p-b", "store-b", "seller-b", 1, new BigDecimal(priceB)))));
        when(catalog.verify("p-a")).thenReturn(new CatalogPort.ProductSnapshot("p-a", "seller-a", "store-a",
            true, true, true, false, new BigDecimal(priceA)));
        when(catalog.verify("p-b")).thenReturn(new CatalogPort.ProductSnapshot("p-b", "seller-b", "store-b",
            true, true, true, true, new BigDecimal(priceB)));
        // Local fixture policy only: the injected productionMoney remains fail-closed.
        var checkout = new CheckoutService(cart, catalog, inventory, keys, writer, orders, json,
            new MoneyRules(true, "0.05", Integer.toString(scale), "true"));
        return new Fixture(customer, key, cart, catalog, inventory, writer, checkout,
            new CheckoutRequest(List.of(new ExpectedItem("p-a", 1), new ExpectedItem("p-b", 1)), null));
    }

    @Test void positiveControlPersistsExactAmountsAndReplayDoesNotReserveOrWriteAgain() {
        assertThrows(IllegalStateException.class, () -> productionMoney.fee(BigDecimal.TEN, false));
        var f = fixture("100.00", "200.00", 2);
        var first = f.checkout.checkout(f.customer, "Bearer fixture", f.key, f.request);
        var replay = f.checkout.checkout(f.customer, "Bearer fixture", f.key, f.request);
        assertEquals(first.orderId(), replay.orderId());
        assertEquals(new BigDecimal("300.00"), replay.total());
        verify(f.inventory, times(1)).reserve(any(UUID.class), anyList());
        verify(f.writer, times(1)).savePlaced(any(), any(), anyString());
        verify(f.cart, times(2)).finalizeCart("Bearer fixture", f.key);
        assertEquals(1, sql.queryForObject("select count(*) from orders where customer_id=?", Integer.class, f.customer));
        assertEquals(1, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?",
            Integer.class, first.orderId()));
        assertEquals(new BigDecimal("5.00"), sql.queryForObject(
            "select sum(platform_fee) from seller_orders where order_id=?", BigDecimal.class, first.orderId()));
    }

    @Test void parentOverflowMustBeRejectedBeforeReserveOrOrderWrite() {
        var f = fixture("60000000000000000.00", "60000000000000000.00", 2);
        assertThrows(RuntimeException.class,
            () -> f.checkout.checkout(f.customer, "Bearer fixture", f.key, f.request));
        UUID attempt = sql.queryForObject("select id from checkout_attempts where customer_id=?", UUID.class, f.customer);
        assertAll("FIN-06: known-invalid parent must have no reservation/write or UNKNOWN outcome",
            () -> verify(f.inventory, never()).reserve(any(), anyList()),
            () -> verify(f.writer, never()).savePlaced(any(), any(), anyString()),
            () -> verify(f.cart).release("Bearer fixture", f.key),
            () -> assertEquals("FAILED", sql.queryForObject("select state from checkout_attempts where id=?", String.class, attempt)),
            () -> assertEquals(0, sql.queryForObject("select count(*) from orders where customer_id=?", Integer.class, f.customer)),
            () -> assertEquals(0, sql.queryForObject("select count(*) from seller_orders where order_id=?", Integer.class, attempt)),
            () -> assertEquals(0, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, attempt)));
        // Current code is expected RED: reserve + writer called, then DB rollback and NEEDS_RECONCILIATION.
    }

    @Test void unsupportedStoragePrecisionMustBeRejectedRatherThanChangingMoneyOnReplay() {
        // Scale3 is allowed by the current helper, but not exact in the existing NUMERIC(19,2) storage.
        var f = fixture("1.001", "2.001", 3);
        RuntimeException rejected = null;
        BigDecimal firstTotal = null;
        BigDecimal replayTotal = null;
        try {
            firstTotal = f.checkout.checkout(f.customer, "Bearer fixture", f.key, f.request).total();
            replayTotal = f.checkout.checkout(f.customer, "Bearer fixture", f.key, f.request).total();
        } catch (RuntimeException error) { rejected = error; }
        var rejection = rejected;
        var initial = firstTotal;
        var replay = replayTotal;
        assertAll("FIN-01: incompatible scale must fail preflight, not commit/replay rounded money",
            () -> assertNotNull(rejection, "Current storage cannot preserve this amount; reject before reserve"),
            () -> verify(f.inventory, never()).reserve(any(), anyList()),
            () -> verify(f.writer, never()).savePlaced(any(), any(), anyString()),
            () -> assertEquals(0, sql.queryForObject("select count(*) from orders where customer_id=?", Integer.class, f.customer)),
            () -> { if (initial != null && replay != null) assertEquals(initial, replay, "Replay must not change committed money"); });
        // Does not choose VND, a new scale or a business bound. It requires exactness for current storage.
    }
}
