package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OutboxEvent;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.ports.NotificationClient;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * At-least-once delivery of outbox events. Every downstream call is idempotent (Inventory by
 * operationId, Notification by eventKey), so redelivery after a crash is harmless.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;

    private final OutboxEventRepository outbox;
    private final OrderRepository orders;
    private final InventoryPort inventory;
    private final NotificationClient notifications;
    private final StoreDirectory stores;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxEventRepository outbox, OrderRepository orders, InventoryPort inventory,
                       NotificationClient notifications, StoreDirectory stores, ObjectMapper json, TransactionTemplate tx) {
        this.outbox = outbox;
        this.orders = orders;
        this.inventory = inventory;
        this.notifications = notifications;
        this.stores = stores;
        this.json = json;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:2000}")
    public void relay() {
        for (UUID id : outbox.pendingIds(BATCH)) {
            try {
                tx.executeWithoutResult(status -> outbox.claim(id).ifPresent(this::deliver));
            } catch (RuntimeException e) {
                log.warn("Outbox event {} could not be processed: {}", id, e.toString());
            }
        }
    }

    void deliver(OutboxEvent event) {
        try {
            switch (event.getTopic()) {
                case OrderEvents.ORDER_PLACED -> placed(json.readTree(event.getPayload()));
                case OrderEvents.INVENTORY -> inventory(json.treeToValue(json.readTree(event.getPayload()), OrderEvents.InventoryOperation.class));
                case OrderEvents.NOTIFY -> notice(json.treeToValue(json.readTree(event.getPayload()), OrderEvents.Notice.class));
                default -> {
                    event.failed("Unknown topic " + event.getTopic(), true);
                    return;
                }
            }
            event.published(Instant.now());
        } catch (InventoryPort.Rejected definitive) {
            // Inventory refused atomically (e.g. reservation already gone): retrying cannot succeed.
            event.failed(definitive.code() + ": " + definitive.getMessage(), true);
            log.error("Outbox event {} rejected by Inventory: {}", event.getId(), definitive.getMessage());
        } catch (Exception transientFailure) {
            event.failed(transientFailure.toString(), false);
        }
    }

    private void inventory(OrderEvents.InventoryOperation op) {
        inventory.apply(op.operationId(), op.type(), op.lines().stream()
                .map(l -> new InventoryPort.Reservation(l.productId(), l.quantity(), l.sku())).toList());
    }

    private void placed(JsonNode payload) {
        UUID orderId = UUID.fromString(payload.path("orderId").asText());
        Order order = orders.findById(orderId).orElseThrow(() -> new IllegalStateException("Order " + orderId + " missing"));
        String code = orderId.toString().substring(0, 8).toUpperCase();
        notifications.send(new NotificationClient.Message(order.getCustomerId(), "ORDER_PLACED", "Đặt hàng thành công",
                "Đơn " + code + " đã được tạo, thanh toán khi nhận hàng (COD).", "/orders/" + orderId,
                "order-" + orderId + "-placed-customer"));
        List<SellerOrder> perStore = order.getSellerOrders();
        Set<String> storeIds = perStore.stream().map(SellerOrder::getStoreId).collect(Collectors.toSet());
        var owners = stores.facts(storeIds).stream()
                .collect(Collectors.toMap(StoreDirectory.Store::storeId, StoreDirectory.Store::ownerReference));
        for (SellerOrder so : perStore) {
            String owner = owners.get(so.getStoreId());
            if (owner == null) throw new IllegalStateException("Store " + so.getStoreId() + " owner unknown");
            notifications.send(new NotificationClient.Message(owner, "NEW_ORDER", "Có đơn hàng mới",
                    "Đơn " + code + " gồm " + so.getItems().size() + " sản phẩm đang chờ xác nhận.", "/seller/orders",
                    "order-" + so.getId() + "-placed-store"));
        }
    }

    private void notice(OrderEvents.Notice n) {
        String recipient = n.recipient();
        if (recipient == null) {
            recipient = stores.facts(Set.of(n.storeId())).stream().findFirst()
                    .map(StoreDirectory.Store::ownerReference)
                    .orElseThrow(() -> new IllegalStateException("Store " + n.storeId() + " owner unknown"));
        }
        notifications.send(new NotificationClient.Message(recipient, n.type(), n.title(), n.message(), n.link(), n.eventKey()));
    }
}
