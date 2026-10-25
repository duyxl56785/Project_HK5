package com.projecthk5.productservice.product.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/** Repository interaction/error unit tests; mocks do not prove SQL constraints or JPA version increments. */
class ProductPersistenceAdapterTest {
    private final UUID id = UUID.randomUUID(), storeId = UUID.randomUUID(), categoryId = UUID.randomUUID();
    private ProductRepository repository;
    private EntityManager entityManager;
    private ProductPersistenceAdapter adapter;

    @BeforeEach void setup() {
        repository = mock(ProductRepository.class);
        entityManager = mock(EntityManager.class);
        adapter = new ProductPersistenceAdapter(repository, entityManager);
    }

    @Test void createFlushesNewNullVersionEntityAndReturnsRepositoryResult() {
        ProductListing listing = listing("Sku", new BigDecimal("10.00000"));
        ProductEntity returned = storedFixture(listing, 0L);
        when(repository.saveAndFlush(any(ProductEntity.class))).thenReturn(returned);

        var result = adapter.create(listing);

        ArgumentCaptor<ProductEntity> inserted = ArgumentCaptor.forClass(ProductEntity.class);
        verify(repository).saveAndFlush(inserted.capture());
        assertNotSame(returned, inserted.getValue());
        assertNull(inserted.getValue().getVersion(), "Null-version input is Spring Data's new-entity insert candidate");
        assertEquals(listing, inserted.getValue().toDomain());
        assertSame(listing.unitPrice(), inserted.getValue().getUnitPrice());
        assertEquals(listing, result.listing());
        assertEquals(0L, result.version());
        verifyNoMoreInteractions(repository); // No exists/read check whose result could race with the insert.
    }

    @Test void findByIdReturnsAuthoritativeListingAndExistingVersion() {
        ProductListing listing = listing("Sku", new BigDecimal("12.00000"));
        when(repository.findById(id)).thenReturn(Optional.of(storedFixture(listing, 7L)));

        var result = adapter.findById(id).orElseThrow();

        assertEquals(listing, result.listing());
        assertSame(listing.unitPrice(), result.listing().unitPrice());
        assertEquals(7L, result.version());
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test void absentIdAndSkuRemainEmptyWithoutWriting() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.findByInventorySku("missing")).thenReturn(Optional.empty());

        assertTrue(adapter.findById(id).isEmpty());
        assertTrue(adapter.findBySku("missing").isEmpty());

        verify(repository).findById(id);
        verify(repository).findByInventorySku("missing");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sku-A", "sku-a", "SKU-A", "\u00e9-SKU", "e\u0301-SKU", "A B", "A\u00a0B"})
    void findBySkuKeepsExactCaseUnicodeAndInternalWhitespace(String sku) {
        ProductListing listing = listing(sku, BigDecimal.ONE);
        when(repository.findByInventorySku(sku)).thenReturn(Optional.of(storedFixture(listing, 3L)));

        var result = adapter.findBySku(sku).orElseThrow();

        assertEquals(sku, result.listing().sku());
        assertEquals(3L, result.version());
        verify(repository).findByInventorySku(sku);
        verifyNoMoreInteractions(repository);
    }

    @Test void successfulUpdateAppliesAndFlushesTheLoadedManagedEntity() {
        ProductEntity managed = storedFixture(listing("Sku", BigDecimal.ONE), 7L);
        ProductListing updated = new ProductListing(id, storeId, UUID.randomUUID(), "Sku", "Updated",
                new BigDecimal("999.00000"), "VND", ProductStatus.REJECTED, "Needs review");
        when(repository.findById(id)).thenReturn(Optional.of(managed));
        when(repository.saveAndFlush(managed)).thenReturn(managed);

        var result = adapter.update(updated, 7L);

        assertEquals(updated, managed.toDomain());
        assertEquals(updated, result.listing());
        assertSame(updated.unitPrice(), managed.getUnitPrice());
        // Fixture version stays 7: provider-managed increment is verified only by PostgreSQL tests.
        assertEquals(7L, result.version());
        var order = inOrder(repository, entityManager);
        order.verify(repository).findById(id);
        order.verify(entityManager).refresh(managed);
        order.verify(repository).saveAndFlush(managed);
        verifyNoMoreInteractions(repository);
        verifyNoMoreInteractions(entityManager);
    }

    @Test void staleExpectedVersionIsRejectedBeforeApplyingEvenAnImmutableIdentityChange() {
        ProductListing original = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(original, 7L);
        ProductListing stale = new ProductListing(id, UUID.randomUUID(), UUID.randomUUID(), "Changed-Sku", "Changed",
                BigDecimal.TEN, "VND", ProductStatus.REJECTED, "Changed reason");
        when(repository.findById(id)).thenReturn(Optional.of(managed));

        assertCode(ProductPersistenceException.Code.STALE_VERSION, () -> adapter.update(stale, 6L));

        assertEquals(original, managed.toDomain());
        assertEquals(7L, managed.getVersion());
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test void unknownUpdateIdIsNotAnUpsert() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertCode(ProductPersistenceException.Code.NOT_FOUND, () -> adapter.update(listing("Sku", BigDecimal.ONE), 0L));

        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"store", "sku"})
    void matchedVersionStillRejectsImmutableIdentityAndDoesNotFlush(String identity) {
        ProductListing original = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(original, 7L);
        ProductListing forbidden = new ProductListing(id, identity.equals("store") ? UUID.randomUUID() : storeId,
                UUID.randomUUID(), identity.equals("sku") ? "sku" : "Sku", "Changed", BigDecimal.TEN,
                "VND", ProductStatus.REJECTED, "Changed reason");
        when(repository.findById(id)).thenReturn(Optional.of(managed));

        assertCode(ProductPersistenceException.Code.IMMUTABLE_IDENTITY, () -> adapter.update(forbidden, 7L));

        assertEquals(original, managed.toDomain());
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    static Stream<Arguments> databaseConstraintFailures() {
        return Stream.of(
                Arguments.of("23505", "products_inventory_sku_uk", ProductPersistenceException.Code.DUPLICATE_SKU),
                Arguments.of("23505", "products_pkey", ProductPersistenceException.Code.CONSTRAINT_VIOLATION),
                Arguments.of("23503", "products_category_fk", ProductPersistenceException.Code.CATEGORY_NOT_FOUND),
                Arguments.of("23503", "unrecognized_fk", ProductPersistenceException.Code.CONSTRAINT_VIOLATION),
                Arguments.of("23514", "products_sku_immutable", ProductPersistenceException.Code.IMMUTABLE_IDENTITY),
                Arguments.of("23514", "products_price_range", ProductPersistenceException.Code.CONSTRAINT_VIOLATION),
                Arguments.of("23502", null, ProductPersistenceException.Code.CONSTRAINT_VIOLATION));
    }

    @ParameterizedTest
    @MethodSource("databaseConstraintFailures")
    void createMapsKnownDatabaseConstraintsAndFailsClosedForOthers(String state, String constraint,
            ProductPersistenceException.Code expectedCode) {
        when(repository.saveAndFlush(any(ProductEntity.class))).thenThrow(constraintFailure(state, constraint));

        assertCode(expectedCode, () -> adapter.create(listing("Sku", BigDecimal.ONE)));
    }

    @ParameterizedTest
    @MethodSource("databaseConstraintFailures")
    void updateMapsFlushConstraintFailures(String state, String constraint, ProductPersistenceException.Code expectedCode) {
        ProductListing listing = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(listing, 7L);
        when(repository.findById(id)).thenReturn(Optional.of(managed));
        when(repository.saveAndFlush(managed)).thenThrow(constraintFailure(state, constraint));

        assertCode(expectedCode, () -> adapter.update(listing, 7L));
    }

    @Test void genericIntegrityFailureRemainsConstraintViolation() {
        when(repository.saveAndFlush(any(ProductEntity.class)))
                .thenThrow(new DataIntegrityViolationException("private-db-detail"));

        assertCode(ProductPersistenceException.Code.CONSTRAINT_VIOLATION,
                () -> adapter.create(listing("Sku", BigDecimal.ONE)));
    }

    @Test void concurrentOptimisticFlushFailureBecomesStaleVersion() {
        ProductListing listing = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(listing, 7L);
        when(repository.findById(id)).thenReturn(Optional.of(managed));
        when(repository.saveAndFlush(managed)).thenThrow(new ObjectOptimisticLockingFailureException(ProductEntity.class, id));

        assertCode(ProductPersistenceException.Code.STALE_VERSION, () -> adapter.update(listing, 7L));
    }

    @Test void refreshOfDeletedEntityReturnsNotFoundWithoutMutationOrFlush() {
        assertRefreshFailure(new EntityNotFoundException("private-db-detail"),
                ProductPersistenceException.Code.NOT_FOUND);
    }

    @Test void optimisticFailureDuringRefreshReturnsStaleVersionWithoutMutationOrFlush() {
        assertRefreshFailure(new OptimisticLockException("private-db-detail"),
                ProductPersistenceException.Code.STALE_VERSION);
    }

    @Test void genericPersistenceFailureDuringRefreshReturnsUnavailableWithoutMutationOrFlush() {
        assertRefreshFailure(new PersistenceException("private-db-detail"),
                ProductPersistenceException.Code.UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "sku", "create", "update-load", "update-flush"})
    void dataAccessFailuresAreUnavailableInsteadOfFalseAbsenceOrSuccess(String operation) {
        ProductListing listing = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(listing, 7L);
        var failure = new DataAccessResourceFailureException("private-db-detail");
        Supplier<?> call;
        switch (operation) {
            case "id" -> {
                when(repository.findById(id)).thenThrow(failure);
                call = () -> adapter.findById(id);
            }
            case "sku" -> {
                when(repository.findByInventorySku("Sku")).thenThrow(failure);
                call = () -> adapter.findBySku("Sku");
            }
            case "create" -> {
                when(repository.saveAndFlush(any(ProductEntity.class))).thenThrow(failure);
                call = () -> adapter.create(listing);
            }
            case "update-load" -> {
                when(repository.findById(id)).thenThrow(failure);
                call = () -> adapter.update(listing, 7L);
            }
            default -> {
                when(repository.findById(id)).thenReturn(Optional.of(managed));
                when(repository.saveAndFlush(managed)).thenThrow(failure);
                call = () -> adapter.update(listing, 7L);
            }
        }

        assertCode(ProductPersistenceException.Code.UNAVAILABLE, call);
    }

    @Test void queryTimeoutIsUnavailableInsteadOfNotFound() {
        when(repository.findById(id)).thenThrow(new QueryTimeoutException("private-db-detail"));

        assertCode(ProductPersistenceException.Code.UNAVAILABLE, () -> adapter.findById(id));
    }

    private DataIntegrityViolationException constraintFailure(String state, String constraint) {
        return new DataIntegrityViolationException("private-db-detail", new ConstraintViolationException(
                "private-db-detail", new SQLException("private-db-detail", state), constraint));
    }

    private void assertRefreshFailure(PersistenceException failure, ProductPersistenceException.Code expectedCode) {
        ProductListing original = listing("Sku", BigDecimal.ONE);
        ProductEntity managed = storedFixture(original, 7L);
        ProductListing updated = new ProductListing(id, storeId, UUID.randomUUID(), "Sku", "Changed",
                BigDecimal.TEN, "VND", ProductStatus.REJECTED, "Changed reason");
        when(repository.findById(id)).thenReturn(Optional.of(managed));
        doThrow(failure).when(entityManager).refresh(managed);

        assertCode(expectedCode, () -> adapter.update(updated, 7L));

        assertEquals(original, managed.toDomain());
        assertSame(original.unitPrice(), managed.getUnitPrice());
        assertEquals(7L, managed.getVersion());
        var order = inOrder(repository, entityManager);
        order.verify(repository).findById(id);
        order.verify(entityManager).refresh(managed);
        verifyNoMoreInteractions(repository, entityManager);
    }

    private void assertCode(ProductPersistenceException.Code expectedCode, Supplier<?> action) {
        ProductPersistenceException error = assertThrows(ProductPersistenceException.class, action::get);
        assertEquals(expectedCode, error.getCode());
        assertFalse(error.getMessage().contains("private-db-detail"));
    }

    private ProductEntity storedFixture(ProductListing listing, long existingVersion) {
        ProductEntity entity = new ProductEntity(listing);
        // Repository fixture only: this never simulates JPA generating/incrementing an @Version field.
        ReflectionTestUtils.setField(entity, "version", existingVersion);
        return entity;
    }

    private ProductListing listing(String sku, BigDecimal price) {
        return new ProductListing(id, storeId, categoryId, sku, "Product", price, "VND", ProductStatus.DRAFT, null);
    }
}
