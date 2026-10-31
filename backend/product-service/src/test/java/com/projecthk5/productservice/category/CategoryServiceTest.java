package com.projecthk5.productservice.category;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock private CategoryRepository repository;
    @Mock private CategoryHierarchyLock hierarchyLock;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository, hierarchyLock);
    }

    @Test
    void createsRootAndTrimsName() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        CategoryResponse result = service.create(new CategoryRequest(" Electronics ", null));
        assertNotNull(result.id());
        assertEquals("Electronics", result.name());
        assertNull(result.parentId());
        InOrder order = inOrder(hierarchyLock, repository);
        order.verify(hierarchyLock).acquire();
        order.verify(repository).saveAndFlush(any());
    }

    @Test
    void createsChildOnlyAfterLockingAndCheckingAncestors() {
        Category root = category("Root", null);
        Category parent = category("Parent", root);
        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CategoryResponse result = service.create(new CategoryRequest("Child", parent.getId()));
        assertEquals(parent.getId(), result.parentId());
        InOrder order = inOrder(hierarchyLock, repository);
        order.verify(hierarchyLock).acquire();
        order.verify(repository).findById(parent.getId());
        order.verify(repository).saveAndFlush(any());
    }

    @Test
    void listsFlatDtosAndGetsChild() {
        Category root = category("Root", null);
        Category child = category("Child", root);
        when(repository.findAll(Sort.by("name", "id"))).thenReturn(List.of(child, root));
        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        assertEquals(root.getId(), service.list().get(0).parentId());
        assertEquals(new CategoryResponse(child.getId(), "Child", root.getId(), "child", CategoryStatus.ACTIVE), service.get(child.getId()));
        verifyNoInteractions(hierarchyLock);
    }

    @Test
    void updatesNameAndMovesChildToRoot() {
        Category child = category("Child", category("Root", null));
        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        when(repository.saveAndFlush(child)).thenReturn(child);
        CategoryResponse result = service.update(child.getId(), new CategoryRequest(" Renamed ", null));
        assertEquals("Renamed", result.name());
        assertNull(result.parentId());
        InOrder order = inOrder(hierarchyLock, repository);
        order.verify(hierarchyLock).acquire();
        order.verify(repository).findById(child.getId());
    }

    @Test
    void movesCategoryToAnotherValidParent() {
        Category child = category("Child", null);
        Category parent = category("Other", null);
        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(repository.saveAndFlush(child)).thenReturn(child);
        assertEquals(parent.getId(), service.update(child.getId(),
                new CategoryRequest("Child", parent.getId())).parentId());
    }

    @Test
    void rejectsSelfParentWithoutChangingEntity() {
        Category root = category("Root", null);
        when(repository.findById(root.getId())).thenReturn(Optional.of(root));
        assertCode("CATEGORY_CYCLE", () -> service.update(root.getId(),
                new CategoryRequest("Changed", root.getId())));
        assertEquals("Root", root.getName());
        assertNull(root.getParent());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsTwoNodeCycle() {
        Category root = category("Root", null);
        Category child = category("Child", root);
        when(repository.findById(root.getId())).thenReturn(Optional.of(root));
        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        assertCode("CATEGORY_CYCLE", () -> service.update(root.getId(),
                new CategoryRequest("Root", child.getId())));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDeepCycle() {
        Category root = category("Root", null);
        Category middle = category("Middle", root);
        Category leaf = category("Leaf", middle);
        when(repository.findById(root.getId())).thenReturn(Optional.of(root));
        when(repository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        assertCode("CATEGORY_CYCLE", () -> service.update(root.getId(),
                new CategoryRequest("Root", leaf.getId())));
    }

    @Test
    void rejectsAlreadyCorruptAncestorChainDuringCreate() {
        Category first = category("First", null);
        Category second = category("Second", first);
        first.update("First", second);
        when(repository.findById(first.getId())).thenReturn(Optional.of(first));
        assertCode("CATEGORY_CYCLE", () -> service.create(new CategoryRequest("New", first.getId())));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reportsMissingCategoryAndParent() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertCode("CATEGORY_NOT_FOUND", () -> service.get(missing));
        assertCode("CATEGORY_NOT_FOUND", () -> service.update(missing, new CategoryRequest("Name", null)));
        assertCode("CATEGORY_NOT_FOUND", () -> service.delete(missing));
        CategoryException error = assertCode("CATEGORY_PARENT_NOT_FOUND",
                () -> service.create(new CategoryRequest("Name", missing)));
        assertEquals(CategoryException.Code.CATEGORY_PARENT_NOT_FOUND, error.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsInvalidNames(String name) {
        assertCode("VALIDATION_ERROR", () -> service.create(new CategoryRequest(name, null)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsLongNameAndNullRequest() {
        assertCode("VALIDATION_ERROR", () -> service.create(new CategoryRequest("x".repeat(256), null)));
        assertCode("VALIDATION_ERROR", () -> service.create(null));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDeletingParent() {
        Category root = category("Root", null);
        when(repository.findById(root.getId())).thenReturn(Optional.of(root));
        when(repository.existsByParent_Id(root.getId())).thenReturn(true);
        assertCode("CATEGORY_HAS_CHILDREN", () -> service.delete(root.getId()));
        verify(repository, never()).delete(any());
    }

    @Test
    void deletesLeafAfterAcquiringLock() {
        Category leaf = category("Leaf", null);
        when(repository.findById(leaf.getId())).thenReturn(Optional.of(leaf));
        service.delete(leaf.getId());
        InOrder order = inOrder(hierarchyLock, repository);
        order.verify(hierarchyLock).acquire();
        order.verify(repository).findById(leaf.getId());
        order.verify(repository).existsByParent_Id(leaf.getId());
        order.verify(repository).delete(leaf);
        order.verify(repository).flush();
    }

    private Category category(String name, Category parent) {
        Category category = new Category(name, CategoryService.slugOf(name), parent);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private CategoryException assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        CategoryException error = assertThrows(CategoryException.class, action);
        assertEquals(code, error.getCode().name());
        return error;
    }
}
