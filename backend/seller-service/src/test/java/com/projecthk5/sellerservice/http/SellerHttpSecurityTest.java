package com.projecthk5.sellerservice.http;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@WebMvcTest(controllers = SellerHttpSecurityTest.Probe.class)
@Import({SellerHttpSecurityConfiguration.class, SellerHttpSecurityTest.Probe.class})
class SellerHttpSecurityTest {
    @Autowired MockMvc http;

    @ParameterizedTest
    @ValueSource(strings = {"/security-probe", "/api/sellers/me", "/api/stores/me", "/api/admin/sellers",
            "/internal/current-account", "/actuator/info", "/login", "/logout"})
    void unconfiguredPathsCannotBeUsedWithClaimedIdentity(String path) throws Exception {
        http.perform(get(path).header("Authorization", "Bearer unverified")
                        .header("X-User-Id", "claimed-owner").header("X-Roles", "ADMIN,SELLER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROUTE_AUTH_NOT_CONFIGURED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-probe"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void evenAnAuthenticatedAdminAndCsrfCannotBypassTheUnconfiguredBoundary(String method) throws Exception {
        http.perform(request(HttpMethod.valueOf(method), "/security-probe")
                        .with(user("test-only-admin").roles("ADMIN", "SELLER")).with(csrf())
                        .contentType("application/json").content("{\"ownerReference\":\"claimed-owner\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROUTE_AUTH_NOT_CONFIGURED"));
    }

    @Test void onlyHealthGetAndHeadRemainAllowed() throws Exception {
        http.perform(get("/actuator/health")).andExpect(status().isOk());
        http.perform(head("/actuator/health")).andExpect(status().isOk());
        http.perform(post("/actuator/health").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void noBasicLoginOrSessionIsOffered() throws Exception {
        http.perform(get("/security-probe").header("Authorization", "Basic bm90LWEtdXNlcjpub3QtYS1wYXNzd29yZA=="))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        http.perform(post("/logout").with(user("test-only")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class Probe {
        @RequestMapping(path = "/security-probe", method = {RequestMethod.GET, RequestMethod.POST,
                RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        Map<String, String> privateProbe() { return Map.of("secret", "private-probe"); }
        @GetMapping("/actuator/health")
        Map<String, String> health() { return Map.of("status", "UP"); }
    }
}
