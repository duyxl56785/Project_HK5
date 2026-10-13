package com.projecthk5.productservice.category;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
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
@Import(CategorySecurityConfiguration.class)
class CategorySecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private CategoryService service;

    @Test
    void permitsAnonymousReads() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(head("/api/categories")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void blocksAnonymousWritesBeforeCallingService(String method) throws Exception {
        String path = method.equals("POST") ? "/api/categories" : "/api/categories/" + UUID.randomUUID();
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_WRITE_AUTH_NOT_CONFIGURED"));
        verifyNoInteractions(service);
    }

    @Test
    void doesNotTrustAnUnverifiedBearerToken() throws Exception {
        mvc.perform(post("/api/categories").header("Authorization", "Bearer unverified-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_WRITE_AUTH_NOT_CONFIGURED"));
        verifyNoInteractions(service);
    }

    @Test
    void blocksEvenAnAuthenticatedAdminUntilContractIsConfirmed() throws Exception {
        mvc.perform(post("/api/categories").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_WRITE_AUTH_NOT_CONFIGURED"));
        verifyNoInteractions(service);
    }
}
