package com.projecthk5.productservice.category;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.projecthk5.productservice.security.ProductSecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({CategorySecurityConfiguration.class, ProductSecurityConfiguration.class})
class CategorySecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private CategoryService service;
    @MockBean private JwtDecoder decoder;

    @BeforeEach
    void rejectUnsignedTokens() {
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("unverified"));
    }

    @Test
    void permitsAnonymousReads() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(head("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void keepsTheFullListForAdmins() throws Exception {
        mvc.perform(get("/api/categories/all")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories/all").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/categories/all").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void blocksAnonymousWritesBeforeCallingService(String method) throws Exception {
        String path = method.equals("POST") ? "/api/categories" : "/api/categories/" + UUID.randomUUID();
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void doesNotTrustAnUnverifiedBearerToken() throws Exception {
        mvc.perform(post("/api/categories").header("Authorization", "Bearer unverified-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void forbidsVerifiedNonAdminWrites() throws Exception {
        mvc.perform(post("/api/categories").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SELLER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void letsAVerifiedAdminReachTheService() throws Exception {
        when(service.create(any())).thenReturn(new CategoryResponse(UUID.randomUUID(), "Name", null, "slug", CategoryStatus.ACTIVE));
        mvc.perform(post("/api/categories").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isCreated());
        verify(service).create(any());
    }
}
