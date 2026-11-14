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

/** Voucher C1: manage Store vouchers, no Checkout redemption or money transfer. */
@Service
public class VoucherService {
    private final JdbcTemplate db;
    private final Clock clock;
    public VoucherService(JdbcTemplate db,@Qualifier("voucherClock") Clock clock) {
        this.db=db; this.clock=clock;
    }

    private static final String COLUMNS = "id, store_id, code, name, discount_type, discount_value, "
            + "max_discount, min_order_amount, usage_limit, starts_at, ends_at, status, version";

    private record Data(UUID id,UUID storeId,VoucherRules.Spec spec,
                        VoucherRules.StoredStatus stored,long version) {}
    public record View(UUID id,String code,String name,String discountType,long discountValue,
                       Long maxDiscount,long minOrderAmount,int usageLimit,
                       Instant startsAt,Instant endsAt,String status,String version) {}

    private static final RowMapper<Data> ROW=(rs,n)->new Data(
            rs.getObject("id",UUID.class),rs.getObject("store_id",UUID.class),
            new VoucherRules.Spec(rs.getString("code"),rs.getString("name"),
                    VoucherRules.DiscountType.valueOf(rs.getString("discount_type")),
                    rs.getBigDecimal("discount_value").longValueExact(),
                    rs.getBigDecimal("max_discount")==null?null:rs.getBigDecimal("max_discount").longValueExact(),
                    rs.getBigDecimal("min_order_amount").longValueExact(),
                    rs.getInt("usage_limit"),time(rs,"starts_at"),time(rs,"ends_at")),
            VoucherRules.StoredStatus.valueOf(rs.getString("status")),rs.getLong("version"));
    private static Instant time(ResultSet rs,String field) throws SQLException {
        return rs.getObject(field,OffsetDateTime.class).toInstant();
    }
    private static OffsetDateTime utc(Instant t) { return OffsetDateTime.ofInstant(t,ZoneOffset.UTC); }
    private Instant now() { return clock.instant(); }
    private static ApiException invalid(String msg) { return ApiException.badRequest("VALIDATION_FAILED",msg); }
    private static ApiException missing() { return ApiException.notFound("VOUCHER_NOT_FOUND","Voucher not found"); }
    private static ApiException conflict(String code,String msg) { return ApiException.conflict(code,msg); }

    /** Read and retract can be available to suspended sellers using persisted ownership. */
    private UUID ownedStore() {
        Actor actor=Actor.current();
        List<UUID> ids=db.query("SELECT st.id FROM stores st JOIN seller_profiles seller ON seller.id=st.seller_id "
                 +"WHERE seller.owner_reference=?", (rs,n)->rs.getObject(1,UUID.class),actor.id());
        if(ids.isEmpty()) throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        if(ids.size()!=1) throw new IllegalStateException("More than one Store for Seller");
        return ids.get(0);
    }
    /** Do not change lock ordering: TV3 publish/resume and TV2 grant approve share this first lock. */
    public void lockStore(UUID storeId) {
        List<UUID> ids=db.query("SELECT id FROM stores WHERE id=? FOR UPDATE",
                (rs,n)->rs.getObject(1,UUID.class),storeId);
        if(ids.size()!=1) throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
    }
    private void requireEligible(UUID storeId) {
        Actor.require("SELLER");
        record Status(String store,String seller) {}
        List<Status> s=db.query("SELECT st.status store_status,seller.status seller_status "
            +"FROM stores st JOIN seller_profiles seller ON seller.id=st.seller_id WHERE st.id=?",
            (rs,n)->new Status(rs.getString("store_status"),rs.getString("seller_status")),storeId);
        if(s.size()!=1) throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        if(!"APPROVED".equals(s.get(0).seller()))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "SELLER_NOT_APPROVED","Seller must be approved");
        if(!"OPEN".equals(s.get(0).store()))
            throw conflict("STORE_CLOSED","Store must be open");
    }
    private Data data(UUID storeId,UUID voucherId,boolean lock) {
        if(voucherId==null) throw missing();
        List<Data> data=db.query("SELECT "+COLUMNS+" FROM vouchers WHERE id=? AND store_id=?"
            +(lock?" FOR UPDATE":""),ROW,voucherId,storeId);
        if(data.size()!=1) throw missing();
        return data.get(0);
    }
    private View view(Data d,Instant moment) {
        VoucherRules.Spec s=d.spec();
        return new View(d.id(),s.code(),s.name(),s.discountType().name(),s.discountValue(),
            s.maxDiscount(),s.minOrderAmount(),s.usageLimit(),s.startsAt(),s.endsAt(),
            VoucherRules.display(d.stored(),s.startsAt(),s.endsAt(),moment).name(),
            Long.toString(d.version()));
    }

    @Transactional(readOnly=true)
    public View get(UUID id) {
        Actor.require("SELLER");
        UUID storeId=ownedStore();
        Instant moment=now();
        return view(data(storeId,id,false),moment);
    }

    @Transactional(readOnly=true)
    public List<View> list(String status,int page,int size) {
        if(page<0||page>20_000||size<1||size>50) throw invalid("Invalid pagination");
        Actor.require("SELLER");
        UUID storeId=ownedStore();
        Instant moment=now();
        String expr="CASE WHEN status='CANCELLED' THEN 'CANCELLED' "
            +"WHEN status='DRAFT' THEN 'DRAFT' WHEN ends_at <= ? THEN 'EXPIRED' "
            +"WHEN status='PAUSED' THEN 'PAUSED' WHEN starts_at > ? THEN 'SCHEDULED' ELSE 'ACTIVE' END";
        List<Object> args=new ArrayList<>();
        args.add(storeId);
        StringBuilder sql=new StringBuilder("SELECT "+COLUMNS+" FROM vouchers WHERE store_id=?");
        if(status!=null&&!status.isBlank()) {
            try { VoucherRules.DisplayStatus.valueOf(status); }
            catch(IllegalArgumentException e) { throw invalid("Invalid status filter"); }
            sql.append(" AND (").append(expr).append(")=?");
            args.add(utc(moment)); args.add(utc(moment)); args.add(status);
        }
        sql.append(" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add(page*size);
        return db.query(sql.toString(),ROW,args.toArray()).stream().map(d->view(d,moment)).toList();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View create(VoucherRules.Spec s) {
        if(s==null) throw invalid("Voucher specification required");
        UUID storeId=ownedStore();
        lockStore(storeId); requireEligible(storeId);
        Instant moment=now(); UUID id=UUID.randomUUID();
        try {
            db.update("INSERT INTO vouchers (id,store_id,code,name,discount_type,discount_value,max_discount,"
                +"min_order_amount,usage_limit,starts_at,ends_at,status,version,created_at,updated_at) "
                +"VALUES (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?)",
                id,storeId,s.code(),s.name(),s.discountType().name(),s.discountValue(),s.maxDiscount(),
                s.minOrderAmount(),s.usageLimit(),utc(s.startsAt()),utc(s.endsAt()),utc(moment),utc(moment));
        } catch(DuplicateKeyException e) {
            throw conflict("VOUCHER_CODE_EXISTS","Voucher code is already used in this Store");
        }
        return view(data(storeId,id,false),moment);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View edit(UUID id,long expectedVersion,VoucherRules.Spec spec) {
        if(spec==null) throw invalid("Voucher specification required");
        UUID storeId=ownedStore();
        lockStore(storeId); requireEligible(storeId);
        Data old=data(storeId,id,true);
        checkVersion(old,expectedVersion);
        if(old.stored()!=VoucherRules.StoredStatus.DRAFT)
            throw conflict("VOUCHER_INVALID_STATE","Only DRAFT may be edited");
        Instant moment=now();
        try {
            int count=db.update("UPDATE vouchers SET code=?,name=?,discount_type=?,discount_value=?,"
                +"max_discount=?,min_order_amount=?,usage_limit=?,starts_at=?,ends_at=?,"
                +"version=version+1,updated_at=GREATEST(updated_at,?) WHERE id=? AND store_id=? AND version=?",
                spec.code(),spec.name(),spec.discountType().name(),spec.discountValue(),spec.maxDiscount(),
                spec.minOrderAmount(),spec.usageLimit(),utc(spec.startsAt()),utc(spec.endsAt()),
                utc(moment),id,storeId,expectedVersion);
            if(count!=1) throw conflict("VOUCHER_VERSION_CONFLICT","Voucher version changed");
        } catch(DuplicateKeyException e) {
            throw conflict("VOUCHER_CODE_EXISTS","Voucher code is already used in this Store");
        }
        return view(data(storeId,id,false),moment);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View act(UUID id,String action,long expectedVersion) {
        if(!List.of("publish","pause","resume","cancel").contains(action))
            throw invalid("Unsupported Voucher action");
        UUID storeId=ownedStore();
        lockStore(storeId);
        Data before=data(storeId,id,true);
        checkVersion(before,expectedVersion);
        Instant moment=now();
        var display=VoucherRules.display(before.stored(),before.spec().startsAt(),before.spec().endsAt(),moment);
        VoucherRules.StoredStatus target;
        switch(action) {
            case "publish" -> {
                if(before.stored()!=VoucherRules.StoredStatus.DRAFT||!before.spec().endsAt().isAfter(moment))
                    throw conflict("VOUCHER_INVALID_STATE","Cannot publish Voucher");
                requireEligible(storeId); checkQuota(storeId,before.spec().startsAt(),moment);
                target=VoucherRules.StoredStatus.PUBLISHED;
            }
            case "resume" -> {
                if(display!=VoucherRules.DisplayStatus.PAUSED)
                    throw conflict("VOUCHER_INVALID_STATE","Cannot resume Voucher");
                requireEligible(storeId); checkQuota(storeId,before.spec().startsAt(),moment);
                target=VoucherRules.StoredStatus.PUBLISHED;
            }
            case "pause" -> {
                if(before.stored()!=VoucherRules.StoredStatus.PUBLISHED||display==VoucherRules.DisplayStatus.EXPIRED)
                    throw conflict("VOUCHER_INVALID_STATE","Cannot pause Voucher");
                target=VoucherRules.StoredStatus.PAUSED;
            }
            case "cancel" -> {
                if(display==VoucherRules.DisplayStatus.CANCELLED||display==VoucherRules.DisplayStatus.EXPIRED)
                    throw conflict("VOUCHER_INVALID_STATE","Cannot cancel Voucher");
                target=VoucherRules.StoredStatus.CANCELLED;
            }
            default -> throw invalid("Unsupported Voucher action");
        }
        int count=db.update("UPDATE vouchers SET status=?,version=version+1,"
            +"updated_at=GREATEST(updated_at,?) WHERE id=? AND store_id=? AND version=?",
            target.name(),utc(moment),id,storeId,expectedVersion);
        if(count!=1) throw conflict("VOUCHER_VERSION_CONFLICT","Voucher version changed");
        db.update("INSERT INTO voucher_status_history (id,voucher_id,from_status,to_status,actor,created_at)"
            +" VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,before.stored().name(),target.name(),
            Actor.current().id(),utc(moment));
        return view(data(storeId,id,false),moment);
    }
    private static void checkVersion(Data d,long expected) {
        if(expected<0||d.version()!=expected)
            throw conflict("VOUCHER_VERSION_CONFLICT","Voucher version changed");
    }
    private void checkQuota(UUID storeId,Instant startsAt,Instant moment) {
        int used=used(storeId,moment);
        VoucherRules.Grant grant=grant(storeId);
        int limit=VoucherRules.permittedLimit(used,startsAt,moment,grant);
        if(used>=limit) throw new VoucherQuotaException(used,limit);
    }
    /** Caller must serialize all writes by locking stores(id). */
    public int used(UUID storeId,Instant moment) {
        Integer count=db.queryForObject("SELECT count(*) FROM vouchers WHERE store_id=? AND "
            +"status='PUBLISHED' AND ends_at>?",Integer.class,storeId,utc(moment));
        return count==null?0:count;
    }
    public int effectiveLimit(UUID storeId,Instant moment) {
        VoucherRules.Grant current=grant(storeId);
        return current!=null&&current.activeAt(moment)?current.limit():VoucherRules.DEFAULT_LIMIT;
    }
    private VoucherRules.Grant grant(UUID storeId) {
        List<VoucherRules.Grant> grants=db.query("SELECT granted_limit,valid_from,valid_until "
            +"FROM store_voucher_limits WHERE store_id=?",
            (rs,n)->new VoucherRules.Grant(rs.getInt("granted_limit"),time(rs,"valid_from"),time(rs,"valid_until")),
            storeId);
        if(grants.size()>1) throw new IllegalStateException("Duplicated Store grant");
        return grants.isEmpty()?null:grants.get(0);
    }
}
