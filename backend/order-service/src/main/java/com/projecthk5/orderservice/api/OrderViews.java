package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.ports.StoreDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Read models for order screens. Store names are display-only and degrade to null if Seller is down. */
@Component
public class OrderViews {
    private final StoreDirectory stores;

    public OrderViews(StoreDirectory stores) { this.stores = stores; }

    public record Item(String productId, String name, String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record StoreOrder(UUID id, UUID orderId, String storeId, String storeName, String status, String codStatus,
                             String statusReason, BigDecimal gross, BigDecimal platformFee, BigDecimal sellerNet,
                             Instant createdAt, Instant updatedAt, Instant deliveredAt, Instant codCollectedAt,
                             Instant settledAt, String version, Order.Shipping shipping, List<Item> items) {}
    public record CustomerOrder(UUID id, String status, BigDecimal total, String paymentMethod, Instant createdAt,
                                Order.Shipping shipping, List<StoreOrder> storeOrders) {}

    public List<CustomerOrder> customer(List<Order> orders) {
        Map<String, String> names = names(orders.stream().flatMap(o -> o.getSellerOrders().stream()).toList());
        return orders.stream().map(o -> new CustomerOrder(o.getId(), o.getStatus().name(), o.getTotal(), o.getPaymentMethod(),
                o.getCreatedAt(), o.getShipping(), o.getSellerOrders().stream().map(so -> store(so, names)).toList())).toList();
    }

    public List<StoreOrder> stores(List<SellerOrder> list) {
        Map<String, String> names = names(list);
        return list.stream().map(so -> store(so, names)).toList();
    }

    public Map<String, String> names(Collection<SellerOrder> list) {
        Set<String> ids = list.stream().map(SellerOrder::getStoreId).collect(Collectors.toSet());
        try {
            return stores.facts(ids).stream().collect(Collectors.toMap(StoreDirectory.Store::storeId, StoreDirectory.Store::name));
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private static StoreOrder store(SellerOrder so, Map<String, String> names) {
        Order o = so.getOrder();
        return new StoreOrder(so.getId(), o.getId(), so.getStoreId(), names.get(so.getStoreId()), so.getStatus().name(),
                so.getCodStatus().name(), so.getStatusReason(), so.getGross(), so.getPlatformFee(), so.getSellerNet(),
                o.getCreatedAt(), so.getUpdatedAt(), so.getDeliveredAt(), so.getCodCollectedAt(), so.getSettledAt(),
                Long.toString(so.getVersion()), o.getShipping(), so.getItems().stream().map(OrderViews::item).toList());
    }

    private static Item item(OrderItem i) {
        return new Item(i.getProductId(), i.getProductName(), i.getSku(), i.getQuantity(), i.getUnitPriceSnapshot(), i.getLineTotal());
    }
}
