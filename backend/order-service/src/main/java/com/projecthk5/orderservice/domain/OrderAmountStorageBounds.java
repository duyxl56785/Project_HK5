package com.projecthk5.orderservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Exact representation check for the existing PostgreSQL NUMERIC(19,2) amount columns.
 *
 * <p>This is a storage constraint, NOT an approved business currency, fee, or rounding
 * policy. CheckoutService checks existing storage exactness before Inventory
 * RESERVE; approved M01 policy limits, currency/snapshots and recovery are separate gates.
 */
public final class OrderAmountStorageBounds {
    public static final BigDecimal MAX_NONNEGATIVE = new BigDecimal("99999999999999999.99");

    private OrderAmountStorageBounds() { }

    /**
     * Rejects values PostgreSQL would round or reject in NUMERIC(19,2).
     * The returned value is numerically identical to the input, at storage scale.
     */
    public static BigDecimal requireExactNonnegative(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("Negative stored money amount");
        final BigDecimal exact;
        try {
            exact = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Money amount cannot be represented exactly at scale 2", e);
        }
        if (exact.compareTo(MAX_NONNEGATIVE) > 0) {
            throw new IllegalArgumentException("Money amount exceeds NUMERIC(19,2)");
        }
        return exact;
    }
}
