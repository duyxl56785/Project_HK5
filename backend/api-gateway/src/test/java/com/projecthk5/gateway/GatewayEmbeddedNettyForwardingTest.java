package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Real Netty-bound Gateway + WebTestClient + separate HTTP upstream.
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
    private record ForwardedRequest(String method, String uri, Map<String, List<String>> headers, String body) {}

    private final ConcurrentLinkedQueue<ForwardedRequest> observed = new ConcurrentLinkedQueue<>();
    private reactor.netty.DisposableServer upstream;
    private ConfigurableApplicationContext gateway;
    private WebTestClient client;

    @BeforeAll
    void startRealServers() throws Exception {
        upstream = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    request.requestHeaders().forEach(entry -> headers
                            .computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                            .add(entry.getValue()));
                    return request.receive().aggregate().asString().defaultIfEmpty("")
                            .flatMap(body -> {
                                observed.add(new ForwardedRequest(request.method().name(), request.uri(), headers, body));
                                return response.status(200).header("Content-Type", "text/plain")
                                        .sendString(reactor.core.publisher.Mono.just("forwarded")).then();
                            });
                }).bindNow(java.time.Duration.ofSeconds(10));

        Map<String, Object> runtime = new HashMap<>();
        runtime.put("HK5_AUTH_UPSTREAM_URI", "http://127.0.0.1:" + upstream.port());
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
        if (upstream != null) upstream.disposeNow(java.time.Duration.ofSeconds(5));
        fixtureProcessEnv = Map.of();
    }

    @Test
    void dummyUpstreamIsReachableIndependentOfGateway() throws Exception {
        observed.clear();
        var http = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://127.0.0.1:" + upstream.port() + "/direct"))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET().build();
        var result = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode(), "Netty dummy upstream did not respond");
        assertEquals("forwarded", result.body());
        assertNotNull(observed.poll());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void actualNettyPublicMutationsForwardWithout500AndStripAllNineSpoofedHeaders(String path) {
        observed.clear();
        String body = path.endsWith("register") || path.endsWith("login")
                ? "{\"email\":\"fixture@example.invalid\",\"password\":\"fixture-only\"}" : "";
        var request = client.post().uri(path)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header("X-CSRF-Protection", "1")
                .header(HttpHeaders.COOKIE, "refresh_token=fixture-only")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer stale-allowed-on-public-post");
        for (String spoof : SPOOFED) {
            request.header(spoof, "client-controlled")
                    .header(spoof.toLowerCase(Locale.ROOT), "duplicate-forgery");
        }
        request.bodyValue(body).exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("forwarded");
        var forwarded = verifyForwarding("POST", path, body, "Bearer stale-allowed-on-public-post");
        assertEquals("refresh_token=fixture-only", first(forwarded.headers(), HttpHeaders.COOKIE));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, first(forwarded.headers(), HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void actualNettySignedMeForwardsWithout500AndStripsAllNineSpoofedHeaders() throws Exception {
        observed.clear();
        String bearer = "Bearer " + signedCustomerJwt();
        var request = client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header("X-CSRF-Protection", "1");
        for (String spoof : SPOOFED) {
            request.header(spoof, "client-controlled")
                    .header(spoof.toLowerCase(Locale.ROOT), "duplicate-forgery");
        }
        request.exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("forwarded");
        verifyForwarding("GET", "/api/auth/me", "", bearer);
    }

    @Test
    void invalidBearerCannotReachActualNettyUpstream() {
        observed.clear();
        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();
        assertTrue(observed.isEmpty());
    }

    @Test
    void missingBearerCannotBeReplacedBySpoofedIdentity() {
        observed.clear();
        var request = client.get().uri("/api/auth/me");
        for (String spoof : SPOOFED) request.header(spoof, "ADMIN");
        request.exchange().expectStatus().isUnauthorized();
        assertTrue(observed.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/me/extra", "/api/auth/unknown",
            "/internal", "/internal/accounts", "/api/products"})
    void signedRequestsOutsideExactGetAllowlistNeverReachUpstream(String path) throws Exception {
        observed.clear();
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + signedCustomerJwt())
                .exchange().expectStatus().isNotFound();
        assertTrue(observed.isEmpty());
    }

    @Test
    void signedMeWithWrongMethodNeverReachesUpstream() throws Exception {
        observed.clear();
        client.post().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedCustomerJwt())
                .exchange().expectStatus().isEqualTo(405).expectHeader().valueEquals("Allow", "GET");
        assertTrue(observed.isEmpty());
    }

    @Test
    void publicMutationWrongMethodAndUnknownPreflightFollowExactContract() throws Exception {
        client.get().uri("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + signedCustomerJwt())
                .exchange().expectStatus().isEqualTo(405).expectHeader().valueEquals("Allow", "POST");
        client.get().uri("/internal/accounts").exchange().expectStatus().isNotFound();
        client.get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_ACCESS_REQUIRED");
        client.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_ACCESS_INVALID");
        client.options().uri("/api/auth/unknown").header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange().expectStatus().isForbidden();
        client.options().uri("/api/auth/login").header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void untrustedOriginIsRejectedBeforePublicForwarding() {
        observed.clear();
        client.post().uri("/api/auth/login").header(HttpHeaders.ORIGIN, "https://untrusted.example.invalid")
                .header("X-CSRF-Protection", "1").exchange().expectStatus().isForbidden();
        assertTrue(observed.isEmpty());
    }

    @Test
    void trustedPreflightAllowsOnlyReviewedHeadersWithoutUpstreamCall() {
        observed.clear();
        client.options().uri("/api/auth/login").header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type, X-CSRF-Protection")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TRUSTED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        assertTrue(observed.isEmpty());
    }

    @Test
    void preflightCannotAuthorizeSpoofedInternalHeader() {
        observed.clear();
        client.options().uri("/api/auth/login").header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Internal-Token")
                .exchange().expectStatus().isForbidden();
        assertTrue(observed.isEmpty());
    }

    private ForwardedRequest verifyForwarding(String method, String path, String body, String expectedBearer) {
        var forwarded = observed.poll();
        assertNotNull(forwarded, "Actual HTTP upstream was not contacted");
        assertEquals(method, forwarded.method());
        assertEquals(path, forwarded.uri());
        assertEquals(body, forwarded.body());
        for (String spoof : SPOOFED) {
            assertFalse(forwarded.headers().containsKey(spoof), "Spoofed header leaked upstream: " + spoof);
        }
        assertEquals(expectedBearer, first(forwarded.headers(), HttpHeaders.AUTHORIZATION));
        assertEquals(TRUSTED_ORIGIN, first(forwarded.headers(), HttpHeaders.ORIGIN));
        assertEquals("1", first(forwarded.headers(), "X-CSRF-Protection"));
        assertTrue(observed.isEmpty(), "Only one upstream request expected");
        return forwarded;
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
