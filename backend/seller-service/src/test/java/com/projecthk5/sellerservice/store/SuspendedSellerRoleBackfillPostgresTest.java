package com.projecthk5.sellerservice.store;

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

/** Sellers suspended before the role was kept lost it at Auth, so they cannot finish open orders until it is sent again. */
@Testcontainers
@Tag("postgresql")
class SuspendedSellerRoleBackfillPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insert(Connection connection, UUID id, String owner, String status, long version) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO seller_profiles(id, owner_reference, status, review_reason, version) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, owner);
            statement.setString(3, status);
            statement.setString(4, "SUSPENDED".equals(status) ? "Kiểm tra" : null);
            statement.setLong(5, version);
            statement.executeUpdate();
        }
    }

    @Test
    void onlySuspendedSellersGetTheRoleBackWithARevisionAuthAccepts() throws Exception {
        flyway("6").migrate();
        UUID suspended = UUID.randomUUID(), approved = UUID.randomUUID();
        try (Connection connection = connect()) {
            insert(connection, suspended, "owner-suspended", "SUSPENDED", 4);
            insert(connection, approved, "owner-approved", "APPROVED", 2);
        }

        assertEquals(1, flyway("latest").migrate().migrationsExecuted);

        try (Connection connection = connect(); var rows = connection.createStatement().executeQuery(
                "SELECT o.seller_id, o.kind, o.payload::jsonb->>'accountId', (o.payload::jsonb->>'granted')::boolean,"
                        + " (o.payload::jsonb->>'revision')::bigint, o.delivered_at, p.version"
                        + " FROM seller_outbox o JOIN seller_profiles p ON p.id = o.seller_id")) {
            assertTrue(rows.next());
            assertEquals(suspended, rows.getObject(1, UUID.class));
            assertEquals("ROLE_SYNC", rows.getString(2));
            assertEquals("owner-suspended", rows.getString(3));
            assertTrue(rows.getBoolean(4));
            // Auth ignores a revision it already applied, and the revoke used version 4.
            assertEquals(5, rows.getLong(5));
            assertNull(rows.getObject(6));
            assertEquals(5, rows.getLong(7));
            assertFalse(rows.next());
        }
    }
}
