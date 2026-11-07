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
import org.springframework.dao.DataIntegrityViolationException;
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
    /**
     * Seller projection deliberately omits the buyer identity and delivery address.  A receipt
     * reference is an auditable Seller attestation of physical receipt; it is not an Inventory
     * restock receipt (that contract is not authorised yet).
     */
    public record SellerReturnView(UUID requestId, UUID orderId, UUID storeOrderId, String status,
                                   String reason, Instant requestedAt, Instant reviewedAt,
                                   Instant receivedAt, String receiptReference) {}
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
        if (!eligible(order.getPaymentMethod(), sellerOrder.getStatus(), sellerOrder.getCodStatus(),
                delivered, sellerOrder.getSettledAt(), now)) {
            throw ApiException.conflict("RETURN_NOT_ELIGIBLE", "Return is unavailable for this Store order");
        }
        if (hasBlockingReturnForSellerOrder(storeOrderId)) {
            throw ApiException.conflict("RETURN_ALREADY_OPEN", "A return is already open for this Store order");
        }

        UUID requestId = UUID.randomUUID();
        try {
            db.update("insert into return_requests "
                        + "(id,order_id,seller_order_id,customer_id,idempotency_key,request_fingerprint,"
                        + "reason,status,requested_at,updated_at) "
                        + "values (?,?,?,?,?,?,?,'REQUESTED',?,?)",
                    requestId, orderId, storeOrderId, customerId, key, fingerprint,
                    reason, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        } catch (DataIntegrityViolationException concurrent) {
            // The unique(customer,key) arbiter also protects concurrent same-key calls
            // against two different SellerOrders (which take different row locks).
            // Never leak SQL details or return a 500; rollback this whole transaction.
            throw ApiException.conflict("RETURN_IDEMPOTENCY_MISMATCH",
                    "Idempotency-Key already used or an active Return exists");
        }

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

    /** Seller-visible requests for the Store resolved from the authenticated principal. */
    @Transactional(readOnly = true)
    public List<SellerReturnView> forStore(String storeId) {
        return db.query("select r.id,r.order_id,r.seller_order_id,r.status,r.reason,r.requested_at,"
                        + "r.reviewed_at,r.received_at,r.receipt_reference "
                        + "from return_requests r join seller_orders s on s.id=r.seller_order_id "
                        + "where s.store_id=? order by r.requested_at desc",
                (rs, i) -> sellerView(rs), storeId);
    }

    /**
     * Serializes the Seller workflow with customer Return creation and Finance settlement on the
     * SellerOrder row.  No Inventory event is emitted here: Order is not authorised to call the
     * privileged INCREASE operation and a physical restock contract is still pending.
     */
    @Transactional
    public SellerReturnView sellerAction(String managedStoreId, String sellerId, UUID requestId,
                                         String actionName, String receiptReference) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sellerId, "sellerId");
        String action = normalizeAction(actionName);

        UUID sellerOrderId = db.query("select seller_order_id from return_requests where id=?",
                        (rs, i) -> rs.getObject(1, UUID.class), requestId)
                .stream().findFirst().orElseThrow(ReturnRequestService::notFound);
        SellerOrder sellerOrder = sellerOrders.lockById(sellerOrderId)
                .orElseThrow(ReturnRequestService::notFound);
        if (!sellerOrder.getStoreId().equals(managedStoreId)) throw notFound();

        SellerReturnView before = db.query("select id,order_id,seller_order_id,status,reason,requested_at,"
                        + "reviewed_at,received_at,receipt_reference from return_requests where id=?",
                (rs, i) -> sellerView(rs), requestId).stream().findFirst()
                .orElseThrow(ReturnRequestService::notFound);
        Instant now = clock.instant();
        int changed = switch (action) {
            case "approve" -> db.update("update return_requests set status='APPROVED',reviewed_by=?,"
                            + "reviewed_at=?,updated_at=? where id=? and status='REQUESTED'",
                    sellerId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), requestId);
            case "reject" -> db.update("update return_requests set status='REJECTED',reviewed_by=?,"
                            + "reviewed_at=?,updated_at=? where id=? and status='REQUESTED'",
                    sellerId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), requestId);
            case "receive" -> {
                String receipt = normalizeReceiptReference(receiptReference);
                yield db.update("update return_requests set status='RECEIVED',received_by=?,received_at=?,"
                                + "receipt_reference=?,updated_at=? where id=? and status='APPROVED'",
                        sellerId, java.sql.Timestamp.from(now), receipt, java.sql.Timestamp.from(now), requestId);
            }
            default -> throw new IllegalStateException("unreachable");
        };
        if (changed != 1) {
            throw ApiException.conflict("RETURN_INVALID_TRANSITION",
                    "Action " + action + " is not allowed from " + before.status());
        }

        // RETURN_REQUESTED is the only Return notification event currently agreed.  Do not
        // invent a customer event contract for Seller review/receipt transitions here.
        return db.query("select id,order_id,seller_order_id,status,reason,requested_at,reviewed_at,received_at,"
                        + "receipt_reference from return_requests where id=?",
                (rs, i) -> sellerView(rs), requestId).stream().findFirst().orElseThrow();
    }

    private static SellerReturnView sellerView(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp reviewed = rs.getTimestamp("reviewed_at");
        java.sql.Timestamp received = rs.getTimestamp("received_at");
        return new SellerReturnView(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("seller_order_id", UUID.class), rs.getString("status"), rs.getString("reason"),
                rs.getTimestamp("requested_at").toInstant(), reviewed == null ? null : reviewed.toInstant(),
                received == null ? null : received.toInstant(), rs.getString("receipt_reference"));
    }

    static String normalizeReceiptReference(String raw) {
        if (raw == null) throw ApiException.badRequest("VALIDATION_FAILED", "Receipt reference is required");
        String result = raw.strip();
        if (result.isBlank() || result.length() > 128 || result.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Receipt reference must contain 1-128 valid characters");
        }
        return result;
    }

    private static String normalizeAction(String raw) {
        if (raw == null || !(raw.equals("approve") || raw.equals("reject") || raw.equals("receive"))) {
            throw ApiException.notFound("ROUTE_NOT_FOUND", "Unknown return action");
        }
        return raw;
    }

    public record AdminReturnView(UUID requestId, UUID orderId, UUID storeOrderId, String storeId,
                                  String status, String reason, Instant requestedAt) {}

    private static final java.util.Set<String> STATUSES = java.util.Set.of("REQUESTED", "APPROVED", "REJECTED", "RECEIVED");
    private static final String ADMIN_SELECT = "select r.id,r.order_id,r.seller_order_id,s.store_id,r.status,r.reason,"
            + "r.requested_at from return_requests r join seller_orders s on s.id=r.seller_order_id ";

    /** Admin read-only oversight (#154): newest 200, optional status filter. No Admin write exists. */
    @Transactional(readOnly = true)
    public List<AdminReturnView> admin(String status) {
        if (status == null || status.isBlank()) {
            return db.query(ADMIN_SELECT + "order by r.requested_at desc limit 200", ReturnRequestService::adminRow);
        }
        if (!STATUSES.contains(status)) throw ApiException.badRequest("VALIDATION_FAILED", "Unknown return status");
        return db.query(ADMIN_SELECT + "where r.status=? order by r.requested_at desc limit 200",
                ReturnRequestService::adminRow, status);
    }

    @Transactional(readOnly = true)
    public AdminReturnView adminOne(UUID requestId) {
        return db.query(ADMIN_SELECT + "where r.id=?", ReturnRequestService::adminRow, requestId).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("RETURN_NOT_FOUND", "Return request not found"));
    }

    private static AdminReturnView adminRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new AdminReturnView(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("seller_order_id", UUID.class), rs.getString("store_id"), rs.getString("status"),
                rs.getString("reason"), rs.getTimestamp("requested_at").toInstant());
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

    /** Pure business rule for deterministic unit cutoff/Finance tests. */
    static boolean eligible(String paymentMethod, SellerOrderLifecycle.Status status,
                            SellerOrder.CodStatus cod, Instant delivered, Instant settled, Instant now) {
        return "COD".equals(paymentMethod) && status == SellerOrderLifecycle.Status.DELIVERED
                && settled == null && cod != SellerOrder.CodStatus.VOID
                && delivered != null && now != null && !now.isBefore(delivered)
                && !now.isAfter(delivered.plus(WINDOW));
    }

    static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.equals(key.trim())
                || key.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Idempotency-Key must be 1-128 valid characters");
        }
    }

    static String normalizeReason(String raw) {
        if (raw == null) throw ApiException.badRequest("VALIDATION_FAILED", "Return reason is required");
        String result = raw.strip();
        if (result.isBlank() || result.length() > 500 || result.chars().anyMatch(c -> (Character.isISOControl(c) && c != 10 && c != 13 && c != 9))) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Return reason must contain 1-500 UTF-16 units");
        }
        return result;
    }

    static String fingerprint(UUID orderId, UUID storeOrderId, String reason) {
        String value = "customer-return-v1|" + orderId + "|" + storeOrderId + "|" + reason.length() + ":" + reason;
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
