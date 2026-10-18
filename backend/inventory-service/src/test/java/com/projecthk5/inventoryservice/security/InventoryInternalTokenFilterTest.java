package com.projecthk5.inventoryservice.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class InventoryInternalTokenFilterTest {
    @Test
    void missingTokenConfigurationFailsClosedForEncodedAndMatrixPaths() throws Exception {
        InventoryInternalTokenFilter filter = new InventoryInternalTokenFilter(" ", " ");

        for (String path : new String[]{"/api/inventory;review=1/items", "/api/%69nventory/items"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    fail("inventory path without configured token must not reach the controller"));

            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus(), path);
            assertTrue(response.getContentAsString().contains("INVENTORY_AUTH_NOT_CONFIGURED"), path);
        }
    }
}
