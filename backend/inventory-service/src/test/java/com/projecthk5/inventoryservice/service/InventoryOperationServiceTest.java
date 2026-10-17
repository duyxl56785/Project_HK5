package com.projecthk5.inventoryservice.service;

import com.projecthk5.inventoryservice.api.InventoryOperationRequest;
import com.projecthk5.inventoryservice.api.InventoryOperationType;
import com.projecthk5.inventoryservice.model.InventoryItem;
import com.projecthk5.inventoryservice.model.InventoryOperation;
import com.projecthk5.inventoryservice.model.InsufficientStockException;
import com.projecthk5.inventoryservice.repository.InventoryItemRepository;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryOperationServiceTest {
    private InventoryItemRepository items;
    private InventoryOperationRepository operations;
    private InventoryOperationService service;

    @BeforeEach
    void setUp() {
        items = mock(InventoryItemRepository.class);
        operations = mock(InventoryOperationRepository.class);
        service = new InventoryOperationService(items, operations);
    }

    @Test
    void successfulMultiSkuReserveChangesAllRequestedBalances() {
        InventoryItem a = new InventoryItem("A", 5);
        InventoryItem b = new InventoryItem("B", 7);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a, b));
        when(operations.findById("checkout-1")).thenReturn(Optional.empty());

        var result = service.apply(new InventoryOperationRequest("checkout-1", InventoryOperationType.RESERVE,
                List.of(new InventoryOperationRequest.ItemQuantity("A", 2),
                        new InventoryOperationRequest.ItemQuantity("B", 3))));

        assertEquals(3, a.getAvailableQuantity());
        assertEquals(2, a.getReservedQuantity());
        assertEquals(4, b.getAvailableQuantity());
        assertEquals(3, b.getReservedQuantity());
        assertFalse(result.replayed());
        verify(operations).saveAndFlush(any(InventoryOperation.class));
    }

    @Test
    void insufficientStockDoesNotRecordSuccessfulOperation() {
        InventoryItem a = new InventoryItem("A", 1);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a));
        when(operations.findById("checkout-2")).thenReturn(Optional.empty());

        assertThrows(InsufficientStockException.class, () -> service.apply(
                new InventoryOperationRequest("checkout-2", InventoryOperationType.RESERVE,
                        List.of(new InventoryOperationRequest.ItemQuantity("A", 2)))));

        assertEquals(1, a.getAvailableQuantity());
        assertEquals(0, a.getReservedQuantity());
        verify(operations, never()).saveAndFlush(any(InventoryOperation.class));
    }

    @Test
    void replayWithSameIdAndPayloadDoesNotApplyStockTwice() throws Exception {
        InventoryItem a = new InventoryItem("A", 3);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a));
        String hash = hash(InventoryOperationType.DECREASE, Map.of("A", 2L));
        when(operations.findById("adjustment-1"))
                .thenReturn(Optional.of(new InventoryOperation("adjustment-1", "DECREASE", hash)));

        var result = service.apply(new InventoryOperationRequest("adjustment-1", InventoryOperationType.DECREASE,
                List.of(new InventoryOperationRequest.ItemQuantity("A", 2))));

        assertTrue(result.replayed());
        assertEquals(3, a.getAvailableQuantity());
        verify(operations, never()).saveAndFlush(any(InventoryOperation.class));
    }

    @Test
    void operationIdCannotBeReusedForDifferentPayload() {
        InventoryItem a = new InventoryItem("A", 3);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a));
        when(operations.findById("same-id")).thenReturn(Optional.of(
                new InventoryOperation("same-id", "DECREASE", "not-the-request-hash")));

        assertThrows(com.projecthk5.inventoryservice.api.InventoryOperationServiceException.class,
                () -> service.apply(new InventoryOperationRequest("same-id", InventoryOperationType.DECREASE,
                        List.of(new InventoryOperationRequest.ItemQuantity("A", 1)))));
        assertEquals(3, a.getAvailableQuantity());
        verify(operations, never()).saveAndFlush(any(InventoryOperation.class));
    }

    private static String hash(InventoryOperationType type, Map<String, Long> input) throws Exception {
        Map<String, Long> sorted = new TreeMap<>(input);
        String canonical = type.name() + "\n" + sorted.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
