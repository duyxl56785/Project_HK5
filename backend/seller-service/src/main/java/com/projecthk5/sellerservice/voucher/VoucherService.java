package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TV3 C1 implementation. V10 owns vouchers; TV2 V9 owns store_voucher_limits.
 * All Store writers MUST lock stores(id) before grant reads, counts and voucher writes.
 */
@Service
public class VoucherService {
    private final JdbcTemplate db;
    private final Clock clock;

    public VoucherService(JdbcTemplate db, @Qualifier("voucherClock") Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    private static final String COLUMNS = "id, store_id, code, name, discount_type, discount_value, "
            + "max_discount, min_order_amount, usage_limit, starts_at, ends_at, status, version";

    private record Data(UUID id, UUID storeId, VoucherRules.Spec spec,
                        VoucherRules.StoredStatus stored, long version) { }

    public record View(UUID id, String code, String name, String discountType, long discountValue,
                       Long maxDiscount, long minOrderAmount, int usageLimit,
                       Instant startsAt, Instant endsAt, String status, String version) { }

    private static final RowMapper<Data> ROW = (rs, n) -> new Data(
            rs.getObject("id", UUID.class), rs.getObject("store_id", UUID.class),
            new VoucherRules.Spec(rs.getString("code"), rs.getString("name"),
                    VoucherRules.DiscountType.valueOf(rs.getString("discount_type")),
                    rs.getBigDecimal("discount_value").longValueExact(),
                    rs.getBigDecimal("max_discount") == null ? null
                            : rs.getBigDecimal("max_discount").longValueExact(),
                    rs.getBigDecimal("min_order_amount").longValueExact(),
                    rs.getInt("usage_limit"), instant(rs, "starts_at"), instant(rs, "ends_at")),
            VoucherRules.StoredStatus.valueOf(rs.getString("status")), rs.getLong("version"));

    private static Instant instant(ResultSet rs, String field) throws SQLException {
        return rs.getObject(field, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant now() { return clock.instant(); }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("VALIDATION_FAILED", message);
    }
    private static ApiException notFound() {
        return ApiException.notFound("VOUCHER_NOT_FOUND", "Voucher not found");
    }
    private static ApiException conflict(String code, String message) {
        return ApiException.conflict(code, message);
    }

    private UUID ownedStore() {
        Actor actor = Actor.require("SELLER");
        List<UUID> ids = db.query("SELECT st.id FROM stores st JOIN seller_profiles seller "
                        + "ON seller.id=st.seller_id WHERE seller.owner_reference=?", (rs,n) -> rs.getObject(1, UUID.class),
                actor.id());
        if (ids.isEmpty()) throw ApiException.notFound("STORE_NOT_FOUND", "Store not found");
        return ids.get(0);
    }

    private void lockStore(UUID storeId) {
        List<UUID> locked = db.query("SELECT id FROM stores WHERE id=? FOR UPDATE",
                (rs,n) -> rs.getObject(1,UUID.class), storeId);
        if (locked.size() != 1) throw ApiException.notFound("STORE_NOT_FOUND", "Store not found");
    }

    private void requireEligible(UUID storeId) {
        record Statuses(String store, String seller) { }
        List<Statuses> rows = db.query("SELECT st.status AS store_status, seller.status AS seller_status "
                        + "FROM stores st JOIN seller_profiles seller ON seller.id=st.seller_id WHERE st.id=?",
                (rs,n)->new Statuses(rs.getString("store_status"),rs.getString("seller_status")),storeId);
        if (rows.size() != 1) throw ApiException.notFound("STORE_NOT_FOUND", "Store not found");
        if (!"APPROVED".equals(rows.get(0).seller()))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "SELLER_NOT_APPROVED", "Seller must be approved");
        if (!"OPEN".equals(rows.get(0).store()))
            throw conflict("STORE_CLOSED", "Store must be open");
    }

    private Data data(UUID storeId, UUID voucherId, boolean locking) {
        if (voucherId == null) throw notFound();
        List<Data> items = db.query("SELECT " + COLUMNS + " FROM vouchers WHERE id=? AND store_id=?"
                        + (locking ? " FOR UPDATE" : ""), ROW, voucherId, storeId);
        if (items.size() != 1) throw notFound();
        return items.get(0);
    }

    private View view(Data row, Instant moment) {
        VoucherRules.Spec s = row.spec();
        return new View(row.id(), s.code(), s.name(), s.discountType().name(), s.discountValue(),
                s.maxDiscount(), s.minOrderAmount(), s.usageLimit(), s.startsAt(), s.endsAt(),
                VoucherRules.display(row.stored(), s.startsAt(), s.endsAt(), moment).name(),
                Long.toString(row.version()));
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        UUID storeId=ownedStore();
        Instant moment=now();
        return view(data(storeId,id,false),moment);
    }

    /** DB-side status filter precedes pagination so it cannot skip matching rows. */
    @Transactional(readOnly = true)
    public List<View> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || page > 20_000)
            throw invalid("Invalid pagination");
        UUID storeId = ownedStore();
        Instant moment=now();
        final String expr = "CASE WHEN status='CANCELLED' THEN 'CANCELLED' "
                + "WHEN status='DRAFT' THEN 'DRAFT' WHEN ends_at <= ? THEN 'EXPIRED' "
                + "WHEN status='PAUSED' THEN 'PAUSED' "
                + "WHEN starts_at > ? THEN 'SCHEDULED' ELSE 'ACTIVE' END";
        List<Object> params = new ArrayList<>();
        params.add(storeId);
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM vouchers WHERE store_id=?");
        if (status != null && !status.isBlank()) {
            try { VoucherRules.DisplayStatus.valueOf(status); }
            catch (IllegalArgumentException bad) { throw invalid("Invalid status filter"); }
            sql.append(" AND (").append(expr).append(")=?");
            params.add(utc(moment));
            params.add(utc(moment));
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return db.query(sql.toString(), ROW, params.toArray()).stream().map(item->view(item,moment)).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View create(VoucherRules.Spec spec) {
        if (spec == null) throw invalid("Voucher specification required");
        UUID storeId=ownedStore();
        lockStore(storeId);
        requireEligible(storeId);
        Instant moment=now();
        UUID id=UUID.randomUUID();
        try {
            db.update("INSERT INTO vouchers (id,store_id,code,name,discount_type,discount_value,max_discount,"
                            + "min_order_amount,usage_limit,starts_at,ends_at,status,version,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?)",
                    id,storeId,spec.code(),spec.name(),spec.discountType().name(),spec.discountValue(),
                    spec.maxDiscount(),spec.minOrderAmount(),spec.usageLimit(),utc(spec.startsAt()),
                    utc(spec.endsAt()),utc(moment),utc(moment));
        } catch (DuplicateKeyException exists) {
            throw conflict("VOUCHER_CODE_EXISTS","Voucher code is already used in this Store");
        }
        return view(data(storeId,id,false), moment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View edit(UUID id, long expectedVersion, VoucherRules.Spec spec) {
        if (spec == null) throw invalid("Voucher specification required");
        UUID storeId=ownedStore();
        lockStore(storeId);
        requireEligible(storeId);
        Data original=data(storeId,id,true);
        verifyVersion(original,expectedVersion);
        if (original.stored()!=VoucherRules.StoredStatus.DRAFT)
            throw conflict("VOUCHER_INVALID_STATE","Only DRAFT can be edited");
        Instant moment=now();
        try {
            int updated=db.update("UPDATE vouchers SET code=?,name=?,discount_type=?,discount_value=?,"
                            + "max_discount=?,min_order_amount=?,usage_limit=?,starts_at=?,ends_at=?,"
                            + "version=version+1,updated_at=GREATEST(updated_at,?) WHERE id=? AND store_id=? AND version=?",
                    spec.code(),spec.name(),spec.discountType().name(),spec.discountValue(),spec.maxDiscount(),
                    spec.minOrderAmount(),spec.usageLimit(),utc(spec.startsAt()),utc(spec.endsAt()),
                    utc(moment),id,storeId,expectedVersion);
            if (updated!=1) throw conflict("VOUCHER_VERSION_CONFLICT","Stale voucher version");
        } catch (DuplicateKeyException exists) {
            throw conflict("VOUCHER_CODE_EXISTS","Voucher code is already used in this Store");
        }
        return view(data(storeId,id,false),moment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public View act(UUID id, String action, long expectedVersion) {
        if (!List.of("publish","pause","resume","cancel").contains(action))
            throw invalid("Unsupported Voucher action");
        UUID storeId=ownedStore();
        // Global ordering: first lock Store row, then voucher, then count/grant. TV2 approve must do likewise.
        lockStore(storeId);
        Data original=data(storeId,id,true);
        verifyVersion(original,expectedVersion);
        Instant moment=now();
        var current=VoucherRules.display(original.stored(),original.spec().startsAt(),original.spec().endsAt(),moment);
        VoucherRules.StoredStatus target;
        switch(action) {
            case "publish" -> {
                if (original.stored()!=VoucherRules.StoredStatus.DRAFT || !original.spec().endsAt().isAfter(moment))
                    throw conflict("VOUCHER_INVALID_STATE", "Cannot publish this Voucher");
                requireEligible(storeId);
                checkQuota(storeId, original.spec().startsAt(),moment);
                target=VoucherRules.StoredStatus.PUBLISHED;
            }
            case "resume" -> {
                if (current!=VoucherRules.DisplayStatus.PAUSED)
                    throw conflict("VOUCHER_INVALID_STATE", "Cannot resume this Voucher");
                requireEligible(storeId);
                checkQuota(storeId, original.spec().startsAt(),moment);
                target=VoucherRules.StoredStatus.PUBLISHED;
            }
            case "pause" -> {
                if (original.stored()!=VoucherRules.StoredStatus.PUBLISHED
                        || current==VoucherRules.DisplayStatus.EXPIRED)
                    throw conflict("VOUCHER_INVALID_STATE", "Cannot pause this Voucher");
                // Seller may retract published vouchers even while suspended/Store closed.
                target=VoucherRules.StoredStatus.PAUSED;
            }
            case "cancel" -> {
                if (current==VoucherRules.DisplayStatus.CANCELLED || current==VoucherRules.DisplayStatus.EXPIRED)
                    throw conflict("VOUCHER_INVALID_STATE", "Cannot cancel this Voucher");
                target=VoucherRules.StoredStatus.CANCELLED;
            }
            default -> throw invalid("Unsupported Voucher action");
        }
        int updated=db.update("UPDATE vouchers SET status=?, version=version+1,updated_at=GREATEST(updated_at,?) "
                        + "WHERE id=? AND store_id=? AND version=?",
                target.name(),utc(moment),id,storeId,expectedVersion);
        if (updated!=1) throw conflict("VOUCHER_VERSION_CONFLICT","Stale voucher version");
        db.update("INSERT INTO voucher_status_history (id,voucher_id,from_status,to_status,actor,created_at) "
                        + "VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,original.stored().name(),target.name(),
                Actor.current().id(),utc(moment));
        return view(data(storeId,id,false),moment);
    }

    private static void verifyVersion(Data row,long expected) {
        if (expected < 0 || row.version()!=expected)
            throw conflict("VOUCHER_VERSION_CONFLICT","Stale voucher version");
    }

    private void checkQuota(UUID storeId, Instant startsAt, Instant moment) {
        int used=used(storeId,moment);
        VoucherRules.Grant grant=grant(storeId);
        int limit=VoucherRules.permittedLimit(used,startsAt,moment,grant);
        if (used >= limit) throw new VoucherQuotaException(used,limit);
    }

    /** TV2's GET /voucher-limit must reuse this count; writes require callers to hold Store FOR UPDATE. */
    public int used(UUID storeId, Instant moment) {
        Integer count=db.queryForObject("SELECT count(*) FROM vouchers "
                        + "WHERE store_id=? AND status='PUBLISHED' AND ends_at>?",Integer.class,storeId,utc(moment));
        return count == null ? 0 : count;
    }

    /** Return current Store grant from TV2 V9, otherwise default 20. Not for historical voucher eligibility. */
    public int effectiveLimit(UUID storeId, Instant moment) {
        VoucherRules.Grant current=grant(storeId);
        return current!=null && current.activeAt(moment) ? current.limit() : VoucherRules.DEFAULT_LIMIT;
    }

    private VoucherRules.Grant grant(UUID storeId) {
        List<VoucherRules.Grant> grants=db.query("SELECT granted_limit,valid_from,valid_until "
                        + "FROM store_voucher_limits WHERE store_id=?",
                (rs,n)->new VoucherRules.Grant(rs.getInt("granted_limit"),
                        instant(rs,"valid_from"),instant(rs,"valid_until")),storeId);
        if (grants.size()>1) throw new IllegalStateException("Duplicate Store grant");
        return grants.isEmpty() ? null : grants.get(0);
    }
}
