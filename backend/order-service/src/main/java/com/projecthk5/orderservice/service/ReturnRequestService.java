package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.security.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TV3 #155. A durable customer Return is distinct from SellerOrder fulfillment, COD and Inventory.
 * Return creation and Finance settlement acquire the very same SellerOrder PESSIMISTIC_WRITE lock.
 * No automatic refund, RELEASE, CONSUME or restock takes place in this service.
 */
@Service
public class ReturnRequestService {
    private static final Duration WINDOW = Duration.ofDays(7);
    private final SellerOrderRepository sellerOrders;
    private final OrderRepository orders;
    private final JdbcTemplate db;
    private final OrderEvents events;
    private final Clock clock;

    @Autowired
    public ReturnRequestService(SellerOrderRepository sellerOrders, OrderRepository orders,
                                JdbcTemplate db, OrderEvents events) {
        this(sellerOrders, orders, db, events, Clock.systemUTC());
    }

    ReturnRequestService(SellerOrderRepository sellerOrders, OrderRepository orders, JdbcTemplate db,
                         OrderEvents events, Clock clock) {
        this.sellerOrders = sellerOrders;
        this.orders = orders;
        this.db = db;
        this.events = events;
        this.clock = clock;
    }

    public record ReturnView(UUID requestId, UUID orderId, UUID storeOrderId,
                             String status, String reason, Instant requestedAt) {}
    public record Created(ReturnView request, boolean newRequest) {}

    @Transactional
    public Created request(String customerId, UUID orderId, UUID storeOrderId, String key, String inputReason) {
        requireKey(key);
        String reason = normalizeReason(inputReason);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(storeOrderId, "storeOrderId");

        // The same lock is acquired by FulfillmentService.settle. All eligibility and
        // idempotency checks run after this lock, preventing accept-return + settle races.
        SellerOrder sellerOrder = sellerOrders.lockById(storeOrderId)
                .orElseThrow(ReturnRequestService::notFound);
        Order order = sellerOrder.getOrder();
        if (!order.getId().equals(orderId) || !order.getCustomerId().equals(customerId)) {
            throw notFound(); // Never reveal another buyer's Store order.
        }

        String fingerprint = fingerprint(orderId, storeOrderId, reason);
        ReturnView replay = findByCustomerKey(customerId, key);
        if (replay != null) {
            if (!replay.orderId().equals(orderId) || !replay.storeOrderId().equals(storeOrderId)
                    || !fingerprint.equals(savedFingerprint(customerId, key))) {
                throw ApiException.conflict("RETURN_IDEMPOTENCY_MISMATCH",
                        "Idempotency-Key was used for a different return request");
            }
            // A committed result is authoritative even after 7 days or after state transitions.
            return new Created(replay, false);
        }

        Instant now = clock.instant();
        Instant delivered = sellerOrder.getDeliveredAt();
        if (!"COD".equals(order.getPaymentMethod())
                || sellerOrder.getStatus() != SellerOrderLifecycle.Status.DELIVERED
                || sellerOrder.getSettledAt() != null
                || sellerOrder.getCodStatus() == SellerOrder.CodStatus.VOID
                || delivered == null || now.isBefore(delivered)
                || now.isAfter(delivered.plus(WINDOW))) {
            throw ApiException.conflict("RETURN_NOT_ELIGIBLE", "Return is unavailable for this Store order");
        }
        if (hasBlockingReturnForSellerOrder(storeOrderId)) {
            throw ApiException.conflict("RETURN_ALREADY_OPEN", "A return is already open for this Store order");
        }

        UUID requestId = UUID.randomUUID();
        db.update("insert into return_requests "
                    + "(id,order_id,seller_order_id,customer_id,idempotency_key,request_fingerprint,"
                    + "reason,status,requested_at,updated_at) "
                    + "values (?,?,?,?,?,?,?,'REQUESTED',?,?)",
                requestId, orderId, storeOrderId, customerId, key, fingerprint,
                reason, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        // Transactional outbox: notify only the right Store and never include shipping/PII.
        events.notifyStore(sellerOrder, "RETURN_REQUESTED", "Khách hàng yêu cầu trả hàng",
                "Có yêu cầu trả hàng cho đơn của cửa hàng.", "return-" + requestId,
                sellerOrder.getStatus().name(), "CUSTOMER");
        return new Created(new ReturnView(requestId, orderId, storeOrderId, "REQUESTED", reason, now), true);
    }

    /** Used ONLY after FulfillmentService has locked the SellerOrder row. */
    @Transactional(readOnly = true)
    public boolean hasBlockingReturnForSellerOrder(UUID storeOrderId) {
        Integer count = db.queryForObject("select count(*) from return_requests where seller_order_id=? "
                        + "and status in ('REQUESTED','APPROVED','RECEIVED')",
                Integer.class, storeOrderId);
        return count != null && count > 0;
    }

    /** Buyer-readable state, checked using the authenticated Order owner. */
    @Transactional(readOnly = true)
    public List<ReturnView> mine(String customerId, UUID orderId) {
        orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(ReturnRequestService::notFound);
        return db.query("select id,order_id,seller_order_id,status,reason,requested_at "
                        + "from return_requests where order_id=? and customer_id=? order by requested_at desc",
                (rs, i) -> new ReturnView(rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class), rs.getObject("seller_order_id", UUID.class),
                        rs.getString("status"), rs.getString("reason"),
                        rs.getTimestamp("requested_at").toInstant()), orderId, customerId);
    }

    private ReturnView findByCustomerKey(String customerId, String key) {
        return db.query("select id,order_id,seller_order_id,status,reason,requested_at "
                + "from return_requests where customer_id=? and idempotency_key=?",
                (rs, i) -> new ReturnView(rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class), rs.getObject("seller_order_id", UUID.class),
                        rs.getString("status"), rs.getString("reason"),
                        rs.getTimestamp("requested_at").toInstant()), customerId, key)
                .stream().findFirst().orElse(null);
    }

    private String savedFingerprint(String customerId, String key) {
        return db.queryForObject("select request_fingerprint from return_requests "
                + "where customer_id=? and idempotency_key=?", String.class, customerId, key).strip();
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.equals(key.trim())
                || key.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Idempotency-Key must be 1-128 valid characters");
        }
    }

    private static String normalizeReason(String raw) {
        if (raw == null) throw ApiException.badRequest("VALIDATION_FAILED", "Return reason is required");
        String result = raw.strip();
        if (result.isBlank() || result.length() > 500 || result.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Return reason must contain 1-500 UTF-16 units");
        }
        return result;
    }

    private static String fingerprint(UUID orderId, UUID storeOrderId, String reason) {
        String value = "customer-return-v1\u0000" + orderId + "\u0000" + storeOrderId + "\u0000" + reason;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
    }
}
