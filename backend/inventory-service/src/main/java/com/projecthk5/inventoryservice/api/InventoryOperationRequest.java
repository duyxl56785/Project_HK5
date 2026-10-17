package com.projecthk5.inventoryservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record InventoryOperationRequest(
        @NotBlank String operationId,
        @NotNull InventoryOperationType type,
        @NotEmpty List<@Valid ItemQuantity> items
) {
    public record ItemQuantity(@NotBlank String sku, @Positive long quantity) {}
}
