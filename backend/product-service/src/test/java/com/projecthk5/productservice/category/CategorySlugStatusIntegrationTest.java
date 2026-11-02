package com.projecthk5.productservice.category;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Tag("postgresql")
class CategorySlugStatusIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private CategoryService service;
    @Autowired private CategoryRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanCategories() {
        jdbc.update("UPDATE categories SET parent_id = NULL");
        jdbc.update("DELETE FROM categories");
    }

    private CategoryResponse create(String name, UUID parentId) {
        return service.create(new CategoryRequest(name, parentId));
    }

    private void hide(CategoryResponse category) {
        service.update(category.id(), new CategoryRequest(category.name(), category.parentId(), CategoryStatus.HIDDEN));
    }

    private static List<UUID> ids(List<CategoryResponse> rows) {
        return rows.stream().map(CategoryResponse::id).toList();
    }

    @Test
    void slugComesFromTheVietnameseNameAndStaysUniqueAndStableOnRename() {
        CategoryResponse first = create("Điện thoại & Phụ kiện", null);
        CategoryResponse second = create("Điện thoại & Phụ kiện", null);
        assertEquals("dien-thoai-phu-kien", first.slug());
        assertEquals("dien-thoai-phu-kien-2", second.slug());
        assertEquals(CategoryStatus.ACTIVE, first.status());

        CategoryResponse renamed = service.update(first.id(), new CategoryRequest("Tên mới", null));
        assertEquals("dien-thoai-phu-kien", renamed.slug());
    }

    @Test
    void aNameWithoutLettersStillGetsAValidSlug() {
        assertEquals("danh-muc", create("***", null).slug());
    }

    @Test
    void hidingACategoryRemovesItAndItsDescendantsFromThePublicTreeOnly() {
        CategoryResponse root = create("Gốc", null);
        CategoryResponse child = create("Con", root.id());
        CategoryResponse leaf = create("Lá", child.id());
        CategoryResponse other = create("Khác", null);

        hide(child);

        assertEquals(List.of(root.id(), other.id()), ids(service.list()));
        assertEquals(4, service.listAll().size());
        assertTrue(repository.isHiddenInChain(child.id()));
        assertTrue(repository.isHiddenInChain(leaf.id()));
        assertFalse(repository.isHiddenInChain(root.id()));

        service.update(child.id(), new CategoryRequest("Con", root.id(), CategoryStatus.ACTIVE));
        assertEquals(4, service.list().size());
        assertFalse(repository.isHiddenInChain(leaf.id()));
    }

    @Test
    void anUpdateThatOmitsStatusKeepsTheCurrentOne() {
        CategoryResponse category = create("Ẩn", null);
        hide(category);
        assertEquals(CategoryStatus.HIDDEN, service.update(category.id(), new CategoryRequest("Vẫn ẩn", null)).status());
    }

    @Test
    void theDatabaseRefusesBadValuesEvenWithoutTheService() {
        UUID id = create("Hợp lệ", null).id();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE categories SET status = 'ARCHIVED' WHERE id = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE categories SET slug = 'Có Dấu' WHERE id = ?", id));
        UUID other = create("Khác", null).id();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE categories SET slug = 'hop-le' WHERE id = ?", other));
    }

    @Test
    void hiddenChainCheckEndsOnACorruptCycle() {
        CategoryResponse first = service.create(new CategoryRequest("First", null));
        CategoryResponse second = service.create(new CategoryRequest("Second", first.id()));
        jdbc.update("UPDATE categories SET parent_id = ? WHERE id = ?", second.id(), first.id());
        assertFalse(repository.isHiddenInChain(first.id()));
        jdbc.update("UPDATE categories SET status = 'HIDDEN' WHERE id = ?", second.id());
        assertTrue(repository.isHiddenInChain(first.id()));
    }
}
