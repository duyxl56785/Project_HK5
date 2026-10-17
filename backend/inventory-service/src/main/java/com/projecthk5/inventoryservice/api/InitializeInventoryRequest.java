package com.projecthk5.inventoryservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record InitializeInventoryRequest(
        @NotBlank String sku,
        @PositiveOrZero long availableQuantity
) {}
