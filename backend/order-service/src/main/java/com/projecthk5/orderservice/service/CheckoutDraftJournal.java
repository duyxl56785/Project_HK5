package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits business facts before Inventory side effects. No transport, scheduler, Order finalization,
 * credentials or compensation belongs here. UNKNOWN remains subject to owner-led reconciliation.
 */
@Service
public class CheckoutDraftJournal {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public CheckoutDraftJournal(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepare(CheckoutDraft draft) {
        // Serialize before the write: a local failure cannot cause Inventory HTTP.
        String payload = encode(draft);
        var attempt = db.query("select customer_id,fingerprint,state from checkout_attempts where id=? for update",
                (r, n) -> new String[]{r.getString(1), r.getString(2), r.getString(3)}, draft.orderId())
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("Missing checkout attempt"));
        if (!draft.customerId().equals(attempt[0]) || !draft.requestFingerprint().equals(attempt[1])
                || !"PROCESSING".equals(attempt[2]))
            throw new IllegalStateException("Checkout draft does not match a processing attempt");
        int added = db.update("insert into checkout_order_drafts "
                + "(checkout_attempt_id,snapshot_version,payload,payload_sha256) values (?,?,?,?) "
                + "on conflict (checkout_attempt_id) do nothing", draft.orderId(), draft.version(), payload, hash(payload));
        if (added == 0) {
            String saved = db.queryForObject("select payload from checkout_order_drafts where checkout_attempt_id=?",
                    String.class, draft.orderId());
            load(draft.orderId()); // Also reject a corrupt pre-existing checksum or attempt binding.
            if (!payload.equals(saved)) throw new IllegalStateException("Checkout draft is immutable");
        }
    }

    /** Reload without Catalog, Cart, current prices or any network/side effects. Internal use only. */
    @Transactional(readOnly = true)
    public CheckoutDraft load(UUID attemptId) {
        return db.query("select d.snapshot_version,d.payload,d.payload_sha256,a.customer_id,a.fingerprint "
                + "from checkout_order_drafts d join checkout_attempts a on a.id=d.checkout_attempt_id "
                + "where d.checkout_attempt_id=?", (r, n) -> {
                    String payload = r.getString("payload");
                    if (!hash(payload).equals(r.getString("payload_sha256")))
                        throw new IllegalStateException("Checkout draft checksum mismatch");
                    CheckoutDraft draft = decode(payload);
                    if (draft.version() != r.getInt("snapshot_version") || !draft.orderId().equals(attemptId)
                            || !draft.customerId().equals(r.getString("customer_id"))
                            || !draft.requestFingerprint().equals(r.getString("fingerprint")))
                        throw new IllegalStateException("Checkout draft binding mismatch");
                    return draft;
                }, attemptId).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Missing checkout draft"));
    }

    private String encode(CheckoutDraft draft) {
        try { return json.writeValueAsString(draft); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode checkout draft"); }
    }
    private CheckoutDraft decode(String payload) {
        try { return json.readValue(payload, CheckoutDraft.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid checkout draft payload"); }
    }
    private static String hash(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
