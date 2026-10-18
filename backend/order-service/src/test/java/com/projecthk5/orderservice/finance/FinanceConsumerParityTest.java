package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.domain.MoneyRules;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Existing helper compatibility; neither the scale range nor rounding stage is an approved M01. */
class FinanceConsumerParityTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void checkoutFeeAndFinanceHelperAgreeAtHalfMinorUnitBoundaries(int scale) {
        var consumer = new MoneyRules(true, "0.05", Integer.toString(scale), "true");
        // Known independent vectors: 9*.05=.45 -> 0, 10*.05=.50 -> 1, 11*.05=.55 -> 1.
        for (int units : new int[] {9, 10, 11}) {
            var subtotal = BigDecimal.valueOf(units, scale);
            var expectedFee = BigDecimal.valueOf(units == 9 ? 0 : 1, scale);
            for (boolean house : new boolean[] {false, true}) {
                var expected = house ? BigDecimal.ZERO.setScale(scale) : expectedFee;
                var snapshot = FinanceRules.snapshot(subtotal, house, scale);
                assertEquals(expected, consumer.fee(subtotal, house));
                assertEquals(expected, snapshot.commission());
                assertEquals(subtotal.subtract(expected), snapshot.sellerNet());
            }
            var tooPrecise = subtotal.add(BigDecimal.ONE.movePointLeft(scale + 1));
            assertThrows(ArithmeticException.class, () -> consumer.fee(tooPrecise, true));
            assertThrows(ArithmeticException.class, () -> FinanceRules.snapshot(tooPrecise, true, scale));
        }
    }

    @Test void perSellerOrderRoundingDiffersFromPerLineAndMustBeSignedExplicitly() {
        var line = new BigDecimal("0.10");
        var oncePerSellerOrder = FinanceRules.snapshot(line.add(line), false, 2);
        var sumOfLineFees = FinanceRules.snapshot(line, false, 2).commission().multiply(BigDecimal.valueOf(2));
        assertEquals(new BigDecimal("0.01"), oncePerSellerOrder.commission());
        assertEquals(new BigDecimal("0.02"), sumOfLineFees);
        assertNotEquals(sumOfLineFees, oncePerSellerOrder.commission());
        // Records the ambiguity; does not implement a Checkout policy or change its grouping.
    }

    @Test void roundingEachStoreIsNotRoundingTheParentOrder() {
        var storeA = FinanceRules.snapshot(new BigDecimal("0.10"), false, 2);
        var storeB = FinanceRules.snapshot(new BigDecimal("0.10"), false, 2);
        assertEquals(new BigDecimal("0.02"), storeA.commission().add(storeB.commission()));
        assertEquals(new BigDecimal("0.01"),
            FinanceRules.snapshot(new BigDecimal("0.20"), false, 2).commission());
        assertEquals(new BigDecimal("0.18"), storeA.sellerNet().add(storeB.sellerNet()));
    }

    @Test void largeQuantityMultiplicationStaysExactBeforeAnyPersistenceBoundCheck() {
        assertEquals(new BigDecimal("21474836.47"),
            MoneyRules.lineTotal(new BigDecimal("0.01"), Integer.MAX_VALUE));
        var aboveDbBound = MoneyRules.lineTotal(new BigDecimal("99999999999999999.99"), 2);
        assertEquals(new BigDecimal("199999999999999999.98"), aboveDbBound);
        assertEquals(new BigDecimal("10000000000000000.00"),
            FinanceRules.snapshot(aboveDbBound, false, 2).commission());
        // BigDecimal arithmetic does not overflow; actual DB-bound validation is a separate open gate.
    }
}
