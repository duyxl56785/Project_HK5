package com.projecthk5.authservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginThrottleTest {
    private static final String ORIGIN = "http://localhost:5173";
    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);
    private final AuthService auth = mock(AuthService.class);
    // Only the Gateway and nginx are proxies; any other internal peer is a client.
    private static final String PROXIES = "10.0.0.5, 172.18.0.4";
    private final AuthController controller = new AuthController(auth, throttle, ORIGIN, PROXIES);

    @Test
    void failuresFromOneClientCannotLockTheVictimOutFromAnother() {
        when(auth.login(anyString(), anyString())).thenThrow(invalid());
        for (int i = 0; i < LoginThrottle.LIMIT; i++) assertStatus(HttpStatus.UNAUTHORIZED, () -> login("victim@example.com", "203.0.113.7"));
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> login("victim@example.com", "203.0.113.7"));
        // The victim, from their own address, still reaches the password check.
        assertStatus(HttpStatus.UNAUTHORIZED, () -> login("victim@example.com", "198.51.100.20"));
    }

    @Test
    void oneClientGuessingManyEmailsIsStopped() {
        when(auth.login(anyString(), anyString())).thenThrow(invalid());
        for (int i = 0; i < LoginThrottle.CLIENT_LIMIT; i++) {
            String email = "user" + i + "@example.com";
            assertStatus(HttpStatus.UNAUTHORIZED, () -> login(email, "203.0.113.7"));
        }
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> login("fresh@example.com", "203.0.113.7"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> login("fresh@example.com", "198.51.100.20"));
    }

    @Test
    void registrationIsLimitedPerClientNotPerGateway() {
        when(auth.register(anyString(), anyString())).thenAnswer(call -> new com.projecthk5.authservice.persistence.Account(
                java.util.UUID.randomUUID(), call.getArgument(0), "hash", AuthService.ACTIVE,
                java.util.Set.of(com.projecthk5.authservice.persistence.Account.Role.CUSTOMER), Instant.now()));
        for (int i = 0; i < LoginThrottle.REGISTER_LIMIT; i++) {
            String email = "new" + i + "@example.com";
            assertDoesNotThrow(() -> register(email, "203.0.113.7"));
        }
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> register("one-more@example.com", "203.0.113.7"));
        // Another browser behind the same Gateway is unaffected.
        assertDoesNotThrow(() -> register("other@example.com", "198.51.100.20"));
    }

    @Test
    void clientAddressSkipsOnlyConfiguredProxiesAndIgnoresHeadersFromAnyOtherPeer() {
        assertEquals("10.0.0.5", address("10.0.0.5", null));
        // Browser -> nginx (overwrites the header) -> Gateway (appends nginx) -> Auth.
        assertEquals("203.0.113.7", address("10.0.0.5", "203.0.113.7, 172.18.0.4"));
        // Straight to the Gateway: a forged left hop is skipped in favour of what the Gateway saw.
        assertEquals("203.0.113.7", address("10.0.0.5", "8.8.8.8, 203.0.113.7"));
        // A public peer is the client itself; its header is not trusted.
        assertEquals("198.51.100.9", address("198.51.100.9", "8.8.8.8"));
        // Another container on the private network is not a proxy: its forged header is ignored.
        assertEquals("172.18.0.9", address("172.18.0.9", "8.8.8.8"));
        assertEquals("127.0.0.1", address("127.0.0.1", "8.8.8.8"));
        // An unlisted private hop between our proxies and the client is the client.
        assertEquals("172.18.0.9", address("10.0.0.5", "8.8.8.8, 172.18.0.9"));
        // Names are never resolved; fall back to the last trusted hop.
        assertEquals("10.0.0.5", address("10.0.0.5", "evil.example"));
        assertEquals("fd00::1", address("10.0.0.5", "fd00::1"));
    }

    @Test
    void withoutConfiguredProxiesOnlyLoopbackIsTrusted() {
        var local = new AuthController(auth, throttle, ORIGIN, "");
        assertEquals("203.0.113.7", address(local, "127.0.0.1", "203.0.113.7"));
        assertEquals("10.0.0.5", address(local, "10.0.0.5", "203.0.113.7"));
    }

    @Test
    void invalidProxyEntryFailsStartup() {
        assertThrows(IllegalArgumentException.class, () -> new AuthController(auth, throttle, ORIGIN, "gateway.internal"));
    }

    private String address(String peer, String forwarded) {
        return address(controller, peer, forwarded);
    }

    private static String address(AuthController target, String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        return target.clientAddress(request);
    }

    @Test
    void expiredWindowsAreDroppedSoRandomKeysCannotGrowMemoryForever() {
        for (int i = 0; i < LoginThrottle.SWEEP_AT; i++) throttle.recordFailure("login:" + i + "@example.com|203.0.113.7");
        assertEquals(LoginThrottle.SWEEP_AT, throttle.size());
        clock.now = clock.now.plusSeconds(LoginThrottle.WINDOW_SECONDS);
        throttle.recordFailure("login:after@example.com|203.0.113.7");
        assertEquals(1, throttle.size());
    }

    private void login(String email, String client) {
        controller.login(request(client), new AuthController.LoginRequest(email, "wrong-password-123"));
    }

    private void register(String email, String client) {
        controller.register(request(client), new AuthController.RegisterRequest(email, "correct-horse-battery"));
    }

    private static MockHttpServletRequest request(String client) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.5"); // the Gateway
        request.addHeader("Origin", ORIGIN);
        request.addHeader("X-CSRF-Protection", "1");
        request.addHeader("X-Forwarded-For", client);
        return request;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        ApiException error = assertThrows(ApiException.class, call::run);
        assertEquals(expected, error.status());
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
