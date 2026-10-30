package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.orderservice.service.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Catalog/Inventory adapters against a stub HTTP peer: what each answer means for the Cart and the attempt. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CheckoutInventoryBoundaryPostgresIT {
    private static final String UNKNOWN = "CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED";
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
    private static HttpServer peer;
    private static volatile String catalogBody;
    private static volatile int inventoryStatus;
    private static volatile String inventoryBody;
    private static final AtomicInteger inventoryCalls = new AtomicInteger();

    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired OrderRepository orders;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;

    private final CartPort carts = mock(CartPort.class);
    private final CheckoutRequest request = new CheckoutRequest(List.of(new ExpectedItem("p1", 2)), null);
    private String customer;
    private String key;
    private CheckoutService checkout;

    @BeforeAll static void startPeer() throws IOException {
        peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/internal/catalog/products/", exchange -> reply(exchange, 200, catalogBody));
        peer.createContext("/api/inventory/operations", exchange -> {
            inventoryCalls.incrementAndGet();
            reply(exchange, inventoryStatus, inventoryBody);
        });
        peer.start();
    }

    @AfterAll static void stopPeer() {
        peer.stop(0);
    }

    @BeforeEach void freshCheckout() throws Exception {
        customer = "boundary-" + UUID.randomUUID();
        key = "key-" + UUID.randomUUID();
        inventoryCalls.set(0);
        inventoryStatus = 200;
        inventoryBody = "{}";
        catalogBody = catalog("AO-001");
        when(carts.claim("Bearer fixture", key, null)).thenReturn(new CartPort.CartSnapshot(customer, 1, true, List.of(
            new CartPort.Item("p1", "store1", "seller1", 2, new BigDecimal("10000")))));
        String url = "http://127.0.0.1:" + peer.getAddress().getPort();
        // Explicit fixture arithmetic does not enable or approve the production Finance policy.
        checkout = new CheckoutService(carts, new CatalogHttpAdapter(url, "pair"), new InventoryHttpAdapter(url, "pair"),
            keys, writer, orders, json, new MoneyRules(true, "0.05", "0", "true"));
    }

    static Stream<String> unusableSkus() {
        return Stream.of(null, "", "   ", " AO-001", "AO-001 ", "AO-001\n", " AO-001", "AO\u0000001", "x".repeat(129));
    }

    @ParameterizedTest @MethodSource("unusableSkus")
    void unusableCatalogSkuFailsBeforeInventoryAndReleasesTheCart(String sku) throws Exception {
        catalogBody = catalog(sku);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, this::place);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertNotEquals(UNKNOWN, error.getReason());
        assertEquals(0, inventoryCalls.get());
        verify(carts).release("Bearer fixture", key);
        assertAttempt(CheckoutAttempt.State.FAILED);
    }

    @Test void canonicalSkuReachesInventoryUnchanged() {
        assertEquals("PLACED", place().status());
        assertEquals(1, inventoryCalls.get());
        assertEquals("AO-001", sql.queryForObject(
            "select i.sku from order_items i join seller_orders s on s.id=i.seller_order_id join orders o on o.id=s.order_id"
                + " where o.customer_id=?", String.class, customer));
    }

    private CheckoutResult place() {
        return checkout.checkout(customer, "Bearer fixture", key, request);
    }

    private void assertAttempt(CheckoutAttempt.State state) {
        assertEquals(state, attempts.findByCustomerIdAndIdempotencyKey(customer, key).orElseThrow().getState());
        assertEquals(0, sql.queryForObject("select count(*) from orders where customer_id=?", Integer.class, customer));
    }

    private String catalog(String sku) throws Exception {
        Map<String, Object> wire = new HashMap<>(Map.of("productId", "p1", "sellerId", "seller1", "storeId", "store1",
            "name", "Fixture", "available", true, "sellerApproved", true, "storeOpen", true, "houseStore", false,
            "unitPrice", new BigDecimal("10000")));
        if (sku != null) wire.put("sku", sku);
        return json.writeValueAsString(wire);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
