package com.projecthk5.sellerservice.voucherlimit;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.sellerservice.voucher.VoucherService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Voucher-limit approvals and voucher publish/resume serialize on the SAME stores(id) row. */
@Service
public class VoucherLimitService {
    private final JdbcTemplate db;
    private final VoucherService vouchers;
    private final Clock clock;
    public VoucherLimitService(JdbcTemplate db, VoucherService vouchers, @Qualifier("voucherClock") Clock clock) {
        this.db=db; this.vouchers=vouchers; this.clock=clock;
    }
    public record Grant(int grantedLimit, Instant validFrom, Instant validUntil, String sourceRequestId) { }
    public record Submission(RequestView view, boolean replay) { }
    public record LimitView(int used, int limit, int defaultLimit, Grant grant) { }
    public record RequestView(UUID id, UUID storeId, int requestedLimit, String reason, String budgetNote,
                              Instant requestedFrom, Instant requestedUntil, String status,
                              Integer approvedLimit, Instant approvedFrom, Instant approvedUntil,
                              String decidedBy, Instant decidedAt, String decisionReason,
                              String version, Instant createdAt) { }
    private static final String COLUMNS="id,store_id,requested_limit,reason,budget_note,requested_from,requested_until,status,"
            +"approved_limit,approved_from,approved_until,decided_by,decided_at,decision_reason,version,created_at";
    private static final org.springframework.jdbc.core.RowMapper<RequestView> ROW = (rs,n) -> new RequestView(
            rs.getObject("id",UUID.class),rs.getObject("store_id",UUID.class),rs.getInt("requested_limit"),
            rs.getString("reason"),rs.getString("budget_note"),instant(rs,"requested_from"),
            instant(rs,"requested_until"),rs.getString("status"),
            (rs.getObject("approved_limit") instanceof Number number ? number.intValue() : null),nullableInstant(rs,"approved_from"),
            nullableInstant(rs,"approved_until"),rs.getString("decided_by"),
            nullableInstant(rs,"decided_at"),rs.getString("decision_reason"),
            Long.toString(rs.getLong("version")),instant(rs,"created_at"));
    private static Instant instant(ResultSet rs,String c)throws SQLException {return rs.getObject(c,OffsetDateTime.class).toInstant();}
    private static Instant nullableInstant(ResultSet rs,String c)throws SQLException {
        OffsetDateTime d=rs.getObject(c,OffsetDateTime.class);return d==null?null:d.toInstant();
    }
    private static OffsetDateTime utc(Instant i){return OffsetDateTime.ofInstant(i,ZoneOffset.UTC);}
    private Instant now(){return clock.instant();}
    private static ApiException conflict(String code){return ApiException.conflict(code,code);}
    private static ApiException missing(){return ApiException.notFound("VOUCHER_LIMIT_REQUEST_NOT_FOUND","Request not found");}
    private static ApiException invalid(){return ApiException.badRequest("VALIDATION_FAILED","Invalid voucher limit decision");}

    private UUID ownStore() {
        Actor actor=Actor.require("SELLER");
        List<UUID> rows=db.query("SELECT st.id FROM stores st JOIN seller_profiles sp ON sp.id=st.seller_id "
                +"WHERE sp.owner_reference=?",(rs,n)->rs.getObject(1,UUID.class),actor.id());
        if(rows.isEmpty())throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        return rows.get(0);
    }
    private void lock(UUID id){
        var rows=db.query("SELECT id FROM stores WHERE id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),id);
        if(rows.size()!=1)throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
    }
    private record Status(String seller,String store,String owner){}
    private Status status(UUID id){
        var rows=db.query("SELECT sp.status seller_status,st.status store_status,sp.owner_reference "
                +"FROM stores st JOIN seller_profiles sp ON sp.id=st.seller_id WHERE st.id=?",
                (rs,n)->new Status(rs.getString(1),rs.getString(2),rs.getString(3)),id);
        if(rows.size()!=1)throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        return rows.get(0);
    }
    private void eligible(UUID id){
        Status state=status(id);
        if(!"APPROVED".equals(state.seller()))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "SELLER_NOT_APPROVED","Seller must be approved");
        if(!"OPEN".equals(state.store()))throw conflict("STORE_CLOSED");
    }
    private RequestView request(UUID id,UUID storeId,boolean lock){
        String sql="SELECT "+COLUMNS+" FROM voucher_limit_requests WHERE id=?"
                +(storeId==null?"":" AND store_id=?")+(lock?" FOR UPDATE":"");
        List<RequestView> found=storeId==null?db.query(sql,ROW,id):db.query(sql,ROW,id,storeId);
        if(found.size()!=1)throw missing();
        return found.get(0);
    }
    private static long version(RequestView r){return Long.parseLong(r.version());}
    private static void requiredVersion(RequestView r,long expected){
        if(expected<0||version(r)!=expected)throw conflict("VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT");
        if(!"PENDING".equals(r.status()))throw conflict("VOUCHER_LIMIT_REQUEST_INVALID_STATE");
    }
    private void audit(UUID id,String before,String after,String reason,String who,Instant now){
        db.update("INSERT INTO voucher_limit_request_history(request_id,from_status,to_status,reason,actor,created_at) "
                +"VALUES (?,?,?,?,?,?)",id,before,after,reason,who,utc(now));
    }
    private static String fingerprint(VoucherLimitJson.Submit d){
        String text=d.requestedLimit()+"\n"+d.reason()+"\n"+d.budgetNote()+"\n"+d.requestedFrom()+"\n"+d.requestedUntil();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void key(String value){
        if(value==null||value.isBlank()||value.length()>128||!value.equals(value.strip()))
            throw ApiException.badRequest("VALIDATION_FAILED","Idempotency-Key required");
    }

    @Transactional(readOnly=true)
    public LimitView limit(){
        UUID storeId=ownStore();Instant moment=now();int used=vouchers.used(storeId,moment);
        List<Grant> grants=db.query("SELECT granted_limit,valid_from,valid_until,source_request_id "
                +"FROM store_voucher_limits WHERE store_id=?",
                (rs,n)->new Grant(rs.getInt(1),instant(rs,"valid_from"),instant(rs,"valid_until"),
                        rs.getObject(4,UUID.class).toString()),storeId);
        Grant grant=grants.isEmpty()?null:grants.get(0);
        int effective=grant!=null&&!moment.isBefore(grant.validFrom())&&moment.isBefore(grant.validUntil())
                ?grant.grantedLimit():20;
        return new LimitView(used,effective,20,grant);
    }
    @Transactional(readOnly=true)
    public List<RequestView> mine(int page,int size){return listing("WHERE store_id=?",page,size,ownStore());}
    @Transactional(readOnly=true)
    public List<RequestView> adminList(String state,int page,int size){
        Actor.require("ADMIN");
        if(state!=null&&!List.of("PENDING","APPROVED","REJECTED","CANCELLED").contains(state))throw invalid();
        return state==null?listing("",page,size):listing("WHERE status=?",page,size,state);
    }
    private List<RequestView> listing(String clause,int page,int size,Object...params){
        if(page<0||page>20_000||size<1||size>50)throw invalid();
        String order=clause.contains("status=?")?" ORDER BY created_at ASC,id ASC":" ORDER BY created_at DESC,id DESC";
        Object[] args=java.util.Arrays.copyOf(params,params.length+2);args[args.length-2]=size;args[args.length-1]=page*size;
        return db.query("SELECT "+COLUMNS+" FROM voucher_limit_requests "+clause+order+" LIMIT ? OFFSET ?",ROW,args);
    }
    @Transactional(readOnly=true)
    public RequestView detail(UUID id){Actor.require("ADMIN");return request(id,null,false);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Submission submit(String idem,VoucherLimitJson.Submit d){
        key(idem);UUID store=ownStore();lock(store);
        Instant moment=now();
        String hash=fingerprint(d);
        List<UUID> existing=db.query("SELECT id FROM voucher_limit_requests WHERE store_id=? AND idempotency_key=?",
                (rs,n)->rs.getObject(1,UUID.class),store,idem);
        if(!existing.isEmpty()){
            UUID prev=existing.get(0);
            String old=db.queryForObject("SELECT request_fingerprint FROM voucher_limit_requests WHERE id=?",String.class,prev);
            if(!hash.equals(old))throw conflict("IDEMPOTENCY_KEY_REUSED");
            return new Submission(request(prev,store,false),true);
        }
        eligible(store);
        if(!d.requestedUntil().isAfter(moment))throw invalid();
        Integer pending=db.queryForObject("SELECT count(*) FROM voucher_limit_requests WHERE store_id=? AND status='PENDING'",
                Integer.class,store);
        if(pending!=null&&pending>0)throw conflict("VOUCHER_LIMIT_REQUEST_PENDING_EXISTS");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO voucher_limit_requests(id,store_id,idempotency_key,request_fingerprint,"
                +"requested_limit,reason,budget_note,requested_from,requested_until,status,created_at,updated_at) "
                +"VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?)",id,store,idem,hash,d.requestedLimit(),d.reason(),
                d.budgetNote(),utc(d.requestedFrom()),utc(d.requestedUntil()),utc(moment),utc(moment));
        audit(id,null,"PENDING",d.reason(),Actor.current().id(),moment);
        return new Submission(request(id,store,false),false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestView cancel(UUID id,long expected){
        UUID store=ownStore();lock(store);RequestView old=request(id,store,true);requiredVersion(old,expected);
        Instant moment=now();
        int updated=db.update("UPDATE voucher_limit_requests SET status='CANCELLED',version=version+1,updated_at=? "
                +"WHERE id=? AND version=? AND status='PENDING'",utc(moment),id,expected);
        if(updated!=1)throw conflict("VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT");
        audit(id,"PENDING","CANCELLED",null,Actor.current().id(),moment);
        return request(id,store,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestView decide(UUID id,String action,long expected,Integer requestedApproved,
                              Instant from,Instant until,String reason){
        Actor admin=Actor.require("ADMIN");
        if(!List.of("approve","reject").contains(action))throw invalid();
        // Initial read is only to locate the row lock. Decision is re-read after locking Store.
        RequestView observed=request(id,null,false);
        lock(observed.storeId());
        RequestView old=request(id,null,true);
        requiredVersion(old,expected);
        Status state=status(old.storeId());
        if("approve".equals(action)){
            if(admin.id().equals(state.owner()))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "SELLER_SELF_MODERATION","Store owner cannot approve their own grant");
            eligible(old.storeId());
            Instant moment=now();
            if(requestedApproved==null||from==null||until==null||requestedApproved<21
                    ||requestedApproved>old.requestedLimit()||from.isBefore(old.requestedFrom())
                    ||until.isAfter(old.requestedUntil())||!until.isAfter(from)||!until.isAfter(moment))throw invalid();
            boolean partial=requestedApproved<old.requestedLimit()||from.isAfter(old.requestedFrom())
                    ||until.isBefore(old.requestedUntil());
            if(partial&&(reason==null||reason.isBlank()))throw invalid();
            int updated=db.update("UPDATE voucher_limit_requests SET status='APPROVED',approved_limit=?,approved_from=?,"
                    +"approved_until=?,decided_by=?,decided_at=?,decision_reason=?,version=version+1,updated_at=? "
                    +"WHERE id=? AND status='PENDING' AND version=?",requestedApproved,utc(from),utc(until),
                    admin.id(),utc(moment),reason,utc(moment),id,expected);
            if(updated!=1)throw conflict("VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT");
            db.update("INSERT INTO store_voucher_limits(store_id,granted_limit,valid_from,valid_until,source_request_id,"
                    +"decided_by,decided_at,version) VALUES (?,?,?,?,?,?,?,0) "
                    +"ON CONFLICT(store_id) DO UPDATE SET granted_limit=EXCLUDED.granted_limit,"
                    +"valid_from=EXCLUDED.valid_from,valid_until=EXCLUDED.valid_until,"
                    +"source_request_id=EXCLUDED.source_request_id,decided_by=EXCLUDED.decided_by,"
                    +"decided_at=EXCLUDED.decided_at,version=store_voucher_limits.version+1",
                    old.storeId(),requestedApproved,utc(from),utc(until),id,admin.id(),utc(moment));
            audit(id,"PENDING","APPROVED",reason,admin.id(),moment);
        }else{
            if(reason==null||reason.isBlank())throw invalid();
            Instant moment=now();
            int updated=db.update("UPDATE voucher_limit_requests SET status='REJECTED',decision_reason=?,decided_by=?,decided_at=?,"
                    +"version=version+1,updated_at=? WHERE id=? AND status='PENDING' AND version=?",
                    reason,admin.id(),utc(moment),utc(moment),id,expected);
            if(updated!=1)throw conflict("VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT");
            audit(id,"PENDING","REJECTED",reason,admin.id(),moment);
        }
        return request(id,null,false);
    }
}
