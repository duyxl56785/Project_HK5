package com.projecthk5.inventoryservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InitializeInventoryRequest(
        @NotBlank @Size(max = 128) String sku,
        @PositiveOrZero long availableQuantity
) {}
