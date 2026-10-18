package com.projecthk5.inventoryservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

@Component
public class InventoryInternalTokenFilter extends OncePerRequestFilter {
    private static final PathPattern INVENTORY_API =
            PathPatternParser.defaultInstance.parse("/api/inventory/**");
    private static final String PAIR_HEADER = "X-Internal-Token";
    private static final String LEGACY_HEADER = "X-Inventory-Internal-Token";
    private static final String OPERATIONS_PATH = "/api/inventory/operations";
    private static final Set<String> ORDER_ALLOWED_TYPES = Set.of("RESERVE", "CONSUME", "RELEASE");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String configuredToken;
    private final String orderPairToken;

    public InventoryInternalTokenFilter(
            @Value("${inventory.internal-token:}") String configuredToken,
            @Value("${inventory.order-pair-token:}") String orderPairToken) {
        this.configuredToken = configuredToken;
        this.orderPairToken = orderPairToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INVENTORY_API.matches(
                ServletRequestPathUtils.parseAndCache(request).pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedPairToken = request.getHeader(PAIR_HEADER);
        if (suppliedPairToken != null) {
            doOrderPairRequest(request, response, filterChain, suppliedPairToken);
            return;
        }

        // Legacy credential remains for existing inventory-owned/admin workflows only.
        // It must never be handed to Checkout/Order.
        if (configuredToken == null || configuredToken.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "INVENTORY_AUTH_NOT_CONFIGURED");
            return;
        }

        String supplied = request.getHeader(LEGACY_HEADER);
        if (!constantTimeEquals(configuredToken, supplied)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void doOrderPairRequest(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain, String suppliedToken)
            throws IOException, ServletException {
        if (orderPairToken == null || orderPairToken.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "INVENTORY_ORDER_PAIR_AUTH_NOT_CONFIGURED");
            return;
        }
        if (!constantTimeEquals(orderPairToken, suppliedToken)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "INTERNAL_AUTH_INVALID");
            return;
        }

        if (!"POST".equals(request.getMethod()) || !OPERATIONS_PATH.equals(request.getRequestURI())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "INTERNAL_FORBIDDEN");
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String operationType;
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode type = root == null ? null : root.get("type");
            operationType = type == null || !type.isTextual()
                    ? null : type.asText().toUpperCase(Locale.ROOT);
        } catch (IOException malformedJson) {
            operationType = null;
        }
        if (operationType == null || !ORDER_ALLOWED_TYPES.contains(operationType)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "INTERNAL_FORBIDDEN");
            return;
        }

        filterChain.doFilter(new ReplayableBodyRequest(request, body), response);
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeError(HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }

    private static final class ReplayableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private ReplayableBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Non-blocking reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }
    }
}
