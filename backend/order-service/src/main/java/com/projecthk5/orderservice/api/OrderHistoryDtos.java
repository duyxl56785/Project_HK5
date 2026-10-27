package com.projecthk5.orderservice.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only stored snapshots. Decimal strings preserve precision for browser clients. */
public final class OrderHistoryDtos {
    private OrderHistoryDtos() {}

    public record Summary(UUID orderId, String status, String total, Instant createdAt, long sellerOrderCount) {}
    public record Page(List<Summary> content, int page, int size, long totalElements, long totalPages) {
        public Page { content = List.copyOf(content); }
    }
    public record Item(String productId, int quantity, String unitPriceSnapshot, String lineTotal) {}
    public record StoreOrder(UUID sellerOrderId, String storeId, String gross, List<Item> items) {
        public StoreOrder { items = List.copyOf(items); }
    }
    public record Detail(UUID orderId, String status, String total, Instant createdAt, List<StoreOrder> sellerOrders) {
        public Detail { sellerOrders = List.copyOf(sellerOrders); }
    }
}
