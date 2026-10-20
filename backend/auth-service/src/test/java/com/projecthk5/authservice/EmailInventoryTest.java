package com.projecthk5.authservice;

import com.projecthk5.authservice.persistence.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EmailInventoryTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "AUTH_EMAIL_INVENTORY_JDBC_URL", matches = ".+")
    void existingDatabaseInventoryIsReadOnly() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("AUTH_EMAIL_INVENTORY_JDBC_URL"),
                "auth_app", Objects.requireNonNull(System.getenv("AUTH_DB_PASSWORD")))) {
            System.out.println("EXISTING_DATABASE_EMAIL_INVENTORY: " + scan(connection));
        }
    }

    record Inventory(long total, List<UUID> noncanonical, List<UUID> unicodeNoncanonical,
                     List<List<UUID>> duplicateCanonicalGroups) {}

    // Only IDs/counts leave this read-only snapshot; raw emails and canonical keys stay in memory.
    static Inventory scan(Connection connection) throws SQLException {
        assertTrue(connection.getAutoCommit(), "Inventory owns a fresh transaction");
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        try {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SHOW transaction_read_only")) {
                assertTrue(rows.next());
                assertEquals("on", rows.getString(1));
            }
            long total = 0;
            var noncanonical = new ArrayList<UUID>();
            var unicodeNoncanonical = new ArrayList<UUID>();
            // shortcut: groups stay in memory; use external sorting when the inventory exceeds heap.
            var canonicalGroups = new LinkedHashMap<String, List<UUID>>();
            try (var statement = connection.createStatement()) {
                statement.setFetchSize(500);
                statement.setQueryTimeout(60);
                try (var rows = statement.executeQuery("SELECT id,email FROM accounts ORDER BY id")) {
                    while (rows.next()) {
                        total++;
                        var id = rows.getObject(1, UUID.class);
                        var email = rows.getString(2);
                        var canonical = Account.normalizeEmail(email);
                        canonicalGroups.computeIfAbsent(canonical, ignored -> new ArrayList<>()).add(id);
                        if (!email.equals(canonical)) {
                            noncanonical.add(id);
                            if (email.codePoints().anyMatch(cp -> cp > 127)) { unicodeNoncanonical.add(id); }
                        }
                    }
                }
            }
            return new Inventory(total, List.copyOf(noncanonical), List.copyOf(unicodeNoncanonical),
                    canonicalGroups.values().stream().filter(ids -> ids.size() > 1).map(List::copyOf).toList());
        } finally {
            connection.rollback();
        }
    }
}
