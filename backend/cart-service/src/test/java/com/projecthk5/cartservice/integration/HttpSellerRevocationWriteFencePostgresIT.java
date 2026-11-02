package com.projecthk5.cartservice.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.authservice.AuthServiceApplication;
import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.cartservice.CartServiceApplication;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.CurrentAuthorityFilter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-service QA regression: a real Cart HTTP write has a live Auth reservation while Auth revokes
 * SELLER through its own internal HTTP endpoint. Product is deliberately a tiny HTTP fixture: it is not
 * the subject under test and only provides the catalog response needed to enter Cart's transaction.
 */
@Tag("postgres-integration")
@Testcontainers
class HttpSellerRevocationWriteFencePostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_B64 = Base64.getEncoder().encodeToString(KEY);
    private static final String ISSUER = "https://tv5-write-fence.test";
    private static final String AUDIENCE = "smart-ecommerce";
    private static final String AUTH_APP_PASSWORD = UUID.randomUUID().toString();
    private static final String AUTH_MIGRATION_PASSWORD = UUID.randomUUID().toString();
    private static ConfigurableApplicationContext auth;
    private static ConfigurableApplicationContext cart;
    private static HttpServer product;
    private static int authPort;
    private static int cartPort;
    private static CountDownLatch productEntered;
    private static CountDownLatch releaseProduct;
    private static UUID accountId;

    @BeforeAll static void startServices() throws Exception {
        PG.start();
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '" + AUTH_MIGRATION_PASSWORD + "'");
            statement.execute("CREATE ROLE auth_app LOGIN PASSWORD '" + AUTH_APP_PASSWORD + "'");
            statement.execute("CREATE DATABASE auth_db OWNER auth_migrator TEMPLATE template0");
            statement.execute("CREATE DATABASE tv5_cart");
        }
        try (Connection connection = DriverManager.getConnection(database("auth_db"), PG.getUsername(), PG.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("REVOKE ALL ON DATABASE auth_db FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE auth_db TO auth_app");
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        }
        authPort = freePort();
        cartPort = freePort();
        startProductFixture();
        auth = app(AuthServiceApplication.class, props(
                "server.port", Integer.toString(authPort),
                "spring.datasource.url", database("auth_db"),
                "spring.datasource.username", "auth_app",
                "spring.datasource.password", AUTH_APP_PASSWORD,
                "spring.flyway.enabled", "true",
                "spring.flyway.url", database("auth_db"),
                "spring.flyway.locations", migrationLocation("auth-service"),
                "spring.flyway.user", "auth_migrator",
                "spring.flyway.password", AUTH_MIGRATION_PASSWORD,
                "AUTH_DB_PASSWORD", AUTH_APP_PASSWORD,
                "AUTH_MIGRATION_PASSWORD", AUTH_MIGRATION_PASSWORD,
                "security.current-authority.enabled", "false",
                "JWT_ISSUER_URI", ISSUER,
                "JWT_AUDIENCE", AUDIENCE,
                "JWT_HS256_CURRENT_KID", "tv5",
                "JWT_HS256_CURRENT_KEY_B64", KEY_B64,
                "SELLER_AUTH_TOKEN", "admin-pair",
                "WRITE_FENCE_TARGETS", "pair@http://127.0.0.1:" + cartPort,
                "ADMIN_BOOTSTRAP_EMAIL", "",
                "ADMIN_BOOTSTRAP_PASSWORD", ""));
        cart = app(CartServiceApplication.class, props(
                "server.port", Integer.toString(cartPort),
                "spring.datasource.url", database("tv5_cart"),
                "spring.datasource.username", PG.getUsername(),
                "spring.datasource.password", PG.getPassword(),
                "spring.flyway.enabled", "true",
                "spring.flyway.url", database("tv5_cart"),
                "spring.flyway.locations", migrationLocation("cart-service"),
                "spring.flyway.user", PG.getUsername(),
                "spring.flyway.password", PG.getPassword(),
                "HK5_CART_DB_PASSWORD", PG.getPassword(),
                "JWT_ISSUER_URI", ISSUER,
                "JWT_AUDIENCE", AUDIENCE,
                "JWT_HS256_CURRENT_KID", "tv5",
                "JWT_HS256_CURRENT_KEY_B64", KEY_B64,
                "app.security.issuer", ISSUER,
                "app.security.audience", AUDIENCE,
                "app.security.current-kid", "tv5",
                "app.security.current-key-b64", KEY_B64,
                "security.current-authority.enabled", "true",
                "AUTH_INTERNAL_URL", "http://127.0.0.1:" + authPort,
                "AUTH_AUTHORITY_TOKEN", "pair",
                "PRODUCT_INTERNAL_URL", "http://127.0.0.1:" + product.getAddress().getPort(),
                "CART_PRODUCT_TOKEN", "product-pair"));
        Account account = auth.getBean(AuthService.class).create("race-" + UUID.randomUUID() + "@test.local", "not-a-real-password",
                Set.of(Account.Role.CUSTOMER, Account.Role.SELLER));
        accountId = account.getId();
    }

    @AfterAll static void stopServices() {
        if (cart != null) cart.close();
        if (auth != null) auth.close();
        if (product != null) product.stop(0);
        PG.stop();
    }

    @Test void authenticatedCartWriteRollsBackWhenAuthRevokesSellerBeforeCommit() throws Exception {
        productEntered = new CountDownLatch(1);
        releaseProduct = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();
        String bearer = token(accountId);
        assertTrue(new String(Base64.getUrlDecoder().decode(bearer.split("\\.")[0]), StandardCharsets.UTF_8).contains("\"typ\":\"JWT\""));
        assertEquals(accountId.toString(), cart.getBean(JwtDecoder.class).decode(bearer).getSubject());
        assertTrue(cart.getBean(FilterChainProxy.class).getFilters("/api/cart/items").stream()
                .anyMatch(CurrentAuthorityFilter.class::isInstance), "Cart API chain must include CurrentAuthorityFilter");
        CompletableFuture<HttpResponse<String>> write = CompletableFuture.supplyAsync(() -> send(client,
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + cartPort + "/api/cart/items"))
                        .header("Authorization", "Bearer " + bearer)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"productId\":\"product-1\",\"quantity\":1}")).build()));

        if (!productEntered.await(10, TimeUnit.SECONDS)) {
            HttpResponse<String> early = write.get(5, TimeUnit.SECONDS);
            fail("Cart business request never reached the live transaction: HTTP " + early.statusCode() + " " + early.body());
        }
        assertEquals(1, auth.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from write_reservations where account_id = ?", Integer.class, accountId));

        HttpResponse<String> revoke = send(client, HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + authPort + "/internal/accounts/" + accountId + "/roles/SELLER"))
                .header("X-Internal-Token", "admin-pair")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"granted\":false,\"revision\":1}")).build());
        assertEquals(200, revoke.statusCode(), revoke.body());

        releaseProduct.countDown();
        HttpResponse<String> outcome = write.get(15, TimeUnit.SECONDS);
        int cartRows = cart.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from carts where customer_id = ?", Integer.class, accountId.toString());
        int reservations = auth.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from write_reservations where account_id = ?", Integer.class, accountId);
        System.out.println("TV5_HTTP_FENCE_OUTCOME status=" + outcome.statusCode()
                + " cartRows=" + cartRows + " reservations=" + reservations);
        assertEquals(0, cartRows);
        assertEquals(0, reservations);
        assertEquals(401, outcome.statusCode(), outcome.body());
        assertTrue(outcome.body().contains("AUTH_ACCESS_INVALID"), outcome.body());
    }

    @Test void writeCommittedBeforeRevokeStaysAndTheSameTokenIsThenRefused() throws Exception {
        productEntered = new CountDownLatch(1);
        releaseProduct = new CountDownLatch(0);
        UUID account = auth.getBean(AuthService.class).create("order-" + UUID.randomUUID() + "@test.local", "not-a-real-password",
                Set.of(Account.Role.CUSTOMER, Account.Role.SELLER)).getId();
        HttpClient client = HttpClient.newHttpClient();
        String bearer = token(account);
        HttpRequest add = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + cartPort + "/api/cart/items"))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"productId\":\"product-1\",\"quantity\":1}")).build();
        HttpResponse<String> committed = send(client, add);
        assertTrue(committed.statusCode() / 100 == 2, committed.body());

        HttpResponse<String> revoke = send(client, HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + authPort + "/internal/accounts/" + account + "/roles/SELLER"))
                .header("X-Internal-Token", "admin-pair")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"granted\":false,\"revision\":1}")).build());
        assertEquals(200, revoke.statusCode(), revoke.body());

        HttpResponse<String> stale = send(client, add);
        assertEquals(401, stale.statusCode(), stale.body());
        assertTrue(stale.body().contains("AUTH_ACCESS_INVALID"), stale.body());
        assertEquals(1, cart.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from carts where customer_id = ?", Integer.class, account.toString()));
        assertEquals(0, auth.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from write_reservations where account_id = ?", Integer.class, account));
    }

    @Test void disablingAnAccountStopsItsWriteInFlightAndEndsItsSessions() throws Exception {
        productEntered = new CountDownLatch(1);
        releaseProduct = new CountDownLatch(1);
        AuthService service = auth.getBean(AuthService.class);
        String email = "lock-" + UUID.randomUUID() + "@test.local";
        UUID account = service.create(email, "not-a-real-password", Set.of(Account.Role.CUSTOMER)).getId();
        UUID admin = service.create("admin-" + UUID.randomUUID() + "@test.local", "not-a-real-password",
                Set.of(Account.Role.ADMIN, Account.Role.CUSTOMER)).getId();
        String refresh = service.login(email, "not-a-real-password").refreshToken();
        HttpClient client = HttpClient.newHttpClient();
        String bearer = token(account, "CUSTOMER"), adminBearer = token(admin, "ADMIN", "CUSTOMER");
        CompletableFuture<HttpResponse<String>> write = CompletableFuture.supplyAsync(() -> send(client,
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + cartPort + "/api/cart/items"))
                        .header("Authorization", "Bearer " + bearer)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"productId\":\"product-1\",\"quantity\":1}")).build()));
        assertTrue(productEntered.await(10, TimeUnit.SECONDS), "Cart request never reached the live transaction");

        assertEquals(403, adminAction(client, bearer, account, "disable").statusCode());
        assertEquals(409, adminAction(client, adminBearer, admin, "disable").statusCode());
        HttpResponse<String> disabled = adminAction(client, adminBearer, account, "disable");
        assertEquals(200, disabled.statusCode(), disabled.body());
        assertTrue(disabled.body().contains("\"status\":\"DISABLED\""), disabled.body());

        releaseProduct.countDown();
        HttpResponse<String> outcome = write.get(15, TimeUnit.SECONDS);
        assertEquals(401, outcome.statusCode(), outcome.body());
        assertTrue(outcome.body().contains("AUTH_ACCESS_INVALID"), outcome.body());
        assertEquals(0, cart.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from carts where customer_id = ?", Integer.class, account.toString()));
        assertEquals(0, auth.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from write_reservations where account_id = ?", Integer.class, account));
        assertThrows(ApiException.class, () -> service.login(email, "not-a-real-password"));

        assertEquals(200, adminAction(client, adminBearer, account, "enable").statusCode());
        assertTrue(service.refresh(refresh).rejected(), "A session from before the lock must stay dead");
        assertEquals(email, service.login(email, "not-a-real-password").account().getEmail());
        HttpResponse<String> found = send(client, HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + authPort + "/api/admin/accounts?term=" + email))
                .header("Authorization", "Bearer " + adminBearer).GET().build());
        assertEquals(200, found.statusCode(), found.body());
        assertTrue(found.body().contains("\"status\":\"ACTIVE\""), found.body());
    }

    /** Pins a known limit: Auth compares status and roles, not when the token was issued. */
    @Test void anAccessTokenFromBeforeALockIsRefusedWhileLockedAndWorksAgainAfterUnlock() throws Exception {
        productEntered = new CountDownLatch(1);
        releaseProduct = new CountDownLatch(0);
        AuthService service = auth.getBean(AuthService.class);
        UUID account = service.create("unlock-" + UUID.randomUUID() + "@test.local", "not-a-real-password",
                Set.of(Account.Role.CUSTOMER)).getId();
        UUID admin = service.create("admin-" + UUID.randomUUID() + "@test.local", "not-a-real-password",
                Set.of(Account.Role.ADMIN, Account.Role.CUSTOMER)).getId();
        HttpClient client = HttpClient.newHttpClient();
        String bearer = token(account, "CUSTOMER"), adminBearer = token(admin, "ADMIN", "CUSTOMER");
        HttpRequest add = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + cartPort + "/api/cart/items"))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"productId\":\"product-1\",\"quantity\":1}")).build();

        assertEquals(200, adminAction(client, adminBearer, account, "disable").statusCode());
        HttpResponse<String> locked = send(client, add);
        assertEquals(401, locked.statusCode(), locked.body());
        assertTrue(locked.body().contains("AUTH_ACCESS_INVALID"), locked.body());

        assertEquals(200, adminAction(client, adminBearer, account, "enable").statusCode());
        // shortcut: the old token writes again until it expires (900s + skew), add an issued-after check before production.
        HttpResponse<String> unlocked = send(client, add);
        assertTrue(unlocked.statusCode() / 100 == 2, unlocked.body());
    }

    private static HttpResponse<String> adminAction(HttpClient client, String bearer, UUID account, String action) {
        return send(client, HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + authPort + "/api/admin/accounts/" + account + "/" + action))
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    private static void startProductFixture() throws Exception {
        product = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        product.createContext("/internal/catalog/products/product-1", exchange -> {
            assertEquals("product-pair", exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            productEntered.countDown();
            try {
                try {
                    if (!releaseProduct.await(15, TimeUnit.SECONDS))
                        throw new IllegalStateException("test did not release the Cart request");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                byte[] body = "{\"productId\":\"product-1\",\"sellerId\":\"seller-1\",\"storeId\":\"store-1\",\"available\":true,\"sellerApproved\":true,\"storeOpen\":true,\"unitPrice\":\"10000\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        product.start();
    }

    private static ConfigurableApplicationContext app(Class<?> source, Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("tv5-http-fence", properties));
        return new SpringApplicationBuilder(source).web(WebApplicationType.SERVLET)
                .environment(environment).run();
    }

    private static Map<String, Object> props(Object... values) {
        assertEquals(0, values.length % 2);
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2)
            result.put((String) values[index], values[index + 1]);
        return result;
    }

    private static String database(String name) {
        return PG.getJdbcUrl().replace("/test", "/" + name).replace("/postgres", "/" + name);
    }

    private static String migrationLocation(String module) {
        Path path = "cart-service".equals(module)
                ? Path.of("src", "main", "resources", "db", "migration")
                : Path.of("..", module, "src", "main", "resources", "db", "migration");
        return "filesystem:" + path.toAbsolutePath().normalize();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String token(UUID subject) throws Exception {
        return token(subject, "CUSTOMER", "SELLER");
    }

    private static String token(UUID subject, String... roles) throws Exception {
        Date issued = new Date();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("tv5").build(),
                new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(subject.toString())
                        .jwtID(UUID.randomUUID().toString()).claim("roles", List.of(roles))
                        .issueTime(issued).notBeforeTime(issued).expirationTime(Date.from(Instant.now().plusSeconds(120))).build());
        jwt.sign(new MACSigner(KEY));
        return jwt.serialize();
    }
}
