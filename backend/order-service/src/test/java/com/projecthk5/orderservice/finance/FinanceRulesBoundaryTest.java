package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Local snapshot/guard tests, not a persisted settlement or authenticated API contract. */
class FinanceRulesBoundaryTest {
    @ParameterizedTest
    @CsvSource({"0.09,0.00,0.09", "0.10,0.01,0.09", "0.11,0.01,0.10", "123.45,6.17,117.28"})
    void roundsCommissionAtHalfCentWithoutRoundingSubtotal(String input, String fee, String net) {
        var result = FinanceRules.snapshot(new BigDecimal(input), false, 2);
        assertEquals(new BigDecimal(input), result.subtotal());
        assertEquals(new BigDecimal(fee), result.commission());
        assertEquals(new BigDecimal(net), result.sellerNet());
    }

    @Test
    void conservesMoneyForDeterministicRangeOfAmountsAndBothStoreTypes() {
        for (int scale : new int[] {0, 2, 4}) {
            for (int minorUnits = 0; minorUnits <= 1000; minorUnits++) {
                BigDecimal subtotal = BigDecimal.valueOf(minorUnits, scale);
                for (boolean house : new boolean[] {false, true}) {
                    var result = FinanceRules.snapshot(subtotal, house, scale);
                    assertEquals(subtotal, result.commission().add(result.sellerNet()));
                    assertTrue(result.commission().signum() >= 0);
                    assertTrue(result.sellerNet().signum() >= 0);
                    assertTrue(result.commission().compareTo(subtotal) <= 0);
                    if (house) {
                        assertEquals(0, result.commission().signum());
                    }
                }
            }
        }
    }

    @Test
    void rejectsMissingAmountInvalidScaleAndHousePrecisionLossToo() {
        assertThrows(NullPointerException.class, () -> FinanceRules.snapshot(null, false, 2));
        // 0..4 is the current helper's local limit, not an approved currency contract.
        assertThrows(IllegalArgumentException.class, () -> FinanceRules.snapshot(BigDecimal.ONE, false, 5));
        assertThrows(ArithmeticException.class,
                () -> FinanceRules.snapshot(new BigDecimal("1.001"), true, 2));
        assertThrows(IllegalArgumentException.class,
                () -> FinanceRules.snapshot(new BigDecimal("-0.01"), true, 2));
    }

    @Test
    void acceptsExactTrailingZeroRescalingWithoutLosingMoney() {
        assertEquals(new BigDecimal("123.45"),
                FinanceRules.snapshot(new BigDecimal("123.4500"), false, 2).subtotal());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void missingIdentityOnEitherSideCannotGrantFinanceAccess(String identity) {
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner(identity, "store-a"));
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner("store-a", identity));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Store-a", " store-a", "store-a ", "store-b"})
    void ownershipUsesExactServerReferences(String other) {
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner("store-a", other));
    }

    @Test
    void twoStoreArithmeticAndGuardsRemainIndependent() {
        var regular = FinanceRules.snapshot(new BigDecimal("100000"), false, 0);
        var house = FinanceRules.snapshot(new BigDecimal("200000"), true, 0);
        assertEquals(new BigDecimal("300000"), regular.subtotal().add(house.subtotal()));
        assertEquals(new BigDecimal("5000"), regular.commission().add(house.commission()));
        assertEquals(new BigDecimal("295000"), regular.sellerNet().add(house.sellerNet()));
        assertDoesNotThrow(() -> FinanceRules.requireStoreOwner("store-a", "store-a"));
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner("store-a", "store-b"));
        assertDoesNotThrow(() -> FinanceRules.requireSettleable(true, true, false));
        // Caller supplies settled=true: this checks rejection, not storage or concurrent deduplication.
        assertThrows(IllegalStateException.class, () -> FinanceRules.requireSettleable(true, true, true));
    }

    @Test
    void computingNewStoreConfigurationDoesNotMutateEarlierSnapshot() {
        var atOrderCreation = FinanceRules.snapshot(new BigDecimal("100000"), false, 0);
        var laterHouseQuote = FinanceRules.snapshot(new BigDecimal("100000"), true, 0);
        assertEquals(new BigDecimal("5000"), atOrderCreation.commission());
        assertEquals(new BigDecimal("95000"), atOrderCreation.sellerNet());
        assertEquals(BigDecimal.ZERO, laterHouseQuote.commission());
        // No settlement loader exists yet; this only proves immutable rule results.
    }
}
