package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductPriceTest {
    @ParameterizedTest
    @ValueSource(strings = {"1", "1.00000", "10.0000", "999999999", "1000000000.00", "1E+9"})
    void validationKeepsOriginalAmountAndCanonicalRoundTripPreservesValue(String value) {
        BigDecimal amount = new BigDecimal(value);
        assertSame(amount, ProductPrice.validate(amount, "VND"));
        String canonical = ProductPrice.canonicalString(amount, "VND");
        assertTrue(canonical.matches("^[1-9][0-9]{0,9}$"));
        assertEquals(amount.toBigIntegerExact().toString(), canonical);
        assertEquals(0, ProductPrice.parseCanonical(canonical, "VND").compareTo(amount));
        assertEquals(0, ProductPrice.parseCanonical(canonical, "VND").scale());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.01", "1.01", "1000000000.01", "1000000001", "1E+10"})
    void rejectsInvalidAmountWithoutRounding(String value) {
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        assertInvalid(() -> ProductPrice.validate(amount, "VND"));
        assertInvalid(() -> ProductPrice.canonicalString(amount, "VND"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USD", "vnd", "Vnd", " VND", "VND ", "VND\n", "\u00a0VND"})
    void requiresExplicitExactVndCurrency(String currency) {
        assertInvalid(() -> ProductPrice.validate(BigDecimal.ONE, currency));
        assertInvalid(() -> ProductPrice.parseCanonical("1", currency));
        assertInvalid(() -> ProductPrice.canonicalString(BigDecimal.ONE, currency));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "00", "01", "0001", "-1", "+1", "1.0", "1.00", "1E+2", "1e2",
            " 1", "1 ", "\t1", "1\n", "\u00a01", "1\u00a0", "\uff11", "\u0661", "1000000001", "10000000000"})
    void canonicalParserRejectsNonCanonicalOrOverLimitStrings(String value) {
        assertInvalid(() -> ProductPrice.parseCanonical(value, "VND"));
    }

    @Test void canonicalParserAcceptsBothLimits() {
        assertEquals(BigDecimal.ONE, ProductPrice.parseCanonical("1", "VND"));
        assertEquals(new BigDecimal("1000000000"), ProductPrice.parseCanonical("1000000000", "VND"));
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT,
                assertThrows(ProductRuleException.class, action).getCode());
    }
}
