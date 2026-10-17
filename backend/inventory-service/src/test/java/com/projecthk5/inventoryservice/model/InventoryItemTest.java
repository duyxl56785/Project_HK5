package com.projecthk5.inventoryservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemTest {
    @Test
    void reserveMovesQuantityFromAvailableToReserved() {
        InventoryItem item = new InventoryItem("SKU-1", 8);

        item.reserve(3);

        assertEquals(5, item.getAvailableQuantity());
        assertEquals(3, item.getReservedQuantity());
    }

    @Test
    void releaseRestoresAvailableQuantityOnlyOncePerRequestedAmount() {
        InventoryItem item = new InventoryItem("SKU-1", 8);
        item.reserve(3);

        item.releaseReserved(2);

        assertEquals(7, item.getAvailableQuantity());
        assertEquals(1, item.getReservedQuantity());
    }

    @Test
    void consumeReducesReservedQuantityWithoutIncreasingAvailableStock() {
        InventoryItem item = new InventoryItem("SKU-1", 8);
        item.reserve(3);

        item.consumeReserved(2);

        assertEquals(5, item.getAvailableQuantity());
        assertEquals(1, item.getReservedQuantity());
    }

    @Test
    void cannotDecreaseAvailableStockBelowZero() {
        InventoryItem item = new InventoryItem("SKU-1", 2);

        assertThrows(InsufficientStockException.class, () -> item.decreaseAvailable(3));
        assertEquals(2, item.getAvailableQuantity());
    }

    @Test
    void cannotReleaseMoreThanReserved() {
        InventoryItem item = new InventoryItem("SKU-1", 4);
        item.reserve(2);

        assertThrows(InsufficientReservationException.class, () -> item.releaseReserved(3));
        assertEquals(2, item.getAvailableQuantity());
        assertEquals(2, item.getReservedQuantity());
    }

    @Test
    void quantitiesMustBePositiveForOperations() {
        InventoryItem item = new InventoryItem("SKU-1", 4);

        assertThrows(IllegalArgumentException.class, () -> item.increaseAvailable(0));
        assertThrows(IllegalArgumentException.class, () -> item.reserve(-1));
    }

    @Test
    void increasingBeyondLongRangeThrowsWithoutChangingBalance() {
        InventoryItem item = new InventoryItem("SKU-1", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> item.increaseAvailable(1));
        assertEquals(Long.MAX_VALUE, item.getAvailableQuantity());
    }
}
