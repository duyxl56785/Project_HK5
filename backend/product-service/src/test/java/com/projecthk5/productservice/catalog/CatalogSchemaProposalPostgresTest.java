package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL prototype checks, NOT Product JPA/runtime migration or Store integration acceptance. */
@Testcontainers
@Tag("postgresql")
class CatalogSchemaProposalPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @BeforeAll static void schemaProposal() throws Exception {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var input = CatalogSchemaProposalPostgresTest.class.getResourceAsStream("/proposals/catalog-product-candidate.sql")) {
            assertNotNull(input);
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @BeforeEach void resetIsolatedDatabase() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM catalog_product_candidate");
            statement.executeUpdate("DELETE FROM categories");
            statement.executeUpdate("INSERT INTO categories(id,name) VALUES ('" + CATEGORY + "','Prototype category')");
        }
    }

    @Test void proposalDoesNotBecomeAnAppliedProductFlywayMigration() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test void globalSkuIsUniqueAcrossDifferentStoreReferences() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "Sku-Unique", "10.1234567890123456789");
            SQLException duplicate = assertThrows(SQLException.class,
                    () -> insert(connection, "store-b", "Sku-Unique", "20"));
            assertEquals("23505", duplicate.getSQLState());
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM catalog_product_candidate")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test void exactSkuMappingAndDecimalPricePersistWithoutCaseFoldingOrRounding() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10.1234567890123456789");
            insert(connection, "store-b", "sku", "20.5");
        }
        try (Connection fresh = connect(); var query = fresh.prepareStatement(
                "SELECT inventory_sku, unit_price, currency, store_reference FROM catalog_product_candidate WHERE inventory_sku=?")) {
            query.setString(1, "SKU");
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("SKU", rows.getString(1));
                assertEquals(0, new BigDecimal("10.1234567890123456789").compareTo(rows.getBigDecimal(2)));
                assertEquals("TEST-CURRENCY", rows.getString(3));
                assertEquals("store-a", rows.getString(4));
                assertFalse(rows.next());
            }
        }
    }

    @Test void categoryReferencePreventsDeletionAndUnknownCategoryInsert() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10");
            try (var statement = connection.createStatement()) {
                assertEquals("23503", assertThrows(SQLException.class,
                        () -> statement.executeUpdate("DELETE FROM categories WHERE id='" + CATEGORY + "'")).getSQLState());
            }
            try (var statement = connection.createStatement()) {
                assertEquals("23503", assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE catalog_product_candidate SET category_id='"
                                + UUID.randomUUID() + "'")).getSQLState());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "NaN", "Infinity", "-Infinity"})
    void candidateSchemaRejectsNonPositiveAndNonFinitePrice(String price) throws Exception {
        try (Connection connection = connect()) {
            assertEquals("23514", assertThrows(SQLException.class,
                    () -> insert(connection, "store-a", "SKU", price)).getSQLState());
        }
    }

    @Test void rollbackDoesNotLeaveCandidateProductBehind() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            insert(connection, "store-a", "SKU", "10");
            connection.rollback();
        }
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test void concurrentDifferentStoresCannotCommitTheSameSku() throws Exception {
        var workers = Executors.newSingleThreadExecutor();
        // Close/rollback the lock holder before closing a possibly still-blocked competitor on failure.
        try (Connection competitor = connect(); Connection winner = connect()) {
            winner.setAutoCommit(false);
            insert(winner, "store-a", "same-sku", "10");
            int competitorPid;
            try (var statement = competitor.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(rows.next());
                competitorPid = rows.getInt(1);
            }
            var pending = workers.submit(() -> {
                try { insert(competitor, "store-b", "same-sku", "20"); return "COMMITTED"; }
                catch (SQLException duplicate) { return duplicate.getSQLState(); }
            });
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                try (Connection observer = connect(); var query = observer.prepareStatement(
                        "SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=?")) {
                    query.setInt(1, competitorPid);
                    while (true) {
                        try (var rows = query.executeQuery()) {
                            if (rows.next() && rows.getBoolean(1)) break;
                        }
                        Thread.sleep(25);
                    }
                }
            });
            winner.commit();
            assertEquals("23505", pending.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    private void insert(Connection connection, String store, String sku, String price) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO catalog_product_candidate"
                + "(id,store_reference,category_id,inventory_sku,unit_price,currency,status) VALUES (?,?,?,?,?::numeric,?,?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, store);
            statement.setObject(3, CATEGORY);
            statement.setString(4, sku);
            statement.setString(5, price);
            statement.setString(6, "TEST-CURRENCY");
            statement.setString(7, "ACTIVE");
            statement.executeUpdate();
        }
    }
}
