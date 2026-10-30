package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
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

/** Real PostgreSQL candidate checks; NOT Store JPA, authorization or production S01 acceptance. */
@Testcontainers
@Tag("postgresql")
class StoreSchemaProposalPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID SELLER_A = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SELLER_B = UUID.fromString("30000000-0000-4000-8000-000000000002");

    @BeforeAll static void schemaProposal() throws Exception {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var input = StoreSchemaProposalPostgresTest.class.getResourceAsStream("/proposals/store-candidate.sql")) {
            assertNotNull(input);
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @BeforeEach void resetIsolatedDatabase() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM store_candidate");
            statement.executeUpdate("DELETE FROM seller_profiles");
        }
        try (Connection connection = connect(); var statement = connection.prepareStatement(
                "INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED')")) {
            statement.setObject(1, SELLER_A);
            statement.setString(2, " Exact-Owner-A ");
            statement.executeUpdate();
            statement.setObject(1, SELLER_B);
            statement.setString(2, "owner-b");
            statement.executeUpdate();
        }
    }

    @Test void candidateDoesNotBecomeARuntimeFlywayMigration() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT version,script FROM flyway_schema_history"
                     + " WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
            assertTrue(rows.next());
            assertEquals("1", rows.getString(1));
            assertEquals("V1__create_seller_profiles.sql", rows.getString(2));
            assertTrue(rows.next());
            assertEquals("2", rows.getString(1));
            assertEquals("V2__create_stores.sql", rows.getString(2));
            assertTrue(rows.next());
            assertEquals("3", rows.getString(1));
            assertEquals("V3__seller_outbox.sql", rows.getString(2));
            assertTrue(rows.next());
            assertEquals("4", rows.getString(1));
            assertEquals("V4__seller_contact.sql", rows.getString(2));
            assertFalse(rows.next());
        }
    }

    @Test void durableStoresResolveExactCurrentOwnershipAndSellerStatus() throws Exception {
        try (Connection connection = connect()) {
            insert(connection, SELLER_A, "store-a");
            insert(connection, SELLER_B, "store-b");
        }
        try (Connection fresh = connect(); var statement = fresh.createStatement()) {
            // A FK guarantees existence, not current APPROVED eligibility; future policy must re-read it.
            statement.executeUpdate("UPDATE seller_profiles SET status='SUSPENDED',review_reason='Review' WHERE id='" + SELLER_A + "'");
            try (var rows = statement.executeQuery("SELECT s.owner_reference,s.status,t.open FROM store_candidate t"
                    + " JOIN seller_profiles s ON s.id=t.seller_id WHERE t.slug='store-a'")) {
                assertTrue(rows.next());
                assertEquals(" Exact-Owner-A ", rows.getString(1));
                assertEquals("SUSPENDED", rows.getString(2));
                assertTrue(rows.getBoolean(3));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM store_candidate")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sellerAndCanonicalSlugAreIndependentlyUnique(boolean sameSeller) throws Exception {
        try (Connection connection = connect()) {
            insert(connection, SELLER_A, "store-a");
            SQLException duplicate = assertThrows(SQLException.class, () -> insert(connection,
                    sameSeller ? SELLER_A : SELLER_B, sameSeller ? "store-b" : "store-a"));
            assertEquals("23505", duplicate.getSQLState());
        }
    }

    @Test void sellerReferenceRejectsMissingSellerAndRestrictsDeletion() throws Exception {
        try (Connection connection = connect()) {
            assertEquals("23503", assertThrows(SQLException.class,
                    () -> insert(connection, UUID.randomUUID(), "missing-seller")).getSQLState());
            insert(connection, SELLER_A, "store-a");
            try (var statement = connection.createStatement()) {
                assertEquals("23503", assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "DELETE FROM seller_profiles WHERE id='" + SELLER_A + "'")).getSQLState());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Upper", "with space", "-first", "last-", "two--parts", "café", "a\nb"})
    void invalidSlugsAreRejected(String slug) throws Exception {
        try (Connection connection = connect()) {
            assertEquals("23514", assertThrows(SQLException.class,
                    () -> insert(connection, SELLER_A, slug)).getSQLState());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"slug=NULL", "name=NULL", "open=NULL", "seller_id=NULL", "name=''",
            "name='   '", "name=' padded '", "slug=repeat('a',129)", "name=repeat('a',256)"})
    void requiredValuesAndBoundsAreEnforced(String assignment) throws Exception {
        try (Connection connection = connect()) {
            insert(connection, SELLER_A, "store-a");
            try (var statement = connection.createStatement()) {
                String state = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE store_candidate SET " + assignment)).getSQLState();
                assertTrue(state.equals("23502") || state.equals("23514") || state.equals("22001"), state);
            }
        }
    }

    @Test void registrationRollbackReleasesBothUniqueKeys() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            insert(connection, SELLER_A, "store-a");
            connection.rollback();
        }
        try (Connection fresh = connect()) {
            insert(fresh, SELLER_A, "store-a");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentRegistrationsCannotBypassEitherUniqueConstraint(boolean sameSeller) throws Exception {
        var workers = Executors.newSingleThreadExecutor();
        // Release the winner's lock before closing a possibly blocked competitor on failure.
        try (Connection competitor = connect(); Connection winner = connect()) {
            winner.setAutoCommit(false);
            insert(winner, SELLER_A, "store-a");
            int pid;
            try (var statement = competitor.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(rows.next());
                pid = rows.getInt(1);
            }
            var pending = workers.submit(() -> {
                try {
                    insert(competitor, sameSeller ? SELLER_A : SELLER_B, sameSeller ? "store-b" : "store-a");
                    return "COMMITTED";
                } catch (SQLException failure) { return failure.getSQLState(); }
            });
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
            assertEquals("23505", pending.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
        try (Connection fresh = connect(); var statement = fresh.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM store_candidate")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    private static void insert(Connection connection, UUID sellerId, String slug) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO store_candidate(id,seller_id,slug,name,open) VALUES (?,?,?,'Candidate store',true)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, sellerId);
            statement.setString(3, slug);
            statement.executeUpdate();
        }
    }
}
