package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.security.CurrentAuthorityFilter;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A real Seller write held inside its transaction by a row lock while Auth closes its reservation.
 * Auth is a stub here; its real revoke and disable paths are covered by Cart's HTTP race test.
 */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.outbox.relay-enabled=false", "security.current-authority.enabled=true", "AUTH_AUTHORITY_TOKEN=pair"})
class SellerWriteFencePostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "https://fence.example.invalid", AUDIENCE = "fence-api";
    private static final HttpServer AUTH = auth();
    private static volatile UUID reservation;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> ISSUER);
        r.add("app.security.audience", () -> AUDIENCE);
        r.add("app.security.current-kid", () -> "fence");
        r.add("app.security.current-key-b64", () -> Base64.getEncoder().encodeToString(KEY.getBytes(StandardCharsets.UTF_8)));
        r.add("AUTH_INTERNAL_URL", () -> "http://127.0.0.1:" + AUTH.getAddress().getPort());
    }

    @LocalServerPort int port;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate sql;
    @Autowired FilterChainProxy filters;
    @MockBean StoreDirectory stores;

    private final HttpClient client = HttpClient.newHttpClient();
    private final String store = UUID.randomUUID().toString(), owner = "owner-" + UUID.randomUUID();

    private static HttpServer auth() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/accounts/", exchange -> {
                boolean take = "POST".equals(exchange.getRequestMethod());
                if (take) reservation = UUID.randomUUID();
                byte[] body = (take ? "{\"granted\":true,\"id\":\"" + reservation + "\"}" : "").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(take ? 200 : 204, take ? body.length : -1);
                if (take) exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @AfterAll static void stopAuth() {
        AUTH.stop(0);
    }

    @Test void sellerWriteHeldInItsTransactionRollsBackWhenAuthClosesItsReservation() throws Exception {
        assertTrue(filters.getFilters("/api/seller/orders/x/accept").stream().anyMatch(CurrentAuthorityFilter.class::isInstance));
        SellerOrder order = seed();
        try (Connection holder = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("select 1 from seller_orders where id=? for update")) {
                lock.setObject(1, order.getId());
                lock.executeQuery();
            }
            CompletableFuture<HttpResponse<String>> write = CompletableFuture.supplyAsync(() -> accept(order));
            awaitBlockedOnTheRow();
            assertEquals(204, close(reservation, "pair"));
            holder.rollback();
            HttpResponse<String> outcome = write.get(15, TimeUnit.SECONDS);
            assertEquals(401, outcome.statusCode(), outcome.body());
            assertTrue(outcome.body().contains("AUTH_ACCESS_INVALID"), outcome.body());
        }
        assertEquals("PLACED", status(order));
        assertEquals(0, sql.queryForObject("select count(*) from outbox_events where aggregate_id=?", Integer.class, order.getId()));
    }

    @Test void writeWhoseReservationStaysOpenCommitsAndALateCloseChangesNothing() throws Exception {
        SellerOrder order = seed();
        assertEquals(200, accept(order).statusCode());
        assertEquals(204, close(reservation, "pair"));
        assertEquals("ACCEPTED", status(order));
    }

    @Test void closeRequiresThePairToken() throws Exception {
        assertEquals(401, close(UUID.randomUUID(), "wrong"));
    }

    private SellerOrder seed() {
        when(stores.managedBy(owner)).thenReturn(new StoreDirectory.Store(store, "seller-1", owner, "Shop", "shop", true, true, false));
        String customer = "customer-" + UUID.randomUUID();
        CheckoutAttempt attempt = attempts.saveAndFlush(new CheckoutAttempt(customer, "k-" + UUID.randomUUID(), "fp"));
        Order order = new Order(attempt.getId(), customer);
        order.ship(new Order.Shipping("Nguyen Van A", "0901234567", "1 Le Loi"));
        SellerOrder seller = new SellerOrder(order, "seller-1", store, new BigDecimal("200000"), new BigDecimal("10000"));
        seller.getItems().add(new OrderItem(seller, "p1", 2, new BigDecimal("100000"), new BigDecimal("200000"), "SKU-1", "Ao"));
        order.addSellerOrder(seller);
        orders.saveAndFlush(order);
        return seller;
    }

    private HttpResponse<String> accept(SellerOrder order) {
        try {
            return client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/seller/orders/" + order.getId() + "/accept"))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }

    private int close(UUID operation, String pair) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + port + "/internal/write-fences/" + operation + "/close"))
                .header("X-Internal-Token", pair)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private void awaitBlockedOnTheRow() throws InterruptedException {
        for (int tries = 0; tries < 200; tries++) {
            Integer blocked = sql.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%seller_orders%'", Integer.class);
            if (blocked != null && blocked > 0) return;
            Thread.sleep(50);
        }
        fail("The Seller write never reached the held row");
    }

    private String status(SellerOrder order) {
        return sql.queryForObject("select status from seller_orders where id=?", String.class, order.getId());
    }

    private String token() throws Exception {
        Date issued = new Date();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("fence").build(),
            new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(owner).jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("SELLER")).issueTime(issued).notBeforeTime(issued)
                .expirationTime(Date.from(Instant.now().plusSeconds(120))).build());
        jwt.sign(new MACSigner(KEY.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
