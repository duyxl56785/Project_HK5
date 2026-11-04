package com.projecthk5.orderservice.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Inert Order-side V3 reservation-owner candidate. It creates no HTTP calls and never
 * assigns an operation ID. One frozen owner belongs to one drafted SellerOrder.
 *
 * The provider fingerprint uses the exact reservation-owner-v1 length-prefixed format
 * from Inventory candidate #69. It is NOT the legacy aggregate Inventory fingerprint.
 */
public record ReservationOwnerSnapshot(
        UUID reservationId,
        UUID checkoutAttemptId,
        UUID rootOrderId,
        UUID sellerOrderId,
        String customerId,
        String storeId,
        String sellerId,
        String checkoutRequestFingerprint,
        List<Line> lines,
        String providerOwnerFingerprint,
        String snapshotSha256) {

    public record Line(String productId, String sku, long quantity) {
        public Line {
            requireIdentity(productId, 200, "productId");
            // Delegate canonical SKU checks to the existing immutable Order intent contract.
            new InventoryOperationIntent.Line(sku, quantity);
        }
    }

    public ReservationOwnerSnapshot {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(checkoutAttemptId, "checkoutAttemptId");
        Objects.requireNonNull(rootOrderId, "rootOrderId");
        Objects.requireNonNull(sellerOrderId, "sellerOrderId");
        requireIdentity(customerId, 200, "customerId");
        requireIdentity(storeId, 128, "storeId");
        requireIdentity(sellerId, 128, "sellerId");
        if (!rootOrderId.equals(checkoutAttemptId)) {
            throw new IllegalArgumentException("Root Order/attempt binding differs from current Order contract");
        }
        if (checkoutRequestFingerprint == null || !checkoutRequestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Checkout request fingerprint");
        }
        lines = canonicalLines(lines);
        String expectedOwner = ownerHash(reservationId, storeId, sellerId, sellerOrderId, lines);
        if (!expectedOwner.equals(providerOwnerFingerprint)) {
            throw new IllegalStateException("Reservation provider owner fingerprint mismatch");
        }
        String expectedSnapshot = snapshotHash(reservationId, checkoutAttemptId, rootOrderId, sellerOrderId,
                customerId, storeId, sellerId, checkoutRequestFingerprint, lines, providerOwnerFingerprint);
        if (!expectedSnapshot.equals(snapshotSha256)) {
            throw new IllegalStateException("Reservation owner snapshot checksum mismatch");
        }
    }

    /**
     * Assign a reservationId only when the initial T1 row is first created.
     * Recovery must load that ID from storage; never call with a fresh UUID on replay.
     */
    public static ReservationOwnerSnapshot fromDraft(CheckoutDraft draft, CheckoutDraft.Store store, UUID reservationId) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(reservationId, "reservationId");
        if (!draft.sellerOrders().contains(store)) {
            throw new IllegalArgumentException("SellerOrder is not part of Checkout Draft");
        }
        List<Line> raw = store.items().stream()
                .map(item -> new Line(item.productId(), item.sku(), item.quantity())).toList();
        List<Line> ordered = canonicalLines(raw);
        String owner = ownerHash(reservationId, store.storeId(), store.sellerId(), store.id(), ordered);
        String snapshot = snapshotHash(reservationId, draft.orderId(), draft.orderId(), store.id(),
                draft.customerId(), store.storeId(), store.sellerId(), draft.requestFingerprint(), ordered, owner);
        return new ReservationOwnerSnapshot(reservationId, draft.orderId(), draft.orderId(), store.id(),
                draft.customerId(), store.storeId(), store.sellerId(), draft.requestFingerprint(),
                ordered, owner, snapshot);
    }

    /**
     * Candidate provider operation hash; use the actual persisted step lines for partial
     * CONSUME/RELEASE. It does NOT assign/authorize a new step operation ID.
     */
    public String providerOperationFingerprint(InventoryOperationIntent.Type type,
                                               List<InventoryOperationIntent.Line> stepLines) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(stepLines, "stepLines");
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (var line : stepLines) {
            Objects.requireNonNull(line, "line");
            if (sorted.putIfAbsent(line.sku(), line.quantity()) != null) {
                throw new IllegalArgumentException("Duplicate SKU in step");
            }
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("Empty operation");
        var data = new StringBuilder();
        append(data, "reservation-operation-v1");
        append(data, reservationId.toString());
        append(data, storeId);
        append(data, sellerId);
        append(data, sellerOrderId.toString());
        append(data, type.name());
        appendSkuLines(data, sorted);
        return sha256(data.toString());
    }

    private static List<Line> canonicalLines(List<Line> source) {
        Objects.requireNonNull(source, "lines");
        if (source.isEmpty()) throw new IllegalArgumentException("Empty SellerOrder reservation");
        TreeMap<String, Line> bySku = new TreeMap<>();
        var productIds = new java.util.HashSet<String>();
        for (Line line : source) {
            Objects.requireNonNull(line, "line");
            if (bySku.putIfAbsent(line.sku(), line) != null || !productIds.add(line.productId())) {
                throw new IllegalArgumentException("Duplicate SKU or Product in SellerOrder");
            }
        }
        return List.copyOf(new ArrayList<>(bySku.values()));
    }

    private static String ownerHash(UUID reservationId, String storeId, String sellerId,
                                    UUID sellerOrderId, List<Line> lines) {
        var data = new StringBuilder();
        append(data, "reservation-owner-v1");
        append(data, reservationId.toString());
        append(data, storeId);
        append(data, sellerId);
        append(data, sellerOrderId.toString());
        TreeMap<String, Long> quantities = new TreeMap<>();
        for (Line line : lines) quantities.put(line.sku(), line.quantity());
        appendSkuLines(data, quantities);
        return sha256(data.toString());
    }

    private static String snapshotHash(UUID reservationId, UUID attemptId, UUID rootOrderId,
                                       UUID sellerOrderId, String customerId, String storeId, String sellerId,
                                       String requestFingerprint, List<Line> lines, String ownerFingerprint) {
        var data = new StringBuilder();
        append(data, "order-reservation-snapshot-v1");
        append(data, reservationId.toString());
        append(data, attemptId.toString());
        append(data, rootOrderId.toString());
        append(data, sellerOrderId.toString());
        append(data, customerId);
        append(data, storeId);
        append(data, sellerId);
        append(data, requestFingerprint);
        append(data, ownerFingerprint);
        append(data, Integer.toString(lines.size()));
        for (Line line : lines) {
            append(data, line.productId());
            append(data, line.sku());
            append(data, Long.toString(line.quantity()));
        }
        return sha256(data.toString());
    }

    private static void appendSkuLines(StringBuilder target, TreeMap<String, Long> quantities) {
        append(target, Integer.toString(quantities.size()));
        quantities.forEach((sku, quantity) -> {
            append(target, sku);
            append(target, Long.toString(quantity));
        });
    }

    /** Java String.length() is the exact Java candidate provider encoding, not UTF-8 byte length. */
    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static void requireIdentity(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        int start = value.codePointAt(0), end = value.codePointBefore(value.length());
        if (Character.isWhitespace(start) || Character.isSpaceChar(start)
                || Character.isWhitespace(end) || Character.isSpaceChar(end)) {
            throw new IllegalArgumentException("Noncanonical " + field);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
