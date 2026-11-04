package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import com.projecthk5.orderservice.domain.ReservationOwnerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inert V3 candidate: there is deliberately NO caller from CheckoutService or the
 * legacy Inventory adapter. The candidate SQL is outside default Flyway locations.
 *
 * A future approved T1 integration may call prepareInsideDraftT1 only *inside*
 * CheckoutDraftJournal.prepare's transaction, AFTER the draft row is inserted.
 * MANDATORY prevents accidentally committing this owner map in a separate T1.
 */
@Service
public class ReservationOwnerCandidateJournal {
    private final JdbcTemplate db;
    private final CheckoutDraftJournal drafts;
    private final ObjectMapper json;

    public ReservationOwnerCandidateJournal(JdbcTemplate db, CheckoutDraftJournal drafts, ObjectMapper json) {
        this.db = db;
        this.drafts = drafts;
        this.json = json;
    }

    /** Requires an active draft T1; no new transaction and no external Inventory call. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReservationOwnerSnapshot> prepareInsideDraftT1(CheckoutDraft draft) {
        Objects.requireNonNull(draft, "draft");
        var attempt = db.query(
                "select customer_id,fingerprint,state from checkout_attempts where id=? for update",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)},
                draft.orderId()).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Missing Checkout attempt"));
        if (!draft.customerId().equals(attempt[0])
                || !draft.requestFingerprint().equals(attempt[1])
                || !"PROCESSING".equals(attempt[2])) {
            throw new IllegalStateException("Reservation owner map requires an exact PROCESSING attempt");
        }
        // The saved draft must be identical. Do not rebuild identities from current Product/Cart reads.
        if (!draft.equals(drafts.load(draft.orderId()))) {
            throw new IllegalStateException("Reservation owner map draft differs from stored T1 facts");
        }

        var owners = new ArrayList<ReservationOwnerSnapshot>();
        for (CheckoutDraft.Store store : draft.sellerOrders()) {
            List<ReservationOwnerSnapshot> current = find(draft.orderId(), store.id());
            UUID reservationId = current.isEmpty() ? UUID.randomUUID() : current.get(0).reservationId();
            ReservationOwnerSnapshot expected = ReservationOwnerSnapshot.fromDraft(draft, store, reservationId);
            if (current.isEmpty()) {
                int inserted = db.update(
                    "insert into checkout_reservation_owner_candidate "
                      + "(reservation_id,checkout_attempt_id,root_order_id,seller_order_id,"
                      + "customer_id,store_id,seller_id,checkout_request_fingerprint,"
                      + "canonical_lines,provider_owner_fingerprint,snapshot_sha256) "
                      + "values (?,?,?,?,?,?,?,?,cast(? as jsonb),?,?) "
                      + "on conflict do nothing",
                    expected.reservationId(), expected.checkoutAttemptId(), expected.rootOrderId(),
                    expected.sellerOrderId(), expected.customerId(), expected.storeId(),
                    expected.sellerId(), expected.checkoutRequestFingerprint(), encode(expected.lines()),
                    expected.providerOwnerFingerprint(), expected.snapshotSha256());
                if (inserted != 1) {
                    // The attempt row is locked, so a conflicting Store/SellerOrder binding is not a retry.
                    throw new IllegalStateException("Reservation owner identity conflict");
                }
            } else if (!current.get(0).equals(expected)) {
                throw new IllegalStateException("Immutable reservation owner map differs from original");
            }
            owners.add(expected);
        }
        if (countForAttempt(draft.orderId()) != draft.sellerOrders().size()) {
            throw new IllegalStateException("Unexpected or stale reservation owner binding");
        }
        return List.copyOf(owners);
    }

    /** Read-only recovery data: corrupt rows must fail closed. No UNKNOWN resolution here. */
    @Transactional(readOnly = true)
    public List<ReservationOwnerSnapshot> loadForAttempt(UUID attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        return db.query("select reservation_id,checkout_attempt_id,root_order_id,seller_order_id,"
                + "customer_id,store_id,seller_id,checkout_request_fingerprint,"
                + "canonical_lines,provider_owner_fingerprint,snapshot_sha256 "
                + "from checkout_reservation_owner_candidate where checkout_attempt_id=? order by store_id",
                (rs, i) -> new ReservationOwnerSnapshot(
                        rs.getObject("reservation_id", UUID.class),
                        rs.getObject("checkout_attempt_id", UUID.class),
                        rs.getObject("root_order_id", UUID.class),
                        rs.getObject("seller_order_id", UUID.class),
                        rs.getString("customer_id"), rs.getString("store_id"), rs.getString("seller_id"),
                        rs.getString("checkout_request_fingerprint"), decode(rs.getString("canonical_lines")),
                        rs.getString("provider_owner_fingerprint"), rs.getString("snapshot_sha256")), attemptId);
    }

    private List<ReservationOwnerSnapshot> find(UUID attemptId, UUID sellerOrderId) {
        return loadForAttempt(attemptId).stream()
                .filter(owner -> owner.sellerOrderId().equals(sellerOrderId)).toList();
    }

    private int countForAttempt(UUID attemptId) {
        Integer count = db.queryForObject(
                "select count(*) from checkout_reservation_owner_candidate where checkout_attempt_id=?",
                Integer.class, attemptId);
        return count == null ? 0 : count;
    }

    private String encode(List<ReservationOwnerSnapshot.Line> lines) {
        try {
            return json.writeValueAsString(lines);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize immutable reservation lines", error);
        }
    }

    private List<ReservationOwnerSnapshot.Line> decode(String body) {
        try {
            return json.readValue(body, new TypeReference<List<ReservationOwnerSnapshot.Line>>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Corrupt persisted reservation lines", error);
        }
    }
}
