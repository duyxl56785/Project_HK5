package com.projecthk5.productservice.image.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgresql")
class ProductImageLifecycleMigrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private String schema;
    private final UUID product = UUID.randomUUID();
    private final UUID pending = UUID.randomUUID();
    private final UUID cancelled = UUID.randomUUID();

    @BeforeEach void startFromAppliedV3WithExistingRows() throws Exception {
        schema = "lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        flyway("3").migrate();
        UUID category = UUID.randomUUID();
        sql("INSERT INTO categories(id,name) VALUES (?, 'Category')", category);
        sql("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                + " VALUES (?,?,?,'migration-sku','Product',1,'VND','DRAFT',0)", product, UUID.randomUUID(), category);
        insert(pending, UUID.randomUUID(), "1".repeat(64), 1);
        insert(cancelled, UUID.randomUUID(), "2".repeat(64), 2);
        sql("UPDATE product_image_metadata SET state='CANCELLED',display_order=NULL,cancelled_at=created_at WHERE id=?", cancelled);
    }

    @Test void upgradePreservesV3HistoryAndBothOldStates() throws Exception {
        Object checksum = value("SELECT checksum FROM flyway_schema_history WHERE version='3'");
        var latest = flyway(null);
        latest.migrate();
        latest.validate();
        assertEquals("4", latest.info().current().getVersion().getVersion());
        assertEquals(checksum, value("SELECT checksum FROM flyway_schema_history WHERE version='3'"));
        assertEquals("PENDING_STORAGE", value("SELECT state FROM product_image_metadata WHERE id=?", pending));
        assertEquals("CANCELLED", value("SELECT state FROM product_image_metadata WHERE id=?", cancelled));
        assertNull(value("SELECT storage_key FROM product_image_metadata WHERE id=?", pending));
        assertNull(value("SELECT ready_at FROM product_image_metadata WHERE id=?", cancelled));
    }

    @Test void boundReadyTransitionWorksButCannotBeReboundOrMadePendingAgain() throws Exception {
        flyway(null).migrate();
        assertDoesNotThrow(() -> ready(pending));
        assertEquals(pending.toString(), value("SELECT storage_key FROM product_image_metadata WHERE id=?", pending));
        assertThrows(SQLException.class, () -> sql("UPDATE product_image_metadata SET storage_key=? WHERE id=?",
                UUID.randomUUID().toString(), pending));
        assertThrows(SQLException.class, () -> sql("UPDATE product_image_metadata SET state='PENDING_STORAGE',"
                + "storage_key=NULL,ready_at=NULL WHERE id=?", pending));
        assertThrows(SQLException.class, () -> sql("UPDATE product_image_metadata SET display_order=2 WHERE id=?", pending));
        sql("UPDATE product_image_metadata SET state='CANCELLED',display_order=NULL,cancelled_at=ready_at WHERE id=?", pending);
        assertThrows(SQLException.class, () -> ready(pending));
    }

    @Test void readyNeedsExactServerKeyAndLiveContentRemainsUnique() throws Exception {
        flyway(null).migrate();
        assertThrows(SQLException.class, () -> sql("UPDATE product_image_metadata SET state='READY',ready_at=created_at WHERE id=?", pending));
        assertThrows(SQLException.class, () -> sql("UPDATE product_image_metadata SET state='READY',ready_at=created_at,storage_key='../escape' WHERE id=?", pending));
        ready(pending);
        assertThrows(SQLException.class, () -> insert(UUID.randomUUID(), UUID.randomUUID(), "1".repeat(64), 2));
        for (int slot = 2; slot <= 5; slot++) insert(UUID.randomUUID(), UUID.randomUUID(), ("" + slot).repeat(64), slot);
        assertThrows(SQLException.class, () -> insert(UUID.randomUUID(), UUID.randomUUID(), "6".repeat(64), 6));
        assertThrows(SQLException.class, () -> insert(UUID.randomUUID(), UUID.randomUUID(), "7".repeat(64), 5));
    }

    private void ready(UUID id) throws Exception {
        sql("UPDATE product_image_metadata SET state='READY',storage_key=id::text,ready_at=created_at WHERE id=?", id);
    }

    private void insert(UUID id, UUID operation, String digest, int slot) throws Exception {
        sql("INSERT INTO product_image_metadata(id,product_id,operation_id,content_sha256,media_type,size_bytes,width,height,"
                + "display_order,state,created_at,version) VALUES (?,?,?,?,'image/png',100,2,2,?,'PENDING_STORAGE',now(),0)",
                id, product, operation, digest, slot);
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).defaultSchema(schema);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void sql(String query, Object... args) throws Exception {
        try (var connection = connection(); var statement = connection.prepareStatement(query)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    private Object value(String query, Object... args) throws Exception {
        try (var connection = connection(); var statement = connection.prepareStatement(query)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getObject(1); }
        }
    }
}
