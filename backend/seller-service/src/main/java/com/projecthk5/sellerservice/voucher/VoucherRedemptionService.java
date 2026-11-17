package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voucher C2 usage (M01-VOUCHER-C2-v2). Only order-service calls this, over the internal token.
 * It never calculates money: it returns the immutable PUBLISHED terms and counts usage.
 * Every write locks the voucher row first, so usage_limit and MD18 cannot be exceeded.
 */
@Service
public class VoucherRedemptionService {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{4,20}");
    private static final Pattern KEY = Pattern.compile("order-[0-9a-f-]{36}-store-[0-9A-Za-z-]{1,60}");

    private final JdbcTemplate db;
    private final Clock clock;

    public VoucherRedemptionService(JdbcTemplate db, @Qualifier("voucherClock") Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Terms frozen at publish (only DRAFT may be edited), so order-service may price with them. */
    public record Terms(UUID voucherId, UUID storeId, String code, String discountType, long discountValue,
                        Long maxDiscount, long minOrderAmount) {}
    public record Redemption(String redemptionKey, UUID voucherId, String status) {}

    private record Row(UUID id, UUID storeId, String code, String type, long value, Long maxDiscount,
                       long minOrder, int usageLimit, int used, Instant startsAt, Instant endsAt,
                       String status, String storeStatus, String sellerStatus) {}

    private static final String SELECT = "SELECT v.id,v.store_id,v.code,v.discount_type,v.discount_value,"
            + "v.max_discount,v.min_order_amount,v.usage_limit,v.used_count,v.starts_at,v.ends_at,v.status,"
            + "st.status store_status,seller.status seller_status FROM vouchers v "
            + "JOIN stores st ON st.id=v.store_id JOIN seller_profiles seller ON seller.id=st.seller_id ";

    private List<Row> rows(String where, Object... args) {
        return db.query(SELECT + where, (rs, n) -> new Row(rs.getObject("id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("code"), rs.getString("discount_type"),
                rs.getBigDecimal("discount_value").longValueExact(),
                rs.getBigDecimal("max_discount") == null ? null : rs.getBigDecimal("max_discount").longValueExact(),
                rs.getBigDecimal("min_order_amount").longValueExact(), rs.getInt("usage_limit"),
                rs.getInt("used_count"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("status"),
                rs.getString("store_status"), rs.getString("seller_status")), args);
    }

    /** Read-only check for preview and Checkout pricing. Usage is checked again, under lock, by redeem. */
    @Transactional(readOnly = true)
    public Terms quote(UUID storeId, String code, String customerId) {
        String normalized = code(code);
        requireCustomer(customerId);
        if (storeId == null) throw invalid("storeId required");
        List<Row> found = rows("WHERE v.store_id=? AND v.code=?", storeId, normalized);
        if (found.size() != 1) throw rejected(404, "VOUCHER_NOT_FOUND", "Voucher not found for this Store");
        Row row = found.get(0);
        requireUsable(row, clock.instant());
        if (activeUse(row.id(), customerId)) throw rejected(409, "VOUCHER_ALREADY_USED", "Customer already used this voucher");
        return terms(row);
    }

    /**
     * MD18/MD22: consume one use atomically, idempotent by redemptionKey.
     * Replaying the same key returns the same ACTIVE redemption; a RELEASED key is never re-consumed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Redemption redeem(String redemptionKey, UUID voucherId, UUID storeId, String code,
                             String customerId, UUID orderId) {
        String key = key(redemptionKey);
        String normalized = code(code);
        requireCustomer(customerId);
        if (voucherId == null || storeId == null || orderId == null) throw invalid("voucherId, storeId and orderId required");
        if (!key.startsWith("order-" + orderId + "-store-" + storeId)) throw invalid("redemptionKey does not match order and Store");
        List<Row> locked = rows("WHERE v.id=? FOR UPDATE OF v", voucherId);
        if (locked.size() != 1 || !locked.get(0).storeId().equals(storeId) || !locked.get(0).code().equals(normalized))
            throw rejected(404, "VOUCHER_NOT_FOUND", "Voucher not found for this Store");
        Row row = locked.get(0);
        var previous = db.query("SELECT voucher_id,customer_id,status FROM voucher_redemptions WHERE redemption_key=? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)}, key);
        if (!previous.isEmpty()) {
            Object[] p = previous.get(0);
            if ("RELEASED".equals(p[2])) throw rejected(409, "VOUCHER_REDEMPTION_RELEASED", "This redemption was already released");
            if (!voucherId.equals(p[0]) || !customerId.equals(p[1]))
                throw rejected(409, "VOUCHER_REDEMPTION_MISMATCH", "redemptionKey already used for another voucher or customer");
            return new Redemption(key, voucherId, "ACTIVE");
        }
        requireUsable(row, clock.instant());
        if (activeUse(row.id(), customerId)) throw rejected(409, "VOUCHER_ALREADY_USED", "Customer already used this voucher");
        Instant now = clock.instant();
        try {
            db.update("INSERT INTO voucher_redemptions(redemption_key,voucher_id,customer_id,order_id,status,created_at) "
                    + "VALUES (?,?,?,?,'ACTIVE',?)", key, voucherId, customerId, orderId, utc(now));
        } catch (org.springframework.dao.DuplicateKeyException raced) {
            // A concurrent release tombstone won the key: this redemption must not consume usage.
            throw rejected(409, "VOUCHER_REDEMPTION_RELEASED", "This redemption was already released");
        }
        int updated = db.update("UPDATE vouchers SET used_count=used_count+1 WHERE id=? AND used_count<usage_limit", voucherId);
        if (updated != 1) throw new IllegalStateException("Voucher usage changed under lock");
        return new Redemption(key, voucherId, "ACTIVE");
    }

    /**
     * MD19: restore one use when a Store order is cancelled or rejected before delivery,
     * or when Checkout definitely failed after redeem. Idempotent; a release that arrives
     * before its redeem leaves a RELEASED tombstone so the late redeem is refused.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Redemption release(String redemptionKey, UUID orderId) {
        String key = key(redemptionKey);
        if (orderId == null || !key.startsWith("order-" + orderId + "-store-")) throw invalid("redemptionKey does not match order");
        Instant now = clock.instant();
        var previous = db.query("SELECT voucher_id,status FROM voucher_redemptions WHERE redemption_key=?",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)}, key);
        if (previous.isEmpty()) {
            int inserted = db.update("INSERT INTO voucher_redemptions(redemption_key,order_id,status,created_at,released_at) "
                    + "VALUES (?,?,'RELEASED',?,?) ON CONFLICT (redemption_key) DO NOTHING", key, orderId, utc(now), utc(now));
            if (inserted == 1) return new Redemption(key, null, "RELEASED");
            previous = db.query("SELECT voucher_id,status FROM voucher_redemptions WHERE redemption_key=?",
                    (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)}, key);
        }
        UUID voucherId = (UUID) previous.get(0)[0];
        if ("RELEASED".equals(previous.get(0)[1])) return new Redemption(key, voucherId, "RELEASED");
        // Same lock order as redeem: voucher row first, then the redemption row.
        db.query("SELECT id FROM vouchers WHERE id=? FOR UPDATE", (rs, n) -> rs.getObject(1, UUID.class), voucherId);
        int changed = db.update("UPDATE voucher_redemptions SET status='RELEASED',released_at=? "
                + "WHERE redemption_key=? AND status='ACTIVE'", utc(now), key);
        if (changed == 1) {
            int restored = db.update("UPDATE vouchers SET used_count=used_count-1 WHERE id=? AND used_count>0", voucherId);
            if (restored != 1) throw new IllegalStateException("Voucher usage count is inconsistent");
        }
        return new Redemption(key, voucherId, "RELEASED");
    }

    private boolean activeUse(UUID voucherId, String customerId) {
        Integer n = db.queryForObject("SELECT count(*) FROM voucher_redemptions WHERE voucher_id=? AND customer_id=? "
                + "AND status='ACTIVE'", Integer.class, voucherId, customerId);
        return n != null && n > 0;
    }

    /** MD22: the voucher must be live at the moment of use; Seller and Store must still be eligible. */
    private static void requireUsable(Row row, Instant now) {
        if (!"PUBLISHED".equals(row.status()) || now.isBefore(row.startsAt()) || !now.isBefore(row.endsAt()))
            throw rejected(409, "VOUCHER_NOT_ACTIVE", "Voucher is not active");
        if (!"APPROVED".equals(row.sellerStatus()) || !"OPEN".equals(row.storeStatus()))
            throw rejected(409, "VOUCHER_NOT_ACTIVE", "Store is not accepting vouchers");
        if (row.used() >= row.usageLimit()) throw rejected(409, "VOUCHER_USAGE_EXHAUSTED", "Voucher has no uses left");
    }

    private static Terms terms(Row row) {
        return new Terms(row.id(), row.storeId(), row.code(), row.type(), row.value(), row.maxDiscount(), row.minOrder());
    }

    private static String code(String code) {
        if (code == null) throw invalid("code required");
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) throw rejected(404, "VOUCHER_NOT_FOUND", "Voucher not found for this Store");
        return normalized;
    }

    private static String key(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw invalid("Invalid redemptionKey");
        return key;
    }

    private static void requireCustomer(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.length() > 200) throw invalid("customerId required");
    }

    private static OffsetDateTime utc(Instant t) { return OffsetDateTime.ofInstant(t, ZoneOffset.UTC); }
    private static ApiException invalid(String message) { return ApiException.badRequest("VALIDATION_FAILED", message); }
    private static ApiException rejected(int status, String code, String message) {
        return new ApiException(org.springframework.http.HttpStatus.valueOf(status), code, message);
    }
}
