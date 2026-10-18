package com.projecthk5.inventoryservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class InventoryOrderPairGateTest {
    private static final String TOKEN = "pair-secret";

    @Test
    void pairTokenOnlyAllowsPostOperationsForReserveConsumeReleaseAndPreservesBody() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, true);
        for (String type : new String[]{"RESERVE", "CONSUME", "RELEASE"}) {
            MockHttpServletRequest request = request("POST", "/api/inventory/operations",
                    "{\"operationId\":\"op-1\",\"type\":\"" + type + "\",\"items\":[]}");
            request.addHeader("X-Internal-Token", TOKEN);
            request.addHeader("X-User-Id", "forged-user");
            request.addHeader("X-Store-Id", "forged-store");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {
                assertEquals(request.getRequestURI(), ((HttpServletRequest) req).getRequestURI());
                assertTrue(new String(req.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).contains("\"type\":\"" + type + "\""));
            });

            assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        }
    }

    @Test
    void pairTokenAloneCannotEnableMutationWhenFeatureFlagIsOff() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, false);
        MockHttpServletRequest request = request("POST", "/api/inventory/operations",
                "{\"operationId\":\"op-1\",\"type\":\"RESERVE\"}");
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("default-off pair gate must not pass"));

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVENTORY_ORDER_PAIR_DISABLED"));
    }

    @Test
    void missingOrWrongPairCredentialIsUnauthorized() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, true);
        MockHttpServletRequest missing = request("POST", "/api/inventory/operations",
                "{\"type\":\"RESERVE\"}");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, (req, res) -> fail("must not pass"));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, missingResponse.getStatus());

        MockHttpServletRequest wrong = request("POST", "/api/inventory/operations",
                "{\"type\":\"RESERVE\"}");
        wrong.addHeader("X-Internal-Token", "wrong");
        MockHttpServletResponse wrongResponse = new MockHttpServletResponse();
        filter.doFilter(wrong, wrongResponse, (req, res) -> fail("must not pass"));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, wrongResponse.getStatus());
    }

    @Test
    void unconfiguredPairVerifierFailsClosed() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", " ", true);
        MockHttpServletRequest request = request("POST", "/api/inventory/operations",
                "{\"type\":\"RESERVE\"}");
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("must not pass"));

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVENTORY_ORDER_PAIR_AUTH_NOT_CONFIGURED"));
    }

    @Test
    void pairCredentialCannotInitializeOrUseIncreaseDecreaseOrWrongMethodPath() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, true);
        assertForbidden(filter, request("POST", "/api/inventory/items", "{\"sku\":\"sku-a\"}"));
        assertForbidden(filter, request("GET", "/api/inventory/operations", ""));
        assertForbidden(filter, request("POST", "/api/inventory/operations", "{\"type\":\"INCREASE\"}"));
        assertForbidden(filter, request("POST", "/api/inventory/operations", "{\"type\":\"DECREASE\"}"));
        assertForbidden(filter, request("POST", "/api/inventory/operations", "{\"type\":\"UNKNOWN\"}"));
        assertForbidden(filter, request("POST", "/api/inventory/operations", "{not-json"));
    }

    @Test
    void exactMaximumBodySizeIsAcceptedAndReplayed() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, true);
        String prefix = "{\"type\":\"RESERVE\"}";
        String body = prefix + " ".repeat(InventoryInternalTokenFilter.MAX_ORDER_OPERATION_BODY_BYTES - prefix.length());
        assertEquals(InventoryInternalTokenFilter.MAX_ORDER_OPERATION_BODY_BYTES,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        MockHttpServletRequest request = request("POST", "/api/inventory/operations", body);
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertEquals(
                InventoryInternalTokenFilter.MAX_ORDER_OPERATION_BODY_BYTES,
                req.getInputStream().readAllBytes().length));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void bodyOneByteOverMaximumIsRejectedWith413() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter("legacy-secret", TOKEN, true);
        String prefix = "{\"type\":\"RESERVE\"}";
        String body = prefix + " ".repeat(InventoryInternalTokenFilter.MAX_ORDER_OPERATION_BODY_BYTES + 1 - prefix.length());
        assertEquals(InventoryInternalTokenFilter.MAX_ORDER_OPERATION_BODY_BYTES + 1,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        MockHttpServletRequest request = request("POST", "/api/inventory/operations", body);
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("oversized body must not reach controller"));

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVENTORY_OPERATION_BODY_TOO_LARGE"));
    }

    private static void assertForbidden(InventoryInternalTokenFilter filter,
                                        MockHttpServletRequest request) throws Exception {
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("out-of-scope request must not pass"));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus(), request.getRequestURI());
        assertTrue(response.getContentAsString().contains("INTERNAL_FORBIDDEN"));
    }

    private static MockHttpServletRequest request(String method, String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
