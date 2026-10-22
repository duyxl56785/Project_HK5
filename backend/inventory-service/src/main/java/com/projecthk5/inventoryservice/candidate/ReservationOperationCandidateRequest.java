package com.projecthk5.inventoryservice.candidate;

import com.projecthk5.inventoryservice.api.InventoryOperationType;
import java.util.List;

/** Java-only draft input. It is deliberately not bound to an HTTP controller. */
public record ReservationOperationCandidateRequest(
        String operationId,
        String reservationId,
        String storeId,
        String sellerId,
        String sellerOrderId,
        InventoryOperationType type,
        List<Line> items) {

    public ReservationOperationCandidateRequest {
        items = items == null ? null : List.copyOf(items);
    }

    public record Line(String sku, long quantity) {}
}
