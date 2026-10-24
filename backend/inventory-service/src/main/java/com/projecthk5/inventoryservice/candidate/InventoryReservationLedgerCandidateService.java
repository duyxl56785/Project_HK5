package com.projecthk5.inventoryservice.candidate;

import com.projecthk5.inventoryservice.api.InventoryOperationType;
import com.projecthk5.inventoryservice.model.InventoryItem;
import com.projecthk5.inventoryservice.model.InventoryOperation;
import com.projecthk5.inventoryservice.repository.InventoryItemRepository;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-side reservation ledger candidate. It is not wired to an HTTP route; pair auth and the
 * final request/error contract remain owner-gated. Every stock and owner-ledger change is atomic.
 */
@Service
public class InventoryReservationLedgerCandidateService {
    private final JdbcTemplate jdbc;
    private final InventoryItemRepository items;
    private final InventoryOperationRepository operations;

    public InventoryReservationLedgerCandidateService(
            JdbcTemplate jdbc,
            InventoryItemRepository items,
            InventoryOperationRepository operations) {
        this.jdbc = jdbc;
        this.items = items;
        this.operations = operations;
    }

    @Transactional
    public ReservationOperationCandidateResponse apply(ReservationOperationCandidateRequest request) {
        CanonicalOperation operation = canonicalize(request);
        String fingerprint = operationFingerprint(request, operation.quantities());

        var prior = operations.findById(request.operationId());
        if (prior.isPresent()) return replayOrReject(prior.get(), request, operation, fingerprint);

        ReservationOwner owner = request.type() == InventoryOperationType.RESERVE
                ? createOrLockReservation(request, operation)
                : lockExistingReservation(request);
        verifyOwner(owner, request);

        // A same-ID waiter resumes after the winner commits while holding the same reservation lock.
        prior = operations.findById(request.operationId());
        if (prior.isPresent()) return replayOrReject(prior.get(), request, operation, fingerprint);

        // A second RESERVE key must not initialize the same reservation twice.
        if (request.type() == InventoryOperationType.RESERVE && reserveAlreadyExists(request.reservationId())) {
            throw conflict("RESERVATION_ALREADY_INITIALIZED", "reservationId already has a committed RESERVE operation");
        }

        List<InventoryItem> locked = items.findAllBySkuForUpdate(operation.quantities().keySet());
        if (locked.size() != operation.quantities().size()) {
            List<String> missing = operation.quantities().keySet().stream()
                    .filter(sku -> locked.stream().noneMatch(item -> item.getSku().equals(sku)))
                    .toList();
            throw conflict("INVENTORY_ITEM_NOT_FOUND", "Inventory items not found: " + String.join(", ", missing));
        }

        // Re-read after the reservation lock so a same-ID waiter sees the committed winner.
        prior = operations.findById(request.operationId());
        if (prior.isPresent()) return replayOrReject(prior.get(), request, operation, fingerprint);

        int claimed = jdbc.update("""
                INSERT INTO inventory_operation
                    (operation_id, operation_type, request_hash, reservation_id, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (operation_id) DO NOTHING
                """, request.operationId(), request.type().name(), fingerprint, request.reservationId());
        if (claimed == 0) {
            var concurrent = operations.findById(request.operationId());
            if (concurrent.isEmpty()) {
                throw conflict("IDEMPOTENCY_CLAIM_CONFLICT", "operationId could not be claimed; retry the exact request");
            }
            return replayOrReject(concurrent.get(), request, operation, fingerprint);
        }

        Map<String, InventoryItem> bySku = locked.stream()
                .collect(Collectors.toMap(InventoryItem::getSku, item -> item));
        if (request.type() == InventoryOperationType.RESERVE) {
            applyReserve(request, operation.quantities(), bySku);
        } else {
            applyHeldOperation(request, operation.quantities(), bySku);
        }
        persistOperationLines(request.operationId(), operation.quantities());
        items.flush();

        return response(request, fingerprint, false, operation.lines());
    }

    /**
     * Candidate authoritative lookup. A missing row deliberately maps to UNKNOWN, never to proof
     * that the operation did not commit. This method is not exposed over HTTP.
     */
    @Transactional(readOnly = true)
    public ReservationOperationLookupCandidate lookup(String operationId, String reservationId) {
        requireIdentifier(operationId, "operationId");
        requireIdentifier(reservationId, "reservationId");
        List<OperationSnapshot> found = jdbc.query("""
                SELECT operation_type, request_hash, created_at
                FROM inventory_operation
                WHERE operation_id = ? AND reservation_id = ?
                """, (rs, row) -> new OperationSnapshot(
                rs.getString("operation_type"), rs.getString("request_hash"),
                rs.getTimestamp("created_at").toInstant()), operationId, reservationId);
        if (found.isEmpty()) {
            var anyOwner = operations.findById(operationId);
            if (anyOwner.isPresent()) {
                String existingReservationId = loadOperationReservationId(operationId);
                if (existingReservationId != null && !reservationId.equals(existingReservationId)) {
                    throw conflict("RESERVATION_OWNER_MISMATCH", "operationId belongs to another reservation");
                }
            }
            return new ReservationOperationLookupCandidate(operationId, reservationId, "UNKNOWN",
                    null, null, null, List.of());
        }
        OperationSnapshot snapshot = found.get(0);
        return new ReservationOperationLookupCandidate(operationId, reservationId, "COMMITTED",
                snapshot.operationType(), snapshot.fingerprint(), snapshot.committedAt(),
                loadOperationLines(operationId));
    }

    private ReservationOwner createOrLockReservation(
            ReservationOperationCandidateRequest request, CanonicalOperation operation) {
        String initialFingerprint = reservationFingerprint(request, operation.quantities());
        jdbc.update("""
                INSERT INTO inventory_reservation
                    (reservation_id, store_id, seller_id, seller_order_id, initial_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, request.reservationId(), request.storeId(), request.sellerId(),
                request.sellerOrderId(), initialFingerprint);
        ReservationOwner owner = selectReservationForUpdate(request.reservationId());
        if (owner == null) {
            Boolean ownerAlreadyBound = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM inventory_reservation
                                  WHERE store_id = ? AND seller_order_id = ?)
                    """, Boolean.class, request.storeId(), request.sellerOrderId());
            if (Boolean.TRUE.equals(ownerAlreadyBound)) {
                throw conflict("RESERVATION_ALREADY_EXISTS", "Store/SellerOrder already has another reservationId");
            }
            throw conflict("RESERVATION_NOT_FOUND", "reservation row could not be created");
        }
        verifyOwner(owner, request);
        if (!owner.initialFingerprint().equals(initialFingerprint)) {
            throw conflict("RESERVATION_FINGERPRINT_MISMATCH", "reservationId is already bound to different initial lines");
        }
        return owner;
    }

    private ReservationOwner lockExistingReservation(ReservationOperationCandidateRequest request) {
        ReservationOwner owner = selectReservationForUpdate(request.reservationId());
        if (owner == null) throw conflict("RESERVATION_NOT_FOUND", "reservationId has no committed RESERVE");
        return owner;
    }

    private ReservationOwner selectReservationForUpdate(String reservationId) {
        List<ReservationOwner> rows = jdbc.query("""
                SELECT reservation_id, store_id, seller_id, seller_order_id, initial_fingerprint
                FROM inventory_reservation WHERE reservation_id = ? FOR UPDATE
                """, (rs, row) -> new ReservationOwner(rs.getString("reservation_id"),
                rs.getString("store_id"), rs.getString("seller_id"), rs.getString("seller_order_id"),
                rs.getString("initial_fingerprint")), reservationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void verifyOwner(ReservationOwner owner, ReservationOperationCandidateRequest request) {
        if (!owner.reservationId().equals(request.reservationId())
                || !owner.storeId().equals(request.storeId())
                || !owner.sellerId().equals(request.sellerId())
                || !owner.sellerOrderId().equals(request.sellerOrderId())) {
            throw conflict("RESERVATION_OWNER_MISMATCH", "reservationId is bound to a different Store/SellerOrder owner");
        }
    }

    private boolean reserveAlreadyExists(String reservationId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM inventory_operation
                              WHERE reservation_id = ? AND operation_type = 'RESERVE')
                """, Boolean.class, reservationId);
        return Boolean.TRUE.equals(exists);
    }

    private String loadOperationReservationId(String operationId) {
        List<String> rows = jdbc.query("""
                SELECT reservation_id FROM inventory_operation WHERE operation_id = ?
                """, (rs, row) -> rs.getString("reservation_id"), operationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void applyReserve(ReservationOperationCandidateRequest request,
                              Map<String, Long> quantities, Map<String, InventoryItem> bySku) {
        quantities.forEach((sku, quantity) -> {
            bySku.get(sku).reserve(quantity);
            jdbc.update("""
                    INSERT INTO inventory_reservation_line
                        (reservation_id, sku, initial_quantity, consumed_quantity, released_quantity, held_quantity)
                    VALUES (?, ?, ?, 0, 0, ?)
                    """, request.reservationId(), sku, quantity, quantity);
        });
    }

    private void applyHeldOperation(ReservationOperationCandidateRequest request,
                                    Map<String, Long> quantities, Map<String, InventoryItem> bySku) {
        for (Map.Entry<String, Long> entry : quantities.entrySet()) {
            String sku = entry.getKey();
            long quantity = entry.getValue();
            List<Long> heldRows = jdbc.query("""
                    SELECT held_quantity FROM inventory_reservation_line
                    WHERE reservation_id = ? AND sku = ? FOR UPDATE
                    """, (rs, row) -> rs.getLong("held_quantity"), request.reservationId(), sku);
            if (heldRows.isEmpty()) {
                throw conflict("RESERVATION_LINE_NOT_FOUND", "SKU is not part of this reservation: " + sku);
            }
            long held = heldRows.get(0);
            if (held < quantity) {
                throw conflict("INSUFFICIENT_RESERVATION", "reservation has insufficient held quantity for SKU " + sku);
            }

            if (request.type() == InventoryOperationType.CONSUME) {
                bySku.get(sku).consumeReserved(quantity);
                jdbc.update("""
                        UPDATE inventory_reservation_line
                        SET consumed_quantity = consumed_quantity + ?, held_quantity = held_quantity - ?
                        WHERE reservation_id = ? AND sku = ?
                        """, quantity, quantity, request.reservationId(), sku);
            } else {
                bySku.get(sku).releaseReserved(quantity);
                jdbc.update("""
                        UPDATE inventory_reservation_line
                        SET released_quantity = released_quantity + ?, held_quantity = held_quantity - ?
                        WHERE reservation_id = ? AND sku = ?
                        """, quantity, quantity, request.reservationId(), sku);
            }
        }
    }

    private void persistOperationLines(String operationId, Map<String, Long> quantities) {
        quantities.forEach((sku, quantity) -> jdbc.update("""
                INSERT INTO inventory_reservation_operation_line (operation_id, sku, quantity)
                VALUES (?, ?, ?)
                """, operationId, sku, quantity));
    }

    private ReservationOperationCandidateResponse replayOrReject(
            InventoryOperation prior,
            ReservationOperationCandidateRequest request,
            CanonicalOperation operation,
            String fingerprint) {
        if (!request.reservationId().equals(loadOperationReservationId(request.operationId()))
                || !request.type().name().equals(prior.getOperationType())
                || !fingerprint.equals(prior.getRequestHash())) {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "operationId was already used with a different owner or request");
        }
        return response(request, fingerprint, true, loadOperationLines(request.operationId()));
    }

    private List<ReservationOperationCandidateRequest.Line> loadOperationLines(String operationId) {
        return jdbc.query("""
                SELECT sku, quantity FROM inventory_reservation_operation_line
                WHERE operation_id = ? ORDER BY sku
                """, (rs, row) -> new ReservationOperationCandidateRequest.Line(
                rs.getString("sku"), rs.getLong("quantity")), operationId);
    }

    private static ReservationOperationCandidateResponse response(
            ReservationOperationCandidateRequest request,
            String fingerprint,
            boolean replayed,
            List<ReservationOperationCandidateRequest.Line> lines) {
        return new ReservationOperationCandidateResponse(request.operationId(), request.reservationId(),
                request.type(), "COMMITTED", replayed, fingerprint, lines);
    }

    private static CanonicalOperation canonicalize(ReservationOperationCandidateRequest request) {
        if (request == null) throw conflict("INVALID_RESERVATION_REQUEST", "request is required");
        requireIdentifier(request.operationId(), "operationId");
        requireIdentifier(request.reservationId(), "reservationId");
        requireIdentifier(request.storeId(), "storeId");
        requireIdentifier(request.sellerId(), "sellerId");
        requireIdentifier(request.sellerOrderId(), "sellerOrderId");
        if (request.type() != InventoryOperationType.RESERVE
                && request.type() != InventoryOperationType.CONSUME
                && request.type() != InventoryOperationType.RELEASE) {
            throw conflict("INVALID_RESERVATION_OPERATION", "reservation candidate supports only RESERVE, CONSUME, RELEASE");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw conflict("INVALID_RESERVATION_LINES", "at least one SKU line is required");
        }
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (ReservationOperationCandidateRequest.Line line : request.items()) {
            if (line == null || line.sku() == null) {
                throw conflict("INVALID_RESERVATION_LINES", "SKU line is required");
            }
            String sku = line.sku();
            if (sku.isEmpty() || sku.length() > 128 || hasBoundaryWhitespace(sku) || line.quantity() <= 0) {
                throw conflict("INVALID_RESERVATION_LINES", "SKU must be 1..128 canonical characters and quantity must be positive");
            }
            if (sorted.putIfAbsent(sku, line.quantity()) != null) {
                throw conflict("DUPLICATE_SKU", "Each SKU may appear only once per operation");
            }
        }
        List<ReservationOperationCandidateRequest.Line> lines = sorted.entrySet().stream()
                .map(entry -> new ReservationOperationCandidateRequest.Line(entry.getKey(), entry.getValue()))
                .toList();
        return new CanonicalOperation(java.util.Collections.unmodifiableMap(new TreeMap<>(sorted)), lines);
    }

    private static boolean hasBoundaryWhitespace(String value) {
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        return isWhitespace(first) || isWhitespace(last);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim())) {
            throw conflict("INVALID_RESERVATION_REQUEST", field + " must be 1..128 non-padded characters");
        }
    }

    private static String operationFingerprint(ReservationOperationCandidateRequest request,
                                               Map<String, Long> quantities) {
        StringBuilder value = new StringBuilder();
        append(value, "reservation-operation-v1");
        append(value, request.reservationId());
        append(value, request.storeId());
        append(value, request.sellerId());
        append(value, request.sellerOrderId());
        append(value, request.type().name());
        appendLines(value, quantities);
        return sha256(value.toString());
    }

    private static String reservationFingerprint(ReservationOperationCandidateRequest request,
                                                 Map<String, Long> quantities) {
        StringBuilder value = new StringBuilder();
        append(value, "reservation-owner-v1");
        append(value, request.reservationId());
        append(value, request.storeId());
        append(value, request.sellerId());
        append(value, request.sellerOrderId());
        appendLines(value, quantities);
        return sha256(value.toString());
    }

    private static void appendLines(StringBuilder value, Map<String, Long> quantities) {
        append(value, Integer.toString(quantities.size()));
        quantities.forEach((sku, quantity) -> {
            append(value, sku);
            append(value, Long.toString(quantity));
        });
    }

    private static void append(StringBuilder target, String field) {
        target.append(field.length()).append(':').append(field);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static ReservationCandidateException conflict(String code, String message) {
        return new ReservationCandidateException(code, message);
    }

    private record CanonicalOperation(
            Map<String, Long> quantities,
            List<ReservationOperationCandidateRequest.Line> lines) {}

    private record ReservationOwner(
            String reservationId,
            String storeId,
            String sellerId,
            String sellerOrderId,
            String initialFingerprint) {}

    private record OperationSnapshot(String operationType, String fingerprint, java.time.Instant committedAt) {}
}
