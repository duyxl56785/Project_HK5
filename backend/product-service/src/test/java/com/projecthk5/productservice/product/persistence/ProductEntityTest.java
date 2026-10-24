package com.projecthk5.productservice.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure mapping checks; JPA version generation and database enforcement require PostgreSQL tests. */
class ProductEntityTest {
    private final UUID id = UUID.randomUUID(), storeId = UUID.randomUUID(), categoryId = UUID.randomUUID();

    @Test void newEntityRoundTripsExactPriceCurrencyAndUnicodeWithoutInventingVersion() {
        BigDecimal amount = new BigDecimal("1000000000.00000");
        ProductListing original = listing(id, storeId, categoryId, "e\u0301 \ud83d\ude00-Sku", " Product ",
                amount, ProductStatus.DRAFT, null);

        ProductEntity entity = new ProductEntity(original);
        ProductListing restored = entity.toDomain();

        assertEquals(original, restored);
        assertSame(amount, restored.unitPrice());
        assertEquals(5, restored.unitPrice().scale());
        assertEquals("VND", restored.currency());
        assertEquals("e\u0301 \ud83d\ude00-Sku", restored.sku());
        assertNull(entity.getVersion(), "A new entity lets JPA identify an insert through its null version");
    }

    @Test void applyChangesOnlyMutableFieldsAndKeepsOriginalBigDecimal() {
        ProductEntity entity = new ProductEntity(listing(id, storeId, categoryId, "Sku", "Original",
                BigDecimal.ONE, ProductStatus.DRAFT, null));
        BigDecimal updatedAmount = new BigDecimal("10.00000");
        ProductListing updated = listing(id, storeId, UUID.randomUUID(), "Sku", " Edited ",
                updatedAmount, ProductStatus.REJECTED, " Fix details ");

        entity.apply(updated);

        assertEquals(updated, entity.toDomain());
        assertSame(updatedAmount, entity.toDomain().unitPrice());
        assertNull(entity.getVersion(), "The mapper must not simulate provider-managed optimistic versions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "store", "sku"})
    void immutableIdentityChangesAreRejectedBeforeAnyMutableFieldChanges(String identity) {
        ProductListing original = listing(id, storeId, categoryId, "Sku", "Original",
                new BigDecimal("12.00000"), ProductStatus.DRAFT, null);
        ProductEntity entity = new ProductEntity(original);
        ProductListing forbidden = listing(identity.equals("id") ? UUID.randomUUID() : id,
                identity.equals("store") ? UUID.randomUUID() : storeId, UUID.randomUUID(),
                identity.equals("sku") ? "sku" : "Sku", "Changed", BigDecimal.TEN,
                ProductStatus.REJECTED, "Changed reason");

        ProductPersistenceException error = assertThrows(ProductPersistenceException.class,
                () -> entity.apply(forbidden));

        assertEquals(ProductPersistenceException.Code.IMMUTABLE_IDENTITY, error.getCode());
        assertEquals(original, entity.toDomain());
        assertSame(original.unitPrice(), entity.toDomain().unitPrice());
        assertNull(entity.getVersion());
    }

    private ProductListing listing(UUID id, UUID store, UUID category, String sku, String name,
                                   BigDecimal amount, ProductStatus status, String reason) {
        return new ProductListing(id, store, category, sku, name, amount, "VND", status, reason);
    }
}
