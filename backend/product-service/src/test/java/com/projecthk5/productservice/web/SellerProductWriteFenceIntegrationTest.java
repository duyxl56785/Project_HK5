package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Tag("postgresql")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "security.current-authority.enabled=true", "AUTH_AUTHORITY_TOKEN=pair"})
@Import(SellerProductWriteFenceIntegrationTest.AuthorityFixture.class)
class SellerProductWriteFenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "https://fence.example.invalid", AUDIENCE = "fence-api", OWNER = "fence-owner";
    private static final UUID STORE = UUID.randomUUID(), SELLER = UUID.randomUUID();
    private static final HttpServer PEERS = peers();
    private static volatile UUID reservation;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("JWT_ISSUER_URI", () -> ISSUER);
        r.add("JWT_AUDIENCE", () -> AUDIENCE);
        r.add("JWT_HS256_CURRENT_KID", () -> "fence");
        r.add("JWT_HS256_CURRENT_KEY_B64", () -> Base64.getEncoder().encodeToString(KEY.getBytes(StandardCharsets.UTF_8)));
        String url = "http://127.0.0.1:" + PEERS.getAddress().getPort();
        r.add("AUTH_INTERNAL_URL", () -> url);
        // Inventory has no row for the SKU: this stub answers 404 on everything but Auth's reservations.
        r.add("INVENTORY_INTERNAL_URL", () -> url);
    }

    @LocalServerPort int port;
    @Autowired ProductApplicationService products;
    @Autowired JdbcTemplate sql;
    @Autowired FilterChainProxy filters;

    private final HttpClient client = HttpClient.newHttpClient();

    private static HttpServer peers() {
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

    @AfterAll static void stopPeers() {
        PEERS.stop(0);
    }

    @TestConfiguration static class AuthorityFixture {
        @Primary @Bean ProductAccessPolicy policy() {
            return new ProductAccessPolicy() {
                private final StoreAccess access = new StoreAccess(STORE, SELLER, OWNER, true, true, false);
                @Override public UUID currentManagementStore(String actor) { return STORE; }
                @Override public StoreAccess requireSellerManagement(String actor, UUID storeId) { return access; }
                @Override public StoreAccess requireModeration(String actor, UUID storeId) { return access; }
                @Override public StoreAccess readStore(UUID storeId) { return access; }
            };
        }
    }

    @Test void sellerEditHeldInItsTransactionRollsBackWhenAuthClosesItsReservation() throws Exception {
        assertTrue(filters.getFilters("/api/seller/products/x").stream().anyMatch(CurrentAuthorityFilter.class::isInstance));
        StoredProduct product = seed();
        try (Connection holder = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("select 1 from products where id=? for update")) {
                lock.setObject(1, product.listing().id());
                lock.executeQuery();
            }
            CompletableFuture<HttpResponse<String>> write = CompletableFuture.supplyAsync(() -> edit(product));
            awaitBlockedOnTheRow();
            assertEquals(204, close(reservation));
            holder.rollback();
            HttpResponse<String> outcome = write.get(15, TimeUnit.SECONDS);
            assertEquals(401, outcome.statusCode(), outcome.body());
            assertTrue(outcome.body().contains("AUTH_ACCESS_INVALID"), outcome.body());
        }
        assertEquals("Fence fixture", name(product));
    }

    @Test void writeWhoseReservationStaysOpenCommits() throws Exception {
        StoredProduct product = seed();
        HttpResponse<String> edited = edit(product);
        assertEquals(200, edited.statusCode(), edited.body());
        assertEquals(204, close(reservation));
        assertEquals("Fence edited", name(product));
    }

    private StoredProduct seed() {
        UUID category = UUID.randomUUID();
        sql.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Fence " + category);
        return products.create(OWNER, STORE, category, "SKU-" + UUID.randomUUID(), "Fence fixture", new BigDecimal("1000"), "VND");
    }

    private HttpResponse<String> edit(StoredProduct product) {
        try {
            String body = "{\"categoryId\":\"" + product.listing().categoryId() + "\",\"name\":\"Fence edited\","
                + "\"unitPrice\":2000,\"expectedVersion\":\"" + product.version() + "\"}";
            return client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/api/seller/products/" + product.listing().id()))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }

    private int close(UUID operation) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + port + "/internal/write-fences/" + operation + "/close"))
                .header("X-Internal-Token", "pair")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private void awaitBlockedOnTheRow() throws InterruptedException {
        for (int tries = 0; tries < 200; tries++) {
            Integer blocked = sql.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%products%'", Integer.class);
            if (blocked != null && blocked > 0) return;
            Thread.sleep(50);
        }
        fail("The Seller write never reached the held row");
    }

    private String name(StoredProduct product) {
        return sql.queryForObject("select name from products where id=?", String.class, product.listing().id());
    }

    private String token() throws Exception {
        Date issued = new Date();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("fence").build(),
            new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(OWNER).jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("SELLER")).issueTime(issued).notBeforeTime(issued)
                .expirationTime(Date.from(Instant.now().plusSeconds(120))).build());
        jwt.sign(new MACSigner(KEY.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
