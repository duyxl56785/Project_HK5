package com.projecthk5.productservice.image;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.image.persistence.ProductImageMetadataEntity;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.persistence.ProductEntity;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production Flyway/JPA/PostgreSQL; policy fixtures never represent live JWT or uploaded files. */
@SpringBootTest
@Import(ProductImageMetadataServiceIntegrationTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgresql")
class ProductImageMetadataServiceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("77000000-0000-4000-8000-000000000001");
    private static final UUID STORE_A = UUID.fromString("77000000-0000-4000-8000-000000000002");
    private static final UUID STORE_B = UUID.fromString("77000000-0000-4000-8000-000000000003");
    private static final UUID PRODUCT_A = UUID.fromString("77000000-0000-4000-8000-000000000004");
    private static final UUID PRODUCT_B = UUID.fromString("77000000-0000-4000-8000-000000000005");
    private static final String OWNER_A = "image-owner-a", OWNER_B = "image-owner-b";

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @Autowired private ProductImageMetadataService images;
    @Autowired private ProductApplicationService products;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagers;
    @Autowired private ObjectMapper json;
    @Autowired private FixturePolicy policy;

    @BeforeEach void resetOnlyDisposablePostgresFixture() {
        jdbc.update("DELETE FROM product_image_metadata");
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "Image category");
        seed(PRODUCT_A, STORE_A, "Image-A");
        seed(PRODUCT_B, STORE_B, "Image-B");
        policy.reset();
    }

    @Test void reservationIsDurableCanonicalMetadataNotAnUploadedImage() throws Exception {
        UUID operation = UUID.randomUUID();
        var image = image(1);
        var receipt = images.reserve(OWNER_A, PRODUCT_A, 0, operation, image);
        assertNotNull(receipt.id());
        assertEquals(PRODUCT_A, receipt.productId());
        assertEquals(operation, receipt.operationId());
        assertEquals("PENDING_STORAGE", receipt.state());
        assertEquals("image/png", receipt.mediaType());
        assertEquals(image.size(), receipt.sizeBytes());
        assertEquals(2, receipt.width());
        assertEquals(2, receipt.height());
        assertEquals(1, receipt.displayOrder());
        assertNotNull(receipt.createdAt());
        assertNull(receipt.cancelledAt());
        assertEquals(0, receipt.version());
        assertEquals(1, receipt.productVersion());
        try (var fresh = entityManagers.createEntityManager()) {
            var stored = fresh.find(ProductImageMetadataEntity.class, receipt.id());
            assertEquals(receipt.productId(), stored.getProductId());
            assertEquals(receipt.operationId(), stored.getOperationId());
            assertEquals("PENDING_STORAGE", stored.getState());
            assertNull(stored.getStorageKey());
            assertEquals(1, fresh.find(ProductEntity.class, PRODUCT_A).getVersion());
        }
        String encoded = json.writeValueAsString(receipt);
        for (String secret : List.of("storageKey", "contentSha256", "ownerReference", "reviewReason", "content")) {
            assertFalse(encoded.contains('"' + secret + '"'), encoded);
        }
    }

    @Test void fiveSlotsAreOrderedAndTheSixthImageDoesNotMutateProductOrMetadata() throws Exception {
        for (int i = 0; i < 5; i++) {
            var receipt = images.reserve(OWNER_A, PRODUCT_A, i, UUID.randomUUID(), image(i));
            assertEquals(i + 1, receipt.displayOrder());
            assertEquals(i + 1, receipt.productVersion());
        }
        imageFailure(ProductImageMetadataException.Code.LIMIT_EXCEEDED,
                () -> images.reserve(OWNER_A, PRODUCT_A, 5, UUID.randomUUID(), imageUnchecked(20)));
        assertEquals(List.of(1, 2, 3, 4, 5), images.list(OWNER_A, PRODUCT_A).stream()
                .map(ProductImageMetadataService.Reservation::displayOrder).toList());
        assertEquals(5, count(PRODUCT_A));
        assertEquals(5, productVersion(PRODUCT_A));
    }

    @Test void concurrentReservationsAtFourCannotExceedFiveOrOverwriteEachOther() throws Exception {
        for (int i = 0; i < 4; i++) images.reserve(OWNER_A, PRODUCT_A, i, UUID.randomUUID(), image(i));
        policy.barrier = new CyclicBarrier(2);
        var outcomes = concurrent(
                () -> images.reserve(OWNER_A, PRODUCT_A, 4, UUID.randomUUID(), imageUnchecked(30)),
                () -> images.reserve(OWNER_A, PRODUCT_A, 4, UUID.randomUUID(), imageUnchecked(31)));
        assertEquals(1, outcomes.stream().filter(ProductImageMetadataService.Reservation.class::isInstance).count());
        var failure = outcomes.stream().filter(Throwable.class::isInstance).findFirst().orElseThrow();
        assertEquals(ProductPersistenceException.Code.STALE_VERSION,
                assertInstanceOf(ProductPersistenceException.class, failure).getCode());
        assertEquals(5, count(PRODUCT_A));
        assertEquals(5, productVersion(PRODUCT_A));
        assertEquals(5, jdbc.queryForObject("SELECT count(DISTINCT display_order) FROM product_image_metadata WHERE product_id=?", Integer.class, PRODUCT_A));
    }

    @Test void concurrentRetryOfTheSameOperationCreatesExactlyOneReservation() throws Exception {
        UUID operation = UUID.randomUUID();
        var validated = image(10);
        policy.barrier = new CyclicBarrier(2);
        var outcomes = concurrent(
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, operation, validated),
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, operation, validated));
        var first = assertInstanceOf(ProductImageMetadataService.Reservation.class, outcomes.get(0));
        var second = assertInstanceOf(ProductImageMetadataService.Reservation.class, outcomes.get(1));
        assertEquals(first.id(), second.id());
        assertEquals(1, count(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void exactRetryWithAnOldProductVersionReturnsTheSameReservation() throws Exception {
        UUID operation = UUID.randomUUID();
        var validated = image(10);
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, operation, validated);
        var second = images.reserve(OWNER_A, PRODUCT_A, 0, operation, validated);
        assertEquals(first, second);
        assertEquals(1, count(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void operationCannotBeReusedForDifferentContent() throws Exception {
        UUID operation = UUID.randomUUID();
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, operation, image(10));
        imageFailure(ProductImageMetadataException.Code.OPERATION_CONFLICT,
                () -> images.reserve(OWNER_A, PRODUCT_A, 1, operation, imageUnchecked(11)));
        assertEquals(List.of(first), images.list(OWNER_A, PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void duplicateContentWithDifferentOperationsIsRejectedWithoutUsingAnotherSlot() throws Exception {
        var validated = image(10);
        images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), validated);
        imageFailure(ProductImageMetadataException.Code.DUPLICATE_IMAGE,
                () -> images.reserve(OWNER_A, PRODUCT_A, 1, UUID.randomUUID(), validated));
        assertEquals(1, count(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void cancellingPreservesIdempotencyTombstoneAndAllowsReuploadInTheReleasedSlot() throws Exception {
        UUID oldOperation = UUID.randomUUID();
        var validated = image(10);
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, oldOperation, validated);
        var cancelled = images.cancel(OWNER_A, PRODUCT_A, 1, first.id(), 0);
        assertEquals(first.id(), cancelled.id());
        assertEquals("CANCELLED", cancelled.state());
        assertNull(cancelled.displayOrder());
        assertNotNull(cancelled.cancelledAt());
        assertEquals(1, cancelled.version());
        assertEquals(2, cancelled.productVersion());
        assertTrue(images.list(OWNER_A, PRODUCT_A).isEmpty());
        assertEquals(cancelled, images.cancel(OWNER_A, PRODUCT_A, 1, first.id(), 0));
        assertEquals(cancelled, images.reserve(OWNER_A, PRODUCT_A, 0, oldOperation, validated));
        var replacement = images.reserve(OWNER_A, PRODUCT_A, 2, UUID.randomUUID(), validated);
        assertNotEquals(first.id(), replacement.id());
        assertEquals(1, replacement.displayOrder());
        assertEquals(2, count(PRODUCT_A));
        assertEquals(1, pendingCount(PRODUCT_A));
        assertEquals(3, productVersion(PRODUCT_A));
    }

    @Test void cancellingAnImageInAnotherProductDoesNotDiscloseOrChangeIt() throws Exception {
        var imageA = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        imageFailure(ProductImageMetadataException.Code.NOT_FOUND,
                () -> images.cancel(OWNER_B, PRODUCT_B, 0, imageA.id(), 0));
        imageFailure(ProductImageMetadataException.Code.NOT_FOUND,
                () -> images.cancel(OWNER_B, PRODUCT_B, 0, UUID.randomUUID(), 0));
        assertEquals(1, pendingCount(PRODUCT_A));
        assertEquals(0, count(PRODUCT_B));
        assertEquals(0, productVersion(PRODUCT_B));
    }

    @Test void operationIdentityIsScopedToProductAndCannotMoveAnExistingImage() throws Exception {
        UUID operation = UUID.randomUUID();
        var validated = image(1);
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, operation, validated);
        var second = images.reserve(OWNER_B, PRODUCT_B, 0, operation, validated);
        assertNotEquals(first.id(), second.id());
        assertEquals(PRODUCT_A, first.productId());
        assertEquals(PRODUCT_B, second.productId());
        assertEquals(List.of(first), images.list(OWNER_A, PRODUCT_A));
        assertEquals(List.of(second), images.list(OWNER_B, PRODUCT_B));
    }

    @Test void requiredOuterRollbackRevertsMetadataAndProductVersionTogether() throws Exception {
        var validated = image(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), validated);
            assertEquals(1, count(PRODUCT_A));
            assertEquals(1, productVersion(PRODUCT_A));
            tx.setRollbackOnly();
        });
        assertEquals(0, count(PRODUCT_A));
        assertEquals(0, productVersion(PRODUCT_A));
    }

    @Test void failedReservationRollsBackEarlierWritesInTheRequiredCallerTransaction() throws Exception {
        var validated = image(1);
        images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), validated);
        imageFailure(ProductImageMetadataException.Code.DUPLICATE_IMAGE, () ->
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    jdbc.update("UPDATE products SET name='Must roll back' WHERE id=?", PRODUCT_B);
                    images.reserve(OWNER_A, PRODUCT_A, 1, UUID.randomUUID(), validated);
                }));
        assertEquals("Image-B", jdbc.queryForObject("SELECT name FROM products WHERE id=?", String.class, PRODUCT_B));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void cancellingRollsBackAtomicallyWithOuterTransaction() throws Exception {
        var receipt = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            images.cancel(OWNER_A, PRODUCT_A, 1, receipt.id(), 0);
            tx.setRollbackOnly();
        });
        assertEquals(List.of(receipt), images.list(OWNER_A, PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void metadataMutationInvalidatesAStaleProductLifecycleSnapshot() throws Exception {
        images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        productFailure(ProductPersistenceException.Code.STALE_VERSION,
                () -> products.submit(OWNER_A, PRODUCT_A, 0));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM products WHERE id=?", String.class, PRODUCT_A));
        assertEquals(1, pendingCount(PRODUCT_A));
    }

    @Test void staleProductVersionCannotReserveOrCancel() throws Exception {
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        productFailure(ProductPersistenceException.Code.STALE_VERSION,
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), imageUnchecked(2)));
        productFailure(ProductPersistenceException.Code.STALE_VERSION,
                () -> images.cancel(OWNER_A, PRODUCT_A, 0, first.id(), 0));
        assertEquals(1, pendingCount(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void staleImageVersionCannotCancelEvenWithCurrentProductVersion() throws Exception {
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        imageFailure(ProductImageMetadataException.Code.STALE_VERSION,
                () -> images.cancel(OWNER_A, PRODUCT_A, 1, first.id(), 1));
        assertEquals(1, pendingCount(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @ParameterizedTest @ValueSource(strings = {"reserve", "cancel", "list"})
    void foreignAndMissingProductHaveTheSameNotFoundFailure(String command) throws Exception {
        var first = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1));
        for (UUID target : List.of(PRODUCT_A, UUID.randomUUID())) {
            var failure = assertThrows(ProductPersistenceException.class, () -> invoke(command, OWNER_B, target, first.id()));
            assertEquals(ProductPersistenceException.Code.NOT_FOUND, failure.getCode());
            assertEquals("Product was not found", failure.getMessage());
        }
        assertEquals(1, pendingCount(PRODUCT_A));
    }

    @ParameterizedTest @ValueSource(strings = {"unapproved", "closed", "wrong-owner", "denied"})
    void untrustedOrIneligibleManagementFactsDenyBeforeAnyImageMutation(String mode) {
        policy.mode = mode;
        rule(ProductRuleException.Code.ACCESS_DENIED,
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), imageUnchecked(1)));
        assertEquals(0, count(PRODUCT_A));
        assertEquals(0, productVersion(PRODUCT_A));
    }

    @ParameterizedTest @ValueSource(strings = {"unavailable", "missing", "wrong-store", "no-scope"})
    void unavailableOrInconsistentAuthorityFailsClosedForMissingAndExistingIds(String mode) {
        policy.mode = mode;
        for (UUID id : List.of(PRODUCT_A, UUID.randomUUID())) {
            rule(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, () -> images.list(OWNER_A, id));
        }
        assertEquals(0, count(PRODUCT_A));
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "PENDING_REVIEW", "SUSPENDED"})
    void imageWritesCannotBypassModerationState(String status) {
        jdbc.update("UPDATE products SET status=?,review_reason=? WHERE id=?", status,
                status.equals("SUSPENDED") ? "Policy" : null, PRODUCT_A);
        rule(ProductRuleException.Code.INVALID_STATE,
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), imageUnchecked(1)));
        assertEquals(0, count(PRODUCT_A));
        assertEquals(0, productVersion(PRODUCT_A));
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "REJECTED", "INACTIVE"})
    void editableProductStatesCanReserve(String status) throws Exception {
        jdbc.update("UPDATE products SET status=?,review_reason=? WHERE id=?", status,
                status.equals("REJECTED") ? "Fix image" : null, PRODUCT_A);
        assertEquals("PENDING_STORAGE", images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), image(1)).state());
        assertEquals(status, jdbc.queryForObject("SELECT status FROM products WHERE id=?", String.class, PRODUCT_A));
    }

    @Test void cachedProductIsRefreshedBeforeVersionAndLifecycleChecks() throws Exception {
        var validated = image(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var cached = entityManager.find(ProductEntity.class, PRODUCT_A);
            assertEquals(0, cached.getVersion());
            var independent = new TransactionTemplate(transactions);
            independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(other -> jdbc.update("UPDATE products SET version=1,status='PENDING_REVIEW' WHERE id=?", PRODUCT_A));
            rule(ProductRuleException.Code.INVALID_STATE,
                    () -> images.reserve(OWNER_A, PRODUCT_A, 1, UUID.randomUUID(), validated));
            tx.setRollbackOnly();
        });
        assertEquals(0, count(PRODUCT_A));
        assertEquals(1, productVersion(PRODUCT_A));
    }

    @Test void cachedCancelledMetadataCannotReappearInListOrConsumeAReleasedSlot() throws Exception {
        var validated = image(1);
        var original = images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), validated);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            // Keep the pending entity in this caller's JPA first-level cache.
            assertEquals(List.of(original), images.list(OWNER_A, PRODUCT_A));
            assertEquals("PENDING_STORAGE", entityManager.find(ProductImageMetadataEntity.class, original.id()).getState());
            var independent = new TransactionTemplate(transactions);
            independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(other ->
                    images.cancel(OWNER_A, PRODUCT_A, 1, original.id(), 0));
            assertTrue(images.list(OWNER_A, PRODUCT_A).isEmpty(), "Cancelled metadata must be filtered using current DB state");
            var replacement = images.reserve(OWNER_A, PRODUCT_A, 2, UUID.randomUUID(), validated);
            assertEquals(1, replacement.displayOrder(), "An old cached pending row must not occupy its released slot");
            assertEquals(3, replacement.productVersion());
        });
        assertEquals(2, count(PRODUCT_A));
        assertEquals(1, pendingCount(PRODUCT_A));
        assertEquals(3, productVersion(PRODUCT_A));
    }

    @Test void publicRecordCannotExposePrivateKeysAndStorageCompletionRequiresValidatedBytes() throws Exception {
        Set<String> fields = Arrays.stream(ProductImageMetadataService.Reservation.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertTrue(fields.containsAll(Set.of("id", "productId", "state", "version", "productVersion")));
        assertFalse(fields.contains("storageKey"));
        assertFalse(fields.contains("contentSha256"));
        assertFalse(fields.contains("ownerReference"));
        assertFalse(Arrays.stream(ProductImageMetadataService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .anyMatch(method -> Set.of("upload", "attachStorageKey").contains(method.getName())));
        assertNotNull(ProductImageMetadataService.class.getMethod("completeStorage", String.class, UUID.class,
                long.class, UUID.class, long.class, ProductImageValidator.ValidatedImage.class, ImageStorage.class));
    }

    @Test void invalidCommandsCannotCreateMetadataOrAdvanceProductVersion() throws Exception {
        var validated = image(1);
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, null, validated));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.reserve(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), null));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.reserve(OWNER_A, PRODUCT_A, -1, UUID.randomUUID(), validated));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.reserve(OWNER_A, null, 0, UUID.randomUUID(), validated));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.cancel(OWNER_A, PRODUCT_A, 0, null, 0));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.cancel(OWNER_A, PRODUCT_A, 0, UUID.randomUUID(), -1));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.cancel(OWNER_A, PRODUCT_A, -1, UUID.randomUUID(), 0));
        imageFailure(ProductImageMetadataException.Code.INVALID_INPUT,
                () -> images.list(OWNER_A, null));
        for (String actor : new String[] { null, "", "  ", "a".repeat(129) }) {
            rule(ProductRuleException.Code.INVALID_INPUT,
                    () -> images.reserve(actor, PRODUCT_A, 0, UUID.randomUUID(), validated));
        }
        assertEquals(0, count(PRODUCT_A));
        assertEquals(0, productVersion(PRODUCT_A));
    }

    private void invoke(String command, String actor, UUID product, UUID imageId) {
        switch (command) {
            case "reserve" -> images.reserve(actor, product, 0, UUID.randomUUID(), imageUnchecked(2));
            case "cancel" -> images.cancel(actor, product, 0, imageId, 0);
            case "list" -> images.list(actor, product);
            default -> throw new IllegalArgumentException(command);
        }
    }

    private List<Object> concurrent(java.util.concurrent.Callable<?> first, java.util.concurrent.Callable<?> second) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(workers.submit(first), workers.submit(second));
            var outcomes = new ArrayList<Object>();
            for (var future : futures) {
                try { outcomes.add(future.get(20, TimeUnit.SECONDS)); }
                catch (ExecutionException failure) { outcomes.add(failure.getCause()); }
            }
            return outcomes;
        } finally {
            policy.barrier = null;
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void seed(UUID id, UUID store, String sku) {
        jdbc.update("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                + " VALUES (?,?,?,?,?,1,'VND','DRAFT',0)", id, store, CATEGORY, sku, sku);
    }

    private long count(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM product_image_metadata WHERE product_id=?", Long.class, id);
    }
    private long pendingCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM product_image_metadata WHERE product_id=? AND state='PENDING_STORAGE'", Long.class, id);
    }
    private long productVersion(UUID id) {
        return jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, id);
    }
    private static ProductImageValidator.ValidatedImage image(int color) throws Exception {
        var raster = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        raster.setRGB(0, 0, color);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(raster, "png", output));
        return new ProductImageValidator().validate(new ProductImageValidator.ImageInput("image.png", "image/png",
                new ByteArrayInputStream(output.toByteArray())));
    }
    private static ProductImageValidator.ValidatedImage imageUnchecked(int color) {
        try { return image(color); }
        catch (Exception failure) { throw new IllegalStateException("PNG fixture failed", failure); }
    }
    private static void imageFailure(ProductImageMetadataException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(ProductImageMetadataException.class, operation::run).getCode());
    }
    private static void productFailure(ProductPersistenceException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(ProductPersistenceException.class, operation::run).getCode());
    }
    private static void rule(ProductRuleException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(ProductRuleException.class, operation::run).getCode());
    }

    @TestConfiguration static class AuthorityFixture {
        @Bean FixturePolicy imagePolicy() { return new FixturePolicy(); }
    }
    static final class FixturePolicy implements ProductAccessPolicy {
        volatile String mode;
        volatile CyclicBarrier barrier;
        void reset() { mode = "normal"; barrier = null; }
        @Override public UUID currentManagementStore(String actor) {
            if (mode.equals("no-scope")) return null;
            if (OWNER_A.equals(actor)) return STORE_A;
            if (OWNER_B.equals(actor)) return STORE_B;
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture principal denied");
        }
        @Override public StoreAccess requireSellerManagement(String actor, UUID store) {
            if (mode.equals("denied")) throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture role denied");
            var gate = barrier;
            if (gate != null) try { gate.await(10, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new IllegalStateException("Fixture race barrier failed", failure); }
            return readStore(store);
        }
        @Override public StoreAccess requireModeration(String actor, UUID store) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "No image moderation in this fixture");
        }
        @Override public StoreAccess readStore(UUID store) {
            if (mode.equals("unavailable")) throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Fixture authority unavailable");
            if (mode.equals("missing")) return null;
            String owner = STORE_A.equals(store) ? OWNER_A : OWNER_B;
            if (mode.equals("wrong-owner")) owner = "another-owner";
            return new StoreAccess(mode.equals("wrong-store") ? UUID.randomUUID() : store, UUID.randomUUID(), owner,
                    !mode.equals("unapproved"), !mode.equals("closed"), false);
        }
    }
}
