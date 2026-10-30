package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
            assertEquals("1,2,3,4,5", rows.getString(1), "Only Category V1, Product V2, image reservations V3, details V4 and write fence V5 are migrations");
        }
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history"
                     + " WHERE script='catalog-product-candidate.sql'")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "The historical SQL candidate remains outside Flyway history");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void candidateRejectsEveryJava21BoundarySpaceWithoutRewritingSku(boolean prefix) throws Exception {
        int[] spaces = IntStream.rangeClosed(0, Character.MAX_CODE_POINT)
                .filter(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp)).toArray();
        try (Connection connection = connect()) {
            for (int cp : spaces) {
                String space = new String(Character.toChars(cp));
                String sku = prefix ? space + "SKU" : "SKU" + space;
                String diagnostic = "U+%04X prefix=%s".formatted(cp, prefix);
                assertThrows(ProductRuleException.class, () -> new ProductListing(UUID.randomUUID(),
                        UUID.randomUUID(), CATEGORY, sku, "Name", BigDecimal.ONE, "VND", ProductStatus.DRAFT, null), diagnostic);
                SQLException rejected = assertThrows(SQLException.class,
                        () -> insert(connection, "store-a", sku, "10"), diagnostic);
                assertEquals("23514", rejected.getSQLState(), diagnostic);
            }
        }
        assertCounts(1, 0);
    }

    @Test void candidateExactLookupPreservesCaseCompositionAndInternalSpaces() throws Exception {
        String[] skus = {"SKU", "sku", "\u00e9-SKU", "e\u0301-SKU", "S\u00a0K\u2007U\u202fX", "S\tKU"};
        try (Connection connection = connect()) {
            for (String sku : skus) insert(connection, "store-a", sku, "10");
        }
        try (Connection fresh = connect()) {
            try (var query = fresh.createStatement(); var rows = query.executeQuery(
                    "SELECT c.collname,c.collisdeterministic,current_setting('server_encoding')"
                            + " FROM pg_attribute a JOIN pg_collation c ON c.oid=a.attcollation"
                            + " WHERE a.attrelid='catalog_product_candidate'::regclass AND a.attname='inventory_sku'")) {
                assertTrue(rows.next());
                assertEquals("C", rows.getString(1));
                assertTrue(rows.getBoolean(2));
                assertEquals("UTF8", rows.getString(3));
            }
            try (var query = fresh.prepareStatement(
                    "SELECT inventory_sku FROM catalog_product_candidate WHERE inventory_sku=?")) {
                for (String sku : skus) {
                    query.setString(1, sku);
                    try (var rows = query.executeQuery()) {
                        assertTrue(rows.next());
                        assertArrayEquals(sku.getBytes(StandardCharsets.UTF_8), rows.getBytes(1));
                        assertFalse(rows.next(), "Exact lookup must not fold case or Unicode composition");
                    }
                }
            }
        }
        assertCounts(1, skus.length);
    }

    @Test void oversizedSkuCannotBeSilentlyTruncatedBeforeBoundaryValidation() throws Exception {
        String maximum = "A".repeat(128);
        try (Connection connection = connect()) {
            assertEquals("23514", assertThrows(SQLException.class,
                    () -> insert(connection, "store-a", maximum + " ", "10")).getSQLState());
        }
        assertCounts(1, 0);
        try (Connection connection = connect()) {
            assertEquals("23514", assertThrows(SQLException.class,
                    () -> insert(connection, "store-a", maximum + "A", "10")).getSQLState());
            insert(connection, "store-a", maximum, "10");
        }
        try (Connection fresh = connect(); var query = fresh.createStatement();
             var rows = query.executeQuery("SELECT inventory_sku FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals(maximum, rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @Test void runtimeFlywayRevalidationLeavesCandidateOutsideMigrationHistory() throws Exception {
        Flyway runtime = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").load();
        runtime.validate();
        assertEquals(0, runtime.migrate().migrationsExecuted);
        assertEquals(java.util.List.of("1", "2", "3", "4", "5"), java.util.Arrays.stream(runtime.info().applied())
                .map(migration -> migration.getVersion().toString()).toList());
        assertEquals("5", runtime.info().current().getVersion().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNIQUE", "FK"})
    void constraintConflictRollsBackEarlierWritesInTheSameTransaction(String conflict) throws Exception {
        try (Connection connection = connect()) {
            insert(connection, "store-a", "existing", "10");
            connection.setAutoCommit(false);
            insert(connection, "store-b", "must-rollback", "20");
            SQLException rejected;
            if (conflict.equals("UNIQUE")) {
                rejected = assertThrows(SQLException.class,
                        () -> insert(connection, "store-b", "existing", "30"));
            } else {
                try (var query = connection.prepareStatement(
                        "UPDATE catalog_product_candidate SET category_id=? WHERE inventory_sku='must-rollback'")) {
                    query.setObject(1, UUID.randomUUID());
                    rejected = assertThrows(SQLException.class, query::executeUpdate);
                }
            }
            assertEquals(conflict.equals("UNIQUE") ? "23505" : "23503", rejected.getSQLState());
            try (var query = connection.createStatement()) {
                assertEquals("25P02", assertThrows(SQLException.class,
                        () -> query.executeQuery("SELECT 1")).getSQLState());
            }
            connection.rollback();
        }
        assertCounts(1, 1);
        try (Connection fresh = connect(); var query = fresh.createStatement();
             var rows = query.executeQuery("SELECT inventory_sku FROM catalog_product_candidate")) {
            assertTrue(rows.next());
            assertEquals("existing", rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void categoryDeleteAndProductInsertCannotCommitAnOrphanInEitherOrder(boolean productFirst) throws Exception {
        var workers = Executors.newSingleThreadExecutor();
        // Close the uncommitted holder first even when observing the competitor fails.
        try (Connection competitor = connect(); Connection holder = connect()) {
            holder.setAutoCommit(false);
            if (productFirst) insert(holder, "store-a", "race-sku", "10");
            else deleteCategory(holder);
            int holderPid = backendPid(holder), competitorPid = backendPid(competitor);
            var pending = workers.submit(() -> {
                try {
                    if (productFirst) deleteCategory(competitor);
                    else insert(competitor, "store-b", "race-sku", "10");
                    return "COMMITTED";
                } catch (SQLException rejected) {
                    return rejected.getSQLState();
                }
            });
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                try (Connection observer = connect(); var query = observer.prepareStatement(
                        "SELECT ? = ANY(pg_blocking_pids(?))")) {
                    query.setInt(1, holderPid);
                    query.setInt(2, competitorPid);
                    while (true) {
                        try (var rows = query.executeQuery()) {
                            assertTrue(rows.next());
                            if (rows.getBoolean(1)) break;
                        }
                        assertFalse(pending.isDone(), "Competitor must wait for the actual FK lock");
                        Thread.sleep(25);
                    }
                }
            });
            holder.commit();
            assertEquals("23503", pending.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
        }
        assertCounts(productFirst ? 1 : 0, productFirst ? 1 : 0);
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT pg_backend_pid()")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void deleteCategory(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM categories WHERE id=?")) {
            query.setObject(1, CATEGORY);
            assertEquals(1, query.executeUpdate());
        }
    }

    private static void assertCounts(int categories, int products) throws SQLException {
        try (Connection fresh = connect(); var query = fresh.createStatement();
             var rows = query.executeQuery("SELECT (SELECT count(*) FROM categories),"
                     + " (SELECT count(*) FROM catalog_product_candidate),"
                     + " (SELECT count(*) FROM catalog_product_candidate p LEFT JOIN categories c"
                     + " ON c.id=p.category_id WHERE c.id IS NULL)")) {
            assertTrue(rows.next());
            assertEquals(categories, rows.getInt(1));
            assertEquals(products, rows.getInt(2));
            assertEquals(0, rows.getInt(3), "No orphan or cascade deletion is permitted");
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
        Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        try (var query = connection.createStatement()) {
            query.execute("SET statement_timeout='15s'");
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
        return connection;
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
