package com.projecthk5.productservice.image.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production migration constraints, including callers that bypass Java orchestration. */
@SpringBootTest
@Testcontainers
@Tag("postgresql")
class ProductImageMetadataSchemaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("81000000-0000-4000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("81000000-0000-4000-8000-000000000002");
    private static final UUID STORE = UUID.fromString("81000000-0000-4000-8000-000000000003");
    private static final Instant CREATED = Instant.parse("2026-10-27T00:00:00Z");
    private static final String INSERT = "INSERT INTO product_image_metadata"
            + "(id,product_id,operation_id,content_sha256,media_type,size_bytes,width,height,display_order,state,created_at,version)"
            + " VALUES (?,?,?,?,?,?,?,?,?,'PENDING_STORAGE',?,0)";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void createProduct() {
        // A fresh disposable database is used by this class; no development data is touched.
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('product_image_metadata') IS NOT NULL", Boolean.class))) {
            jdbc.update("DELETE FROM product_image_metadata");
        }
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Image category')", CATEGORY);
        jdbc.update("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                + " VALUES (?,?,?,'image-schema','Product',1,'VND','DRAFT',0)", PRODUCT, STORE, CATEGORY);
    }

    @Test
    void productionMigrationCreatesReservationTable() {
        assertEquals("3", flyway.info().current().getVersion().getVersion());
        flyway.validate();
        UUID image = insert(1);
        assertEquals(image, jdbc.queryForObject("SELECT id FROM product_image_metadata", UUID.class));
        assertNull(jdbc.queryForObject("SELECT storage_key FROM product_image_metadata", String.class));
        assertEquals("PENDING_STORAGE", jdbc.queryForObject("SELECT state FROM product_image_metadata", String.class));
    }

    @Test
    void upgradeFromCategoryV1PreservesDataAndValidatesAllProductionMigrations() {
        String schema = "image_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway v1 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion("1")).load();
        v1.migrate();
        jdbc.update("INSERT INTO " + schema + ".categories(id,name) VALUES (?, 'Preserved category')", CATEGORY);
        Flyway v2 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion("2")).load();
        v2.migrate();
        jdbc.update("INSERT INTO " + schema + ".products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                + " VALUES (?,?,?,'preserved-image-product','Preserved Product',10,'VND','DRAFT',0)", PRODUCT, STORE, CATEGORY);
        Flyway latest = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load();
        latest.migrate();
        latest.validate();
        assertEquals("3", latest.info().current().getVersion().getVersion());
        assertEquals("Preserved category", jdbc.queryForObject("SELECT name FROM " + schema + ".categories WHERE id=?", String.class, CATEGORY));
        assertEquals("Preserved Product", jdbc.queryForObject("SELECT name FROM " + schema + ".products WHERE id=?", String.class, PRODUCT));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".product_image_metadata", Integer.class));
    }

    @Test
    void foreignKeyRestrictsMissingProductAndProductDeletion() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), hash(1), 1));
        insert(1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM products WHERE id=?", PRODUCT));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test
    void directSqlCannotCreateSixthPendingImageOrReuseSlot() {
        for (int i = 1; i <= 5; i++) insert(i);
        assertThrows(DataIntegrityViolationException.class, () -> insert(6));
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), PRODUCT, UUID.randomUUID(), hash(7), 5));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM product_image_metadata", Integer.class));
    }

    @Test
    void operationAndPendingContentHaveIndependentUniqueConstraints() {
        UUID operation = UUID.randomUUID();
        insert(UUID.randomUUID(), PRODUCT, operation, hash(1), 1);
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), PRODUCT, operation, hash(2), 2));
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), PRODUCT, UUID.randomUUID(), hash(1), 2));
    }

    @Test
    void cancellationReleasesSlotAndContentButRetainsOperationTombstone() {
        UUID operation = UUID.randomUUID();
        UUID image = insert(UUID.randomUUID(), PRODUCT, operation, hash(1), 1);
        cancel(image);
        insert(UUID.randomUUID(), PRODUCT, UUID.randomUUID(), hash(1), 1);
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), PRODUCT, operation, hash(2), 2));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM product_image_metadata", Integer.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM products WHERE id=?", PRODUCT));
    }

    @Test
    void cancellationIsTerminalAndCannotReassignDisplayOrder() {
        UUID image = insert(1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET display_order=2 WHERE id=?", image));
        cancel(image);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET state='PENDING_STORAGE',display_order=1,cancelled_at=NULL WHERE id=?", image));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET cancelled_at=created_at WHERE id=?", image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"display_order=NULL", "state='CANCELLED',display_order=NULL", "cancelled_at=created_at"})
    void lifecycleChecksRejectIncoherentPendingOrCancelledMetadata(String changes) {
        UUID image = insert(1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET " + changes + " WHERE id=?", image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "product_id", "operation_id"})
    void directSqlCannotRebindReservationIdentity(String column) {
        UUID image = insert(1);
        UUID replacement = UUID.randomUUID();
        if ("product_id".equals(column)) {
            jdbc.update("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                    + " VALUES (?,?,?,'other-image-product','Other Product',1,'VND','DRAFT',0)", replacement, STORE, CATEGORY);
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET " + column + "=? WHERE id=?", replacement, image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content_sha256", "media_type", "size_bytes", "width", "height", "created_at"})
    void directSqlCannotChangeValidatedContent(String column) {
        UUID image = insert(1);
        String replacement = switch (column) {
            case "content_sha256" -> "'" + hash(2) + "'";
            case "media_type" -> "'image/jpeg'";
            case "created_at" -> "created_at + interval '1 second'";
            default -> "2";
        };
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET " + column + "=" + replacement + " WHERE id=?", image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../object.png", "/private/absolute.png", "known-object"})
    void arbitraryStorageKeysCannotBeAttachedWhileStorageIntegrationIsDisabled(String key) {
        UUID image = insert(1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET storage_key=? WHERE id=?", key, image));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE product_image_metadata SET state='READY' WHERE id=?", image));
    }

    @ParameterizedTest
    @ValueSource(strings = {"size_bytes=0", "size_bytes=2097153", "width=0", "height=8193", "width=8192,height=8192", "media_type='image/gif'", "content_sha256='NOT-A-HASH'", "version=-1"})
    void invalidDirectSqlMetadataIsRejected(String changes) {
        // INSERT from a valid row exercises CHECK constraints, rather than the UPDATE immutability trigger.
        UUID image = insert(1);
        var expressions = new java.util.LinkedHashMap<String, String>();
        expressions.put("content_sha256", "'" + hash(2) + "'");
        expressions.put("media_type", "media_type");
        expressions.put("size_bytes", "size_bytes");
        expressions.put("width", "width");
        expressions.put("height", "height");
        expressions.put("version", "0");
        for (String assignment : changes.split(",")) {
            String[] parts = assignment.split("=", 2);
            expressions.put(parts[0], parts[1]);
        }
        String sql = "INSERT INTO product_image_metadata"
                + "(id,product_id,operation_id,content_sha256,media_type,size_bytes,width,height,display_order,state,created_at,version)"
                + " SELECT ?,product_id,?," + expressions.get("content_sha256") + "," + expressions.get("media_type")
                + "," + expressions.get("size_bytes") + "," + expressions.get("width") + "," + expressions.get("height")
                + ",2,'PENDING_STORAGE',created_at," + expressions.get("version") + " FROM product_image_metadata WHERE id=?";
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, UUID.randomUUID(), UUID.randomUUID(), image));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM product_image_metadata", Integer.class));
    }

    private UUID insert(int slot) {
        return insert(UUID.randomUUID(), PRODUCT, UUID.randomUUID(), hash(slot), slot);
    }

    private UUID insert(UUID id, UUID product, UUID operation, String digest, int slot) {
        jdbc.update(INSERT, id, product, operation, digest, "image/png", 100, 1, 1, slot, Timestamp.from(CREATED));
        return id;
    }

    private void cancel(UUID image) {
        jdbc.update("UPDATE product_image_metadata SET state='CANCELLED',display_order=NULL,cancelled_at=? WHERE id=?", Timestamp.from(CREATED.plusSeconds(1)), image);
    }

    private static String hash(int value) { return String.format("%064x", value); }
}
