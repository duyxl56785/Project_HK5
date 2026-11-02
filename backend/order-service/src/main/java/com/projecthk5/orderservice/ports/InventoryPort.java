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
        private final String sku;
        public Rejected(String code, String message) { this(code, message, null); }
        public Rejected(String code, String message, String sku) { super(message); this.code = code; this.sku = sku; }
        public String code() { return code; }
        /** The SKU Inventory named, when it named one. Only Catalog-verified Order lines may turn it into a product. */
        public String sku() { return sku; }
    }
}
