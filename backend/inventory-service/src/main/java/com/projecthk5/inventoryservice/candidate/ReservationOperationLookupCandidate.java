package com.projecthk5.inventoryservice.candidate;

import java.time.Instant;
import java.util.List;

/** Read-only Java candidate used to validate recovery semantics before a route is approved. */
public record ReservationOperationLookupCandidate(
        String operationId,
        String reservationId,
        String status,
        String operationType,
        String requestFingerprint,
        Instant committedAt,
        List<ReservationOperationCandidateRequest.Line> items) {

    public ReservationOperationLookupCandidate {
        items = List.copyOf(items);
    }
}
