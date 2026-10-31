package com.projecthk5.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;

class CurrentAuthorityFilterTest {
    private static final String GRANTED = "{\"granted\":true,\"id\":\"11111111-1111-1111-1111-111111111111\"}";
    private final List<String> released = new ArrayList<>();
    private HttpServer auth;

    @AfterEach
    void stop() {
        SecurityContextHolder.clearContext();
        if (auth != null) auth.stop(0);
    }

    @Test
    void reservedWriteProceedsAndIsReleased() throws Exception {
        assertEquals(200, write(GRANTED, 200, "POST").getStatus());
        assertEquals(List.of("DELETE"), released);
    }

    @Test
    void writeProceedsWhenReleaseFails() throws Exception {
        auth = stub(GRANTED, 200, 500);
        assertEquals(200, run(new CurrentAuthorityFilter(url(), "pair"), "PUT", "/api/seller/products").getStatus());
    }

    @Test
    void refusedReservationRejectsTheWrite() throws Exception {
        var response = write("{\"granted\":false}", 200, "POST");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_ACCESS_INVALID"));
        assertTrue(released.isEmpty());
    }

    @Test
    void authRefusalOrOutageFailsClosed() throws Exception {
        assertEquals(503, write("{}", 500, "POST").getStatus());
        var refused = run(new CurrentAuthorityFilter("http://127.0.0.1:1", "pair"), "POST", "/api/seller/products");
        assertEquals(503, refused.getStatus());
        assertTrue(refused.getContentAsString().contains("AUTH_AUTHORITY_UNAVAILABLE"));
    }

    @Test
    void readsAndInternalRoutesSkipTheCheck() throws Exception {
        var unreachable = new CurrentAuthorityFilter("http://127.0.0.1:1", "pair");
        assertEquals(200, run(unreachable, "GET", "/api/seller/products").getStatus());
        assertEquals(200, run(unreachable, "POST", "/internal/notifications").getStatus());
    }

    private MockHttpServletResponse write(String body, int status, String method) throws Exception {
        auth = stub(body, status, 204);
        return run(new CurrentAuthorityFilter(url(), "pair"), method, "/api/seller/products");
    }

    private String url() {
        return "http://127.0.0.1:" + auth.getAddress().getPort();
    }

    private HttpServer stub(String body, int status, int releaseStatus) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/accounts/", exchange -> {
            assertEquals("pair", exchange.getRequestHeaders().getFirst(InternalTokens.HEADER));
            if ("DELETE".equals(exchange.getRequestMethod())) {
                released.add("DELETE");
                exchange.sendResponseHeaders(releaseStatus, -1);
                exchange.close();
                return;
            }
            assertEquals("/internal/accounts/account-1/write-reservations", exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static MockHttpServletResponse run(CurrentAuthorityFilter filter, String method, String path) throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("account-1")
                .claim("roles", List.of("CUSTOMER", "SELLER")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), "account-1"));
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, path), response, new MockFilterChain());
        return response;
    }
}
