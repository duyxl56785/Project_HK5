package com.projecthk5.cartservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Current cart_items.unit_price_snapshot PostgreSQL NUMERIC(19,2) representation.
 * This protects exact Cart snapshot storage only; it does not approve M01
 * currency, minimum Product price, Product unit cap, fees or rounding policy.
 */
public final class CartAmountStorageBounds {
    public static final BigDecimal MAX_NONNEGATIVE = new BigDecimal("99999999999999999.99");

    private CartAmountStorageBounds() { }

    /** Reject any price PostgreSQL would round or overflow; do not round it. */
    public static BigDecimal requireExactNonnegative(BigDecimal price) {
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) throw new IllegalArgumentException("Negative cart price");
        final BigDecimal exact;
        try {
            exact = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Cart price is not exactly representable at scale 2", e);
        }
        if (exact.compareTo(MAX_NONNEGATIVE) > 0)
            throw new IllegalArgumentException("Cart price exceeds NUMERIC(19,2)");
        return exact;
    }
}
