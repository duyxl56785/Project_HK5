package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Real Gateway Netty -> separate Auth bootJar/Tomcat -> disposable PG16, no mocked beans. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayAuthPostgresE2ETest {
    private static final String ORIGIN = "https://frontend.example.invalid";
    private static final String ISSUER = "https://identity.example.invalid";
    private static final String AUDIENCE = "smart-ecommerce-api";
    private static final String PASSWORD = "fixture-only-long-password";
    private static final List<String> SPOOFED = List.of("X-User-Id", "X-Customer-Id", "X-Seller-Id",
            "X-Store-Id", "X-Roles", "X-Authorities", "X-Authenticated-User", "X-Internal-Token", "X-Service-Name");
    private final ObjectMapper json = new ObjectMapper();
    // No CookieManager: Secure cookies are inspected and explicitly replayed over loopback HTTP.
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final byte[] key = new SecureRandom().generateSeed(32);
    private final byte[] previousKey = new SecureRandom().generateSeed(32);
    private final String appPassword = UUID.randomUUID().toString();
    private final String migrationPassword = UUID.randomUUID().toString();
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("postgres").withUsername("hk5").withPassword(UUID.randomUUID().toString())
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../../docker/postgres/pg_hba.conf").toAbsolutePath()),
                    "/etc/postgresql/test_hba.conf")
            .withCommand("postgres", "-c", "hba_file=/etc/postgresql/test_hba.conf");
    private ConfigurableApplicationContext gateway;
    private Process auth;
    private String authUrl;
    private String gatewayUrl;
    private String jdbcUrl;
    private static volatile Map<String, Object> gatewayEnvironment = Map.of();

    @BeforeAll
    void database() throws Exception {
        // Docker absence fails the test; no disabledWithoutDocker, reuse or shared database.
        postgres.start();
        try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var s = c.createStatement()) {
            s.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '" + migrationPassword + "'");
            s.execute("CREATE ROLE auth_app LOGIN PASSWORD '" + appPassword + "'");
            s.execute("CREATE DATABASE auth_db OWNER auth_migrator TEMPLATE template0");
        }
        jdbcUrl = postgres.getJdbcUrl().replace("/postgres", "/auth_db");
        sql("REVOKE ALL ON DATABASE auth_db FROM PUBLIC",
                "GRANT CONNECT ON DATABASE auth_db TO auth_app",
                "REVOKE ALL ON SCHEMA public FROM PUBLIC", "GRANT USAGE ON SCHEMA public TO auth_app");
        assertEquals("16", scalar("SELECT current_setting('server_version_num')::int / 10000").toString());
    }

    @BeforeEach
    void applications(TestInfo test) throws Exception {
        boolean enabled = !test.getTags().contains("default-off");
        // New Auth process resets only its single-instance throttle; DB state stays durable.
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        authUrl = "http://127.0.0.1:" + port;
        var runtime = new HashMap<String, String>();
        runtime.put("HK5_AUTH_ALLOWED_ORIGINS", ORIGIN);
        runtime.put("HK5_AUTH_JWT_ISSUER", ISSUER);
        runtime.put("HK5_AUTH_JWT_AUDIENCE", AUDIENCE);
        runtime.put("HK5_AUTH_JWT_CURRENT_KID", "e2e-current");
        runtime.put("HK5_AUTH_JWT_CURRENT_KEY_B64", Base64.getEncoder().encodeToString(key));
        runtime.put("HK5_AUTH_JWT_PREVIOUS_KID", "e2e-previous");
        runtime.put("HK5_AUTH_JWT_PREVIOUS_KEY_B64", Base64.getEncoder().encodeToString(previousKey));
        runtime.put("HK5_AUTH_ACTIVE_STATUS", "ACTIVE");
        runtime.put("AUTH_DB_PASSWORD", appPassword);
        runtime.put("AUTH_MIGRATION_PASSWORD", migrationPassword);
        runtime.put("SPRING_DATASOURCE_URL", jdbcUrl);
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.timezone=UTC", "-jar", System.getProperty("hk5.test.authJar"),
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.profiles.active=migration",
                "--spring.datasource.hikari.connection-timeout=1000", "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.main.banner-mode=off",
                "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration");
        if (enabled) command.command().addAll(List.of("--hk5.auth.bootstrap.enabled=true", "--hk5.auth.http.enabled=true"));
        // Isolate from developer/CI runtime secrets and Java agents; never print process env.
        command.environment().keySet().removeIf(name -> name.startsWith("HK5_") || name.startsWith("SPRING_")
                || name.startsWith("AUTH_") || List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(name));
        command.environment().putAll(runtime);
        Path log = Path.of("build", "auth-e2e", test.getTestMethod().orElseThrow().getName() + ".log");
        Files.createDirectories(log.getParent());
        auth = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        boolean ready = false;
        while (auth.isAlive() && System.nanoTime() < deadline) {
            try {
                ready = send(authUrl, "GET", "/actuator/health", "", Map.of()).statusCode() == 200;
                if (ready) break;
            } catch (java.io.IOException starting) { /* connection not yet bound */ }
            Thread.sleep(100);
        }
        assertTrue(ready, "Auth bootJar did not become healthy; see " + log);
        var env = new HashMap<String, Object>(runtime);
        env.put("HK5_AUTH_UPSTREAM_URI", authUrl);
        gatewayEnvironment = Map.copyOf(env);
        var environment = new StandardReactiveWebEnvironment() {
            @Override public Map<String, Object> getSystemEnvironment() { return gatewayEnvironment; }
        };
        var properties = new HashMap<String, Object>(Map.of("server.address", "127.0.0.1", "server.port", "0"));
        if (enabled) properties.put("hk5.gateway.auth.enabled", "true");
        environment.getPropertySources().addFirst(new MapPropertySource("e2e-only", properties));
        var application = new SpringApplication(ApiGatewayApplication.class);
        application.setWebApplicationType(WebApplicationType.REACTIVE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setEnvironment(environment);
        gateway = application.run();
        gatewayUrl = "http://127.0.0.1:" + ((ReactiveWebServerApplicationContext) gateway).getWebServer().getPort();
    }

    @AfterEach
    void stopApplications() throws Exception {
        if (gateway != null) { gateway.close(); gateway = null; }
        if (auth != null) {
            auth.destroy();
            if (!auth.waitFor(10, TimeUnit.SECONDS)) {
                auth.destroyForcibly();
                assertTrue(auth.waitFor(10, TimeUnit.SECONDS), "Auth JVM must stop before DB cleanup");
            }
        }
    }

    @AfterAll
    void stopDatabase() { postgres.stop(); }

    @Test
    @Tag("default-off")
    void defaultFlagsKeepBothRealApplicationsClosed() throws Exception {
        long accounts = number("SELECT count(*) FROM accounts");
        for (String base : List.of(authUrl, gatewayUrl)) {
            for (String route : List.of("register", "login", "refresh", "logout")) {
                int status = send(base, "POST", "/api/auth/" + route, credentials(email()), mutationHeaders(null)).statusCode();
                assertTrue(status == 401 || status == 403, "Default-off must reject every Auth mutation");
            }
            int status = me(base, signed(UUID.randomUUID().toString(), ISSUER, AUDIENCE, "e2e-current", key, 0)).statusCode();
            assertTrue(status == 401 || status == 403, "Default-off must reject even a signed JWT");
        }
        assertEquals(accounts, number("SELECT count(*) FROM accounts"));
    }

    @Test
    void singleInstanceThrottleIsEnforcedThroughRealHttp() throws Exception {
        int throttled = 0;
        long started = System.nanoTime();
        // 41 requests exceed 20/min even if this short burst crosses one minute boundary.
        for (int i = 0; i < 41; i++) {
            var headers = mutationHeaders(null);
            headers.put("X-Forwarded-For", "192.0.2." + i);
            var response = send(gatewayUrl, "POST", "/api/auth/refresh", "", headers);
            if (response.statusCode() == 429) {
                error(response, 429, "AUTH_RATE_LIMITED");
                assertEquals("60", header(response, "Retry-After"));
                throttled++;
            } else error(response, 401, "AUTH_REFRESH_INVALID");
        }
        assertTrue(System.nanoTime() - started < Duration.ofSeconds(60).toNanos());
        assertTrue(throttled > 0, "Real servlet gate must throttle, not a mocked guard");
    }

    @Test
    void registerLoginRotateReplayLogoutAndIndependentFamilies() throws Exception {
        String email = email();
        var registered = post("register", credentials(email), null);
        privateStatus(registered, 201);
        assertEquals("/api/auth/me", header(registered, "Location"));
        assertTrue(registered.headers().allValues("Set-Cookie").isEmpty());
        String id = body(registered).get("userId").asText();
        assertEquals(List.of("CUSTOMER"), roles(body(registered)));
        assertEquals(1L, number("SELECT count(*) FROM profile_provisioning_tasks WHERE account_id='" + id + "'"));
        assertNotEquals(PASSWORD, scalar("SELECT password_hash FROM accounts WHERE id='" + id + "'"));
        privateStatus(post("register", credentials(email), null), 409);
        var first = login(email);
        var jwt = SignedJWT.parse(token(first));
        assertTrue(jwt.verify(new MACVerifier(key)), "Use actual Auth-issued signature");
        assertEquals(ISSUER, jwt.getJWTClaimsSet().getIssuer());
        assertEquals(List.of(AUDIENCE), jwt.getJWTClaimsSet().getAudience());
        assertEquals(id, jwt.getJWTClaimsSet().getSubject());
        assertEquals("e2e-current", jwt.getHeader().getKeyID());
        assertEquals(900, body(first).get("expiresIn").asInt());
        assertEquals(900000, jwt.getJWTClaimsSet().getExpirationTime().getTime() - jwt.getJWTClaimsSet().getIssueTime().getTime());
        cookiePolicy(first, false);
        assertEquals(4, body(first).size());
        privateStatus(me(gatewayUrl, token(first)), 200);
        privateStatus(send(gatewayUrl, "GET", "/api/auth/me", "", Map.of("Cookie", cookie(first))), 401);
        var otherDevice = login(email);
        var rotated = post("refresh", "", cookie(first));
        privateStatus(rotated, 200);
        cookiePolicy(rotated, false);
        assertNotEquals(cookie(first), cookie(rotated));
        assertEquals(familyExpiry(cookie(first)), familyExpiry(cookie(rotated)));
        String hash = hashCookie(cookie(first));
        assertEquals(1L, number("SELECT count(*) FROM refresh_tokens WHERE token_hash='" + hash + "' AND used_at IS NOT NULL"));
        var replay = post("refresh", "", cookie(first));
        error(replay, 401, "AUTH_REFRESH_INVALID");
        cookiePolicy(replay, true);
        assertEquals(1L, number("SELECT count(*) FROM refresh_families f JOIN refresh_tokens t ON t.family_id=f.id WHERE t.token_hash='"
                + hash + "' AND f.revoked_at IS NOT NULL"));
        error(post("refresh", "", cookie(rotated)), 401, "AUTH_REFRESH_INVALID");
        var otherRotated = post("refresh", "", cookie(otherDevice));
        privateStatus(otherRotated, 200);
        var logout = post("logout", "", cookie(otherDevice)); // historical token revokes its family
        privateStatus(logout, 204);
        assertTrue(logout.body().isEmpty());
        cookiePolicy(logout, true);
        error(post("refresh", "", cookie(otherRotated)), 401, "AUTH_REFRESH_INVALID");
        privateStatus(post("logout", "", null), 204);
        privateStatus(post("logout", "", cookie(otherDevice)), 204);
        privateStatus(me(gatewayUrl, token(otherRotated)), 200); // logout does not promise JWT revocation
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(new SecureRandom().generateSeed(32));
        String expiredCookie = "refresh_token=" + raw;
        error(post("refresh", "", expiredCookie), 401, "AUTH_REFRESH_INVALID"); // unknown token
        String family = UUID.randomUUID().toString();
        sql("INSERT INTO refresh_families(id,account_id,created_at,expires_at) VALUES ('" + family + "','" + id
                        + "',now()-interval '8 days',now()-interval '1 day')",
                "INSERT INTO refresh_tokens(id,family_id,token_hash,created_at,expires_at) SELECT '" + UUID.randomUUID()
                        + "',id,'" + hashCookie(expiredCookie) + "',created_at,expires_at FROM refresh_families WHERE id='" + family + "'");
        var expired = post("refresh", "", expiredCookie);
        error(expired, 401, "AUTH_REFRESH_INVALID");
        cookiePolicy(expired, true);
    }

    @Test
    void currentRolesNeverPromoteStaleJwtAndRevocationFailsClosed() throws Exception {
        String email = register();
        var customer = login(email);
        String id = body(customer).get("account").get("userId").asText();
        // DB fixture only; no invented Seller->Auth wire command or public role endpoint.
        sql("INSERT INTO account_role_grants VALUES ('" + id + "','ADMIN'),('" + id + "','SELLER')");
        assertEquals(List.of("CUSTOMER"), roles(body(me(gatewayUrl, token(customer)))));
        var promoted = post("refresh", "", cookie(customer));
        privateStatus(promoted, 200);
        assertEquals(List.of("ADMIN", "CUSTOMER", "SELLER"), roles(body(promoted).get("account")));
        assertEquals(List.of("ADMIN", "CUSTOMER", "SELLER"), roles(body(me(gatewayUrl, token(promoted)))));
        sql("DELETE FROM account_role_grants WHERE account_id='" + id + "' AND role IN ('ADMIN','SELLER')");
        assertEquals(List.of("CUSTOMER"), roles(body(me(gatewayUrl, token(promoted)))));
        sql("DELETE FROM account_role_grants WHERE account_id='" + id + "'",
                "INSERT INTO account_role_grants VALUES ('" + id + "','SELLER')");
        error(me(gatewayUrl, token(customer)), 403, "AUTH_FORBIDDEN");
        sql("UPDATE accounts SET status='DISABLED' WHERE id='" + id + "'");
        error(me(gatewayUrl, token(promoted)), 401, "AUTH_ACCESS_INVALID");
        error(post("refresh", "", cookie(promoted)), 401, "AUTH_REFRESH_INVALID");
        error(post("login", credentials(email), null), 401, "AUTH_INVALID_CREDENTIALS");
        privateStatus(me(gatewayUrl, signed(UUID.randomUUID().toString(), ISSUER, AUDIENCE, "e2e-current", key, 0)), 401);
    }

    @Test
    void issuerVerifierRejectInvalidSignaturesClaimsAndUnknownKeysAtBothHttpBoundaries() throws Exception {
        var session = login(register());
        String id = body(session).get("account").get("userId").asText();
        var invalid = List.of("not-a-jwt",
                signed(id, "https://wrong.example.invalid", AUDIENCE, "e2e-current", key, 0),
                signed(id, ISSUER, "wrong-audience", "e2e-current", key, 0),
                signed(id, ISSUER, AUDIENCE, "unknown", key, 0),
                signed(id, ISSUER, AUDIENCE, "e2e-current", previousKey, 0),
                signed(id, ISSUER, AUDIENCE, "e2e-current", key, -1000),
                signed(id, ISSUER, AUDIENCE, "e2e-current", key, 120));
        for (String base : List.of(gatewayUrl, authUrl)) {
            for (String jwt : invalid) error(me(base, jwt), 401, "AUTH_ACCESS_INVALID");
            privateStatus(me(base, signed(id, ISSUER, AUDIENCE, "e2e-previous", previousKey, 0)), 200);
            error(send(base, "GET", "/api/auth/me", "", Map.of()), 401, "AUTH_ACCESS_REQUIRED");
        }
        privateStatus(send(gatewayUrl, "GET", "/api/auth/me?access_token=forbidden", "",
                Map.of("Authorization", "Bearer " + token(session))), 400);
    }

    @Test
    void csrfOriginAndCorsDenialsCannotMutateDatabaseOrConsumeCookie() throws Exception {
        var session = login(register());
        long accounts = number("SELECT count(*) FROM accounts");
        long families = number("SELECT count(*) FROM refresh_families");
        long tokens = number("SELECT count(*) FROM refresh_tokens");
        for (String base : List.of(gatewayUrl, authUrl)) {
            for (String route : List.of("register", "login", "refresh", "logout")) {
                String data = List.of("register", "login").contains(route) ? credentials(email()) : "";
                for (var guard : List.of(Map.of("X-CSRF-Protection", "1"), Map.of("Origin", ORIGIN),
                        Map.of("Origin", "https://evil.example.invalid", "X-CSRF-Protection", "1"),
                        Map.of("Origin", ORIGIN, "X-CSRF-Protection", "0"))) {
                    var headers = new HashMap<>(guard);
                    headers.put("Cookie", cookie(session));
                    headers.put("Content-Type", "application/json");
                    error(send(base, "POST", "/api/auth/" + route, data, headers), 403, "AUTH_CSRF_REJECTED");
                }
            }
        }
        assertEquals(accounts, number("SELECT count(*) FROM accounts"));
        assertEquals(families, number("SELECT count(*) FROM refresh_families"));
        assertEquals(tokens, number("SELECT count(*) FROM refresh_tokens"));
        assertEquals(0L, number("SELECT count(*) FROM refresh_tokens WHERE token_hash='" + hashCookie(cookie(session)) + "' AND used_at IS NOT NULL"));
        privateStatus(post("refresh", "", cookie(session)), 200);
        var preflight = send(gatewayUrl, "OPTIONS", "/api/auth/login", "", Map.of("Origin", ORIGIN,
                "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "Authorization,Content-Type,X-CSRF-Protection"));
        assertEquals(200, preflight.statusCode());
        assertEquals(ORIGIN, header(preflight, "Access-Control-Allow-Origin"));
        assertEquals("true", header(preflight, "Access-Control-Allow-Credentials"));
        assertEquals(403, send(gatewayUrl, "OPTIONS", "/api/auth/login", "", Map.of("Origin", ORIGIN,
                "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "X-Internal-Token")).statusCode());
    }

    @Test
    void concurrentRefreshCommitsOneChildThenReplayRevokesFamily() throws Exception {
        var session = login(register());
        var request = request(gatewayUrl, "POST", "/api/auth/refresh", "", mutationHeaders(cookie(session)));
        var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);
        assertEquals(List.of(200, 401), List.of(first.join().statusCode(), second.join().statusCode()).stream().sorted().toList());
        var accepted = first.join().statusCode() == 200 ? first.join() : second.join();
        assertEquals(1L, number("SELECT count(*) FROM refresh_tokens WHERE parent_id=(SELECT id FROM refresh_tokens WHERE token_hash='"
                + hashCookie(cookie(session)) + "')"));
        error(post("refresh", "", cookie(accepted)), 401, "AUTH_REFRESH_INVALID");
    }

    @Test
    void realDatabaseConnectionFailureReturns503WithoutIssuingOrRevokingSessions() throws Exception {
        String email = register();
        var session = login(email);
        long accounts = number("SELECT count(*) FROM accounts");
        long families = number("SELECT count(*) FROM refresh_families");
        long tokens = number("SELECT count(*) FROM refresh_tokens");
        sql("ALTER ROLE auth_app NOLOGIN", "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='auth_app'");
        try {
            error(me(gatewayUrl, token(session)), 503, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE");
            for (String route : List.of("register", "login", "refresh", "logout")) {
                var result = post(route, route.equals("register") ? credentials(email())
                        : route.equals("login") ? credentials(email) : "", cookie(session));
                error(result, 503, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE");
                assertTrue(result.headers().allValues("Set-Cookie").isEmpty(), "No cookie on failed commit");
                assertFalse(result.body().contains("accessToken"));
            }
        } finally { sql("ALTER ROLE auth_app LOGIN"); }
        assertEquals(accounts, number("SELECT count(*) FROM accounts"));
        assertEquals(families, number("SELECT count(*) FROM refresh_families"));
        assertEquals(tokens, number("SELECT count(*) FROM refresh_tokens"));
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (send(authUrl, "GET", "/actuator/health", "", Map.of()).statusCode() != 200) {
            assertTrue(System.nanoTime() < deadline, "Auth pool must reconnect after restoring DB login");
            Thread.sleep(100);
        }
        privateStatus(post("refresh", "", cookie(session)), 200); // failed logout did not revoke
    }

    @Test
    void malformedBodiesAndConcurrentRegistrationKeepAtomicAccountProvisioning() throws Exception {
        String email = email();
        for (String route : List.of("register", "login")) {
            for (String data : List.of("{}", "{", "{\"email\":1,\"password\":\"ignored\"}",
                    credentials(email).replace("}", ",\"roles\":[\"ADMIN\"]}"))) {
                privateStatus(post(route, data, null), 400);
            }
        }
        for (String route : List.of("refresh", "logout")) privateStatus(post(route, "{}", null), 400);
        error(post("login", credentials(email), null), 401, "AUTH_INVALID_CREDENTIALS");
        long accounts = number("SELECT count(*) FROM accounts");
        long tasks = number("SELECT count(*) FROM profile_provisioning_tasks");
        var request = request(gatewayUrl, "POST", "/api/auth/register", credentials(email), mutationHeaders(null));
        var a = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var b = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).get(20, TimeUnit.SECONDS);
        assertEquals(List.of(201, 409), List.of(a.join().statusCode(), b.join().statusCode()).stream().sorted().toList());
        assertEquals(accounts + 1, number("SELECT count(*) FROM accounts"));
        assertEquals(tasks + 1, number("SELECT count(*) FROM profile_provisioning_tasks"));
        sql("REVOKE INSERT ON profile_provisioning_tasks FROM auth_app");
        try { error(post("register", credentials(email()), null), 503, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE"); }
        finally { sql("GRANT INSERT ON profile_provisioning_tasks TO auth_app"); }
        assertEquals(accounts + 1, number("SELECT count(*) FROM accounts"), "Provisioning failure rolls back account");
    }

    @Test
    void realAuthReceivesBearerBodyCookieAndOnlyAllowlistedGatewayRoutes() throws Exception {
        String email = email();
        var headers = mutationHeaders(null);
        headers.put("Authorization", "Bearer stale-public-token");
        for (String spoof : SPOOFED) headers.put(spoof, "ADMIN");
        var registered = send(gatewayUrl, "POST", "/api/auth/register", credentials(email), headers);
        privateStatus(registered, 201);
        assertEquals(List.of("CUSTOMER"), roles(body(registered)));
        var session = send(gatewayUrl, "POST", "/api/auth/login", credentials(email), headers);
        privateStatus(session, 200);
        headers.put("Cookie", cookie(session));
        var rotated = send(gatewayUrl, "POST", "/api/auth/refresh", "", headers);
        privateStatus(rotated, 200);
        var protectedHeaders = new HashMap<>(headers);
        protectedHeaders.put("Authorization", "Bearer " + token(rotated));
        privateStatus(send(gatewayUrl, "GET", "/api/auth/me", "", protectedHeaders), 200);
        for (String path : List.of("/api/auth/me/extra", "/api/auth/unknown", "/internal",
                "/internal/accounts", "/api/products", "/api/sellers", "/api/users/me")) {
            assertEquals(404, send(gatewayUrl, "GET", path, "", protectedHeaders).statusCode());
        }
        var bearerOnly = Map.of("Authorization", "Bearer " + token(rotated));
        assertEquals(405, send(gatewayUrl, "GET", "/api/auth/login", "", bearerOnly).statusCode());
        assertEquals(405, send(gatewayUrl, "POST", "/api/auth/me", "", bearerOnly).statusCode());
        var spoofOnly = new HashMap<>(protectedHeaders);
        spoofOnly.remove("Authorization");
        privateStatus(send(gatewayUrl, "GET", "/api/auth/me", "", spoofOnly), 401);
        headers.put("Cookie", cookie(rotated));
        privateStatus(send(gatewayUrl, "POST", "/api/auth/logout", "", headers), 204);
        error(post("refresh", "", cookie(rotated)), 401, "AUTH_REFRESH_INVALID");
    }

    private String register() throws Exception {
        String email = email();
        privateStatus(post("register", credentials(email), null), 201);
        return email;
    }
    private HttpResponse<String> login(String email) throws Exception {
        var result = post("login", credentials(email), null);
        privateStatus(result, 200);
        return result;
    }
    private static String email() { return "e2e-" + UUID.randomUUID() + "@example.invalid"; }
    private String credentials(String email) throws Exception { return json.writeValueAsString(Map.of("email", email, "password", PASSWORD)); }
    private JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    private String token(HttpResponse<String> response) throws Exception { return body(response).get("accessToken").asText(); }
    private static List<String> roles(JsonNode node) {
        var roles = new ArrayList<String>();
        node.get("roles").forEach(value -> roles.add(value.asText()));
        return roles.stream().sorted().toList();
    }
    private static String header(HttpResponse<?> response, String name) { return response.headers().firstValue(name).orElse(""); }
    private static String cookie(HttpResponse<?> response) { return header(response, "Set-Cookie").split(";", 2)[0]; }
    private static void cookiePolicy(HttpResponse<?> response, boolean cleared) {
        String value = header(response, "Set-Cookie");
        assertTrue(value.startsWith("refresh_token="));
        for (String attribute : List.of("Path=/api/auth", "Secure", "HttpOnly", "SameSite=Lax")) assertTrue(value.contains(attribute), attribute);
        assertFalse(value.contains("Domain="));
        long maxAge = Long.parseLong(value.split("Max-Age=")[1].split(";")[0]);
        assertTrue(cleared ? maxAge == 0 : maxAge > 0 && maxAge <= 604800);
        if (cleared) assertEquals("refresh_token=", cookie(response));
    }
    private static void privateStatus(HttpResponse<?> response, int status) {
        // Never include response body/headers (JWT/cookie/PII) in assertion diagnostics.
        assertEquals(status, response.statusCode());
        assertTrue(header(response, "Cache-Control").contains("no-store"));
        assertEquals("no-cache", header(response, "Pragma"));
    }
    private void error(HttpResponse<String> response, int status, String code) throws Exception {
        privateStatus(response, status);
        assertEquals(code, body(response).get("code").asText());
    }
    private static Map<String, String> mutationHeaders(String cookie) {
        var headers = new HashMap<>(Map.of("Origin", ORIGIN, "X-CSRF-Protection", "1", "Content-Type", "application/json"));
        if (cookie != null) headers.put("Cookie", cookie);
        return headers;
    }
    private HttpResponse<String> post(String route, String body, String cookie) throws Exception {
        return send(gatewayUrl, "POST", "/api/auth/" + route, body, mutationHeaders(cookie));
    }
    private HttpResponse<String> me(String base, String jwt) throws Exception {
        return send(base, "GET", "/api/auth/me", "", Map.of("Authorization", "Bearer " + jwt));
    }
    private HttpResponse<String> send(String base, String method, String path, String body, Map<String, String> headers) throws Exception {
        return http.send(request(base, method, path, body, headers), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpRequest request(String base, String method, String path, String body, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return request.build();
    }
    private Connection admin() throws Exception { return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword()); }
    private void sql(String... statements) throws Exception {
        try (var c = admin(); var s = c.createStatement()) { for (String statement : statements) s.execute(statement); }
    }
    private Object scalar(String query) throws Exception {
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery(query)) { assertTrue(r.next()); return r.getObject(1); }
    }
    private long number(String query) throws Exception { return ((Number) scalar(query)).longValue(); }
    private static String hashCookie(String cookie) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cookie.substring("refresh_token=".length()).getBytes(StandardCharsets.US_ASCII)));
    }
    private Object familyExpiry(String cookie) throws Exception {
        return scalar("SELECT expires_at FROM refresh_tokens WHERE token_hash='" + hashCookie(cookie) + "'");
    }
    private static String signed(String id, String issuer, String audience, String kid, byte[] key, long offset) throws Exception {
        Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(offset);
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(id)
                .issueTime(Date.from(issued)).notBeforeTime(Date.from(issued)).expirationTime(Date.from(issued.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID(kid).build(), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
