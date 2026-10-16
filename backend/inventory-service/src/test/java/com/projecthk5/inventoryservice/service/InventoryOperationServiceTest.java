package com.projecthk5.inventoryservice.service;

import com.projecthk5.inventoryservice.api.InventoryOperationRequest;
import com.projecthk5.inventoryservice.domain.Stock;
import com.projecthk5.inventoryservice.error.InventoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryOperationServiceTest {
    private InventoryOperationService service;

    @BeforeEach void setUp() {
        service = new InventoryOperationService();
        service.setAvailableStock("SKU-1", 10);
    }

    @Test void reserveDecreasesAvailableAndIncreasesReserved() {
        Stock stock = service.reserve(new InventoryOperationRequest("checkout-1", "SKU-1", 3));
        assertEquals(7, stock.availableQuantity());
        assertEquals(3, stock.reservedQuantity());
    }

    @Test void reserveReplayDoesNotReserveTwice() {
        var request = new InventoryOperationRequest("checkout-1", "SKU-1", 3);
        service.reserve(request);
        Stock replay = service.reserve(request);
        assertEquals(7, replay.availableQuantity());
        assertEquals(3, replay.reservedQuantity());
        assertEquals(7, service.getStock("SKU-1").availableQuantity());
    }

    @Test void insufficientStockDoesNotMakeAvailableNegative() {
        assertThrows(InventoryException.class, () ->
                service.reserve(new InventoryOperationRequest("checkout-2", "SKU-1", 11)));
        assertEquals(10, service.getStock("SKU-1").availableQuantity());
    }

    @Test void releaseReplayRestoresStockOnlyOnce() {
        service.reserve(new InventoryOperationRequest("checkout-3", "SKU-1", 4));
        var release = new InventoryOperationRequest("checkout-3", "SKU-1", 4);
        service.release(release);
        Stock replay = service.release(release);
        assertEquals(10, replay.availableQuantity());
        assertEquals(0, replay.reservedQuantity());
    }

    @Test void consumeRemovesReservedQuantityAndReplayDoesNotConsumeTwice() {
        service.reserve(new InventoryOperationRequest("checkout-4", "SKU-1", 2));
        var consume = new InventoryOperationRequest("checkout-4", "SKU-1", 2);
        service.consume(consume);
        Stock replay = service.consume(consume);
        assertEquals(8, replay.availableQuantity());
        assertEquals(0, replay.reservedQuantity());
    }

    @Test void operationIdCannotBeReusedWithDifferentPayload() {
        service.reserve(new InventoryOperationRequest("checkout-5", "SKU-1", 2));
        assertThrows(InventoryException.class, () ->
                service.reserve(new InventoryOperationRequest("checkout-5", "SKU-1", 3)));
        assertEquals(8, service.getStock("SKU-1").availableQuantity());
    }

    @Test void cannotOverwriteStockWhileReservationExists() {
        service.reserve(new InventoryOperationRequest("checkout-6", "SKU-1", 1));
        assertThrows(InventoryException.class, () -> service.setAvailableStock("SKU-1", 20));
    }
}
