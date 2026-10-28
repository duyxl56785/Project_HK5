package com.projecthk5.productservice.image;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.image.ProductImageMetadataService.Reservation;
import com.projecthk5.productservice.image.ProductImageValidator.ImageInput;
import com.projecthk5.productservice.image.ProductImageValidator.ValidatedImage;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(ProductImageMetadataServiceIntegrationTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgresql")
class ProductImageLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("77000000-0000-4000-8000-000000000001");
    private static final UUID STORE_A = UUID.fromString("77000000-0000-4000-8000-000000000002");
    private static final UUID PRODUCT = UUID.fromString("77000000-0000-4000-8000-000000000004");
    private static final String OWNER = "image-owner-a";
    @TempDir Path root;
    @Autowired ProductImageMetadataService metadata;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ProductImageMetadataServiceIntegrationTest.FixturePolicy policy;
    private LocalFileSystemImageStorage storage;
    private ProductImageLifecycleService lifecycle;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM product_image_metadata");
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Image category')", CATEGORY);
        jdbc.update("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                + " VALUES (?,?,?,'lifecycle-sku','Product',123,'VND','DRAFT',0)", PRODUCT, STORE_A, CATEGORY);
        policy.reset();
        storage = new LocalFileSystemImageStorage(root);
        lifecycle = lifecycle(storage);
    }

    @Test void uploadsAndReadsActualBytesWithDurableReadyMetadata() throws Exception {
        var receipt = upload(0, UUID.randomUUID(), 1);
        assertEquals("READY", receipt.state());
        assertEquals(2, receipt.productVersion());
        assertEquals(1, receipt.version());
        assertEquals(receipt.id().toString(), value("storage_key"));
        assertNotNull(value("ready_at"));
        assertArrayEquals(ReservedImageStorageTest.image(1).content(), lifecycle.read(OWNER, PRODUCT, receipt.id()));
        assertEquals("lifecycle-sku", jdbc.queryForObject("SELECT inventory_sku FROM products WHERE id=?", String.class, PRODUCT));
        assertEquals("123", jdbc.queryForObject("SELECT unit_price::text FROM products WHERE id=?", String.class, PRODUCT));
        assertEquals("VND", jdbc.queryForObject("SELECT currency FROM products WHERE id=?", String.class, PRODUCT));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .writeValueAsString(receipt).contains("storageKey"));
    }

    @Test void readyImagesCountTowardsFiveAndDuplicateContentCannotReserveAgain() throws Exception {
        for (int i = 0; i < 5; i++) upload(2L * i, UUID.randomUUID(), i);
        assertEquals(5, metadata.list(OWNER, PRODUCT).size());
        assertEquals(ProductImageMetadataException.Code.LIMIT_EXCEEDED,
                assertThrows(ProductImageMetadataException.class, () -> upload(10, UUID.randomUUID(), 20)).getCode());
        assertEquals(ProductImageMetadataException.Code.DUPLICATE_IMAGE,
                assertThrows(ProductImageMetadataException.class, () -> upload(10, UUID.randomUUID(), 1)).getCode());
        assertEquals(5, count());
        assertEquals(10, version());
    }

    @Test void exactRetryKeepsIdentityVersionsAndSinglePhysicalObject() throws Exception {
        UUID operation = UUID.randomUUID();
        var first = upload(0, operation, 1);
        assertEquals(first, upload(0, operation, 1));
        assertEquals(1, count());
        try (var directories = Files.list(root)) { assertEquals(1, directories.count()); }
        assertEquals(ProductImageMetadataException.Code.OPERATION_CONFLICT,
                assertThrows(ProductImageMetadataException.class, () -> upload(0, operation, 2)).getCode());
        assertEquals(first, upload(0, operation, 1));
    }

    @Test void concurrentLastSlotCannotExceedFive() throws Exception {
        for (int i = 0; i < 4; i++) upload(2L * i, UUID.randomUUID(), i);
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> first = () -> racedUpload(barrier, 30);
            Callable<Object> second = () -> racedUpload(barrier, 31);
            var futures = List.of(pool.submit(first), pool.submit(second));
            var results = List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Reservation.class::isInstance).count());
            assertEquals(1, results.stream().filter(ProductPersistenceException.class::isInstance).count());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
        assertEquals(5, count());
        assertEquals(10, version());
    }

    @Test void failedStorageLeavesCommittedPendingReservationAndSameOperationRecovers() throws Exception {
        var failing = new DelegatingStorage(storage) {
            @Override public StoredImage storeReserved(UUID id, ValidatedImage image) {
                throw new ImageStorageException(ImageStorageException.Code.IO_FAILURE);
            }
        };
        UUID operation = UUID.randomUUID();
        assertThrows(ImageStorageException.class, () -> lifecycle(failing).upload(OWNER, PRODUCT, 0, operation, input(1)));
        assertEquals("PENDING_STORAGE", value("state"));
        UUID id = (UUID) value("id");
        assertEquals(1, version());
        assertEquals(id, upload(0, operation, 1).id());
        assertEquals("READY", value("state"));
    }

    @Test void fileSurvivesDatabaseFailureAndRetryReusesIt() throws Exception {
        UUID operation = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_ready_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                + " IF NEW.state='READY' THEN RAISE EXCEPTION 'fixture ready rejected' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_ready_fixture BEFORE UPDATE ON product_image_metadata FOR EACH ROW EXECUTE FUNCTION reject_ready_fixture()");
        UUID id;
        try {
            assertThrows(ProductImageMetadataException.class, () -> upload(0, operation, 1));
            assertEquals("PENDING_STORAGE", value("state"));
            id = (UUID) value("id");
            assertArrayEquals(ReservedImageStorageTest.image(1).content(), storage.read(id.toString()));
            assertEquals(1, version());
        } finally {
            jdbc.execute("DROP TRIGGER reject_ready_fixture ON product_image_metadata");
            jdbc.execute("DROP FUNCTION reject_ready_fixture()");
        }
        assertEquals(id, upload(0, operation, 1).id());
        assertEquals(1, count());
        try (var objects = Files.list(root)) { assertEquals(1, objects.count()); }
    }

    @Test void stoppedProcessAfterReserveOrPartialWriteCanRetry() throws Exception {
        UUID operation = UUID.randomUUID();
        var image = ReservedImageStorageTest.image(1);
        var pending = metadata.reserve(OWNER, PRODUCT, 0, operation, image);
        storage.storeReserved(pending.id(), image);
        Files.delete(root.resolve(pending.id().toString()).resolve("content"));
        Files.write(root.resolve(pending.id().toString()).resolve(".pending"), new byte[]{1});
        assertEquals(pending.id(), upload(0, operation, 1).id());
        assertArrayEquals(image.content(), lifecycle.read(OWNER, PRODUCT, pending.id()));
    }

    @Test void cancellationIsTerminalAndCleanupIsIdempotent() throws Exception {
        UUID operation = UUID.randomUUID();
        var ready = upload(0, operation, 1);
        var cancelled = lifecycle.cancel(OWNER, PRODUCT, ready.productVersion(), ready.id(), ready.version());
        assertEquals("CANCELLED", cancelled.state());
        assertEquals(0, metadata.list(OWNER, PRODUCT).size());
        assertFalse(Files.exists(root.resolve(ready.id().toString()).resolve("content")));
        assertEquals(cancelled, lifecycle.cancel(OWNER, PRODUCT, 0, ready.id(), 0));
        assertEquals("CANCELLED", upload(0, operation, 1).state());
        assertThrows(ProductImageMetadataException.class, () -> lifecycle.read(OWNER, PRODUCT, ready.id()));
        assertFalse(Files.exists(root.resolve(ready.id().toString()).resolve("content")));
    }

    @Test void cleanupFailureDoesNotRollbackCancellationAndCanBeRetried() throws Exception {
        var ready = upload(0, UUID.randomUUID(), 1);
        var failing = new DelegatingStorage(storage) {
            @Override public void deleteReserved(UUID id, String digest) {
                throw new ImageStorageException(ImageStorageException.Code.IO_FAILURE);
            }
        };
        assertThrows(ImageStorageException.class, () -> lifecycle(failing).cancel(OWNER, PRODUCT,
                ready.productVersion(), ready.id(), ready.version()));
        assertEquals("CANCELLED", value("state"));
        assertTrue(Files.exists(root.resolve(ready.id().toString()).resolve("content")));
        lifecycle.cancel(OWNER, PRODUCT, 0, ready.id(), 0);
        assertFalse(Files.exists(root.resolve(ready.id().toString()).resolve("content")));
    }

    @Test void staleVersionAndChangedStatusDoNotMaterializeAnImage() throws Exception {
        assertEquals(ProductPersistenceException.Code.STALE_VERSION,
                assertThrows(ProductPersistenceException.class, () -> upload(5, UUID.randomUUID(), 1)).getCode());
        UUID operation = UUID.randomUUID();
        metadata.reserve(OWNER, PRODUCT, 0, operation, ReservedImageStorageTest.image(1));
        jdbc.update("UPDATE products SET status='PENDING_REVIEW',version=version+1 WHERE id=?", PRODUCT);
        assertEquals(ProductRuleException.Code.INVALID_STATE,
                assertThrows(ProductRuleException.class, () -> upload(0, operation, 1)).getCode());
        try (var objects = Files.list(root)) { assertEquals(0, objects.count()); }
        assertEquals("PENDING_STORAGE", value("state"));
    }

    @Test void foreignAndMissingProductHaveSameScopedNotFoundBeforeStorage() throws Exception {
        var ready = upload(0, UUID.randomUUID(), 1);
        var foreign = assertThrows(ProductPersistenceException.class,
                () -> lifecycle.read("image-owner-b", PRODUCT, ready.id()));
        var missing = assertThrows(ProductPersistenceException.class,
                () -> lifecycle.read("image-owner-b", UUID.randomUUID(), ready.id()));
        assertEquals(ProductPersistenceException.Code.NOT_FOUND, foreign.getCode());
        assertEquals(foreign.getCode(), missing.getCode());
        assertArrayEquals(ReservedImageStorageTest.image(1).content(), lifecycle.read(OWNER, PRODUCT, ready.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unapproved", "closed", "wrong-owner", "denied", "unavailable", "no-scope"})
    void unavailableOrDeniedCurrentAuthorityNeverWritesAFile(String mode) throws Exception {
        policy.mode = mode;
        assertThrows(ProductRuleException.class, () -> upload(0, UUID.randomUUID(), 1));
        assertEquals(0, count());
        try (var objects = Files.list(root)) { assertEquals(0, objects.count()); }
    }

    @Test void missingReadyFileFailsClosedAndIdenticalRetryRepairsIt() throws Exception {
        UUID operation = UUID.randomUUID();
        var ready = upload(0, operation, 1);
        Files.delete(root.resolve(ready.id().toString()).resolve("content"));
        assertThrows(ImageStorageException.class, () -> lifecycle.read(OWNER, PRODUCT, ready.id()));
        assertEquals(ready, upload(0, operation, 1));
        assertArrayEquals(ReservedImageStorageTest.image(1).content(), lifecycle.read(OWNER, PRODUCT, ready.id()));
    }

    @Test void corruptedReadyBytesAndUnknownOrphanNeverBecomeUsableOrGetDeleted() throws Exception {
        var ready = upload(0, UUID.randomUUID(), 1);
        Files.write(root.resolve(ready.id().toString()).resolve("content"), new byte[]{1, 2, 3});
        assertThrows(ProductImageMetadataException.class, () -> lifecycle.read(OWNER, PRODUCT, ready.id()));
        Path orphan = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        Files.writeString(orphan.resolve("content"), "unowned");
        assertThrows(ImageStorageException.class, () -> lifecycle.cancel(OWNER, PRODUCT,
                ready.productVersion(), ready.id(), ready.version()));
        assertEquals("unowned", Files.readString(orphan.resolve("content")));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve(ready.id().toString()).resolve("content")));
    }

    @Test void ambientTransactionIsRejectedBeforeCreatingAReservation() throws Exception {
        var tx = new TransactionTemplate(transactions);
        assertThrows(ProductImageMetadataException.class, () -> tx.execute(status -> {
            try { return upload(0, UUID.randomUUID(), 1); }
            catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }));
        assertEquals(0, count());
        assertEquals(0, version());
    }

    @Test void invalidContentNeverReservesSlotAndNoRawPathIsAccepted() {
        assertThrows(ImageValidationException.class, () -> lifecycle.upload(OWNER, PRODUCT, 0, UUID.randomUUID(),
                new ImageInput("../../bad.png", "image/png", new ByteArrayInputStream(new byte[]{1, 2, 3}))));
        assertEquals(0, count());
        assertEquals(0, version());
    }


    @Test void requiredStageRollbackLeavesRecoverablePendingMetadata() throws Exception {
        UUID operation = UUID.randomUUID();
        var image = ReservedImageStorageTest.image(1);
        var pending = metadata.reserve(OWNER, PRODUCT, 0, operation, image);
        new TransactionTemplate(transactions).execute(status -> {
            metadata.completeStorage(OWNER, PRODUCT, pending.productVersion(), pending.id(), pending.version(), image, storage);
            status.setRollbackOnly();
            return null;
        });
        assertEquals("PENDING_STORAGE", value("state"));
        assertEquals(1, version());
        assertArrayEquals(image.content(), storage.read(pending.id().toString()));
        assertEquals(pending.id(), upload(0, operation, 1).id());
        assertEquals("READY", value("state"));
    }

    @Test void activeReservationCannotBeCleanedAndStaleImageOrProductCannotPublish() throws Exception {
        var image = ReservedImageStorageTest.image(1);
        var pending = metadata.reserve(OWNER, PRODUCT, 0, UUID.randomUUID(), image);
        assertEquals(ProductImageMetadataException.Code.OPERATION_CONFLICT,
                assertThrows(ProductImageMetadataException.class,
                        () -> metadata.cleanupCancelled(OWNER, PRODUCT, pending.id(), storage)).getCode());
        assertEquals(ProductImageMetadataException.Code.STALE_VERSION,
                assertThrows(ProductImageMetadataException.class,
                        () -> metadata.completeStorage(OWNER, PRODUCT, 1, pending.id(), 42, image, storage)).getCode());
        jdbc.update("UPDATE products SET version=version+1 WHERE id=?", PRODUCT);
        assertEquals(ProductPersistenceException.Code.STALE_VERSION,
                assertThrows(ProductPersistenceException.class,
                        () -> metadata.completeStorage(OWNER, PRODUCT, 1, pending.id(), 0, image, storage)).getCode());
        assertEquals("PENDING_STORAGE", value("state"));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"upload", "cancel", "cleanup"})
    void foreignStoreCannotMutateImagesOrProbeTheirExistence(String command) throws Exception {
        var ready = upload(0, UUID.randomUUID(), 1);
        for (UUID target : List.of(PRODUCT, UUID.randomUUID())) {
            var denied = assertThrows(ProductPersistenceException.class, () -> {
                switch (command) {
                    case "upload" -> lifecycle.upload("image-owner-b", target, 2, UUID.randomUUID(), input(2));
                    case "cancel" -> lifecycle.cancel("image-owner-b", target, 2, ready.id(), ready.version());
                    case "cleanup" -> metadata.cleanupCancelled("image-owner-b", target, ready.id(), storage);
                    default -> throw new IllegalArgumentException(command);
                }
            });
            assertEquals(ProductPersistenceException.Code.NOT_FOUND, denied.getCode());
        }
        assertEquals("READY", value("state"));
        assertArrayEquals(ReservedImageStorageTest.image(1).content(), lifecycle.read(OWNER, PRODUCT, ready.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sample.jpg", "sample.png", "sample.webp"})
    void realValidatedFormatsRoundTripThroughDatabaseAndFilesystem(String resource) throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/images/" + resource)) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        String type = resource.endsWith("jpg") ? "image/jpeg" : resource.endsWith("png") ? "image/png" : "image/webp";
        var ready = lifecycle.upload(OWNER, PRODUCT, 0, UUID.randomUUID(),
                new ImageInput(resource, type, new ByteArrayInputStream(bytes)));
        assertEquals(type, ready.mediaType());
        assertEquals("READY", ready.state());
        assertArrayEquals(bytes, lifecycle.read(OWNER, PRODUCT, ready.id()));
    }

    @Test void removingMetadataDoesNotAuthorizeOrphanFileDeletion() throws Exception {
        var ready = upload(0, UUID.randomUUID(), 1);
        jdbc.update("DELETE FROM product_image_metadata WHERE id=?", ready.id());
        assertThrows(ProductImageMetadataException.class,
                () -> metadata.cleanupCancelled(OWNER, PRODUCT, ready.id(), storage));
        assertArrayEquals(ReservedImageStorageTest.image(1).content(), storage.read(ready.id().toString()));
    }

    @Test void lifecycleHasNoAutomaticProductionRegistration() {
        assertNull(org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                ProductImageLifecycleService.class, org.springframework.stereotype.Component.class));
        assertNull(org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                ProductImageLifecycleService.class, org.springframework.web.bind.annotation.RestController.class));
    }

    private Object racedUpload(CyclicBarrier barrier, int color) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try { return upload(8, UUID.randomUUID(), color); }
        catch (RuntimeException failure) { return failure; }
    }
    private Reservation upload(long version, UUID operation, int color) throws Exception {
        return lifecycle.upload(OWNER, PRODUCT, version, operation, input(color));
    }
    private ImageInput input(int color) throws Exception {
        return new ImageInput("image.png", "image/png", new ByteArrayInputStream(ReservedImageStorageTest.image(color).content()));
    }
    private ProductImageLifecycleService lifecycle(ImageStorage objectStorage) {
        return new ProductImageLifecycleService(metadata, objectStorage, new ProductImageValidator());
    }
    private long count() { return jdbc.queryForObject("SELECT count(*) FROM product_image_metadata", Long.class); }
    private long version() { return jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, PRODUCT); }
    private Object value(String column) {
        return switch (column) {
            case "id" -> jdbc.queryForObject("SELECT id FROM product_image_metadata", UUID.class);
            case "state" -> jdbc.queryForObject("SELECT state FROM product_image_metadata", String.class);
            case "storage_key" -> jdbc.queryForObject("SELECT storage_key FROM product_image_metadata", String.class);
            case "ready_at" -> jdbc.queryForObject("SELECT ready_at FROM product_image_metadata", java.sql.Timestamp.class);
            default -> throw new IllegalArgumentException(column);
        };
    }
    private static class DelegatingStorage implements ImageStorage {
        private final ImageStorage delegate;
        DelegatingStorage(ImageStorage delegate) { this.delegate = delegate; }
        @Override public StoredImage store(ValidatedImage image) { return delegate.store(image); }
        @Override public byte[] read(String key) { return delegate.read(key); }
        @Override public StoredImage storeReserved(UUID id, ValidatedImage image) { return delegate.storeReserved(id, image); }
        @Override public void deleteReserved(UUID id, String digest) { delegate.deleteReserved(id, digest); }
    }
}
