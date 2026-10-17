package com.projecthk5.orderservice.ports;

import java.util.List;
import java.util.UUID;

public interface InventoryPort {
    void reserve(UUID orderId, List<Reservation> items);
    void release(UUID orderId);
    record Reservation(String productId, int quantity) {}
}
