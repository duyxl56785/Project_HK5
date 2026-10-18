package com.projecthk5.inventoryservice.model;

public class InsufficientStockException extends RuntimeException {
    private final String sku;
    private final long available;
    private final long requested;

    public InsufficientStockException(String sku, long available, long requested) {
        super("Insufficient available stock for SKU " + sku);
        this.sku = sku;
        this.available = available;
        this.requested = requested;
    }

    public String getSku() { return sku; }
    public long getAvailable() { return available; }
    public long getRequested() { return requested; }
}
