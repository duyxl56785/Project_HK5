package com.projecthk5.inventoryservice.api;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(String sku) {
        super("Inventory item not found for SKU " + sku);
    }
}
