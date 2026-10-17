package com.projecthk5.inventoryservice.api;

import java.time.OffsetDateTime;

public record InventoryBalanceResponse(
        String sku,
        long availableQuantity,
        long reservedQuantity,
        OffsetDateTime updatedAt
) {}
