package com.projecthk5.orderservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyRules {
    private static final BigDecimal RATE = new BigDecimal("0.05");
    private MoneyRules() {}
    public static BigDecimal fee(BigDecimal gross, boolean houseStore) {
        if (gross == null || gross.signum() < 0) throw new IllegalArgumentException("Negative or missing gross");
        // VND: provisional integer-dong rounding; confirm with TV5 before merge.
        return houseStore ? BigDecimal.ZERO : gross.multiply(RATE).setScale(0, RoundingMode.HALF_UP);
    }
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || unitPrice.signum() < 0 || quantity < 1) throw new IllegalArgumentException("Invalid line item");
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
