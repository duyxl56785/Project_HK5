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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InventoryOperationServiceTest {
    private InventoryItemRepository items;
    private InventoryOperationRepository operations;
    private InventoryOperationService service;

    @BeforeEach
    void setUp() {
        items = mock(InventoryItemRepository.class);
        operations = mock(InventoryOperationRepository.class);
        when(operations.claimOperationId(anyString(), anyString(), anyString())).thenReturn(1);
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
        verify(operations).claimOperationId("checkout-1", "RESERVE", anyString());
    }

    @Test
    void insufficientStockDoesNotChangeBalance() {
        InventoryItem a = new InventoryItem("A", 1);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a));
        when(operations.findById("checkout-2")).thenReturn(Optional.empty());

        assertThrows(InsufficientStockException.class, () -> service.apply(
                new InventoryOperationRequest("checkout-2", InventoryOperationType.RESERVE,
                        List.of(new InventoryOperationRequest.ItemQuantity("A", 2)))));

        assertEquals(1, a.getAvailableQuantity());
        assertEquals(0, a.getReservedQuantity());
        verify(operations).claimOperationId("checkout-2", "RESERVE", anyString());
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
        verify(operations, never()).claimOperationId(anyString(), anyString(), anyString());
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
        verify(operations, never()).claimOperationId(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentClaimWithSamePayloadIsTreatedAsReplay() throws Exception {
        InventoryItem a = new InventoryItem("A", 3);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a));
        String hash = hash(InventoryOperationType.DECREASE, Map.of("A", 2L));
        when(operations.findById("race-id")).thenReturn(Optional.empty(),
                Optional.of(new InventoryOperation("race-id", "DECREASE", hash)));
        when(operations.claimOperationId("race-id", "DECREASE", hash)).thenReturn(0);

        var result = service.apply(new InventoryOperationRequest("race-id", InventoryOperationType.DECREASE,
                List.of(new InventoryOperationRequest.ItemQuantity("A", 2))));

        assertTrue(result.replayed());
        assertEquals(3, a.getAvailableQuantity());
    }

    @Test
    void delimiterContainingSkuCannotCollideWithMultipleItems() throws Exception {
        InventoryItem a = new InventoryItem("A", 10);
        InventoryItem b = new InventoryItem("B", 10);
        when(items.findAllBySkuForUpdate(any())).thenReturn(List.of(a, b));

        String firstPayloadHash = hash(InventoryOperationType.INCREASE, Map.of("A:1\nB", 2L));
        when(operations.findById("same-id")).thenReturn(Optional.of(
                new InventoryOperation("same-id", "INCREASE", firstPayloadHash)));

        assertThrows(com.projecthk5.inventoryservice.api.InventoryOperationServiceException.class,
                () -> service.apply(new InventoryOperationRequest("same-id", InventoryOperationType.INCREASE,
                        List.of(new InventoryOperationRequest.ItemQuantity("A", 1),
                                new InventoryOperationRequest.ItemQuantity("B", 2)))));

        assertEquals(10, a.getAvailableQuantity());
        assertEquals(10, b.getAvailableQuantity());
        verify(operations, never()).claimOperationId(anyString(), anyString(), anyString());
    }

    private static String hash(InventoryOperationType type, Map<String, Long> input) throws Exception {
        Map<String, Long> sorted = new TreeMap<>(input);
        StringBuilder canonical = new StringBuilder();
        appendLengthPrefixed(canonical, type.name());
        appendLengthPrefixed(canonical, Integer.toString(sorted.size()));
        sorted.forEach((sku, quantity) -> {
            appendLengthPrefixed(canonical, sku);
            appendLengthPrefixed(canonical, Long.toString(quantity));
        });
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void appendLengthPrefixed(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }
}
