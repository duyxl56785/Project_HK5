package com.projecthk5.orderservice.ports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.service.InventoryOperationJournal;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Order->Inventory pair (X-Internal-Token, RESERVE/CONSUME/RELEASE only). Operation ids are
 * deterministic per order/store order, so a retry after an unknown outcome is replayed, not re-applied.
 */
@Component
public class InventoryHttpAdapter implements InventoryPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Inventory rolled these back. Any other 4xx may sit on a ledger row, so its outcome stays unknown. */
    private static final Set<String> NOTHING_MOVED = Set.of("INSUFFICIENT_STOCK", "INSUFFICIENT_RESERVATION",
            "INVENTORY_ITEM_NOT_FOUND", "QUANTITY_OVERFLOW", "INVALID_REQUEST", "INVALID_OPERATION_ID", "INVALID_SKU",
            "INVALID_QUANTITY", "DUPLICATE_SKU");
    private final RestClient inventory;
    private final String token;
    private final InventoryOperationJournal journal;

    @Autowired
    public InventoryHttpAdapter(@Value("${INVENTORY_INTERNAL_URL:http://localhost:8105}") String url,
                                @Value("${ORDER_INVENTORY_TOKEN:}") String token,
                                InventoryOperationJournal journal) {
        this.inventory = JwtResourceServer.internalClient(url);
        this.token = token;
        this.journal = journal;
    }

    /** Standalone construction remains useful for non-RESERVE calls; RESERVE has no unjournaled fallback. */
    public InventoryHttpAdapter(String url, String token) { this(url, token, null); }

    public static String reserveId(UUID orderId) { return "order-" + orderId + "-reserve"; }

    @Override
    public void reserve(UUID orderId, List<Reservation> items) {
        requireReservationContext();
        var intent = InventoryOperationIntent.prepare(orderId, reserveId(orderId),
                InventoryOperationIntent.Type.RESERVE, reservationLines(items));
        journal.prepare(intent); // REQUIRES_NEW T1 commits before dispatch can perform HTTP.
        dispatchReserve(intent.operationId(), intent);
    }

    @Override
    public void release(UUID orderId) {
        // Whole-order release needs the item list; callers use apply(...) with the stored lines.
        throw new UnsupportedOperationException("Use apply(operationId, RELEASE, lines)");
    }

    @Override
    public void apply(String operationId, String type, List<Reservation> items) {
        if ("RESERVE".equals(type)) {
            requireReservationContext();
            // An explicit recovery call may use only the already committed tuple.
            var saved = journal.load(operationId).intent();
            saved.requireSamePayload(InventoryOperationIntent.Type.RESERVE, reservationLines(items));
            dispatchReserve(operationId, saved);
            return;
        }
        if (token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory internal contract not configured");
        if (items.stream().anyMatch(i -> i.sku() == null || i.sku().isBlank()))
            throw new IllegalArgumentException("Inventory lines require a SKU");
        List<Map<String, Object>> lines = items.stream()
                .map(i -> Map.<String, Object>of("sku", i.sku(), "quantity", i.quantity())).toList();
        post(operationId, type, lines, false); // Existing Fulfillment/COD boundary is unchanged.
    }

    private void requireReservationContext() {
        if (journal == null || token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory reservation journal/pair not configured");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Inventory RESERVE cannot run inside an outer database transaction");
    }

    private static List<InventoryOperationIntent.Line> reservationLines(List<Reservation> items) {
        return items.stream().map(i -> new InventoryOperationIntent.Line(i.sku(), i.quantity())).toList();
    }

    private void dispatchReserve(String operationId, InventoryOperationIntent verifiedReservation) {
        var snapshot = journal.load(operationId); // Check fingerprint and reload exact immutable T1 facts.
        // Bind the Checkout-verified association to the exact immutable tuple actually sent.
        snapshot.intent().requireSamePayload(verifiedReservation.type(), verifiedReservation.lines());
        if (!snapshot.intent().checkoutAttemptId().equals(verifiedReservation.checkoutAttemptId()))
            throw new IllegalArgumentException("Inventory reservation attempt mismatch");
        if (snapshot.intent().type() != InventoryOperationIntent.Type.RESERVE)
            throw new IllegalArgumentException("Inventory journal operation is not RESERVE");
        if (snapshot.state() == InventoryOperationJournal.State.CONFIRMED) return;
        if (snapshot.state() == InventoryOperationJournal.State.REJECTED)
            throw new Rejected(snapshot.errorCode(), "Inventory reservation was definitively rejected");
        if (snapshot.state() == InventoryOperationJournal.State.IN_FLIGHT)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory reservation already in flight");
        if (snapshot.state() == InventoryOperationJournal.State.UNKNOWN)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory reservation requires owner reconciliation");
        // A stale PENDING snapshot must not claim a row another sender has already made UNKNOWN.
        Integer generation = journal.claimPendingForSend(operationId);
        if (generation == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Concurrent Inventory reservation sender");
        List<Map<String, Object>> lines = snapshot.intent().lines().stream()
                .map(i -> Map.<String, Object>of("sku", i.sku(), "quantity", i.quantity())).toList();
        try {
            JsonNode reply = post(operationId, "RESERVE", lines, true);
            if (!matchingReservationReply(snapshot.intent(), reply))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory reservation response mismatch");
            journal.confirm(operationId, generation); // T2, fenced by this send generation.
        } catch (Rejected definitive) {
            try { journal.reject(operationId, generation, definitive.code()); }
            catch (RuntimeException outcomeWriteFailed) {
                markUnknown(operationId, generation, outcomeWriteFailed);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Inventory rejection could not be durably recorded", outcomeWriteFailed);
            }
            throw definitive;
        } catch (RuntimeException uncertain) {
            markUnknown(operationId, generation, uncertain);
            throw uncertain;
        }
    }

    private void markUnknown(String operationId, int generation, RuntimeException original) {
        try { journal.unknown(operationId, generation, "INVENTORY_OUTCOME_UNKNOWN"); }
        catch (RuntimeException outcomeWriteFailed) {
            // A failed T2 write never proves no commit; preserve T1/IN_FLIGHT for reconciliation.
            original.addSuppressed(outcomeWriteFailed);
        }
    }

    private static boolean matchingReservationReply(InventoryOperationIntent intent, JsonNode reply) {
        if (reply == null || !reply.path("operationId").isTextual()
                || !intent.operationId().equals(reply.path("operationId").asText())
                || !"RESERVE".equals(reply.path("type").asText())
                || !reply.path("replayed").isBoolean() || !reply.path("items").isArray()) return false;
        Set<String> skus = new java.util.HashSet<>();
        for (JsonNode item : reply.path("items")) {
            if (!item.path("sku").isTextual() || !skus.add(item.path("sku").asText())) return false;
        }
        return skus.equals(intent.lines().stream().map(InventoryOperationIntent.Line::sku)
                .collect(java.util.stream.Collectors.toSet()));
    }

    private JsonNode post(String operationId, String type, List<Map<String, Object>> lines, boolean verifyReservation) {
        try {
            var response = inventory.post().uri("/api/inventory/operations").header(InternalTokens.HEADER, token)
                    .body(Map.of("operationId", operationId, "type", type, "items", lines)).retrieve();
            if (verifyReservation) return response.body(JsonNode.class);
            response.toBodilessEntity();
            return null;
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest refused) {
            String code = code(refused);
            if (!NOTHING_MOVED.contains(code))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory outcome unknown: " + code, refused);
            String sku = sku(refused);
            throw new Rejected(code, "INSUFFICIENT_STOCK".equals(code)
                    ? "Not enough stock for " + (sku == null ? "an item" : sku) : "Inventory rejected " + type + ": " + code, sku);
        } catch (RestClientException unknown) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable", unknown);
        }
    }

    private static String code(HttpClientErrorException e) {
        try {
            JsonNode body = JSON.readTree(e.getResponseBodyAsString());
            return body.path("code").asText("INVENTORY_REJECTED");
        } catch (Exception unreadable) {
            return "INVENTORY_REJECTED";
        }
    }

    private static String sku(HttpClientErrorException e) {
        try {
            JsonNode value = JSON.readTree(e.getResponseBodyAsString()).get("sku");
            return value != null && value.isTextual() ? value.textValue() : null;
        } catch (Exception unreadable) {
            return null;
        }
    }
}
