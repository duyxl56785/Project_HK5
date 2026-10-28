package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductListingValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1.1", "1000000000.0001", "1000000001", "1E+10"})
    void rejectsFractionalOrOverLimitPriceWithoutRounding(String value) {
        ProductRuleException error = assertThrows(ProductRuleException.class,
                () -> listing("SKU", new BigDecimal(value)));
        assertEquals(ProductRuleException.Code.INVALID_INPUT, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A\u0000B", "\u0000SKU", "SKU\u0000", "A\ud800B", "A\udc00B", "\ud800", "\udc00"})
    void rejectsNulAndUnpairedSurrogatesBeforePersistence(String sku) {
        ProductRuleException error = assertThrows(ProductRuleException.class,
                () -> listing(sku, BigDecimal.ONE));
        assertEquals(ProductRuleException.Code.INVALID_INPUT, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A\u0000B", "A\ud800B", "A\udc00B", "\ud800", "\udc00"})
    void rejectsInvalidNameBeforePersistence(String value) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> new ProductListing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", value,
                        BigDecimal.ONE, "VND", ProductStatus.DRAFT, null)).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A\u0000B", "A\ud800B", "A\udc00B", "\ud800", "\udc00"})
    void rejectsInvalidModerationReasonBeforePersistence(String value) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> new ProductListing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "Name",
                        BigDecimal.ONE, "VND", ProductStatus.REJECTED, value)).getCode());
    }

    @Test void validUnicodeKeepsNameUtf16AndReasonCodePointLimits() {
        String name = "\ud83d\ude00".repeat(127) + "x";
        String reason = "\ud83d\ude00".repeat(500);
        ProductListing product = new ProductListing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU",
                " " + name + " ", BigDecimal.ONE, "VND", ProductStatus.REJECTED, reason);
        assertEquals(name, product.name());
        assertSame(reason, product.reviewReason());
        assertEquals(255, name.length());
        assertEquals(500, reason.codePointCount(0, reason.length()));
        assertThrows(ProductRuleException.class, () -> new ProductListing(product.id(), product.storeId(), product.categoryId(),
                "SKU", name + "x", BigDecimal.ONE, "VND", ProductStatus.DRAFT, null));
        assertThrows(ProductRuleException.class, () -> new ProductListing(product.id(), product.storeId(), product.categoryId(),
                "SKU", name, BigDecimal.ONE, "VND", ProductStatus.REJECTED, reason + "x"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USD", "vnd", " VND", "VND ", "VND\n"})
    void listingRequiresExplicitExactVndCurrency(String currency) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> new ProductListing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU", "Name",
                        BigDecimal.ONE, currency, ProductStatus.DRAFT, null)).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sku-A", "sku-a", "SKU-A", "\u00e9-SKU", "e\u0301-SKU", "A B", "A\u00a0B", "A\tB", "A\ud83d\ude00B"})
    void validSkuAndAmountRemainExactAcrossStateChange(String sku) {
        BigDecimal amount = new BigDecimal("1000000000.00000");
        ProductListing listing = listing(sku, amount);
        ProductListing submitted = listing.withState(ProductStatus.PENDING_REVIEW, null);
        assertSame(sku, listing.sku());
        assertSame(sku, submitted.sku());
        assertSame(amount, listing.unitPrice());
        assertSame(amount, submitted.unitPrice());
        assertEquals("VND", listing.currency());
        assertEquals("VND", submitted.currency());
    }

    @Test void skuLengthUsesJavaUtf16UnitsIncludingValidSurrogatePairs() {
        String maximum = "\ud83d\ude00".repeat(64);
        assertEquals(128, maximum.length());
        assertSame(maximum, listing(maximum, BigDecimal.ONE).sku());
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> listing(maximum + "x", BigDecimal.ONE)).getCode());
    }

    private ProductListing listing(String sku, BigDecimal amount) {
        return new ProductListing(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sku, "Name",
                amount, "VND", ProductStatus.DRAFT, null);
    }
}
