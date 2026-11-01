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
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The real Seller image endpoints must keep reviewed content behind the Product lifecycle. */
@Tag("postgresql")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SellerProductImageGateIntegrationTest.AuthorityFixture.class)
class SellerProductImageGateIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "https://images.example.invalid", AUDIENCE = "images-api", OWNER = "image-owner";
    private static final String BOUNDARY = "image-gate-boundary";
    private static final UUID STORE = UUID.randomUUID(), SELLER = UUID.randomUUID();
    private static final HttpServer INVENTORY = inventory();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        String images = Files.createTempDirectory("product-images").toString();
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("JWT_ISSUER_URI", () -> ISSUER);
        r.add("JWT_AUDIENCE", () -> AUDIENCE);
        r.add("JWT_HS256_CURRENT_KID", () -> "images");
        r.add("JWT_HS256_CURRENT_KEY_B64", () -> Base64.getEncoder().encodeToString(KEY.getBytes(StandardCharsets.UTF_8)));
        r.add("PRODUCT_IMAGE_DIR", () -> images);
        r.add("INVENTORY_INTERNAL_URL", () -> "http://127.0.0.1:" + INVENTORY.getAddress().getPort());
    }

    @LocalServerPort int port;
    @Autowired ProductApplicationService products;
    @Autowired JdbcTemplate sql;

    private final HttpClient client = HttpClient.newHttpClient();

    /** No contexts: every balance lookup is a 404, which the Seller view reads as no Inventory row. */
    private static HttpServer inventory() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
            return server;
        } catch (java.io.IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @AfterAll static void stopInventory() {
        INVENTORY.stop(0);
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

    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "PENDING_REVIEW", "SUSPENDED"})
    void reviewedProductsRefuseImageChanges(String status) throws Exception {
        UUID product = product();
        assertEquals(200, upload(product, version(product)).statusCode());
        UUID image = image(product);
        status(product, status);
        long version = version(product);

        HttpResponse<String> added = upload(product, version);
        assertEquals(409, added.statusCode(), added.body());
        assertTrue(added.body().contains("PRODUCT_INVALID_STATE"), added.body());
        assertEquals(409, delete(product, image, version).statusCode());

        assertEquals(1, images(product));
        assertEquals(status, status(product));
        assertEquals(version, version(product));
    }

    @Test void uploadOnAnInactiveProductCannotBeActivatedWithoutReview() throws Exception {
        UUID product = product();
        status(product, "INACTIVE");
        assertEquals(200, upload(product, version(product)).statusCode());
        assertEquals("DRAFT", status(product));
        assertEquals(1, images(product));
        assertEquals(409, activate(product).statusCode());
        assertEquals("DRAFT", status(product));
    }

    @Test void deleteOnAnInactiveProductCannotBeActivatedWithoutReview() throws Exception {
        UUID product = product();
        assertEquals(200, upload(product, version(product)).statusCode());
        UUID image = image(product);
        status(product, "INACTIVE");
        assertEquals(200, delete(product, image, version(product)).statusCode());
        assertEquals("DRAFT", status(product));
        assertEquals(0, images(product));
        assertEquals(409, activate(product).statusCode());
    }

    @Test void imageChangesFollowTheProductVersion() throws Exception {
        UUID product = product();
        long version = version(product);
        HttpResponse<String> uploaded = upload(product, version);
        assertEquals(200, uploaded.statusCode(), uploaded.body());
        assertEquals(version + 1, version(product), "a submit read before the upload must go stale");
        assertTrue(uploaded.body().contains("\"version\":\"" + (version + 1) + "\""), uploaded.body());

        assertEquals(409, upload(product, version).statusCode());
        assertEquals(409, delete(product, image(product), version).statusCode());
        assertEquals(1, images(product));
    }

    @Test void imageChangesRequireAnExpectedVersion() throws Exception {
        UUID product = product();
        assertEquals(400, send(multipart(product, null)).statusCode());
        assertEquals(0, images(product));
    }

    private UUID product() {
        UUID category = UUID.randomUUID();
        sql.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Images " + category);
        return products.create(OWNER, STORE, category, "SKU-" + UUID.randomUUID(), "Image fixture", new BigDecimal("1000"), "VND")
                .listing().id();
    }

    private HttpResponse<String> upload(UUID product, long version) throws Exception {
        return send(multipart(product, version));
    }

    private HttpRequest.Builder multipart(UUID product, Long version) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (version != null) {
            body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"expectedVersion\"\r\n\r\n" + version + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"a.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", body);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("/api/seller/products/" + product + "/images").header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
    }

    private HttpResponse<String> delete(UUID product, UUID image, long version) throws Exception {
        return send(request("/api/seller/products/" + product + "/images/" + image + "?expectedVersion=" + version).DELETE());
    }

    private HttpResponse<String> activate(UUID product) throws Exception {
        return send(request("/api/seller/products/" + product + "/activate").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":\"" + version(product) + "\"}")));
    }

    private HttpRequest.Builder request(String path) throws Exception {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void status(UUID product, String status) {
        sql.update("UPDATE products SET status=?,review_reason=? WHERE id=?", status, status.equals("SUSPENDED") ? "Policy" : null, product);
    }

    private String status(UUID product) {
        return sql.queryForObject("SELECT status FROM products WHERE id=?", String.class, product);
    }

    private long version(UUID product) {
        return sql.queryForObject("SELECT version FROM products WHERE id=?", Long.class, product);
    }

    private int images(UUID product) {
        return sql.queryForObject("SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, product);
    }

    private UUID image(UUID product) {
        return sql.queryForObject("SELECT id FROM product_images WHERE product_id=?", UUID.class, product);
    }

    private String token() throws Exception {
        Date issued = new Date();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("images").build(),
            new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(OWNER).jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("SELLER")).issueTime(issued).notBeforeTime(issued)
                .expirationTime(Date.from(Instant.now().plusSeconds(120))).build());
        jwt.sign(new MACSigner(KEY.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
