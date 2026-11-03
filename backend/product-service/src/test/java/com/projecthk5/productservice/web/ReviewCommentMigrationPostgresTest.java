package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R06 on a database that already holds a longer comment: V9 keeps it and rejects new ones over 1000. */
@Testcontainers
@Tag("postgresql")
class ReviewCommentMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID PRODUCT = UUID.randomUUID();

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static UUID review(Connection connection, String comment) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement("INSERT INTO product_reviews(id, order_id, product_id, customer_id, rating, comment, created_at)"
                + " VALUES (?, ?, ?, 'customer', 5, ?, now())")) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, PRODUCT);
            statement.setString(4, comment);
            statement.executeUpdate();
        }
        return id;
    }

    @Test
    void keepsOlderLongCommentsAndChecksNewOnesInUtf16Units() throws Exception {
        flyway("8").migrate();
        UUID category = UUID.randomUUID();
        UUID older;
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO categories(id, name) VALUES ('" + category + "', 'Review category')");
            statement.executeUpdate("INSERT INTO products(id, store_id, category_id, inventory_sku, name, unit_price, currency, status, version)"
                    + " VALUES ('" + PRODUCT + "', '" + UUID.randomUUID() + "', '" + category + "', 'review-limit', 'Product', 1, 'VND', 'ACTIVE', 0)");
            older = review(connection, "a".repeat(1001));
        }

        assertEquals(1, flyway("9").migrate().migrationsExecuted);

        try (Connection connection = connect()) {
            try (var row = connection.createStatement().executeQuery("SELECT char_length(comment) FROM product_reviews WHERE id = '" + older + "'")) {
                assertTrue(row.next());
                assertEquals(1001, row.getInt(1));
            }
            assertNotNull(review(connection, "a".repeat(1000)));
            assertNotNull(review(connection, null));
            assertEquals("23514", assertThrows(SQLException.class, () -> review(connection, "a".repeat(1001))).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> review(connection, "😀".repeat(500) + "a")).getSQLState());
        }
        flyway("9").validate();
    }
}
