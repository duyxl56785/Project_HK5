package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.entity.OutboxEvent;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.repository.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes side effects into the outbox inside the caller's transaction; the relay delivers them. */
@Component
public class OrderEvents {
    public static final String ORDER_PLACED = "order.placed.v1";
    public static final String INVENTORY = "inventory.operation.v1";
    public static final String NOTIFY = "notification.v1";

    private final OutboxEventRepository outbox;
    private final ObjectMapper json;

    public OrderEvents(OutboxEventRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public record Line(String productId, int quantity, String sku) {}
    public record InventoryOperation(String operationId, String type, List<Line> lines) {}
    /** Exactly one of recipient (account id) or storeId (resolved to the Store owner at delivery). */
    public record Notice(String recipient, String storeId, String type, String title, String message, String link, String eventKey) {}

    /** RELEASE on cancel/reject, CONSUME on delivery; the id is fixed per Store order and type. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void inventory(SellerOrder so, String type) {
        List<Line> lines = so.getItems().stream().map(i -> new Line(i.getProductId(), i.getQuantity(), i.getSku())).toList();
        String operationId = "seller-order-" + so.getId() + "-" + type.toLowerCase();
        write(INVENTORY, so.getId(), new InventoryOperation(operationId, type, lines));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCustomer(SellerOrder so, String type, String title, String message, String eventSuffix) {
        write(NOTIFY, so.getId(), new Notice(so.getOrder().getCustomerId(), null, type, title, message,
                "/orders/" + so.getOrder().getId(), "order-" + so.getId() + "-" + eventSuffix + "-customer"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyStore(SellerOrder so, String type, String title, String message, String eventSuffix) {
        write(NOTIFY, so.getId(), new Notice(null, so.getStoreId(), type, title, message,
                "/seller/orders", "order-" + so.getId() + "-" + eventSuffix + "-store"));
    }

    private void write(String topic, UUID aggregate, Object payload) {
        try {
            outbox.save(new OutboxEvent(topic, aggregate, json.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to write outbox event", e);
        }
    }

}
