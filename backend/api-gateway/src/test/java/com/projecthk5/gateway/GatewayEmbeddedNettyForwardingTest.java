package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Real Netty-bound Gateway + WebTestClient + separate JDK HTTP upstream.
 * Uses production route/decoder/CORS/security beans, not MockServerHttpRequest.
 * Fixture-only process environment: no deployment secrets or production flags.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayEmbeddedNettyForwardingTest {
    private static final String TRUSTED_ORIGIN = "https://frontend.example.invalid";
    private static final byte[] FIXTURE_KEY = new byte[32];
    private static final List<String> SPOOFED = List.of(
            "X-User-Id", "X-Customer-Id", "X-Seller-Id", "X-Store-Id",
            "X-Roles", "X-Authorities", "X-Authenticated-User",
            "X-Internal-Token", "X-Service-Name");

    // StandardReactiveWebEnvironment calls getSystemEnvironment while constructing.
    private static volatile Map<String, Object> fixtureProcessEnv = Map.of();
    private final ConcurrentLinkedQueue<Map<String, List<String>>> observed = new ConcurrentLinkedQueue<>();
    private HttpServer upstream;
    private ConfigurableApplicationContext gateway;
    private WebTestClient client;

    @BeforeAll
    void startRealServers() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", request -> {
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            request.getRequestHeaders().forEach((name, values) ->
                    headers.put(name, List.copyOf(values)));
            observed.add(headers);
            request.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            byte[] response = "forwarded".getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "text/plain");
            request.sendResponseHeaders(200, response.length);
            try (var output = request.getResponseBody()) {
                output.write(response);
            }
        });
        upstream.start();

        Map<String, Object> runtime = new HashMap<>();
        runtime.put("HK5_AUTH_UPSTREAM_URI", "http://127.0.0.1:" + upstream.getAddress().getPort());
        runtime.put("HK5_AUTH_ALLOWED_ORIGINS", TRUSTED_ORIGIN);
        runtime.put("HK5_AUTH_JWT_ISSUER", "https://identity.example.invalid");
        runtime.put("HK5_AUTH_JWT_AUDIENCE", "smart-ecommerce-api");
        runtime.put("HK5_AUTH_JWT_CURRENT_KID", "fixture-kid");
        runtime.put("HK5_AUTH_JWT_CURRENT_KEY_B64",
                Base64.getEncoder().encodeToString(FIXTURE_KEY));
        fixtureProcessEnv = Map.copyOf(runtime);

        var environment = new StandardReactiveWebEnvironment() {
            @Override
            public Map<String, Object> getSystemEnvironment() {
                return fixtureProcessEnv;
            }
        };
        environment.getPropertySources().addFirst(new MapPropertySource("fixture-opt-in", Map.of(
                "hk5.gateway.auth.enabled", "true",
                "server.port", "0",
                "spring.main.web-application-type", "reactive")));
        SpringApplication application = new SpringApplication(ApiGatewayApplication.class);
        application.setWebApplicationType(WebApplicationType.REACTIVE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setEnvironment(environment);
        gateway = application.run();
        int gatewayPort = ((ReactiveWebServerApplicationContext) gateway).getWebServer().getPort();
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gatewayPort)
                .responseTimeout(java.time.Duration.ofSeconds(15))
                .build();
    }

    @AfterAll
    void closeServers() {
        if (gateway != null) gateway.close();
        if (upstream != null) upstream.stop(0);
        fixtureProcessEnv = Map.of();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void actualNettyPublicMutationsForwardWithout500AndStripAllNineSpoofedHeaders(String path) {
        observed.clear();
        var request = client.post().uri(path)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header("X-CSRF-Protection", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer stale-allowed-on-public-post");
        for (String spoof : SPOOFED) request.header(spoof, "client-controlled");
        request.exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("forwarded");
        verifyForwarding("Bearer stale-allowed-on-public-post");
    }

    @Test
    void actualNettySignedMeForwardsWithout500AndStripsAllNineSpoofedHeaders() throws Exception {
        observed.clear();
        String bearer = "Bearer " + signedCustomerJwt();
        var request = client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header("X-CSRF-Protection", "1");
        for (String spoof : SPOOFED) request.header(spoof, "client-controlled");
        request.exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("forwarded");
        verifyForwarding(bearer);
    }

    @Test
    void invalidBearerCannotReachActualNettyUpstream() {
        observed.clear();
        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();
        assertTrue(observed.isEmpty());
    }

    private void verifyForwarding(String expectedBearer) {
        var forwarded = observed.poll();
        assertNotNull(forwarded, "Actual HTTP upstream was not contacted");
        for (String spoof : SPOOFED) {
            assertFalse(forwarded.containsKey(spoof), "Spoofed header leaked upstream: " + spoof);
        }
        assertEquals(expectedBearer, first(forwarded, HttpHeaders.AUTHORIZATION));
        assertEquals(TRUSTED_ORIGIN, first(forwarded, HttpHeaders.ORIGIN));
        assertEquals("1", first(forwarded, "X-CSRF-Protection"));
        assertTrue(observed.isEmpty(), "Only one upstream request expected");
    }

    private static String first(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String signedCustomerJwt() throws Exception {
        Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).keyID("fixture-kid").build();
        var claims = new JWTClaimsSet.Builder()
                .issuer("https://identity.example.invalid")
                .audience("smart-ecommerce-api")
                .subject("00000000-0000-4000-8000-000000000001")
                .issueTime(Date.from(issued))
                .notBeforeTime(Date.from(issued))
                .expirationTime(Date.from(issued.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("CUSTOMER"))
                .build();
        var signed = new SignedJWT(header, claims);
        signed.sign(new MACSigner(FIXTURE_KEY));
        return signed.serialize();
    }
}
