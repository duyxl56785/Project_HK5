package com.projecthk5.inventoryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_item")
public class InventoryItem {
    @Id
    @Column(name = "sku", length = 128, nullable = false)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected InventoryItem() {}

    public InventoryItem(String sku, long availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getSku() { return sku; }
    public long getAvailableQuantity() { return availableQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void increaseAvailable(long quantity) {
        requirePositive(quantity);
        availableQuantity = Math.addExact(availableQuantity, quantity);
        touch();
    }

    public void decreaseAvailable(long quantity) {
        requirePositive(quantity);
        if (availableQuantity < quantity) {
            throw new InsufficientStockException(sku, availableQuantity, quantity);
        }
        availableQuantity -= quantity;
        touch();
    }

    public void reserve(long quantity) {
        requirePositive(quantity);
        decreaseAvailable(quantity);
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
        touch();
    }

    public void consumeReserved(long quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new InsufficientReservationException(sku, reservedQuantity, quantity);
        }
        reservedQuantity -= quantity;
        touch();
    }

    public void releaseReserved(long quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new InsufficientReservationException(sku, reservedQuantity, quantity);
        }
        reservedQuantity -= quantity;
        availableQuantity = Math.addExact(availableQuantity, quantity);
        touch();
    }

    private void touch() { updatedAt = OffsetDateTime.now(); }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than zero");
    }
}
