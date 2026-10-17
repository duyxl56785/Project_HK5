package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.ResolvedProduct;
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
                new BigDecimal(price), ProductStatus.ACTIVE, null),
                new StoreAccess(storeId, sellerId, "private-owner-reference", true, true, false), "TEST-CURRENCY", true);
    }

    private ExpectedItem expected(ResolvedProduct facts) {
        return new ExpectedItem(facts.product().id(), facts.store().storeId(), facts.store().sellerId(),
                facts.product().unitPrice(), facts.currency());
    }

    @Test void resolvesTwoStoresOnceAndPreservesRequestOrderAndAuthoritativeSku() throws Exception {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sku-A", "10.1234567");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sku-B", "20");
        CatalogSnapshotSource source = mock(CatalogSnapshotSource.class);
        when(source.load(Set.of(first.product().id(), second.product().id()))).thenReturn(List.of(second, first));
        Result result = new CatalogBatchValidator(source).validate(List.of(expected(first), expected(second)));
        assertTrue(result.valid());
        assertEquals(List.of(first.product().id(), second.product().id()), result.items().stream().map(ItemResult::productId).toList());
        assertEquals("Sku-A", result.items().get(0).snapshot().inventorySku());
        assertEquals(new BigDecimal("10.1234567"), result.items().get(0).snapshot().unitPrice());
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
                "SKU", "Private", BigDecimal.TEN, status, reason);
        var result = new CatalogBatchValidator(ids -> List.of(new ResolvedProduct(product, row.store(), row.currency(), true)))
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
        var result = new CatalogBatchValidator(ids -> List.of(new ResolvedProduct(row.product(), store,
                row.currency(), !invalid.equals("category")))).validate(List.of(expected(row)));
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, result.items().get(0).code());
        assertNull(result.items().get(0).snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"store", "seller", "price", "currency"})
    void clientExpectationsCannotReplaceAuthoritativeFacts(String mismatch) {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10.01");
        ExpectedItem item = new ExpectedItem(row.product().id(), mismatch.equals("store") ? UUID.randomUUID() : row.store().storeId(),
                mismatch.equals("seller") ? UUID.randomUUID() : row.store().sellerId(),
                mismatch.equals("price") ? BigDecimal.ONE : row.product().unitPrice(),
                mismatch.equals("currency") ? "OTHER-TEST-CURRENCY" : row.currency());
        Result result = new CatalogBatchValidator(ids -> List.of(row)).validate(List.of(item));
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
        assertTrue(new CatalogBatchValidator(ids -> List.of(row)).validate(List.of(item)).valid());
    }

    @Test void missingProductIsExplicitAndPartialResultIsNotAllValid() {
        var row = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var missing = new ExpectedItem(UUID.randomUUID(), row.store().storeId(), row.store().sellerId(), BigDecimal.ONE, row.currency());
        Result result = new CatalogBatchValidator(ids -> List.of(row)).validate(List.of(expected(row), missing));
        assertFalse(result.valid());
        assertEquals(ItemCode.PRODUCT_NOT_FOUND, result.items().get(1).code());
        assertNull(result.items().get(1).snapshot());
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

    @ParameterizedTest
    @ValueSource(strings = {"extra", "duplicate-product", "duplicate-sku", "store", "currency", "null"})
    void inconsistentProviderDataIsNeverAccepted(String fault) {
        var first = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "10");
        var second = facts(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "20");
        List<ExpectedItem> input = fault.equals("duplicate-sku") ? List.of(expected(first), expected(second)) : List.of(expected(first));
        List<ResolvedProduct> rows = switch (fault) {
            case "extra" -> List.of(first, facts(UUID.randomUUID(), first.store().storeId(), first.store().sellerId(), "other", "10"));
            case "duplicate-product" -> List.of(first, first);
            case "duplicate-sku" -> List.of(first, second);
            case "store" -> List.of(new ResolvedProduct(first.product(), second.store(), first.currency(), true));
            case "currency" -> List.of(new ResolvedProduct(first.product(), first.store(), null, true));
            default -> Arrays.asList((ResolvedProduct) null);
        };
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, assertThrows(CatalogBatchException.class,
                () -> new CatalogBatchValidator(ids -> rows).validate(input)).getCode());
    }
}
