package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.entity.OutboxEvent;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.orderservice.repository.OutboxEventRepository;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.orderservice.service.FinanceReport;
import com.projecthk5.orderservice.service.FulfillmentService;
import com.projecthk5.security.Actor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** ADMIN COD reconciliation, settlement and platform finance. */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {
    private final SellerOrderRepository sellerOrders;
    private final FulfillmentService fulfillment;
    private final OrderViews views;
    private final FinanceReport finance;
    private final OutboxEventRepository outbox;

    public AdminOrderController(SellerOrderRepository sellerOrders, FulfillmentService fulfillment, OrderViews views,
                                FinanceReport finance, OutboxEventRepository outbox) {
        this.sellerOrders = sellerOrders;
        this.fulfillment = fulfillment;
        this.views = views;
        this.finance = finance;
        this.outbox = outbox;
    }

    public record DeadEvent(UUID id, String topic, UUID aggregateId, int attempts, String lastError, Instant createdAt) {}

    @GetMapping("/store-orders")
    @Transactional(readOnly = true)
    public List<OrderViews.StoreOrder> list(@RequestParam(required = false) SellerOrderLifecycle.Status status,
                                            @RequestParam(required = false) SellerOrder.CodStatus cod) {
        Actor.require("ADMIN");
        return views.stores(sellerOrders.findForAdmin(status, cod, PageRequest.of(0, 200)));
    }

    @PostMapping("/store-orders/{id}/cod-collected")
    @Transactional
    public OrderViews.StoreOrder codCollected(@PathVariable UUID id) {
        return views.stores(List.of(fulfillment.collectCod(Actor.require("ADMIN").id(), id))).get(0);
    }

    @PostMapping("/store-orders/{id}/settle")
    @Transactional
    public OrderViews.StoreOrder settle(@PathVariable UUID id) {
        return views.stores(List.of(fulfillment.settle(Actor.require("ADMIN").id(), id))).get(0);
    }

    @GetMapping("/finance")
    @Transactional(readOnly = true)
    public FinanceReport.Platform platform() {
        Actor.require("ADMIN");
        return finance.platform();
    }

    /** Side effects that exhausted retries or were refused; operators reconcile these by hand. */
    @GetMapping("/outbox/dead")
    @Transactional(readOnly = true)
    public List<DeadEvent> dead() {
        Actor.require("ADMIN");
        return outbox.findDead().stream().map((OutboxEvent e) -> new DeadEvent(e.getId(), e.getTopic(), e.getAggregateId(),
                e.getAttempts(), e.getLastError(), e.getCreatedAt())).toList();
    }
}
