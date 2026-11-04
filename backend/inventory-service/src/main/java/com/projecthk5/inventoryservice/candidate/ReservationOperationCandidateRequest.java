package com.projecthk5.inventoryservice.candidate;

import com.projecthk5.inventoryservice.api.InventoryOperationType;
import java.util.ArrayList;
import java.util.Collections;
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
        // Preserve malformed null entries for the service boundary to reject
        // consistently as INVALID_RESERVATION_LINES. List.copyOf would throw
        // before apply() can prove there was no stock or ledger side effect.
        items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }

    public record Line(String sku, long quantity) {}
}
