package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Action;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Status;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store-order state changes. Each runs under a row lock; inventory and notification side effects
 * are written to the outbox in the same transaction, so a crash can never lose or duplicate them.
 */
@Service
public class FulfillmentService {
    /** Seller-facing action names in the API path. */
    public static final Map<String, Action> SELLER_ACTIONS = Map.of(
            "accept", Action.ACCEPT,
            "prepare", Action.START_PREPARING,
            "ready", Action.READY_FOR_HANDOFF,
            "dispatch", Action.DISPATCH,
            "deliver", Action.DELIVER,
            "reject", Action.REJECT);

    private final SellerOrderRepository sellerOrders;
    private final OrderEvents events;
    private final Clock clock;

    @Autowired
    public FulfillmentService(SellerOrderRepository sellerOrders, OrderEvents events) {
        this(sellerOrders, events, Clock.systemUTC());
    }

    FulfillmentService(SellerOrderRepository sellerOrders, OrderEvents events, Clock clock) {
        this.sellerOrders = sellerOrders;
        this.events = events;
        this.clock = clock;
    }

    /** A Seller acts only on orders of the Store it manages right now (resolved server-side). */
    @Transactional
    public SellerOrder sellerAction(String managedStoreId, UUID sellerOrderId, String actionName, String reason) {
        Action action = SELLER_ACTIONS.get(actionName);
        if (action == null) throw ApiException.notFound("ROUTE_NOT_FOUND", "Unknown fulfillment action");
        SellerOrder so = lock(sellerOrderId);
        if (!so.getStoreId().equals(managedStoreId))
            throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        String from = so.getStatus().name();
        Status next = transition(so, action, reason);
        switch (next) {
            case ACCEPTED -> events.notifyCustomer(so, "ORDER_ACCEPTED", "Đơn hàng đã được xác nhận",
                    "Cửa hàng đã xác nhận đơn hàng của bạn.", "accepted", from, "SELLER");
            case DISPATCHED -> {
                // Reserved stock leaves Inventory at the handoff; it never waits for delivery or COD.
                events.inventory(so, "CONSUME");
                events.notifyCustomer(so, "ORDER_DISPATCHED", "Đơn hàng đang được giao",
                        "Đơn hàng đã được giao cho đơn vị vận chuyển. Vui lòng chuẩn bị tiền mặt khi nhận hàng.", "dispatched", from, "SELLER");
            }
            case DELIVERED -> events.notifyCustomer(so, "ORDER_DELIVERED", "Đã giao hàng thành công",
                    "Đơn hàng đã được giao. Bạn có thể đánh giá sản phẩm.", "delivered", from, "SELLER");
            case REJECTED -> {
                events.inventory(so, "RELEASE");
                events.notifyCustomer(so, "ORDER_REJECTED", "Cửa hàng từ chối đơn hàng",
                        "Lý do: " + so.getStatusReason(), "rejected", from, "SELLER");
            }
            default -> { }
        }
        return so;
    }

    /** The buyer may cancel a Store order only before the Seller accepts it. */
    @Transactional
    public SellerOrder customerCancel(String customerId, UUID orderId, UUID sellerOrderId, String reason) {
        SellerOrder so = lock(sellerOrderId);
        if (!so.getOrder().getId().equals(orderId) || !so.getOrder().getCustomerId().equals(customerId))
            throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        String from = so.getStatus().name();
        transition(so, Action.CANCEL, reason == null || reason.isBlank() ? "Khách hàng hủy đơn" : reason);
        events.inventory(so, "RELEASE");
        events.notifyStore(so, "ORDER_CANCELLED", "Khách hàng đã hủy đơn",
                "Đơn " + shortId(so) + " đã bị khách hàng hủy.", "cancelled", from, "CUSTOMER");
        return so;
    }

    @Transactional
    public SellerOrder collectCod(String adminId, UUID sellerOrderId) {
        SellerOrder so = lock(sellerOrderId);
        try {
            so.collectCod(adminId, clock.instant());
        } catch (IllegalStateException invalid) {
            throw ApiException.conflict("COD_NOT_COLLECTABLE", invalid.getMessage());
        }
        events.notifyStore(so, "COD_COLLECTED", "Đã thu tiền COD",
                "Sàn đã nhận tiền COD cho đơn " + shortId(so) + ".", "cod-collected", so.getStatus().name(), "ADMIN");
        return so;
    }

    @Transactional
    public SellerOrder settle(String adminId, UUID sellerOrderId) {
        SellerOrder so = lock(sellerOrderId);
        try {
            so.settle(adminId, clock.instant());
        } catch (IllegalStateException invalid) {
            throw ApiException.conflict("SETTLEMENT_NOT_ALLOWED", invalid.getMessage());
        }
        events.notifyStore(so, "SETTLED", "Đã đối soát thanh toán",
                "Đơn " + shortId(so) + " đã được thanh toán " + so.getSellerNet().toPlainString() + " ₫ (sau phí sàn).", "settled", so.getStatus().name(), "ADMIN");
        return so;
    }

    private Status transition(SellerOrder so, Action action, String reason) {
        try {
            Status next = so.apply(action, reason, Instant.now(clock));
            so.getOrder().refreshStatus();
            return next;
        } catch (IllegalArgumentException missingReason) {
            throw ApiException.badRequest("VALIDATION_FAILED", "A reason is required");
        } catch (IllegalStateException invalid) {
            throw ApiException.conflict("ORDER_INVALID_TRANSITION", "Action " + action + " is not allowed from " + so.getStatus());
        }
    }

    private SellerOrder lock(UUID id) {
        return sellerOrders.lockById(id).orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
    }

    private static String shortId(SellerOrder so) {
        return so.getOrder().getId().toString().substring(0, 8).toUpperCase();
    }
}
