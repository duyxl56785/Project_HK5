package com.projecthk5.inventoryservice.api;

import java.util.List;

public record InventoryOperationResponse(
        String operationId,
        InventoryOperationType type,
        boolean replayed,
        List<InventoryBalanceResponse> items
) {}
