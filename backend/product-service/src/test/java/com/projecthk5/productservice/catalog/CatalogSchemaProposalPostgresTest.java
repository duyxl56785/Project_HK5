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
             var rows = statement.executeQuery("SELECT string_agg(version, ',' ORDER BY installed_rank)"
                     + " FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            assertTrue(rows.next());
            assertEquals("1,2", rows.getString(1), "Only Category V1 and production Product V2 are migrations");
        }
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history"
                     + " WHERE script='catalog-product-candidate.sql'")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "The historical SQL candidate remains outside Flyway history");
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

    @Test void versionAndTimestampsAreDurableAndUpdatesRetainSku() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10");
        }
        java.time.OffsetDateTime created;
        try (Connection fresh = connect(); var statement = fresh.createStatement();
             var rows = statement.executeQuery("SELECT version,created_at,updated_at FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getLong(1));
            created = rows.getObject(2, java.time.OffsetDateTime.class);
            assertNotNull(created);
            assertEquals(created, rows.getObject(3, java.time.OffsetDateTime.class));
        }
        try (Connection connection = connect()) {
            assertEquals(1, updatePrice(connection, 0, "20.1234567890123456789"));
            assertEquals(0, updatePrice(connection, 0, "999"));
        }
        try (Connection fresh = connect(); var statement = fresh.createStatement();
             var rows = statement.executeQuery("SELECT inventory_sku,unit_price,version,created_at,updated_at FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals("SKU", rows.getString(1));
            assertEquals(0, new BigDecimal("20.1234567890123456789").compareTo(rows.getBigDecimal(2)));
            assertEquals(1, rows.getLong(3));
            assertEquals(created, rows.getObject(4, java.time.OffsetDateTime.class));
            assertFalse(rows.getObject(5, java.time.OffsetDateTime.class).isBefore(created));
        }
    }

    @Test void rollbackRestoresPriceVersionAndUpdateTimestampTogether() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10");
            connection.setAutoCommit(false);
            assertEquals(1, updatePrice(connection, 0, "20"));
            connection.rollback();
        }
        try (Connection fresh = connect(); var statement = fresh.createStatement();
             var rows = statement.executeQuery("SELECT unit_price,version,created_at=updated_at FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals(0, BigDecimal.TEN.compareTo(rows.getBigDecimal(1)));
            assertEquals(0, rows.getLong(2));
            assertTrue(rows.getBoolean(3));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"version=-1", "version=NULL", "status='UNKNOWN'", "inventory_sku=''",
            "inventory_sku=' padded '", "currency=' '", "store_reference=' '", "created_at=NULL",
            "updated_at=NULL", "updated_at=created_at-interval '1 second'"})
    void candidateMetadataConstraintsRejectInvalidWrites(String assignment) throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10");
            try (var statement = connection.createStatement()) {
                String state = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE catalog_product_candidate SET " + assignment)).getSQLState();
                assertTrue(state.equals("23514") || state.equals("23502"), state);
            }
        }
    }

    @Test void concurrentCompareAndSetCannotOverwriteTheSameVersionTwice() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "SKU", "10");
        }
        var workers = Executors.newSingleThreadExecutor();
        try (Connection competitor = connect(); Connection winner = connect()) {
            winner.setAutoCommit(false);
            assertEquals(1, updatePrice(winner, 0, "20"));
            int pid;
            try (var statement = competitor.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(rows.next());
                pid = rows.getInt(1);
            }
            var pending = workers.submit(() -> updatePrice(competitor, 0, "999"));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                try (Connection observer = connect(); var query = observer.prepareStatement(
                        "SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=?")) {
                    query.setInt(1, pid);
                    while (true) {
                        try (var rows = query.executeQuery()) {
                            if (rows.next() && rows.getBoolean(1)) break;
                        }
                        Thread.sleep(25);
                    }
                }
            });
            winner.commit();
            assertEquals(0, pending.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
        try (Connection fresh = connect(); var statement = fresh.createStatement();
             var rows = statement.executeQuery("SELECT unit_price,version FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals(0, new BigDecimal("20").compareTo(rows.getBigDecimal(1)));
            assertEquals(1, rows.getLong(2));
        }
    }

    // Candidate SQL CAS only: not a JPA @Version integration or an external Catalog freshness lease.
    private static int updatePrice(Connection connection, long expectedVersion, String price) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE catalog_product_candidate"
                + " SET unit_price=?::numeric,version=version+1,updated_at=CURRENT_TIMESTAMP"
                + " WHERE inventory_sku='SKU' AND version=?")) {
            statement.setString(1, price);
            statement.setLong(2, expectedVersion);
            return statement.executeUpdate();
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
