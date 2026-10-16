package com.projecthk5.inventoryservice.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InventoryOperationRequest(
        @NotBlank String operationId,
        @NotBlank String sku,
        @Min(1) int quantity
) {}
