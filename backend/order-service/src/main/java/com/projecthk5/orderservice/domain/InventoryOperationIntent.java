package com.projecthk5.orderservice.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An immutable Order-side Inventory operation payload, not a live Inventory adapter.
 *
 * <p>The caller must assign a new operationId once per RESERVE/RELEASE/CONSUME step
 * and durably commit it with these exact lines before issuing any HTTP call.
 * Retries must reconstruct this value from the journal, not generate another ID.
 *
 * <p>Fingerprinting follows TV4's current Inventory provider representation:
 * length-prefixed operation type, number of unique SKUs, and each sorted SKU and
 * signed 64-bit quantity. operationId and CheckoutAttempt correlation are not
 * part of the provider's request hash.
 */
public final class InventoryOperationIntent {
    public enum Type { RESERVE, RELEASE, CONSUME }

    public record Line(String sku, long quantity) {
        public Line {
            Objects.requireNonNull(sku, "sku");
            // Inventory trims SKU before hashing. Require the authoritative ID
            // already be canonical instead of silently changing its identity.
            if (sku.isBlank() || sku.length() > 128 || !sku.equals(sku.trim())) {
                throw new IllegalArgumentException("SKU must be nonblank, canonical and at most 128 characters");
            }
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private final UUID checkoutAttemptId;
    private final String operationId;
    private final Type type;
    private final List<Line> lines;
    private final String fingerprint;

    private InventoryOperationIntent(UUID checkoutAttemptId, String operationId, Type type,
                                     List<Line> lines) {
        this.checkoutAttemptId = Objects.requireNonNull(checkoutAttemptId, "checkoutAttemptId");
        this.operationId = requireOperationId(operationId);
        this.type = Objects.requireNonNull(type, "type");
        this.lines = canonicalLines(lines);
        this.fingerprint = hash(type, this.lines);
    }

    /** Build only when first persisting a step's intent, never with a fresh ID on retry. */
    public static InventoryOperationIntent prepare(UUID checkoutAttemptId, String operationId,
                                                    Type type, List<Line> lines) {
        return new InventoryOperationIntent(checkoutAttemptId, operationId, type, lines);
    }

    /** Rehydrate journal data and reject changed/corrupt persisted payloads. */
    public static InventoryOperationIntent fromJournal(UUID checkoutAttemptId, String operationId,
                                                        Type type, List<Line> lines, String savedFingerprint) {
        InventoryOperationIntent value = prepare(checkoutAttemptId, operationId, type, lines);
        if (!value.fingerprint.equals(Objects.requireNonNull(savedFingerprint, "savedFingerprint"))) {
            throw new IllegalStateException("Persisted Inventory operation payload fingerprint mismatch");
        }
        return value;
    }

    /** Reject a changed replay, keeping the same operationId and immutable journal data. */
    public void requireSamePayload(Type replayType, List<Line> replayLines) {
        if (type != Objects.requireNonNull(replayType, "replayType")
                || !fingerprint.equals(hash(replayType, canonicalLines(replayLines)))) {
            throw new IllegalArgumentException("operationId reused with a different Inventory payload");
        }
    }

    public UUID checkoutAttemptId() { return checkoutAttemptId; }
    public String operationId() { return operationId; }
    public Type type() { return type; }
    public List<Line> lines() { return lines; }
    public String fingerprint() { return fingerprint; }

    private static String requireOperationId(String id) {
        Objects.requireNonNull(id, "operationId");
        if (id.isBlank() || id.length() > 128 || !id.equals(id.trim())) {
            throw new IllegalArgumentException("operationId must be canonical and 1-128 characters");
        }
        return id;
    }

    private static List<Line> canonicalLines(List<Line> input) {
        Objects.requireNonNull(input, "lines");
        if (input.isEmpty()) throw new IllegalArgumentException("Inventory operation requires lines");
        Map<String, Long> bySku = new TreeMap<>();
        for (Line line : input) {
            Objects.requireNonNull(line, "line");
            if (bySku.putIfAbsent(line.sku(), line.quantity()) != null) {
                throw new IllegalArgumentException("Duplicate Inventory SKU within operation");
            }
        }
        List<Line> ordered = new ArrayList<>(bySku.size());
        bySku.forEach((sku, quantity) -> ordered.add(new Line(sku, quantity)));
        return List.copyOf(ordered);
    }

    private static String hash(Type type, List<Line> ordered) {
        StringBuilder canonical = new StringBuilder();
        appendLengthPrefixed(canonical, type.name());
        appendLengthPrefixed(canonical, Integer.toString(ordered.size()));
        for (Line line : ordered) {
            appendLengthPrefixed(canonical, line.sku());
            appendLengthPrefixed(canonical, Long.toString(line.quantity()));
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void appendLengthPrefixed(StringBuilder sb, String value) {
        sb.append(value.length()).append(':').append(value);
    }
}
