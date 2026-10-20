package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.domain.InventoryOperationIntent.Line;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-owned T1/T2 Inventory journal, deliberately separate from CheckoutService
 * until Product SKU, actor/system authorization and lifecycle contracts are signed.
 * All write methods use REQUIRES_NEW so T1 is committed before a transport call,
 * and T2 is committed only after a matching verified provider response.
 */
@Service
public class InventoryOperationJournal {
    public enum State { PENDING, IN_FLIGHT, UNKNOWN, CONFIRMED, REJECTED }
    public record Snapshot(InventoryOperationIntent intent, State state, int sends, String errorCode) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public InventoryOperationJournal(JdbcTemplate db, ObjectMapper json) {
        this.db = db; this.json = json;
    }

    /** T1: save exact step ID, canonical sorted SKU lines and fingerprint, then commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepare(InventoryOperationIntent intent) {
        Objects.requireNonNull(intent);
        String body = serialize(intent.lines());
        int added = db.update("insert into inventory_operation_journal "
                + "(operation_id,checkout_attempt_id,operation_type,canonical_lines,payload_fingerprint,state) "
                + "values (?,?,?,cast(? as jsonb),?,'PENDING') on conflict (operation_id) do nothing",
                intent.operationId(), intent.checkoutAttemptId(), intent.type().name(), body, intent.fingerprint());
        if (added == 0) {
            InventoryOperationIntent stored = load(intent.operationId()).intent();
            if (!stored.checkoutAttemptId().equals(intent.checkoutAttemptId())
                    || !stored.type().equals(intent.type())
                    || !stored.fingerprint().equals(intent.fingerprint())
                    || !stored.lines().equals(intent.lines()))
                throw new IllegalArgumentException("Inventory operationId was reused with a different intent");
        }
    }

    /** Reload T1 facts from DB, never re-mint an ID or trust request/HTTP caller items. */
    @Transactional(readOnly = true)
    public Snapshot load(String operationId) {
        return db.query("select checkout_attempt_id,operation_id,operation_type,"
                + "canonical_lines,payload_fingerprint,state,send_attempts,last_error_code "
                + "from inventory_operation_journal where operation_id = ?",
                (rs, row) -> {
                    InventoryOperationIntent intent = InventoryOperationIntent.fromJournal(
                            rs.getObject("checkout_attempt_id", java.util.UUID.class),
                            rs.getString("operation_id"),
                            InventoryOperationIntent.Type.valueOf(rs.getString("operation_type")),
                            deserialize(rs.getString("canonical_lines")),
                            rs.getString("payload_fingerprint"));
                    return new Snapshot(intent, State.valueOf(rs.getString("state")),
                            rs.getInt("send_attempts"), rs.getString("last_error_code"));
                }, operationId).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Inventory operationId"));
    }

    /** Atomic claim: concurrent senders cannot start a second request for the same step. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForSend(String operationId) {
        return db.update("update inventory_operation_journal set state='IN_FLIGHT',"
                + "send_attempts=send_attempts+1,updated_at=now() "
                + "where operation_id=? and state in ('PENDING','UNKNOWN')", operationId) == 1;
    }

    /** T2: after validating provider ID, operation type and exact SKU-set. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(String operationId) {
        transitionFromInFlight(operationId, State.CONFIRMED, null);
    }

    /** Only a positive proof of an atomic rejected/no-commit response is definitive. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(String operationId, String code) {
        transitionFromInFlight(operationId, State.REJECTED, code);
    }

    /** Timeout/ambiguous response, including invalid success echo: no blind RELEASE. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unknown(String operationId, String code) {
        transitionFromInFlight(operationId, State.UNKNOWN, code);
    }

    /** Disabled adapter guarantees that NO request was sent; keep T1 for later. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notSent(String operationId, String code) {
        transitionFromInFlight(operationId, State.PENDING, code);
    }

    /** Crash after T1 or during T2: flag stale IN_FLIGHT for human reconciliation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int flagStaleInFlight(Duration age) {
        if (age == null || age.compareTo(Duration.ofMinutes(2)) < 0)
            throw new IllegalArgumentException("Unsafe Inventory reconciliation interval");
        return db.update("update inventory_operation_journal "
                + "set state='UNKNOWN',last_error_code='STALE_IN_FLIGHT',updated_at=now() "
                + "where state='IN_FLIGHT' and updated_at < now() - (? * interval '1 second')",
                age.getSeconds());
    }

    private void transitionFromInFlight(String id, State next, String code) {
        if (code != null && (code.isBlank() || code.length() > 96))
            throw new IllegalArgumentException("Invalid bounded Inventory error code");
        int changed = db.update("update inventory_operation_journal "
                + "set state=?,last_error_code=?,updated_at=now() "
                + "where operation_id=? and state='IN_FLIGHT'", next.name(), code, id);
        if (changed != 1) throw new IllegalStateException("Stale or conflicting Inventory journal transition");
    }

    private String serialize(List<Line> lines) {
        try { return json.writeValueAsString(lines); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize journal intent", e); }
    }
    private List<Line> deserialize(String body) {
        try { return json.readValue(body, new TypeReference<List<Line>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Corrupt Inventory journal payload", e); }
    }
}
