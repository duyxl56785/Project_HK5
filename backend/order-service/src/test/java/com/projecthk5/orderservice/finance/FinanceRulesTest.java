package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FinanceRulesTest {
    @ParameterizedTest
    @CsvSource({"100000,5000,95000", "10,1,9", "0,0,0", "999999999999999999,50000000000000000,949999999999999999"})
    void snapshotsFivePercentWithHalfUp(String subtotal, String commission, String net) {
        var result = FinanceRules.snapshot(new BigDecimal(subtotal), false, 0);
        assertEquals(new BigDecimal(commission), result.commission());
        assertEquals(new BigDecimal(net), result.sellerNet());
        assertEquals(result.subtotal(), result.commission().add(result.sellerNet()));
    }

    @Test
    void houseStoreHasZeroCommission() {
        var result = FinanceRules.snapshot(new BigDecimal("123.45"), true, 2);
        assertEquals(new BigDecimal("0.00"), result.commission());
        assertEquals(result.subtotal(), result.sellerNet());
    }

    @Test
    void rejectsNegativeMoneyAndSilentPrecisionLoss() {
        assertThrows(IllegalArgumentException.class,
                () -> FinanceRules.snapshot(new BigDecimal("-1"), false, 0));
        assertThrows(ArithmeticException.class,
                () -> FinanceRules.snapshot(new BigDecimal("1.001"), false, 2));
        assertThrows(IllegalArgumentException.class,
                () -> FinanceRules.snapshot(BigDecimal.ONE, false, -1));
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,true", "false,false,true", "false,true,true", "true,false,true"})
    void rejectsEveryIneligibleSettlementCombination(boolean delivered, boolean paid, boolean settled) {
        assertThrows(IllegalStateException.class,
                () -> FinanceRules.requireSettleable(delivered, paid, settled));
    }

    @Test
    void permitsOnlyDeliveredPaidUnsettledOrder() {
        assertDoesNotThrow(() -> FinanceRules.requireSettleable(true, true, false));
    }

    @Test
    void deniesOtherStoreAndUnverifiedStore() {
        assertDoesNotThrow(() -> FinanceRules.requireStoreOwner("store-a", "store-a"));
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner("store-a", "store-b"));
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner(null, "store-a"));
        assertThrows(SecurityException.class, () -> FinanceRules.requireStoreOwner(" ", " "));
    }
}
