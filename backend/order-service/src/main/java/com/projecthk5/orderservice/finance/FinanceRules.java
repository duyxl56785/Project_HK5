package com.projecthk5.orderservice.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Internal snapshot rules; no HTTP, persistence, JWT or settlement side effects. */
public final class FinanceRules {
    private static final BigDecimal PLATFORM_RATE = new BigDecimal("0.05");

    private FinanceRules() { }

    public record Amounts(BigDecimal subtotal, BigDecimal commission, BigDecimal sellerNet) { }

    /** Use only at order creation; persist the result instead of recalculating with current Store data. */
    public static Amounts snapshot(BigDecimal subtotal, boolean houseStore, int currencyScale) {
        Objects.requireNonNull(subtotal, "Subtotal is required");
        if (subtotal.signum() < 0 || currencyScale < 0 || currencyScale > 4) {
            throw new IllegalArgumentException("Invalid subtotal or currency scale");
        }
        // Reject silent loss of subtotal precision. Currency/scale must be agreed with TV3/TV4.
        BigDecimal amount = subtotal.setScale(currencyScale, RoundingMode.UNNECESSARY);
        BigDecimal fee = amount.multiply(houseStore ? BigDecimal.ZERO : PLATFORM_RATE)
                .setScale(currencyScale, RoundingMode.HALF_UP);
        return new Amounts(amount, fee, amount.subtract(fee));
    }

    public static void requireSettleable(boolean delivered, boolean codPaid, boolean alreadySettled) {
        if (!delivered || !codPaid) {
            throw new IllegalStateException("Settlement requires DELIVERED and COD PAID");
        }
        if (alreadySettled) {
            throw new IllegalStateException("Seller order is already settled");
        }
    }

    /** References must be resolved server-side from verified Seller identity and stored order data. */
    public static void requireStoreOwner(String verifiedStoreId, String financeStoreId) {
        if (verifiedStoreId == null || verifiedStoreId.isBlank()
                || financeStoreId == null || financeStoreId.isBlank()
                || !verifiedStoreId.equals(financeStoreId)) {
            throw new SecurityException("Finance access requires Store ownership");
        }
    }
}
