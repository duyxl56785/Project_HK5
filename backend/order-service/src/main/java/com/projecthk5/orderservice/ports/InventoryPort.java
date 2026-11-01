package com.projecthk5.orderservice.ports;

import java.util.List;
import java.util.UUID;

public interface InventoryPort {
    void reserve(UUID orderId, List<Reservation> items);
    void release(UUID orderId);
    /** Idempotent by operationId: Inventory replays a repeated id without moving stock twice. */
    default void apply(String operationId, String type, List<Reservation> items) {
        throw new UnsupportedOperationException("Inventory operation " + type + " not supported");
    }
    record Reservation(String productId, int quantity, String sku) {
        public Reservation(String productId, int quantity) { this(productId, quantity, null); }
    }

    /** Inventory answered with a definitive non-commit (e.g. insufficient stock): nothing moved. */
    final class Rejected extends IllegalStateException {
        private final String code;
        public Rejected(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
