package com.projecthk5.orderservice.ports;

import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import java.util.List;
import java.util.Objects;

/**
 * The versioned body Order would send to TV4 Inventory. Constructed only from a
 * rehydrated durable journal intent. NO positive HTTP authorization exists here.
 */
public final class InventoryOperationWire {
    public static final String METHOD = "POST";
    public static final String PATH = "/api/inventory/operations";
    public static final String PAIR_HEADER = "X-Internal-Token";
    private InventoryOperationWire() {}

    public record Item(String sku, long quantity) {}
    public record Request(String operationId, String type, List<Item> items) {
        public Request {
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(type);
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("Inventory wire items required");
        }
    }
    public static Request from(InventoryOperationIntent intent) {
        Objects.requireNonNull(intent);
        return new Request(intent.operationId(), intent.type().name(),
                intent.lines().stream().map(l -> new Item(l.sku(), l.quantity())).toList());
    }
}
