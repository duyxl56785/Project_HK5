package com.projecthk5.orderservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.OrderHistoryService;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, JWT cryptographic verification and PostgreSQL. No mocked beans or repositories. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.checkout.reconcile-scan-ms=86400000")
@Import(OrderHistoryPostgresIT.Database.class)
class OrderHistoryPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final byte[] KEY = new byte[32];
    static { new SecureRandom().nextBytes(KEY); }
    private static final String ISSUER = "https://history-test.example.invalid";
    private static final Instant STORED_TIME = Instant.parse("2026-10-01T12:00:00Z");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> ISSUER);
        r.add("app.security.audience", () -> "history-test");
        r.add("app.security.current-kid", () -> "history-test");
        r.add("app.security.current-key-b64", () -> Base64.getEncoder().encodeToString(KEY));
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate db;
    @Autowired OrderHistoryService history;
    @Autowired CountingDataSource counted;

    @Test void customerReadsRealStoredOrderAndTwoStoreItemsOverHttp() throws Exception {
        String customer = customer(); UUID id = seed(customer, STORED_TIME, 2);
        var listResponse = get("/api/orders", token(customer, List.of("CUSTOMER")));
        assertEquals(200, listResponse.getStatusCode().value());
        JsonNode list = json.readTree(listResponse.getBody());
        assertEquals(1, list.path("totalElements").asInt());
        var summary = list.path("content").get(0);
        assertEquals(id.toString(), summary.path("orderId").asText());
        assertEquals("PLACED", summary.path("status").asText());
        assertEquals("60.00", summary.path("total").asText());
        assertTrue(summary.path("total").isTextual());
        assertEquals(STORED_TIME.toString(), summary.path("createdAt").asText());
        assertEquals(2, summary.path("sellerOrderCount").asInt());
        assertEquals(Set.of("orderId","status","total","createdAt","sellerOrderCount"), fields(summary));
        var detailResponse = get("/api/orders/" + id, token(customer, List.of("CUSTOMER")));
        assertEquals(200, detailResponse.getStatusCode().value());
        JsonNode detail = json.readTree(detailResponse.getBody());
        assertEquals(2, detail.path("sellerOrders").size());
        Set<String> stores = new HashSet<>();
        for (JsonNode store : detail.path("sellerOrders")) {
            stores.add(store.path("storeId").asText());
            assertEquals(Set.of("sellerOrderId","storeId","gross","items"), fields(store));
            assertEquals(2, store.path("items").size());
            for (JsonNode item : store.path("items")) {
                assertEquals(Set.of("productId","quantity","unitPriceSnapshot","lineTotal"), fields(item));
                assertEquals(1, item.path("quantity").asInt());
                String price = store.path("storeId").asText().equals("store-A") ? "10.00" : "20.00";
                assertEquals(price, item.path("unitPriceSnapshot").asText());
                assertEquals(price, item.path("lineTotal").asText());
            }
        }
        assertEquals(Set.of("store-A", "store-B"), stores);
        assertEquals(Set.of("orderId","status","total","createdAt","sellerOrders"), fields(detail));
        assertFalse(detailResponse.getBody().contains("idempotency"));
        Path samples = Path.of("build/order-history-samples"); Files.createDirectories(samples);
        Files.writeString(samples.resolve("list.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(list));
        Files.writeString(samples.resolve("detail.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(detail));
    }

    @Test void foreignAndMissingOrderAreIndistinguishable404() throws Exception {
        UUID id = seed(customer(), STORED_TIME, 1);
        String bearer = token(customer(), List.of("CUSTOMER"));
        var foreign = get("/api/orders/" + id, bearer);
        var absent = get("/api/orders/" + UUID.randomUUID(), bearer);
        assertEquals(404, foreign.getStatusCode().value());
        assertEquals(404, absent.getStatusCode().value());
        assertEquals(json.readTree(absent.getBody()), json.readTree(foreign.getBody()));
    }

    @Test void listIsScopedToJwtSubjectEvenWithForgedCustomerQueryParameter() throws Exception {
        String a = customer(), b = customer();
        UUID aid = seed(a, STORED_TIME, 1), bid = seed(b, STORED_TIME, 1);
        JsonNode list = body(get("/api/orders?customerId=" + a, token(b, List.of("CUSTOMER"))));
        assertEquals(1, list.path("totalElements").asInt());
        assertEquals(bid.toString(), list.path("content").get(0).path("orderId").asText());
        assertNotEquals(aid.toString(), list.path("content").get(0).path("orderId").asText());
    }

    @Test void paginationUsesOrderRowsAndStableNewestFirstTieBreaker() throws Exception {
        String customer = customer(); var tied = new ArrayList<UUID>();
        for (int i=0; i<5; i++) tied.add(seed(customer, STORED_TIME, 3));
        UUID newest = seed(customer, STORED_TIME.plusSeconds(1), 3);
        tied.sort(Comparator.comparing(UUID::toString).reversed());
        var expected = new ArrayList<UUID>(); expected.add(newest); expected.addAll(tied);
        var actual = new ArrayList<String>(); String bearer = token(customer, List.of("CUSTOMER"));
        for (int page=0; page<3; page++) {
            JsonNode result = body(get("/api/orders?page="+page+"&size=2", bearer));
            assertEquals(6, result.path("totalElements").asInt()); assertEquals(3, result.path("totalPages").asInt());
            assertEquals(page, result.path("page").asInt()); assertEquals(2, result.path("size").asInt());
            assertEquals(2, result.path("content").size());
            for (JsonNode order : result.path("content")) {
                actual.add(order.path("orderId").asText()); assertEquals(2, order.path("sellerOrderCount").asInt());
            }
        }
        assertEquals(expected.stream().map(UUID::toString).toList(), actual);
        assertEquals(6, new HashSet<>(actual).size());
        assertEquals(0, body(get("/api/orders?page=100&size=2", bearer)).path("content").size());
        assertEquals(2, body(get("/api/orders?page=0&size=2", bearer)).path("content").size());
    }

    @Test void emptyHistoryAndInvalidPaginationAreHandled() throws Exception {
        String bearer = token(customer(), List.of("CUSTOMER"));
        JsonNode empty = body(get("/api/orders", bearer));
        assertEquals(0, empty.path("content").size()); assertEquals(0, empty.path("totalElements").asInt());
        assertEquals(0, empty.path("totalPages").asInt());
        for (String query : List.of("page=-1", "size=0", "size=101", "page=bad", "size=2147483648"))
            assertEquals(400, get("/api/orders?"+query, bearer).getStatusCode().value(), query);
        assertEquals(400, get("/api/orders/not-a-uuid", bearer).getStatusCode().value());
        assertEquals(200, get("/api/orders?page=2147483647&size=100", bearer).getStatusCode().value());
    }

    @Test void realSecurityRejectsAbsentInvalidExpiredAndNonCustomerTokens() throws Exception {
        UUID id = seed(customer(), STORED_TIME, 1);
        for (String path : List.of("/api/orders", "/api/orders/" + id)) {
            assertEquals(401, get(path, null).getStatusCode().value());
            assertEquals(401, get(path, "invalid.jwt.value").getStatusCode().value());
            byte[] wrongKey = new byte[32]; new SecureRandom().nextBytes(wrongKey);
            assertEquals(401, get(path, token(customer(), List.of("CUSTOMER"), wrongKey, false)).getStatusCode().value());
            assertEquals(401, get(path, token(customer(), List.of("CUSTOMER"), KEY, true)).getStatusCode().value());
            assertEquals(401, get(path, token(customer(), List.of())).getStatusCode().value());
            assertEquals(403, get(path, token(customer(), List.of("SELLER"))).getStatusCode().value());
            assertEquals(403, get(path, token(customer(), List.of("ADMIN"))).getStatusCode().value());
        }
    }

    @Test void queryCountIsBoundedIndependentOfPageSizeOrChildCount() {
        String customer = customer(); UUID id = null;
        for (int i=0; i<25; i++) id = seed(customer, STORED_TIME, 3);
        counted.reset(); var one = history.list(customer, 0, 1);
        assertEquals(1, one.content().size()); assertEquals(2, counted.selects());
        counted.reset(); var all = history.list(customer, 0, 25);
        assertEquals(25, all.content().size()); assertEquals(25, all.totalElements());
        assertEquals(2, counted.selects(), "No extra query per Order/SellerOrder");
        counted.reset(); var detail = history.detail(customer, id);
        assertEquals(2, detail.sellerOrders().size()); assertEquals(3, detail.sellerOrders().get(0).items().size());
        assertEquals(2, counted.selects(), "No extra query per Store/item");
    }

    @Test void decimalSnapshotsAreLosslessStringsEvenAboveJavascriptIntegerPrecision() throws Exception {
        String customer = customer(); String maximum = "99999999999999999.99";
        BigDecimal money = new BigDecimal(maximum);
        var order = new Order(UUID.randomUUID(), customer);
        var child = new SellerOrder(order, "seller", "store", money, BigDecimal.ZERO);
        child.getItems().add(new OrderItem(child, "product", 1, money, money));
        order.addSellerOrder(child); orders.saveAndFlush(order);
        JsonNode detail = body(get("/api/orders/"+order.getId(), token(customer, List.of("CUSTOMER"))));
        assertEquals(maximum, detail.path("total").asText()); assertTrue(detail.path("total").isTextual());
        var stored = detail.path("sellerOrders").get(0);
        assertEquals(maximum, stored.path("gross").asText());
        assertEquals(maximum, stored.path("items").get(0).path("unitPriceSnapshot").asText());
        assertEquals(maximum, stored.path("items").get(0).path("lineTotal").asText());
    }

    @Test void storeWithNoItemsIsNotDroppedOrReturnedAsANullProduct() throws Exception {
        String customer = customer(); UUID id = seed(customer, STORED_TIME, 0);
        JsonNode detail = body(get("/api/orders/"+id, token(customer, List.of("CUSTOMER"))));
        assertEquals(2, detail.path("sellerOrders").size());
        detail.path("sellerOrders").forEach(s -> assertEquals(0, s.path("items").size()));
    }

    private UUID seed(String customer, Instant time, int itemsPerStore) {
        Order order = new Order(UUID.randomUUID(), customer);
        for (String store : List.of("A","B")) {
            BigDecimal unit = new BigDecimal(store.equals("A") ? "10.00" : "20.00");
            var child = new SellerOrder(order, "seller-"+store, "store-"+store,
                    unit.multiply(BigDecimal.valueOf(itemsPerStore)), BigDecimal.ZERO);
            for (int i=0; i<itemsPerStore; i++) child.getItems().add(new OrderItem(child,
                    "product-"+store+"-"+i, 1, unit, unit));
            order.addSellerOrder(child);
        }
        orders.saveAndFlush(order);
        db.update("update orders set created_at=? where id=?", java.sql.Timestamp.from(time), order.getId());
        return order.getId();
    }
    private static String customer() { return "customer-"+UUID.randomUUID(); }
    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    private JsonNode body(ResponseEntity<String> response) throws Exception {
        assertEquals(200, response.getStatusCode().value()); return json.readTree(response.getBody());
    }
    private static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }
    private static String token(String customer, List<String> roles) throws Exception { return token(customer, roles, KEY, false); }
    private static String token(String customer, List<String> roles, byte[] key, boolean expired) throws Exception {
        Instant now = Instant.now(); Instant issued = expired ? now.minusSeconds(300) : now;
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(customer).audience("history-test")
                .issueTime(Date.from(issued)).notBeforeTime(Date.from(issued))
                .expirationTime(Date.from(expired ? now.minusSeconds(120) : now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString()).claim("roles", roles).build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("history-test").build(), claims);
        signed.sign(new MACSigner(key)); return signed.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean(destroyMethod = "close") CountingDataSource dataSource(DataSourceProperties properties) {
            return new CountingDataSource(properties.initializeDataSourceBuilder().type(HikariDataSource.class).build());
        }
    }
    /** Observes actual SELECT executions; delegates all calls to the real PostgreSQL driver. */
    static class CountingDataSource extends DelegatingDataSource implements AutoCloseable {
        private final ThreadLocal<Integer> selects = ThreadLocal.withInitial(() -> 0);
        CountingDataSource(HikariDataSource target) { super(target); }
        void reset() { selects.set(0); }
        int selects() { return selects.get(); }
        @Override public Connection getConnection() throws java.sql.SQLException { return wrap(super.getConnection()); }
        @Override public Connection getConnection(String u, String p) throws java.sql.SQLException { return wrap(super.getConnection(u,p)); }
        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                try {
                    Object result = method.invoke(connection, args);
                    if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                        String sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                            if (m.getName().startsWith("execute") && sql.startsWith("select")) selects.set(selects.get()+1);
                            try { return m.invoke(statement, a); } catch (InvocationTargetException e) { throw e.getCause(); }
                        });
                    }
                    return result;
                } catch (InvocationTargetException e) { throw e.getCause(); }
            });
        }
        @Override public void close() { ((HikariDataSource) getTargetDataSource()).close(); }
    }
}
