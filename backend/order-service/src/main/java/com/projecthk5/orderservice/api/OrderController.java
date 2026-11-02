package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.FulfillmentService;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** CUSTOMER order history; every read is scoped to the verified JWT subject. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository orders;
    private final FulfillmentService fulfillment;
    private final OrderViews views;

    public OrderController(OrderRepository orders, FulfillmentService fulfillment, OrderViews views) {
        this.orders = orders;
        this.fulfillment = fulfillment;
        this.views = views;
    }

    public record ReasonRequest(@Size(max = 500) String reason) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderViews.CustomerOrder> mine(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 50) throw ApiException.badRequest("VALIDATION_FAILED", "Invalid paging");
        String me = Actor.require("CUSTOMER").id();
        return views.customer(orders.findByCustomerIdOrderByCreatedAtDesc(me, PageRequest.of(page, size)));
    }

    @GetMapping("/{orderId}")
    @Transactional(readOnly = true)
    public OrderViews.CustomerOrder one(@PathVariable UUID orderId) {
        String me = Actor.require("CUSTOMER").id();
        return orders.findByIdAndCustomerId(orderId, me).map(o -> views.customer(List.of(o)).get(0))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
    }

    @PostMapping("/{orderId}/store-orders/{storeOrderId}/cancel")
    @Transactional
    public OrderViews.CustomerOrder cancel(@PathVariable UUID orderId, @PathVariable UUID storeOrderId,
                                           @Valid @RequestBody(required = false) ReasonRequest body) {
        String me = Actor.require("CUSTOMER").id();
        fulfillment.customerCancel(me, orderId, storeOrderId, body == null ? null : body.reason());
        return one(orderId);
    }
}
