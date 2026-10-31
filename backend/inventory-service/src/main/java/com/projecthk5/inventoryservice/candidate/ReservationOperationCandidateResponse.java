package com.projecthk5.inventoryservice.candidate;

import com.projecthk5.inventoryservice.api.InventoryOperationType;
import java.util.List;

/** Candidate service result; not a shared REST response contract. */
public record ReservationOperationCandidateResponse(
        String operationId,
        String reservationId,
        InventoryOperationType type,
        String status,
        boolean replayed,
        String requestFingerprint,
        List<ReservationOperationCandidateRequest.Line> items) {

    public ReservationOperationCandidateResponse {
        items = List.copyOf(items);
    }
}
