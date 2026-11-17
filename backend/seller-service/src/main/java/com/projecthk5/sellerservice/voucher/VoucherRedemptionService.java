package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Seller-side ledger. Its callers must supply identities and lifecycle facts from durable Order data. */
@Service
@ConditionalOnProperty(name = "hk5.voucher-c2.enabled", havingValue = "true")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class VoucherRedemptionService {
    public enum State { RESERVED, COMMITTED, RELEASED }

    public enum ReleaseReason {
        CHECKOUT_FAILED, CANCELLED_BEFORE_DISPATCH, REJECTED_BEFORE_DISPATCH
    }

    public record Quote(UUID voucherId, long version, String code, VoucherRules.DiscountType type,
                        long discountValue, Long maxDiscount, long merchandiseAmount, long discountAmount) {
        public long gross() {
            return merchandiseAmount - discountAmount;
        }
    }

    public record Reservation(UUID sellerOrderId, UUID storeId, String customerId, Quote quote,
                              State state, ReleaseReason releaseReason) { }

    public record Redeem(UUID sellerOrderId, UUID storeId, String customerId, UUID voucherId,
                         long expectedVersion, long merchandiseAmount, long expectedDiscount) {
        public Redeem {
            Objects.requireNonNull(sellerOrderId, "sellerOrderId");
            Objects.requireNonNull(storeId, "storeId");
            Objects.requireNonNull(voucherId, "voucherId");
            validateCustomer(customerId);
            if (expectedVersion < 0 || merchandiseAmount < 1 || merchandiseAmount > VoucherRules.MAX_VND
                    || expectedDiscount < 0 || expectedDiscount >= merchandiseAmount) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Invalid voucher quote");
            }
        }
    }

    private record Voucher(UUID id, long version, VoucherRules.StoredStatus status, VoucherRules.Spec spec) { }

    private static final String VOUCHER = """
            SELECT id, version, status, code, name, discount_type, discount_value, max_discount,
                   min_order_amount, usage_limit, starts_at, ends_at
            FROM vouchers WHERE store_id = ? AND
            """;

    private static final RowMapper<Voucher> VOUCHER_ROW = (rs, row) -> {
        var cap = rs.getBigDecimal("max_discount");
        return new Voucher(rs.getObject("id", UUID.class), rs.getLong("version"),
                VoucherRules.StoredStatus.valueOf(rs.getString("status")),
                new VoucherRules.Spec(rs.getString("code"), rs.getString("name"),
                        VoucherRules.DiscountType.valueOf(rs.getString("discount_type")),
                        rs.getBigDecimal("discount_value").longValueExact(), cap == null ? null : cap.longValueExact(),
                        rs.getBigDecimal("min_order_amount").longValueExact(), rs.getInt("usage_limit"),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant()));
    };

    private static final RowMapper<Reservation> RESERVATION_ROW = (rs, row) -> {
        String reason = rs.getString("release_reason");
        return new Reservation(rs.getObject("seller_order_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("customer_id"),
                new Quote(rs.getObject("voucher_id", UUID.class), rs.getLong("voucher_version"),
                        rs.getString("voucher_code"), VoucherRules.DiscountType.valueOf(rs.getString("discount_type")),
                        rs.getLong("discount_value"), rs.getObject("max_discount", Long.class),
                        rs.getLong("merchandise_amount"), rs.getLong("discount_amount")),
                State.valueOf(rs.getString("state")), reason == null ? null : ReleaseReason.valueOf(reason));
    };

    private final JdbcTemplate db;
    private final VoucherService vouchers;
    private final Clock clock;

    public VoucherRedemptionService(JdbcTemplate db, VoucherService vouchers, @Qualifier("voucherClock") Clock clock) {
        this.db = db;
        this.vouchers = vouchers;
        this.clock = clock;
    }

    /** An advisory quote; all criteria are re-evaluated under lock when consuming a slot. */
    @Transactional(readOnly = true)
    public Quote quote(UUID storeId, String customerId, String code, long merchandiseAmount) {
        Objects.requireNonNull(storeId, "storeId");
        validateCustomer(customerId);
        String normalizedCode = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{4,20}")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Invalid voucher code");
        }
        Voucher voucher = findVoucher("code = ?", storeId, normalizedCode);
        return priceIfAvailable(storeId, customerId, merchandiseAmount, voucher);
    }

    public Reservation redeem(Redeem request) {
        // Use the C1 lock order so State changes and admissions serialize for the same Store.
        vouchers.lockStore(request.storeId());
        Reservation existing = find(request.sellerOrderId());
        if (existing != null) {
            return replay(request, existing);
        }
        Voucher voucher = findVoucher("id = ?", request.storeId(), request.voucherId());
        if (voucher.version() != request.expectedVersion()) {
            throw changed();
        }
        Quote price = priceIfAvailable(request.storeId(), request.customerId(), request.merchandiseAmount(), voucher);
        if (price.discountAmount() != request.expectedDiscount()) {
            throw changed();
        }

        insert(request, price, State.RESERVED, null);
        return replay(request, find(request.sellerOrderId()));
    }

    /**
     * Terminal proof from Order: fence a delayed redeem, including when a status
     * read found no row yet. A tombstone occupies neither the customer slot nor quota.
     */
    public Reservation abortCheckout(Redeem request, Quote quote) {
        if (request == null || quote == null || !request.voucherId().equals(quote.voucherId())
                || request.expectedVersion() != quote.version()
                || request.merchandiseAmount() != quote.merchandiseAmount()
                || request.expectedDiscount() != quote.discountAmount()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Abort requires the original quote");
        }
        vouchers.lockStore(request.storeId());
        insert(request, quote, State.RELEASED, ReleaseReason.CHECKOUT_FAILED);
        Reservation row = replay(request, find(request.sellerOrderId()));
        if (!row.quote().equals(quote)) throw invalidTransition();
        return release(request.storeId(), request.sellerOrderId(), ReleaseReason.CHECKOUT_FAILED);
    }

    private void insert(Redeem request, Quote price, State state, ReleaseReason reason) {
        // The primary key also protects against conflicting requests that locked different Stores.
        db.update("""
                INSERT INTO voucher_redemptions (seller_order_id, voucher_id, store_id, customer_id,
                    voucher_version, voucher_code, discount_type, discount_value, max_discount,
                    merchandise_amount, discount_amount, state, release_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (seller_order_id) DO NOTHING
                """, request.sellerOrderId(), price.voucherId(), request.storeId(), request.customerId(),
                price.version(), price.code(), price.type().name(), price.discountValue(), price.maxDiscount(),
                price.merchandiseAmount(), price.discountAmount(), state.name(), reason == null ? null : reason.name());
    }

    @Transactional(readOnly = true)
    public Reservation status(UUID storeId, UUID sellerOrderId) {
        Reservation row = find(sellerOrderId);
        if (row == null || !row.storeId().equals(storeId)) {
            throw ApiException.notFound("VOUCHER_REDEMPTION_NOT_FOUND", "Voucher redemption not found");
        }
        return row;
    }

    public Reservation commit(UUID storeId, UUID sellerOrderId) {
        vouchers.lockStore(storeId);
        Reservation row = status(storeId, sellerOrderId);
        if (row.state() == State.COMMITTED) {
            return row;
        }
        if (row.state() != State.RESERVED) {
            throw invalidTransition();
        }
        return transition(row, State.COMMITTED, null);
    }

    /** Only definitive Checkout failure or a confirmed cancellation/rejection before dispatch may release a slot. */
    public Reservation release(UUID storeId, UUID sellerOrderId, ReleaseReason reason) {
        Objects.requireNonNull(reason, "release reason");
        vouchers.lockStore(storeId);
        Reservation row = status(storeId, sellerOrderId);
        if (row.state() == State.RELEASED && row.releaseReason() == reason) {
            return row;
        }
        State required = reason == ReleaseReason.CHECKOUT_FAILED ? State.RESERVED : State.COMMITTED;
        if (row.state() != required) {
            throw invalidTransition();
        }
        return transition(row, State.RELEASED, reason);
    }

    private Reservation transition(Reservation row, State state, ReleaseReason reason) {
        int changed = db.update("""
                UPDATE voucher_redemptions SET state = ?, release_reason = ?
                WHERE seller_order_id = ? AND store_id = ? AND state = ?
                """, state.name(), reason == null ? null : reason.name(),
                row.sellerOrderId(), row.storeId(), row.state().name());
        if (changed != 1) {
            throw invalidTransition();
        }
        return status(row.storeId(), row.sellerOrderId());
    }

    private Reservation replay(Redeem request, Reservation row) {
        if (!row.storeId().equals(request.storeId()) || !row.customerId().equals(request.customerId())
                || !row.quote().voucherId().equals(request.voucherId())
                || row.quote().version() != request.expectedVersion()
                || row.quote().merchandiseAmount() != request.merchandiseAmount()
                || row.quote().discountAmount() != request.expectedDiscount()) {
            throw ApiException.conflict("VOUCHER_REDEMPTION_KEY_REUSED", "SellerOrder already has different voucher data");
        }
        return row;
    }

    private Quote priceIfAvailable(UUID storeId, String customerId, long merchandiseAmount, Voucher voucher) {
        var spec = voucher.spec();
        if (VoucherRules.display(voucher.status(), spec.startsAt(), spec.endsAt(), clock.instant())
                != VoucherRules.DisplayStatus.ACTIVE) {
            throw changed();
        }
        boolean eligible = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM stores s JOIN seller_profiles p ON s.seller_id = p.id
                               WHERE s.id = ? AND s.status = 'OPEN' AND p.status = 'APPROVED')
                """, Boolean.class, storeId));
        if (!eligible) {
            throw changed();
        }
        boolean alreadyUsed = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM voucher_redemptions
                               WHERE voucher_id = ? AND customer_id = ? AND state IN ('RESERVED', 'COMMITTED'))
                """, Boolean.class, voucher.id(), customerId));
        if (alreadyUsed) {
            throw ApiException.conflict("VOUCHER_ALREADY_USED", "Customer already used this voucher");
        }
        long count = db.queryForObject("""
                SELECT count(*) FROM voucher_redemptions
                WHERE voucher_id = ? AND state IN ('RESERVED', 'COMMITTED')
                """, Long.class, voucher.id());
        if (count >= spec.usageLimit()) {
            throw ApiException.conflict("VOUCHER_USAGE_LIMIT_REACHED", "Voucher usage limit reached");
        }
        long discount = VoucherRedemptionPricing.discount(spec, merchandiseAmount);
        return new Quote(voucher.id(), voucher.version(), spec.code(), spec.discountType(),
                spec.discountValue(), spec.maxDiscount(), merchandiseAmount, discount);
    }

    private Voucher findVoucher(String predicate, UUID storeId, Object key) {
        List<Voucher> result = db.query(VOUCHER + predicate, VOUCHER_ROW, storeId, key);
        if (result.isEmpty()) {
            throw ApiException.notFound("VOUCHER_NOT_FOUND", "Voucher not found for this Store");
        }
        return result.getFirst();
    }

    private Reservation find(UUID sellerOrderId) {
        return db.query("""
                SELECT seller_order_id, voucher_id, store_id, customer_id, voucher_version, voucher_code,
                       discount_type, discount_value, max_discount, merchandise_amount, discount_amount,
                       state, release_reason
                FROM voucher_redemptions WHERE seller_order_id = ?
                """, RESERVATION_ROW, sellerOrderId).stream().findFirst().orElse(null);
    }

    private static void validateCustomer(String id) {
        if (id == null || id.isBlank() || id.length() > 200) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Invalid customer identity");
        }
    }

    private static ApiException changed() {
        return ApiException.conflict("VOUCHER_CHANGED", "Voucher is unavailable or differs from the quote");
    }

    private static ApiException invalidTransition() {
        return ApiException.conflict("VOUCHER_REDEMPTION_INVALID_STATE", "Voucher redemption transition is invalid");
    }
}
