package com.projecthk5.productservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecthk5.productservice.category.CategoryController;
import com.projecthk5.productservice.category.CategorySecurityConfiguration;
import com.projecthk5.productservice.category.CategoryService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(CategoryController.class)
@Import({CategorySecurityConfiguration.class, ProductSecurityConfiguration.class,
        ProductServiceSecurityTest.ProbeController.class})
class ProductServiceSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private ProbeController probe;
    @MockBean private CategoryService categoryService;
    @MockBean private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @BeforeEach
    void resetProbe() {
        probe.invocations.set(0);
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/products/probe", "POST,/api/products/probe", "PUT,/api/products/probe",
        "PATCH,/api/products/probe", "DELETE,/api/products/probe", "HEAD,/api/reviews/probe",
        "GET,/api/reviews/probe", "GET,/internal/products/probe", "GET,/api/categories-unprotected",
        "POST,/api/products/probe/images", "GET,/api/product-images/probe",
        "DELETE,/api/products/probe/images/probe"
    })
    void blocksUnconfiguredRoutes(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isForbidden());
        assertEquals(0, probe.invocations.get());
        verifyNoInteractions(categoryService);
    }

    @Test
    void blocksEvenAMockedAdminWithoutAnExplicitRoutePolicy() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/products/probe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
        assertEquals(0, probe.invocations.get());
    }

    @Test
    void rejectsUnverifiedBearerAndForgedIdentityHeaders() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/products/probe")
                .header("Authorization", "Bearer unverified-token")
                .header("X-User-Id", "admin").header("X-Roles", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
        assertEquals(0, probe.invocations.get());
    }

    @Test
    void rejectsUnmatchedPathsToo() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/unregistered-endpoint"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void preservesTheRootHealthReadPolicy(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/actuator/health")).andExpect(status().isOk());
        assertEquals(1, probe.invocations.get());
    }

    @Test
    void deniesHealthMutations() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/actuator/health")).andExpect(status().isForbidden());
        assertEquals(0, probe.invocations.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "bearer", "headers", "mock-admin", "internal-token"})
    void proposedBatchRouteRemainsDeniedBeforeAnyHandler(String credential) throws Exception {
        var request = post("/internal/catalog/checkout/validate").contentType("application/json").content("{\"items\":[]}");
        switch (credential) {
            case "bearer" -> request.header("Authorization", "Bearer unverified-fixture");
            case "headers" -> request.header("X-User-Id", "admin").header("X-Roles", "ADMIN");
            case "mock-admin" -> request.with(user("admin").roles("ADMIN"));
            case "internal-token" -> request.header("X-Internal-Token", "unverified-fixture");
            default -> { }
        }
        mvc.perform(request).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
        assertEquals(0, probe.invocations.get());
        verifyNoInteractions(categoryService);
    }

    // Test-only handlers prove denial occurs before a newly registered controller executes.
    @RestController
    static class ProbeController {
        private final AtomicInteger invocations = new AtomicInteger();

        @RequestMapping({"/api/products/probe", "/api/reviews/probe", "/internal/products/probe",
                "/api/categories-unprotected", "/actuator/health", "/internal/catalog/checkout/validate",
                "/api/products/probe/images", "/api/product-images/probe", "/api/products/probe/images/probe"})
        String probe() {
            invocations.incrementAndGet();
            return "probe";
        }
    }
}
