package com.projecthk5.productservice.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Approved VND whole-unit policy, shared by domain and persistence boundaries. */
public final class ProductPrice {
    private static final BigDecimal MAXIMUM = new BigDecimal("1000000000");
    private static final Pattern CANONICAL = Pattern.compile("^[1-9][0-9]{0,9}$");

    private ProductPrice() { }

    public static BigDecimal validate(BigDecimal amount, String currency) {
        if (!"VND".equals(currency) || amount == null || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAXIMUM) > 0) {
            throw invalid();
        }
        try {
            // Validation only: bind and return the original value, never this rescaled result.
            amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException fractional) {
            throw invalid();
        }
        return amount;
    }

    public static BigDecimal parseCanonical(String value, String currency) {
        if (value == null || !CANONICAL.matcher(value).matches()) throw invalid();
        return validate(new BigDecimal(value), currency);
    }

    public static String canonicalString(BigDecimal amount, String currency) {
        return validate(amount, currency).toBigIntegerExact().toString();
    }

    private static ProductRuleException invalid() {
        return new ProductRuleException(ProductRuleException.Code.INVALID_INPUT,
                "Product price must be an exact whole VND amount from 1 to 1000000000");
    }
}
