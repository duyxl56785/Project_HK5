package com.projecthk5.orderservice.integration;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Real #107 HTTP/JWT verifier/PostgreSQL, test-issued tokens and stored synthetic Order fixtures. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.checkout.reconcile-scan-ms=86400000")
class OrderHistoryUiAcceptanceTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final byte[] KEY = new byte[32];
    static { new SecureRandom().nextBytes(KEY); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://ui-history.example.invalid");
        r.add("app.security.audience", () -> "ui-history-test");
        r.add("app.security.current-kid", () -> "ui-history-test");
        r.add("app.security.current-key-b64", () -> Base64.getEncoder().encodeToString(KEY));
    }
    @LocalServerPort int port;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate db;

    @Test void actualFrontendClientAndDomUseRealOrderHistoryHttp() throws Exception {
        String a = "customer-A-"+UUID.randomUUID(), b = "customer-B-"+UUID.randomUUID();
        UUID own = seed(a, Instant.parse("2026-10-20T00:00:00Z"));
        for (int i=0; i<20; i++) seed(a, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i));
        UUID foreign = seed(b, Instant.parse("2026-10-20T00:00:00Z"));
        var big = new Order(UUID.randomUUID(), a); var amount = new BigDecimal("99999999999999999.99");
        var store = new SellerOrder(big, "seller-large", "store-large", amount, BigDecimal.ZERO);
        store.getItems().add(new OrderItem(store, "product-large", 1, amount, amount));
        big.addSellerOrder(store); orders.saveAndFlush(big);
        db.update("update orders set created_at=? where id=?", java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")), big.getId());

        Path frontend = Path.of(System.getProperty("tv3.ui.root"));
        var builder = new ProcessBuilder("node", "--experimental-strip-types", "--test",
                "src/features/orders/OrderHistory.real.test.mjs").directory(frontend.toFile()).redirectErrorStream(true);
        var env = builder.environment();
        env.put("TV3_HISTORY_URL", "http://127.0.0.1:"+port);
        env.put("TV3_HISTORY_TOKEN_A", token(a)); env.put("TV3_HISTORY_TOKEN_B", token(b));
        env.put("TV3_HISTORY_ORDER_A", own.toString()); env.put("TV3_HISTORY_ORDER_B", foreign.toString());
        env.put("TV3_HISTORY_BIG_ID", big.getId().toString()); env.put("TV3_DOM_MODULE", System.getProperty("tv3.dom.module"));
        Process process = builder.start();
        var output = CompletableFuture.supplyAsync(() -> {
            try { return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        if (!process.waitFor(120, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Frontend real-HTTP suite timed out"); }
        String result = output.get(10, TimeUnit.SECONDS); System.out.println(result);
        assertEquals(0, process.exitValue(), "Real HTTP client/DOM suite must pass");
        assertTrue(result.contains("pass 8")); assertTrue(result.contains("fail 0")); assertTrue(result.contains("skipped 0"));
    }
    private UUID seed(String customer, Instant time) {
        var order = new Order(UUID.randomUUID(), customer);
        for (String s : List.of("A", "B")) {
            var unit = new BigDecimal(s.equals("A") ? "10.00" : "20.00");
            var store = new SellerOrder(order, "seller-"+s, "store-"+s, unit.multiply(BigDecimal.valueOf(2)), BigDecimal.ZERO);
            for (int i=0; i<2; i++) store.getItems().add(new OrderItem(store, "product-"+s+"-"+i, 1, unit, unit));
            order.addSellerOrder(store);
        }
        orders.saveAndFlush(order);
        db.update("update orders set created_at=? where id=?", java.sql.Timestamp.from(time), order.getId());
        return order.getId();
    }
    private static String token(String customer) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("https://ui-history.example.invalid").subject(customer)
                .audience("ui-history-test").issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600))).jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("CUSTOMER")).build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("ui-history-test").build(), claims);
        signed.sign(new MACSigner(KEY)); return signed.serialize();
    }
}
