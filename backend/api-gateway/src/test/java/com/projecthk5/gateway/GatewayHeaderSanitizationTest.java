package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** The header scrubber must not mutate ReadOnlyHttpHeaders on original request. */
class GatewayHeaderSanitizationTest {
    @Test
    void preservesOnlyTrustedTransportHeadersOnWritableExchange() {
        var request = MockServerHttpRequest.post("/api/auth/logout")
                .header("X-User-Id", "forged-id")
                .header("X-Customer-Id", "forged-customer")
                .header("X-Roles", "ADMIN")
                .header("X-Internal-Token", "forged-secret")
                .header("X-Service-Name", "attacker")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-token")
                .header(HttpHeaders.ORIGIN, "https://trusted.example.invalid").build();
        var exchange = MockServerWebExchange.from(request);
        var forwarded = new AtomicBoolean();
        assertDoesNotThrow(() ->
                GatewayAuthConfiguration.OptIn.sanitizeHeaders()
                        .filter(exchange, forwardedExchange -> {
                            var headers = forwardedExchange.getRequest().getHeaders();
                            assertNull(headers.getFirst("X-User-Id"));
                            assertNull(headers.getFirst("X-Customer-Id"));
                            assertNull(headers.getFirst("X-Roles"));
                            assertNull(headers.getFirst("X-Internal-Token"));
                            assertNull(headers.getFirst("X-Service-Name"));
                            assertEquals("Bearer signed-token",
                                    headers.getFirst(HttpHeaders.AUTHORIZATION));
                            assertEquals("https://trusted.example.invalid",
                                    headers.getFirst(HttpHeaders.ORIGIN));
                            forwarded.set(true);
                            return Mono.empty();
                        }).block());
        assertTrue(forwarded.get());
    }
}
