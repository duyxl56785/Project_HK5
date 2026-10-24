package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.ResolvedProduct;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.LookupResult;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.LookupStatus;
import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogBatchValidatorTest {
    private final UUID categoryId = UUID.randomUUID();

    private ResolvedProduct facts(UUID id, UUID storeId, UUID sellerId, String sku, String price) {
        return new ResolvedProduct(new ProductListing(id, storeId, categoryId, sku, "Product",
                new BigDecimal(price), "VND", ProductStatus.ACTIVE, null),
                new StoreAccess(storeId, sellerId, "private-owner-reference", true, true, false), "VND", true);
    }

    private ExpectedItem expected(ResolvedProduct facts) {
        return new ExpectedItem(facts.product().id(), facts.store().storeId(), facts.store().sellerId(),
                facts.product().unitPrice(), facts.currency());
    }

    private LookupResult found(ResolvedProduct facts) {
        return new LookupResult(facts.product().id(), LookupStatus.FOUND, facts);
    }

    @Test void conflictingProductAndResolvedCurrencyFailsTheWholeBatchClosed() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var conflicting = new ResolvedProduct(row.product(), row.store(), "USD", true);
        CatalogBatchException error = assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> List.of(found(conflicting)))
                        .validate(List.of(expected(conflicting))));
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    @Test void resolvesTwoStoresOnceAndPreservesRequestOrderAndAuthoritativeSku() throws Exception {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sku-A", "10.0000000");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sku-B", "20");
        CatalogSnapshotSource source = mock(CatalogSnapshotSource.class);
        when(source.load(Set.of(first.product().id(), second.product().id()))).thenReturn(List.of(found(second), found(first)));
        Result result = new CatalogBatchValidator(source).validate(List.of(expected(first), expected(second)));
        assertTrue(result.valid());
        assertEquals(List.of(first.product().id(), second.product().id()), result.items().stream().map(ItemResult::productId).toList());
        assertEquals("Sku-A", result.items().get(0).snapshot().inventorySku());
        assertEquals(new BigDecimal("10.0000000"), result.items().get(0).snapshot().unitPrice());
        verify(source, times(1)).load(Set.of(first.product().id(), second.product().id()));
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
        String serialized = new ObjectMapper().writeValueAsString(result);
        assertFalse(serialized.contains("private-owner-reference"));
        assertFalse(serialized.contains("reviewReason"));
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void privateOrInactiveProductsNeverReturnTheirSnapshot(ProductStatus status) {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        String reason = status == ProductStatus.REJECTED || status == ProductStatus.SUSPENDED ? "private reason" : null;
        ProductListing product = new ProductListing(row.product().id(), row.product().storeId(), categoryId,
                "SKU", "Private", BigDecimal.TEN, "VND", status, reason);
        var result = new CatalogBatchValidator(ids -> List.of(found(new ResolvedProduct(product, row.store(), row.currency(), true))))
                .validate(List.of(expected(row)));
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, result.items().get(0).code());
        assertNull(result.items().get(0).snapshot());
        assertFalse(result.valid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"seller", "store", "category"})
    void currentStatusAndCategoryAreChecked(String invalid) {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var store = new StoreAccess(row.store().storeId(), row.store().sellerId(), "owner",
                !invalid.equals("seller"), !invalid.equals("store"), false);
        var result = new CatalogBatchValidator(ids -> List.of(found(new ResolvedProduct(row.product(), store,
                row.currency(), !invalid.equals("category"))))).validate(List.of(expected(row)));
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, result.items().get(0).code());
        assertNull(result.items().get(0).snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"store", "seller", "price", "currency"})
    void clientExpectationsCannotReplaceAuthoritativeFacts(String mismatch) {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10.00");
        ExpectedItem item = new ExpectedItem(row.product().id(), mismatch.equals("store") ? UUID.randomUUID() : row.store().storeId(),
                mismatch.equals("seller") ? UUID.randomUUID() : row.store().sellerId(),
                mismatch.equals("price") ? BigDecimal.ONE : row.product().unitPrice(),
                mismatch.equals("currency") ? "OTHER-TEST-CURRENCY" : row.currency());
        Result result = new CatalogBatchValidator(ids -> List.of(found(row))).validate(List.of(item));
        assertEquals(switch (mismatch) {
            case "store", "seller" -> ItemCode.OWNERSHIP_MISMATCH;
            case "price" -> ItemCode.PRICE_CHANGED;
            default -> ItemCode.CURRENCY_MISMATCH;
        }, result.items().get(0).code());
        assertNull(result.items().get(0).snapshot());
    }

    @Test void numericComparisonDoesNotRoundOrTreatDifferentScaleAsDifferentPrice() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10.0000");
        var item = new ExpectedItem(row.product().id(), row.store().storeId(), row.store().sellerId(),
                new BigDecimal("10.0"), row.currency());
        assertTrue(new CatalogBatchValidator(ids -> List.of(found(row))).validate(List.of(item)).valid());
    }

    @Test void missingProductIsExplicitAndPartialResultIsNotAllValid() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var missing = new ExpectedItem(UUID.randomUUID(), row.store().storeId(), row.store().sellerId(), BigDecimal.ONE, row.currency());
        Result result = new CatalogBatchValidator(ids -> List.of(found(row),
                new LookupResult(missing.productId(), LookupStatus.ABSENT, null))).validate(List.of(expected(row), missing));
        assertFalse(result.valid());
        assertEquals(ItemCode.PRODUCT_NOT_FOUND, result.items().get(1).code());
        assertNull(result.items().get(1).snapshot());
        assertNull(result.items().get(0).snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "partial"})
    void unconfirmedMissingResponseNeverBecomesNotFound(String response) {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-B", "20");
        CatalogSnapshotSource source = ids -> response.equals("empty") ? List.of() : List.of(found(first));
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(source).validate(List.of(expected(first), expected(second)))).getCode());
    }

    @Test void oneChangedPriceWithholdsEverySnapshot() {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-B", "20");
        var changed = new ExpectedItem(second.product().id(), second.store().storeId(), second.store().sellerId(),
                BigDecimal.ONE, second.currency());
        Result result = new CatalogBatchValidator(ids -> List.of(found(first), found(second)))
                .validate(List.of(expected(first), changed));
        assertFalse(result.valid());
        assertEquals(ItemCode.PRICE_CHANGED, result.items().get(1).code());
        assertTrue(result.items().stream().allMatch(item -> item.snapshot() == null));
    }

    @Test void invalidBatchesAreRejectedBeforeLoadingData() {
        CatalogSnapshotSource source = mock(CatalogSnapshotSource.class);
        var validator = new CatalogBatchValidator(source);
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var item = expected(row);
        List<ExpectedItem> withNull = new ArrayList<>();
        withNull.add(null);
        for (List<ExpectedItem> batch : Arrays.<List<ExpectedItem>>asList(null, List.of(), withNull, List.of(item, item),
                IntStream.range(0, 101).mapToObj(i -> new ExpectedItem(UUID.randomUUID(), row.store().storeId(),
                        row.store().sellerId(), BigDecimal.ONE, row.currency())).toList(),
                List.of(new ExpectedItem(null, row.store().storeId(), row.store().sellerId(), BigDecimal.ONE, row.currency())),
                List.of(new ExpectedItem(row.product().id(), row.store().storeId(), row.store().sellerId(), BigDecimal.ZERO, row.currency())),
                List.of(new ExpectedItem(row.product().id(), row.store().storeId(), row.store().sellerId(), BigDecimal.ONE, " ")))) {
            assertEquals(CatalogBatchException.Code.INVALID_REQUEST,
                    assertThrows(CatalogBatchException.class, () -> validator.validate(batch)).getCode());
        }
        verifyNoInteractions(source);
    }

    @Test void sourceTimeoutOrMissingResponseFailsWholeBatchClosed() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        for (CatalogSnapshotSource source : List.<CatalogSnapshotSource>of(ids -> null,
                ids -> { throw new IllegalStateException("fixture dependency offline"); })) {
            assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                    () -> new CatalogBatchValidator(source).validate(List.of(expected(row)))).getCode());
        }
    }

    @Test void verifiedAbsenceAloneReturnsNotFoundWithoutUsingExpectedFacts() {
        var item = new ExpectedItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("999"), "CALLER-ONLY-CURRENCY");
        Result result = new CatalogBatchValidator(ids -> List.of(
                new LookupResult(item.productId(), LookupStatus.ABSENT, null))).validate(List.of(item));
        assertFalse(result.valid());
        assertEquals(ItemCode.PRODUCT_NOT_FOUND, result.items().get(0).code());
        assertNull(result.items().get(0).snapshot());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unavailableOrStaleSourceRejectsWholeBatchEvenWithPlausibleFacts(boolean hasStaleFacts) {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-B", "20");
        // A source unable to verify freshness must report UNAVAILABLE, even if it has cached data.
        var error = assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> List.of(found(first), new LookupResult(second.product().id(),
                        LookupStatus.UNAVAILABLE, hasStaleFacts ? second : null)))
                        .validate(List.of(expected(first), expected(second))));
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertFalse(error.getMessage().contains("SKU-A"));
        assertFalse(error.getMessage().contains("private-owner-reference"));
    }

    @Test void sourceTimeoutCannotFallbackToCallerExpectations() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var error = assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> { throw new IllegalStateException(
                        new java.net.SocketTimeoutException("private upstream location")); }).validate(List.of(expected(row))));
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertFalse(error.getMessage().contains("private upstream location"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found-without-facts", "absent-with-facts", "wrong-product-id", "null-status", "null-id",
            "unrequested-absence", "duplicate-absence", "omitted-after-absence", "null-product", "null-store", "blank-currency"})
    void malformedOrIncompleteLookupResultsNeverProveAbsence(String fault) {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-B", "20");
        UUID id = first.product().id();
        List<ExpectedItem> input = fault.equals("omitted-after-absence")
                ? List.of(expected(first), expected(second)) : List.of(expected(first));
        List<LookupResult> rows = switch (fault) {
            case "found-without-facts" -> List.of(new LookupResult(id, LookupStatus.FOUND, null));
            case "absent-with-facts" -> List.of(new LookupResult(id, LookupStatus.ABSENT, first));
            case "wrong-product-id" -> List.of(new LookupResult(id, LookupStatus.FOUND, second));
            case "null-status" -> List.of(new LookupResult(id, null, first));
            case "null-id" -> List.of(new LookupResult(null, LookupStatus.ABSENT, null));
            case "unrequested-absence" -> List.of(new LookupResult(UUID.randomUUID(), LookupStatus.ABSENT, null));
            case "duplicate-absence" -> List.of(new LookupResult(id, LookupStatus.ABSENT, null),
                    new LookupResult(id, LookupStatus.ABSENT, null));
            case "null-product" -> List.of(new LookupResult(id, LookupStatus.FOUND,
                    new ResolvedProduct(null, first.store(), first.currency(), true)));
            case "null-store" -> List.of(new LookupResult(id, LookupStatus.FOUND,
                    new ResolvedProduct(first.product(), null, first.currency(), true)));
            case "blank-currency" -> List.of(found(new ResolvedProduct(first.product(), first.store(), " ", true)));
            default -> List.of(new LookupResult(id, LookupStatus.ABSENT, null));
        };
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> rows).validate(input)).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"seller", "owner", "approved", "open", "house"})
    void conflictingFactsForTheSameStoreFailTheWholeBatch(String conflict) {
        UUID storeId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        var first = facts(UUID.randomUUID(), storeId, sellerId, "SKU-A", "10");
        var second = facts(UUID.randomUUID(), storeId, sellerId, "SKU-B", "20");
        var changedStore = new StoreAccess(storeId, conflict.equals("seller") ? UUID.randomUUID() : sellerId,
                conflict.equals("owner") ? "different-owner" : first.store().ownerReference(),
                !conflict.equals("approved"), !conflict.equals("open"), conflict.equals("house"));
        var changed = new ResolvedProduct(second.product(), changedStore, second.currency(), true);
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> List.of(found(first), found(changed)))
                        .validate(List.of(expected(first), expected(second)))).getCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oneSellerResolvedToDifferentStoresFailsTheWholeBatch(boolean reverseSourceOrder) {
        UUID sellerId = UUID.randomUUID();
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), sellerId, "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), sellerId, "SKU-B", "20");
        List<LookupResult> rows = reverseSourceOrder
                ? List.of(found(second), found(first)) : List.of(found(first), found(second));

        CatalogBatchException failure = assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> rows).validate(List.of(expected(first), expected(second))));

        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, failure.getCode());
    }

    @Test void oneStoreMaySupplyMultipleProductsForItsSeller() {
        UUID storeId = UUID.randomUUID(), sellerId = UUID.randomUUID();
        var first = facts(UUID.randomUUID(), storeId, sellerId, "SKU-A", "10");
        var second = facts(UUID.randomUUID(), storeId, sellerId, "SKU-B", "20");

        Result result = new CatalogBatchValidator(ids -> List.of(found(second), found(first)))
                .validate(List.of(expected(first), expected(second)));

        assertTrue(result.valid());
        assertEquals(List.of("SKU-A", "SKU-B"), result.items().stream()
                .map(item -> item.snapshot().inventorySku()).toList());
        assertTrue(result.items().stream().allMatch(item -> item.snapshot().storeId().equals(storeId)
                && item.snapshot().sellerId().equals(sellerId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownership", "currency", "private", "absent"})
    void oneRejectedItemPreventsUsingAnyReadySubset(String rejection) throws Exception {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-B", "20");
        var item = new ExpectedItem(second.product().id(), second.store().storeId(),
                rejection.equals("ownership") ? UUID.randomUUID() : second.store().sellerId(), second.product().unitPrice(),
                rejection.equals("currency") ? "CALLER-CURRENCY" : second.currency());
        var privateProduct = new ProductListing(second.product().id(), second.product().storeId(), categoryId,
                "SKU-B", "Private", BigDecimal.TEN, "VND", ProductStatus.REJECTED, "private reason");
        var rejected = switch (rejection) {
            case "absent" -> new LookupResult(second.product().id(), LookupStatus.ABSENT, null);
            case "private" -> found(new ResolvedProduct(privateProduct, second.store(), second.currency(), true));
            default -> found(second);
        };
        Result result = new CatalogBatchValidator(ids -> List.of(found(first), rejected)).validate(List.of(expected(first), item));
        assertFalse(result.valid());
        assertEquals(ItemCode.READY, result.items().get(0).code());
        assertEquals(switch (rejection) {
            case "ownership" -> ItemCode.OWNERSHIP_MISMATCH;
            case "currency" -> ItemCode.CURRENCY_MISMATCH;
            case "private" -> ItemCode.PRODUCT_NOT_SELLABLE;
            default -> ItemCode.PRODUCT_NOT_FOUND;
        }, result.items().get(1).code());
        assertTrue(result.items().stream().allMatch(itemResult -> itemResult.snapshot() == null));
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("SKU-A"));
        assertFalse(json.contains("SKU-B"));
        assertFalse(json.contains("private reason"));
        assertFalse(json.contains("private-owner-reference"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra", "duplicate-product", "duplicate-sku", "store", "currency", "null"})
    void inconsistentProviderDataIsNeverAccepted(String fault) {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "20");
        List<ExpectedItem> input = fault.equals("duplicate-sku") ? List.of(expected(first), expected(second)) : List.of(expected(first));
        List<LookupResult> rows = switch (fault) {
            case "extra" -> List.of(found(first), found(facts(UUID.randomUUID(), first.store().storeId(), first.store().sellerId(), "other", "10")));
            case "duplicate-product" -> List.of(found(first), found(first));
            case "duplicate-sku" -> List.of(found(first), found(second));
            case "store" -> List.of(found(new ResolvedProduct(first.product(), second.store(), first.currency(), true)));
            case "currency" -> List.of(found(new ResolvedProduct(first.product(), first.store(), null, true)));
            default -> Arrays.asList((LookupResult) null);
        };
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> rows).validate(input)).getCode());
    }
}
