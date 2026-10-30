package com.projecthk5.productservice.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Acceptance T01 executes the actual production migrations against a disposable V1 database. */
@Testcontainers
@Tag("postgresql")
class ProductMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @Test
    void T01_upgradeCategoryV1PreservesChecksumAndDataThenValidatesAndRerunsWithoutChanges() throws Exception {
        Flyway v1 = migration("1");
        assertEquals(1, v1.migrate().migrationsExecuted);
        UUID category = UUID.randomUUID();
        Integer checksum;
        try (Connection connection = connect()) {
            checksum = v1Checksum(connection);
            try (var insert = connection.prepareStatement("INSERT INTO categories(id,name) VALUES (?,?)")) {
                insert.setObject(1, category);
                insert.setString(2, "Existing V1 category");
                assertEquals(1, insert.executeUpdate());
            }
        }

        Flyway current = migration(null);
        assertEquals(4, current.migrate().migrationsExecuted, "V1 must upgrade through Product V2, image reservation V3, details V4 and write fence V5");
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted, "A second migration run must be a no-op");

        try (Connection connection = connect()) {
            assertEquals(checksum, v1Checksum(connection));
            try (var query = connection.prepareStatement("SELECT name FROM categories WHERE id=?")) {
                query.setObject(1, category);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("Existing V1 category", rows.getString(1));
                    assertFalse(rows.next());
                }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version='2'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT count(*) FROM flyway_schema_history WHERE success AND version='3'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT to_regclass('products')::text")) {
                assertTrue(rows.next());
                assertEquals("products", rows.getString(1));
            }
        }
    }

    private static Flyway migration(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static Integer v1Checksum(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT checksum FROM flyway_schema_history WHERE success AND version='1'")) {
            assertTrue(rows.next());
            Integer checksum = rows.getObject(1, Integer.class);
            assertNotNull(checksum);
            assertFalse(rows.next());
            return checksum;
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
