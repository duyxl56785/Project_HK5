package com.projecthk5.inventoryservice.api;

import jakarta.validation.constraints.Min;

public record StockRequest(@Min(0) int availableQuantity) {}
