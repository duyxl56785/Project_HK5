package com.projecthk5.productservice.category;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.Normalizer;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Upgrades a database that already holds categories, which is the case the empty test schema cannot show. */
@Testcontainers
@Tag("postgresql")
class CategorySlugMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    private static void insert(Connection connection, UUID id, String name) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO categories(id, name) VALUES (?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static String slug(Connection connection, UUID id) throws Exception {
        try (var statement = connection.prepareStatement("SELECT slug, status FROM categories WHERE id = ?")) {
            statement.setObject(1, id);
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals("ACTIVE", row.getString("status"));
                return row.getString("slug");
            }
        }
    }

    @Test
    void existingCategoriesGetTheSlugTheServiceWouldGiveAndDuplicatesStayUnique() throws Exception {
        flyway("6").migrate();
        UUID accented = UUID.randomUUID(), decomposed = UUID.randomUUID(), upper = UUID.randomUUID();
        UUID symbols = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID older = UUID.fromString("00000000-0000-0000-0000-000000000001"), newer = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID idSuffixed = UUID.randomUUID(), numbered = UUID.randomUUID();
        String tones = "Mẹ và bé - Đồ chơi";
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            insert(connection, accented, tones);
            insert(connection, decomposed, Normalizer.normalize("Nhà cửa", Normalizer.Form.NFD));
            insert(connection, upper, "ĐIỆN TỬ");
            insert(connection, symbols, "***");
            insert(connection, first, "Sách");
            insert(connection, second, "sách");
            insert(connection, older, "Sổ");
            insert(connection, newer, "Sổ");
            insert(connection, idSuffixed, "So 11111111");
            insert(connection, numbered, "So 2");
        }

        assertEquals(1, flyway("latest").migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertEquals(CategoryService.slugOf(tones), slug(connection, accented));
            assertEquals("nha-cua", slug(connection, decomposed));
            assertEquals("dien-tu", slug(connection, upper));
            assertEquals("danh-muc", slug(connection, symbols));
            String a = slug(connection, first), b = slug(connection, second);
            assertNotEquals(a, b);
            assertTrue(a.startsWith("sach") && b.startsWith("sach"));
            assertEquals("so", slug(connection, older));
            assertEquals("so-11111111", slug(connection, idSuffixed));
            assertEquals("so-2", slug(connection, numbered));
            assertEquals("so-3", slug(connection, newer));
        }
        flyway("latest").validate();
    }
}
