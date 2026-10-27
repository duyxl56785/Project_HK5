package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;

class ProductCatalogSnapshotSourceTest {
    @Test void databaseConnectionFailureDoesNotProveAnyAbsence() {
        var products = mock(ProductRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<CurrentStoreFactsSource> stores = mock(ObjectProvider.class);
        var ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
        when(products.findCatalogRows(ids)).thenThrow(new DataAccessResourceFailureException("offline"));
        var loaded = new ProductCatalogSnapshotSource(products, stores).load(ids);
        assertEquals(ids, loaded.stream().map(CatalogSnapshotSource.LookupResult::productId).collect(Collectors.toSet()));
        assertTrue(loaded.stream().allMatch(r -> r.status()==CatalogSnapshotSource.LookupStatus.UNAVAILABLE && r.facts()==null));
        verifyNoInteractions(stores);
    }

    @ParameterizedTest @ValueSource(strings={"null-list","null-row","unrequested","duplicate","price","currency","category","id"})
    void malformedDatabaseProjectionIsUnavailableRatherThanConfirmedAbsent(String invalid) {
        UUID id = UUID.randomUUID();
        var row = mock(ProductRepository.CatalogRow.class);
        when(row.getId()).thenReturn(invalid.equals("unrequested") ? UUID.randomUUID() : invalid.equals("id") ? null : id);
        when(row.getStoreId()).thenReturn(UUID.randomUUID());
        when(row.getCategoryId()).thenReturn(UUID.randomUUID());
        when(row.getInventorySku()).thenReturn("SKU"); when(row.getName()).thenReturn("Name");
        when(row.getUnitPrice()).thenReturn(invalid.equals("price") ? new BigDecimal("1.5") : BigDecimal.ONE);
        when(row.getCurrency()).thenReturn(invalid.equals("currency") ? null : "VND");
        when(row.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(row.getCategoryExists()).thenReturn(invalid.equals("category") ? null : true);
        List<ProductRepository.CatalogRow> rows = switch(invalid) {
            case "null-list" -> null;
            case "null-row" -> Arrays.asList(row,null);
            case "duplicate" -> List.of(row,row);
            default -> List.of(row);
        };
        var products = mock(ProductRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<CurrentStoreFactsSource> stores = mock(ObjectProvider.class);
        when(products.findCatalogRows(Set.of(id))).thenReturn(rows);
        var loaded = new ProductCatalogSnapshotSource(products,stores).load(Set.of(id));
        assertEquals(CatalogSnapshotSource.LookupStatus.UNAVAILABLE, loaded.getFirst().status());
        assertNull(loaded.getFirst().facts()); verifyNoInteractions(stores);
    }

    @Test void invalidInputNeverReadsDatabaseOrDependencies() {
        var products = mock(ProductRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<CurrentStoreFactsSource> stores = mock(ObjectProvider.class);
        var source = new ProductCatalogSnapshotSource(products, stores);
        Set<UUID> tooMany = IntStream.range(0,101).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());
        Set<UUID> withNull = new HashSet<>(); withNull.add(null);
        for (Set<UUID> ids : Arrays.asList(null, Set.<UUID>of(), withNull, tooMany)) {
            assertEquals(CatalogBatchException.Code.INVALID_REQUEST,
                    assertThrows(CatalogBatchException.class, () -> source.load(ids)).getCode());
        }
        verifyNoInteractions(products, stores);
    }
}
