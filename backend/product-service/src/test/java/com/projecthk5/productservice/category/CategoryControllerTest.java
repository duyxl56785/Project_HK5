package com.projecthk5.productservice.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// Tests the HTTP contract in isolation; production write denial is tested separately.
class CategoryControllerTest {
    private CategoryService service;
    private MockMvc mvc;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(CategoryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(service))
                .setControllerAdvice(new CategoryExceptionHandler()).build();
    }

    @Test
    void listsAndGetsFlatCategoryDtos() throws Exception {
        CategoryResponse response = new CategoryResponse(id, "Root", null);
        when(service.list()).thenReturn(List.of(response));
        when(service.get(id)).thenReturn(response);
        mvc.perform(get("/api/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Root"));
    }

    @Test
    void createsWithLocationAndParentId() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(service.create(any())).thenReturn(new CategoryResponse(id, "Child", parentId));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child\",\"parentId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/categories/" + id))
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));
        verify(service).create(new CategoryRequest("Child", parentId));
    }

    @Test
    void updatesAndDeletes() throws Exception {
        when(service.update(eq(id), any())).thenReturn(new CategoryResponse(id, "Changed", null));
        mvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Changed\",\"parentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Changed"));
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).delete(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void rejectsMissingOrBlankName(String body) throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsLongNameOnUpdate() throws Exception {
        mvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedUuidAndJson() throws Exception {
        mvc.perform(get("/api/categories/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Name\",\"parentId\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void reportsMissingCategory() throws Exception {
        when(service.get(id)).thenThrow(new CategoryException(
                CategoryException.Code.CATEGORY_NOT_FOUND, "Category does not exist"));
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void reportsMissingParentAndCycle() throws Exception {
        when(service.create(any())).thenThrow(new CategoryException(
                CategoryException.Code.CATEGORY_PARENT_NOT_FOUND, "Parent category does not exist"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Name\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_PARENT_NOT_FOUND"));
        when(service.update(eq(id), any())).thenThrow(new CategoryException(
                CategoryException.Code.CATEGORY_CYCLE, "Category parent relationship would contain a cycle"));
        mvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Name\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
    }

    @Test
    void reportsChildrenConflictAndHidesDatabaseDetails() throws Exception {
        doThrow(new CategoryException(CategoryException.Code.CATEGORY_HAS_CHILDREN, "Has children"))
                .when(service).delete(id);
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));
        doThrow(new DataIntegrityViolationException("sensitive SQL details")).when(service).delete(id);
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Category operation conflicts with stored relationships"));
    }

    @Test
    void hidesUnexpectedFailureDetails() throws Exception {
        when(service.get(id)).thenThrow(new IllegalStateException("internal database detail"));
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Category operation failed"));
    }
}
