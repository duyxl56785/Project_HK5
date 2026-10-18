package com.projecthk5.inventoryservice.model;

public class InsufficientReservationException extends RuntimeException {
    private final String sku;
    private final long reserved;
    private final long requested;

    public InsufficientReservationException(String sku, long reserved, long requested) {
        super("Insufficient reserved stock for SKU " + sku);
        this.sku = sku;
        this.reserved = reserved;
        this.requested = requested;
    }

    public String getSku() { return sku; }
    public long getReserved() { return reserved; }
    public long getRequested() { return requested; }
}
