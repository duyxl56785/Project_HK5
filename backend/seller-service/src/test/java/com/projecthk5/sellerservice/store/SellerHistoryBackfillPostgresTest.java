package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V6 on a database that already holds sellers, which a fresh migration never exercises. */
@Testcontainers
@Tag("postgresql")
class SellerHistoryBackfillPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Test void existingSellersStartHistoryFromTheirCurrentStatus() throws Exception {
        UUID approved = UUID.randomUUID(), rejected = UUID.randomUUID();
        flyway("5").migrate();
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            try (var insert = connection.prepareStatement(
                    "INSERT INTO seller_profiles(id,owner_reference,status,review_reason) VALUES (?,?,?,?)")) {
                insert.setObject(1, approved);
                insert.setString(2, "owner-approved");
                insert.setString(3, "APPROVED");
                insert.setString(4, null);
                insert.executeUpdate();
                insert.setObject(1, rejected);
                insert.setString(2, "owner-rejected");
                insert.setString(3, "REJECTED");
                insert.setString(4, "Sai dia chi");
                insert.executeUpdate();
            }
            flyway("latest").migrate();
            try (var query = connection.prepareStatement(
                    "SELECT from_status, to_status, reason, reviewed_by FROM seller_status_history WHERE seller_id = ?")) {
                query.setObject(1, rejected);
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertNull(row.getString("from_status"));
                    assertEquals("REJECTED", row.getString("to_status"));
                    assertEquals("Sai dia chi", row.getString("reason"));
                    assertNull(row.getString("reviewed_by"));
                    assertFalse(row.next());
                }
                query.setObject(1, approved);
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("APPROVED", row.getString("to_status"));
                    assertFalse(row.next());
                }
            }
        }
    }
}
