package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OutboxEvent;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * At-least-once delivery of outbox events: Inventory over HTTP, notifications and review rights
 * through Kafka. Consumers are idempotent (operationId, eventKey, reviewable key), so redelivery
 * after a crash is harmless, and a broker outage only leaves events queued.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;
    private static final int SEND_TIMEOUT_SECONDS = 6;
    public record Message(String recipient, String type, String title, String message, String link, String eventKey) {}
    public record OrderPlaced(String eventId, String orderId, String accountId, java.math.BigDecimal total, Message notification) {}
    public record StatusChanged(String eventId, String sellerOrderId, String orderId, String storeId, String sellerAccountId,
                                String customerAccountId, String fromStatus, String toStatus, String actorRole,
                                List<String> productIds, Message notification) {}

    private final OutboxEventRepository outbox;
    private final OrderRepository orders;
    private final InventoryPort inventory;
    private final KafkaTemplate<String, String> kafka;
    private final StoreDirectory stores;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private boolean brokerDown;

    public OutboxRelay(OutboxEventRepository outbox, OrderRepository orders, InventoryPort inventory,
                       KafkaTemplate<String, String> kafka, StoreDirectory stores, ObjectMapper json, TransactionTemplate tx) {
        this.outbox = outbox;
        this.orders = orders;
        this.inventory = inventory;
        this.kafka = kafka;
        this.stores = stores;
        this.json = json;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:2000}")
    public void relay() {
        brokerDown = false;
        for (UUID id : outbox.pendingIds(BATCH)) {
            try {
                tx.executeWithoutResult(status -> outbox.claim(id).ifPresent(this::deliver));
            } catch (RuntimeException e) {
                log.warn("Outbox event {} could not be processed: {}", id, e.toString());
            }
        }
    }

    void deliver(OutboxEvent event) {
        // One failed send per pass is enough: each would otherwise wait out the producer timeout.
        if (brokerDown && !OrderEvents.INVENTORY.equals(event.getTopic())) return;
        try {
            switch (event.getTopic()) {
                case OrderEvents.ORDER_PLACED -> placed(event);
                case OrderEvents.INVENTORY -> inventory(json.treeToValue(json.readTree(event.getPayload()), OrderEvents.InventoryOperation.class));
                case OrderEvents.NOTIFY -> notice(event);
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
        } catch (ExecutionException | TimeoutException | KafkaException unavailable) {
            brokerDown = true;
            event.postponed(unavailable.toString());
        } catch (Exception transientFailure) {
            event.failed(transientFailure.toString(), false);
        }
    }

    private void inventory(OrderEvents.InventoryOperation op) {
        inventory.apply(op.operationId(), op.type(), op.lines().stream()
                .map(l -> new InventoryPort.Reservation(l.productId(), l.quantity(), l.sku())).toList());
    }

    private void placed(OutboxEvent event) throws Exception {
        UUID orderId = UUID.fromString(json.readTree(event.getPayload()).path("orderId").asText());
        Order order = orders.findById(orderId).orElseThrow(() -> new IllegalStateException("Order " + orderId + " missing"));
        String code = orderId.toString().substring(0, 8).toUpperCase();
        publish(OrderEvents.ORDER_PLACED, orderId.toString(), new OrderPlaced(event.getId().toString(), orderId.toString(),
                order.getCustomerId(), order.getTotal(), new Message(order.getCustomerId(), "ORDER_PLACED", "Đặt hàng thành công",
                "Đơn " + code + " đã được tạo, thanh toán khi nhận hàng (COD).", "/orders/" + orderId,
                "order-" + orderId + "-placed-customer")));
        List<SellerOrder> perStore = order.getSellerOrders();
        Set<String> storeIds = perStore.stream().map(SellerOrder::getStoreId).collect(Collectors.toSet());
        var owners = stores.facts(storeIds).stream()
                .collect(Collectors.toMap(StoreDirectory.Store::storeId, StoreDirectory.Store::ownerReference));
        for (SellerOrder so : perStore) {
            String owner = owners.get(so.getStoreId());
            if (owner == null) throw new IllegalStateException("Store " + so.getStoreId() + " owner unknown");
            publish(OrderEvents.STATUS_CHANGED, so.getId().toString(), new StatusChanged(event.getId() + "-" + so.getId(), so.getId().toString(),
                    orderId.toString(), so.getStoreId(), owner, order.getCustomerId(), null, so.getStatus().name(), "CUSTOMER", null,
                    new Message(owner, "NEW_ORDER", "Có đơn hàng mới",
                            "Đơn " + code + " gồm " + so.getItems().size() + " sản phẩm đang chờ xác nhận.", "/seller/orders",
                            "order-" + so.getId() + "-placed-store")));
        }
    }

    private void notice(OutboxEvent event) throws Exception {
        OrderEvents.Notice n = json.treeToValue(json.readTree(event.getPayload()), OrderEvents.Notice.class);
        String owner = stores.facts(Set.of(n.storeId())).stream().findFirst()
                .map(StoreDirectory.Store::ownerReference)
                .orElseThrow(() -> new IllegalStateException("Store " + n.storeId() + " owner unknown"));
        String recipient = n.recipient() == null ? owner : n.recipient();
        publish(OrderEvents.STATUS_CHANGED, n.sellerOrderId(), new StatusChanged(event.getId().toString(), n.sellerOrderId(), n.orderId(),
                n.storeId(), owner, n.customerId(), n.fromStatus(), n.toStatus(), n.actorRole(), n.productIds(),
                new Message(recipient, n.type(), n.title(), n.message(), n.link(), n.eventKey())));
    }

    private void publish(String topic, String key, Object event) throws Exception {
        kafka.send(topic, key, json.writeValueAsString(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
