package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Real gateway routing against a local stub upstream that records what it received. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {
    record Seen(String service, String path, String userIdHeader, String internalTokenHeader, String authorization) {}

    static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    static final HttpServer UPSTREAM = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String service = exchange.getRequestHeaders().getFirst("Host");
                SEEN.add(new Seen(service, exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                        exchange.getRequestHeaders().getFirst("Authorization")));
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() { UPSTREAM.stop(0); }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry r) {
        // One stub per service name via distinct loopback hosts that all resolve to the stub port.
        int port = UPSTREAM.getAddress().getPort();
        for (String svc : new String[]{"AUTH", "USER", "SELLER", "PRODUCT", "CART", "ORDER", "NOTIFICATION"})
            r.add(svc + "_URL", () -> "http://127.0.0.1:" + port + "/" + svc.toLowerCase());
        r.add("ALLOWED_ORIGINS", () -> "http://localhost:5173,https://shop.example.test");
    }

    @Autowired WebTestClient web;

    @BeforeEach
    void clear() { SEEN.clear(); }

    private Seen only() {
        assertEquals(1, SEEN.size(), SEEN.toString());
        return SEEN.get(0);
    }

    @Test
    void routesEachPublicPrefixToItsService() {
        String[][] cases = {
                {"/api/auth/login", "/api/auth/login"},
                {"/api/users/me/addresses", "/api/users/me/addresses"},
                {"/api/admin/sellers/x/approve", "/api/admin/sellers/x/approve"},
                {"/api/stores/by-slug/a", "/api/stores/by-slug/a"},
                {"/api/catalog/products", "/api/catalog/products"},
                {"/api/seller/products/1/images", "/api/seller/products/1/images"},
                {"/api/cart/items", "/api/cart/items"},
                {"/api/seller/orders/1/accept", "/api/seller/orders/1/accept"},
                {"/api/admin/finance", "/api/admin/finance"},
                {"/api/notifications/unread-count", "/api/notifications/unread-count"}};
        for (String[] c : cases) {
            SEEN.clear();
            web.get().uri(c[0]).exchange().expectStatus().isOk();
            assertEquals(c[1], only().path(), c[0]);
        }
    }

    @Test
    void separatesSellerProductAndSellerOrderRoutes() {
        // Same /api/seller prefix, different owners: the gateway must not mix them up.
        assertNotEquals(routeOf("/api/seller/products"), routeOf("/api/seller/orders"));
        assertEquals(routeOf("/api/orders"), routeOf("/api/seller/orders"));
        assertEquals(routeOf("/api/catalog/products"), routeOf("/api/seller/products"));
    }

    @Autowired org.springframework.cloud.gateway.route.RouteLocator routes;

    private String routeOf(String path) {
        return routes.getRoutes().filter(r -> r.getPredicate() != null)
                .filterWhen(r -> r.getPredicate().apply(org.springframework.mock.web.server.MockServerWebExchange.from(
                        org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(path).build())))
                .map(org.springframework.cloud.gateway.route.Route::getId).blockFirst();
    }

    @Test
    void neverExposesInternalOrInventoryRoutes() {
        web.get().uri("/internal/stores/eligible").exchange().expectStatus().isNotFound();
        web.post().uri("/internal/notifications").exchange().expectStatus().isNotFound();
        web.post().uri("/api/inventory/operations").exchange().expectStatus().isNotFound();
        web.get().uri("/api/unknown").exchange().expectStatus().isNotFound();
        assertTrue(SEEN.isEmpty(), SEEN.toString());
        assertTrue(EdgeGuardFilter.blocked("/INTERNAL/x"));
        assertTrue(EdgeGuardFilter.blocked("/api/catalog/%2e%2e/internal"));
    }

    @Test
    void stripsForgedIdentityHeadersButKeepsBearer() {
        web.get().uri("/api/orders").header("X-User-Id", "admin").header("X-Internal-Token", "stolen")
                .header("Authorization", "Bearer abc").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
        Seen seen = only();
        assertNull(seen.userIdHeader());
        assertNull(seen.internalTokenHeader());
        assertEquals("Bearer abc", seen.authorization());
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsWithCredentials() {
        web.options().uri("/api/auth/login").header("Origin", "https://shop.example.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-protection")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://shop.example.test")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
        web.options().uri("/api/auth/login").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange().expectStatus().isForbidden();
        assertTrue(SEEN.isEmpty());
    }
}
