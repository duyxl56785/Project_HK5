package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.orderservice.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CheckoutReplayPostgresIT {
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
    @Autowired CheckoutService checkout;
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired OrderRepository orders;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @MockBean CartPort carts;
    @MockBean CatalogPort catalog;
    @MockBean InventoryPort inventory;

    private final CheckoutRequest request = new CheckoutRequest(
        List.of(new ExpectedItem("p1", 2), new ExpectedItem("p2", 1)), null);

    @Test void committedOrderReplaysTwiceOutsideTransactionWithoutAnotherOrderOrReservation() {
        String customer = "replay-" + UUID.randomUUID();
        UUID id = place(customer, "same-key");
        for (int replay = 0; replay < 2; replay++) {
            CheckoutResult result = checkout.checkout(customer, "Bearer fixture", "same-key", request);
            assertEquals(id, result.orderId());
            assertEquals(2, result.sellerOrderCount());
            assertEquals(0, new BigDecimal("35000").compareTo(result.total()));
        }
        assertEquals(1, sql.queryForObject("select count(*) from orders where customer_id=?",
            Integer.class, customer));
        verify(carts, times(2)).finalizeCart("Bearer fixture", "same-key");
        verifyNoInteractions(catalog, inventory);
    }

    @Test void changedPayloadStillRejectsSuccessfulKeyWithoutCallingOtherServices() {
        String customer = "payload-" + UUID.randomUUID();
        place(customer, "same-key");
        CheckoutRequest changed = new CheckoutRequest(List.of(new ExpectedItem("p1", 9)), null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> checkout.checkout(customer, "Bearer fixture", "same-key", changed));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(carts, catalog, inventory);
    }

    @Test void successfulSameKeyRejectsChangedShippingBeforeRemoteSideEffects() {
        String customer = "shipping-" + UUID.randomUUID();
        String key = "shipping-key-" + UUID.randomUUID();
        Order.Shipping original = new Order.Shipping("Receiver A", "0901234567", "Address A");
        UUID id = place(customer, key, original);

        CheckoutResult same = checkout.checkout(customer, "Bearer fixture", key, request, original);
        assertEquals(id, same.orderId());
        verify(carts).finalizeCart("Bearer fixture", key);
        clearInvocations(carts, catalog, inventory);

        Order.Shipping changed = new Order.Shipping("Receiver A", "0901234567", "Address B");
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> checkout.checkout(customer, "Bearer fixture", key, request, changed));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", conflict.getReason());
        verifyNoInteractions(carts, catalog, inventory);
        assertEquals(1, sql.queryForObject("select count(*) from orders where customer_id=?",
            Integer.class, customer));
        assertEquals(original, orders.findByIdAndCustomerId(id, customer).orElseThrow().getShipping());
    }

    @Test void inconsistentAttemptCannotReadOrFinalizeAnotherCustomersOrder() {
        UUID foreign = place("foreign-" + UUID.randomUUID(), "foreign-key");
        String customer = "own-" + UUID.randomUUID();
        CheckoutAttempt corrupt = keys.begin(customer, "own-key", RequestFingerprint.compute(request)).attempt();
        corrupt.succeed(foreign);
        attempts.saveAndFlush(corrupt);
        assertThrows(IllegalStateException.class,
            () -> checkout.checkout(customer, "Bearer fixture", "own-key", request));
        verifyNoInteractions(carts, catalog, inventory);
    }

    @Test void committedOutcomeReadAfterLostCommitResponseReturnsCompleteResultWithoutCompensation() {
        String customer = "uncertain-" + UUID.randomUUID();
        when(carts.claim("Bearer fixture", "uncertain-key", null)).thenReturn(
            new CartPort.CartSnapshot(customer, 1, true, List.of(
                new CartPort.Item("p1", "store1", "seller1", 2, new BigDecimal("10000")),
                new CartPort.Item("p2", "store2", "seller2", 1, new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1", "seller1", "store1",
            true, true, true, false, new BigDecimal("10000")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2", "seller2", "store2",
            true, true, true, true, new BigDecimal("15000")));
        OrderWriteService lostResponse = mock(OrderWriteService.class);
        doAnswer(invocation -> {
            // This is the real Spring transaction proxy: commit happens before the simulated transport failure.
            writer.savePlaced(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            throw new IllegalStateException("Fixture lost COMMIT response");
        }).when(lostResponse).savePlaced(any(Order.class), any(UUID.class), anyString());
        // Explicit fixture arithmetic does not enable or approve the production Finance policy.
        CheckoutService caller = new CheckoutService(carts, catalog, inventory, keys, lostResponse,
            orders, json, new MoneyRules(true, "0.05", "0", "true"));
        CheckoutResult result = caller.checkout(customer, "Bearer fixture", "uncertain-key", request);
        assertEquals("PLACED", result.status());
        assertEquals(2, result.sellerOrderCount());
        CheckoutAttempt stored = attempts.findByCustomerIdAndIdempotencyKey(customer, "uncertain-key").orElseThrow();
        assertEquals(CheckoutAttempt.State.SUCCEEDED, stored.getState());
        assertEquals(result.orderId(), stored.getOrderId());
        verify(inventory, never()).release(any());
        verify(carts, never()).release(anyString(), anyString());
    }

    private UUID place(String customer, String key) {
        return place(customer, key, null);
    }

    private UUID place(String customer, String key, Order.Shipping shipping) {
        CheckoutAttempt attempt = keys.begin(customer, key, RequestFingerprint.compute(request)).attempt();
        Order order = new Order(attempt.getId(), customer);
        order.ship(shipping);
        SellerOrder first = new SellerOrder(order, "seller1", "store1", new BigDecimal("20000"), new BigDecimal("1000"));
        SellerOrder second = new SellerOrder(order, "seller2", "store2", new BigDecimal("15000"), BigDecimal.ZERO);
        first.getItems().add(new OrderItem(first, "p1", 2, new BigDecimal("10000"), new BigDecimal("20000")));
        second.getItems().add(new OrderItem(second, "p2", 1, new BigDecimal("15000"), new BigDecimal("15000")));
        order.addSellerOrder(first);
        order.addSellerOrder(second);
        writer.savePlaced(order, attempt.getId(), "{\"eventId\":\"fixture-event\"}");
        return order.getId();
    }
}
