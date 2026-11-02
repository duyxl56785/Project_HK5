package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Private, versioned checkout snapshot. It is not a placed Order or permission to recover one. */
public record CheckoutDraft(int version, UUID orderId, String customerId, String requestFingerprint,
        Long expectedRevision, long claimedCartRevision, Instant createdAt, String paymentMethod,
        Order.Shipping shipping, BigDecimal total, List<Store> sellerOrders, String outboxPayload) {
    public CheckoutDraft {
        if (version != 1 || orderId == null || customerId == null || customerId.isBlank()
                || requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")
                || createdAt == null || !"COD".equals(paymentMethod) || claimedCartRevision < 0
                || outboxPayload == null || outboxPayload.isBlank())
            throw new IllegalArgumentException("Invalid checkout draft metadata");
        sellerOrders = List.copyOf(sellerOrders);
        if (sellerOrders.isEmpty()) throw new IllegalArgumentException("Empty checkout draft");
        Set<UUID> ids = new HashSet<>();
        Set<String> stores = new HashSet<>();
        Set<String> sellers = new HashSet<>();
        for (Store store : sellerOrders) {
            if (!ids.add(store.id()) || !stores.add(store.storeId()) || !sellers.add(store.sellerId()))
                throw new IllegalArgumentException("Duplicate checkout draft identity");
            for (Line item : store.items()) {
                if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate checkout draft item");
            }
        }
        exact(total);
        if (sum(sellerOrders.stream().map(Store::gross).toList()).compareTo(total) != 0)
            throw new IllegalArgumentException("Checkout draft total mismatch");
    }

    public record Store(UUID id, String sellerId, String storeId, boolean houseStore, String status,
            String codStatus, Instant updatedAt, BigDecimal gross, BigDecimal platformFee,
            BigDecimal sellerNet, List<Line> items) {
        public Store {
            if (id == null || sellerId == null || sellerId.isBlank() || storeId == null || storeId.isBlank()
                    || !"PLACED".equals(status) || !"PENDING".equals(codStatus) || updatedAt == null)
                throw new IllegalArgumentException("Invalid checkout Store draft");
            items = List.copyOf(items);
            exact(gross); exact(platformFee); exact(sellerNet);
            if (items.isEmpty() || sum(items.stream().map(Line::lineTotal).toList()).compareTo(gross) != 0
                    || gross.subtract(platformFee).compareTo(sellerNet) != 0)
                throw new IllegalArgumentException("Checkout Store draft money mismatch");
        }
    }

    public record Line(UUID id, String productId, String sku, String productName, int quantity,
            BigDecimal unitPriceSnapshot, BigDecimal lineTotal) {
        public Line {
            if (id == null || productId == null || productId.isBlank() || quantity < 1)
                throw new IllegalArgumentException("Invalid checkout item draft");
            // Null labels are retained only for existing legacy fixtures; the real Catalog/SKU guard is unchanged.
            exact(unitPriceSnapshot); exact(lineTotal);
            if (unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity)).compareTo(lineTotal) != 0)
                throw new IllegalArgumentException("Checkout item draft money mismatch");
        }
    }

    public static CheckoutDraft capture(Order order, String fingerprint, Long expectedRevision,
            long claimedCartRevision, Map<String, Boolean> houseStores, String outboxPayload) {
        if (order.getStatus() != Order.Status.PLACED)
            throw new IllegalArgumentException("Only an initial Order can be drafted");
        List<Store> stores = order.getSellerOrders().stream().map(s -> new Store(s.getId(), s.getSellerId(),
                s.getStoreId(), Objects.requireNonNull(houseStores.get(s.getStoreId())), s.getStatus().name(),
                s.getCodStatus().name(), s.getUpdatedAt(), s.getGross(), s.getPlatformFee(), s.getSellerNet(),
                s.getItems().stream().map(i -> new Line(i.getId(), i.getProductId(), i.getSku(), i.getProductName(),
                        i.getQuantity(), i.getUnitPriceSnapshot(), i.getLineTotal())).toList())).toList();
        return new CheckoutDraft(1, order.getId(), order.getCustomerId(), fingerprint, expectedRevision,
                claimedCartRevision, order.getCreatedAt(), order.getPaymentMethod(), order.getShipping(),
                order.getTotal(), stores, outboxPayload);
    }

    private static void exact(BigDecimal amount) { OrderAmountStorageBounds.requireExactNonnegative(amount); }
    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Avoid accidental shipping/customer data disclosure from diagnostic logging. */
    @Override public String toString() { return "CheckoutDraft[orderId=" + orderId + ", version=" + version + "]"; }
}
