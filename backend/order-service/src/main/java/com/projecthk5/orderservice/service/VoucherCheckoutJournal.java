package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import com.projecthk5.orderservice.domain.VoucherSnapshot;
import com.projecthk5.orderservice.ports.InventoryHttpAdapter;
import com.projecthk5.orderservice.ports.VoucherPort.Intent;
import com.projecthk5.orderservice.ports.VoucherPort.ReleaseReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Order DB state only. All HTTP is outside these short transactions. */
@Repository
public class VoucherCheckoutJournal {
    public enum Phase { REDEEM, INVENTORY, PLACED, ABORTED }
    public enum Progress { PENDING, REDEEMING, RESERVED, COMMITTED, RELEASED }
    public record Lease(UUID attemptId, UUID token) { }
    public record Attempt(UUID id, String customer, String key, long revision, Phase phase,
                          boolean cartDone, String failure) { }
    public record Step(Intent intent, Progress progress, ReleaseReason closure) { }

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public VoucherCheckoutJournal(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Shares the committed draft transaction, including its attempt/fingerprint validation. */
    @Transactional
    public void register(CheckoutDraft draft) {
        if (draft.version() != 2) return;
        int inserted = db.update("""
                INSERT INTO checkout_voucher_sagas(attempt_id, claimed_revision, phase)
                VALUES (?, ?, 'REDEEM') ON CONFLICT (attempt_id) DO NOTHING
                """, draft.orderId(), draft.claimedCartRevision());
        if (inserted == 0) return;
        for (CheckoutDraft.Store child : draft.sellerOrders()) {
            if (child.voucher() == null) continue;
            db.update("""
                    INSERT INTO checkout_voucher_intents(seller_order_id, attempt_id, store_id, quote, state)
                    VALUES (?, ?, ?, CAST(? AS jsonb), 'PENDING')
                    """, child.id(), draft.orderId(), UUID.fromString(child.storeId()), encode(child.voucher()));
        }
    }

    public Optional<Attempt> find(UUID id) {
        return db.query("""
                SELECT a.id, a.customer_id, a.idempotency_key, v.claimed_revision, v.phase, v.cart_done, v.failure_code
                FROM checkout_voucher_sagas v JOIN checkout_attempts a ON a.id=v.attempt_id WHERE a.id=?
                """, (rs, n) -> new Attempt(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getLong(4), Phase.valueOf(rs.getString(5)), rs.getBoolean(6), rs.getString(7)), id)
                .stream().findFirst();
    }

    public List<UUID> due() {
        return db.query("""
                SELECT v.attempt_id FROM checkout_voucher_sagas v
                WHERE v.lease_until <= now() AND v.retry_at <= now()
                  AND (NOT v.cart_done OR v.phase IN ('REDEEM','INVENTORY') OR EXISTS (
                    SELECT 1 FROM checkout_voucher_intents i
                    LEFT JOIN seller_orders s ON s.id=i.seller_order_id
                    WHERE i.attempt_id=v.attempt_id AND i.state<>'RELEASED'
                      AND (v.phase='ABORTED' OR i.state<>'COMMITTED' OR s.status IN ('CANCELLED','REJECTED'))))
                ORDER BY v.retry_at, v.attempt_id LIMIT 20
                """, (rs, n) -> rs.getObject(1, UUID.class));
    }

    public Optional<Lease> acquire(UUID id) {
        UUID token = UUID.randomUUID();
        int updated = db.update("""
                UPDATE checkout_voucher_sagas SET lease_token=?, lease_until=now()+interval '2 minutes'
                WHERE attempt_id=? AND lease_until<=now()
                """, token, id);
        return updated == 1 ? Optional.of(new Lease(id, token)) : Optional.empty();
    }

    public void renew(Lease lease) {
        requireOne(db.update("""
                UPDATE checkout_voucher_sagas SET lease_until=now()+interval '2 minutes'
                WHERE attempt_id=? AND lease_token=? AND lease_until>now()
                """, lease.attemptId(), lease.token()));
    }

    public void relinquish(Lease lease) {
        db.update("""
                UPDATE checkout_voucher_sagas SET lease_token=NULL, lease_until=now(), retry_at=now()+interval '5 seconds'
                WHERE attempt_id=? AND lease_token=?
                """, lease.attemptId(), lease.token());
    }

    public List<Step> steps(Attempt attempt) {
        return db.query("""
                SELECT i.seller_order_id, i.store_id, i.quote, i.state, s.status
                FROM checkout_voucher_intents i LEFT JOIN seller_orders s
                  ON s.id=i.seller_order_id AND s.order_id=i.attempt_id AND s.store_id=i.store_id::text
                WHERE i.attempt_id=? ORDER BY i.store_id
                """, (rs, n) -> {
                    String status = rs.getString(5);
                    ReleaseReason closure = switch (status == null ? "" : status) {
                        case "CANCELLED" -> ReleaseReason.CANCELLED_BEFORE_DISPATCH;
                        case "REJECTED" -> ReleaseReason.REJECTED_BEFORE_DISPATCH;
                        default -> null;
                    };
                    return new Step(new Intent(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), attempt.customer(),
                            decode(rs.getString(3))), Progress.valueOf(rs.getString(4)), closure);
                }, attempt.id());
    }

    @Transactional
    public void step(Lease lease, UUID sellerOrder, Progress from, Progress to, ReleaseReason reason) {
        lock(lease);
        requireOne(db.update("""
                UPDATE checkout_voucher_intents SET state=?, release_reason=?
                WHERE attempt_id=? AND seller_order_id=? AND state=?
                """, to.name(), reason == null ? null : reason.name(), lease.attemptId(), sellerOrder, from.name()));
    }

    @Transactional
    public void startInventory(Lease lease) {
        if (lock(lease) != Phase.REDEEM || !allReserved(lease.attemptId()))
            throw new IllegalStateException("Voucher reservations are incomplete");
        phase(lease, Phase.INVENTORY, null);
    }

    /** Locks this saga through Order, outbox and CheckoutAttempt commit. */
    @Transactional
    public void requirePlacement(Lease lease) {
        if (lock(lease) != Phase.INVENTORY || !allReserved(lease.attemptId())
                || !hasInventoryState(lease.attemptId(), "CONFIRMED"))
            throw new IllegalStateException("Order placement lacks confirmed Voucher/Inventory evidence");
    }

    @Transactional
    public void placed(Lease lease) {
        if (lock(lease) != Phase.INVENTORY) throw new IllegalStateException("Checkout phase changed");
        phase(lease, Phase.PLACED, null);
    }

    @Transactional
    public void abort(Lease lease, String failure) {
        Phase current = lock(lease);
        if (current != Phase.REDEEM && (current != Phase.INVENTORY
                || !hasInventoryState(lease.attemptId(), "REJECTED")))
            throw new IllegalStateException("Checkout cannot abort without definitive non-commit evidence");
        requireOne(db.update("""
                UPDATE checkout_attempts SET state='FAILED'
                WHERE id=? AND state IN ('PROCESSING','NEEDS_RECONCILIATION') AND order_id IS NULL
                """, lease.attemptId()));
        phase(lease, Phase.ABORTED, failure);
    }

    @Transactional
    public void cartDone(Lease lease) {
        Phase current = lock(lease);
        if (current != Phase.PLACED && current != Phase.ABORTED)
            throw new IllegalStateException("Checkout Cart is still in use");
        requireOne(db.update("UPDATE checkout_voucher_sagas SET cart_done=true WHERE attempt_id=?", lease.attemptId()));
    }

    private boolean allReserved(UUID id) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM checkout_voucher_intents WHERE attempt_id=?)
                   AND NOT EXISTS (SELECT 1 FROM checkout_voucher_intents WHERE attempt_id=? AND state<>'RESERVED')
                """, Boolean.class, id, id));
    }

    private boolean hasInventoryState(UUID attempt, String state) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM inventory_operation_journal
                    WHERE checkout_attempt_id=? AND operation_id=? AND operation_type='RESERVE' AND state=?)
                """, Boolean.class, attempt, InventoryHttpAdapter.reserveId(attempt), state));
    }

    private Phase lock(Lease lease) {
        return db.query("""
                SELECT phase FROM checkout_voucher_sagas
                WHERE attempt_id=? AND lease_token=? AND lease_until>now() FOR UPDATE
                """, (rs, n) -> Phase.valueOf(rs.getString(1)), lease.attemptId(), lease.token())
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Checkout recovery lease was lost"));
    }

    private void phase(Lease lease, Phase to, String failure) {
        requireOne(db.update("UPDATE checkout_voucher_sagas SET phase=?, failure_code=? WHERE attempt_id=?",
                to.name(), failure, lease.attemptId()));
    }

    private static void requireOne(int updated) {
        if (updated != 1) throw new IllegalStateException("Checkout voucher journal did not advance");
    }

    private String encode(VoucherSnapshot quote) {
        try { return json.writeValueAsString(quote); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode voucher intent", error); }
    }

    private VoucherSnapshot decode(String payload) {
        try { return json.readValue(payload, VoucherSnapshot.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Invalid voucher intent", error); }
    }
}
