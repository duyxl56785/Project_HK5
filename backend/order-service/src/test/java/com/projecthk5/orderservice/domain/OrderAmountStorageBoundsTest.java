package com.projecthk5.orderservice.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderAmountStorageBoundsTest {
    @Test void acceptsExactZeroAndBoundary() {
        assertEquals(new BigDecimal("0.00"), OrderAmountStorageBounds.requireExactNonnegative(BigDecimal.ZERO));
        assertEquals(new BigDecimal("99999999999999999.99"),
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("99999999999999999.99")));
    }

    @Test void acceptsHigherInputScaleOnlyWhenExact() {
        assertEquals(new BigDecimal("1.00"),
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("1.0000")));
    }

    @Test void rejectsSilentPrecisionLossBeforeAnyPersistence() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("1.005")));
    }

    @Test void rejectsParentOverflowEvenWhenStoreAmountsFit() {
        BigDecimal store = new BigDecimal("60000000000000000.00");
        assertEquals(store, OrderAmountStorageBounds.requireExactNonnegative(store));
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(store.add(store)));
    }

    @Test void rejectsOversizeProductAndLineTotals() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("100000000000000000.00")));
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("50000000000000000.00").multiply(new BigDecimal("3"))));
    }

    @Test void rejectsNegativeAndNull() {
        assertThrows(IllegalArgumentException.class, () ->
                OrderAmountStorageBounds.requireExactNonnegative(new BigDecimal("-0.01")));
        assertThrows(NullPointerException.class, () -> OrderAmountStorageBounds.requireExactNonnegative(null));
    }
}
