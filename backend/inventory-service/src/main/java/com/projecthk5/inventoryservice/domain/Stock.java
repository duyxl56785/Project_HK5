package com.projecthk5.inventoryservice.domain;

public record Stock(String sku, int availableQuantity, int reservedQuantity) {
    public Stock {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku must not be blank");
        if (availableQuantity < 0 || reservedQuantity < 0) throw new IllegalArgumentException("stock quantities must not be negative");
    }
}
