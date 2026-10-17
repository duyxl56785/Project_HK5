package com.projecthk5.inventoryservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryOperationRequest(
        @NotBlank @Size(max = 128) String operationId,
        @NotNull InventoryOperationType type,
        @NotEmpty List<@Valid ItemQuantity> items
) {
    public record ItemQuantity(@NotBlank @Size(max = 128) String sku, @Positive long quantity) {}
}
