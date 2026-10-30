package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Product->Order pair: proves a delivered purchase before a review is accepted. */
@RestController
public class InternalOrderController {
    private final SellerOrderRepository sellerOrders;
    private final String productToken;

    public InternalOrderController(SellerOrderRepository sellerOrders, @Value("${PRODUCT_ORDER_TOKEN:}") String productToken) {
        this.sellerOrders = sellerOrders;
        this.productToken = productToken;
    }

    public record Purchase(String customerId, boolean delivered, boolean containsProduct) {}

    @GetMapping("/internal/orders/{orderId}/purchases/{productId}")
    @Transactional(readOnly = true)
    public Purchase purchase(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                             @PathVariable UUID orderId, @PathVariable String productId) {
        InternalTokens.require(productToken, token);
        List<SellerOrder> perStore = sellerOrders.findByOrderId(orderId);
        if (perStore.isEmpty()) throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        SellerOrder holder = perStore.stream()
                .filter(so -> so.getItems().stream().anyMatch(i -> i.getProductId().equals(productId)))
                .findFirst().orElse(null);
        return new Purchase(perStore.get(0).getOrder().getCustomerId(),
                holder != null && holder.getStatus() == SellerOrderLifecycle.Status.DELIVERED, holder != null);
    }
}
