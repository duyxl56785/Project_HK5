package com.projecthk5.sellerservice.voucherlimit;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.sellerservice.voucher.VoucherRules;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Store-locked C1 approval flow. No monetary voucher redemption in this service. */
@Service
public class VoucherLimitService {
    private final JdbcTemplate db;
    private final VoucherService vouchers;
    private final Clock clock;
    public VoucherLimitService(JdbcTemplate db,VoucherService vouchers,
                               @Qualifier("voucherClock") Clock clock) {
        this.db=db; this.vouchers=vouchers; this.clock=clock;
    }
    public record RequestSpec(int requestedLimit,String reason,String budgetNote,Instant requestedFrom,Instant requestedUntil) {}
    public record Decision(long expectedVersion,Integer approvedLimit,Instant approvedFrom,
                           Instant approvedUntil,String reason) {}
    public record RequestView(UUID id,UUID storeId,int requestedLimit,String reason,String budgetNote,
                  Instant requestedFrom,Instant requestedUntil,String status,Integer approvedLimit,
                  Instant approvedFrom,Instant approvedUntil,String decidedBy,Instant decidedAt,
                  String decisionReason,String version,Instant createdAt) {}
    public record GrantView(int limit,Instant validFrom,Instant validUntil,UUID sourceRequestId,
                            String decidedBy,Instant decidedAt) {}
    public record LimitView(int used,int limit,int defaultLimit,GrantView grant) {}
    public record AdminDetail(RequestView request,int used) {}
    public record CreateResult(RequestView request,boolean created) {}
    private record Request(UUID id,UUID storeId,int requestedLimit,String reason,String budgetNote,
                  Instant requestedFrom,Instant requestedUntil,String status,Integer approvedLimit,
                  Instant approvedFrom,Instant approvedUntil,String decidedBy,Instant decidedAt,
                  String decisionReason,long version,Instant createdAt,String fingerprint) {}
    private static final String FIELDS="id,store_id,requested_limit,reason,budget_note,requested_from,"
        +"requested_until,status,approved_limit,approved_from,approved_until,decided_by,decided_at,"
        +"decision_reason,version,created_at,request_fingerprint";
    private static Instant ts(ResultSet rs,String name) throws SQLException {
        OffsetDateTime value=rs.getObject(name,OffsetDateTime.class);
        return value==null?null:value.toInstant();
    }
    private static OffsetDateTime utc(Instant x) {return OffsetDateTime.ofInstant(x,ZoneOffset.UTC);}
    private static final RowMapper<Request> ROW=(rs,n)->new Request(
        rs.getObject("id",UUID.class),rs.getObject("store_id",UUID.class),rs.getInt("requested_limit"),
        rs.getString("reason"),rs.getString("budget_note"),ts(rs,"requested_from"),ts(rs,"requested_until"),
        rs.getString("status"),rs.getObject("approved_limit",Integer.class),
        ts(rs,"approved_from"),ts(rs,"approved_until"),rs.getString("decided_by"),ts(rs,"decided_at"),
        rs.getString("decision_reason"),rs.getLong("version"),ts(rs,"created_at"),rs.getString("request_fingerprint"));
    private static ApiException bad(String msg){return ApiException.badRequest("VALIDATION_FAILED",msg);}
    private static ApiException conflict(String code,String msg){return ApiException.conflict(code,msg);}
    private static ApiException missing(){return ApiException.notFound("VOUCHER_LIMIT_REQUEST_NOT_FOUND","Request not found");}
    private Instant now(){return clock.instant();}
    private static RequestView view(Request r){
        return new RequestView(r.id(),r.storeId(),r.requestedLimit(),r.reason(),r.budgetNote(),
            r.requestedFrom(),r.requestedUntil(),r.status(),r.approvedLimit(),r.approvedFrom(),
            r.approvedUntil(),r.decidedBy(),r.decidedAt(),r.decisionReason(),Long.toString(r.version()),r.createdAt());
    }
    private UUID ownStore(){
        String owner=Actor.current().id();
        List<UUID> stores=db.query("SELECT st.id FROM stores st JOIN seller_profiles s ON s.id=st.seller_id "
            +"WHERE s.owner_reference=?", (rs,n)->rs.getObject(1,UUID.class),owner);
        if(stores.isEmpty()) throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        if(stores.size()!=1) throw new IllegalStateException("Multiple stores for Seller");
        return stores.get(0);
    }
    private void requireEligible(UUID storeId){
        Actor.require("SELLER");
        record States(String store,String seller){}
        List<States> states=db.query("SELECT st.status store_status,s.status seller_status "
            +"FROM stores st JOIN seller_profiles s ON s.id=st.seller_id WHERE st.id=?",
            (rs,n)->new States(rs.getString("store_status"),rs.getString("seller_status")),storeId);
        if(states.size()!=1) throw ApiException.notFound("STORE_NOT_FOUND","Store not found");
        if(!"APPROVED".equals(states.get(0).seller()))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "SELLER_NOT_APPROVED","Seller not approved");
        if(!"OPEN".equals(states.get(0).store())) throw conflict("STORE_CLOSED","Store is closed");
    }
    private void eligibleForAdminApprove(UUID storeId){
        record States(String store,String seller,String owner){}
        List<States> s=db.query("SELECT st.status store_status,sp.status seller_status,sp.owner_reference "
            +"FROM stores st JOIN seller_profiles sp ON sp.id=st.seller_id WHERE st.id=?",
            (rs,n)->new States(rs.getString("store_status"),rs.getString("seller_status"),
                rs.getString("owner_reference")),storeId);
        if(s.size()!=1) throw missing();
        if(s.get(0).owner().equals(Actor.current().id()))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                 "SELLER_SELF_MODERATION","Admin may not approve own Store");
        if(!"APPROVED".equals(s.get(0).seller())||!"OPEN".equals(s.get(0).store()))
            throw conflict("VOUCHER_LIMIT_REQUEST_INVALID_STATE","Seller or Store not eligible");
    }
    private Request row(UUID id,boolean lock){
        List<Request> rows=db.query("SELECT "+FIELDS+" FROM voucher_limit_requests WHERE id=?"
            +(lock?" FOR UPDATE":""),ROW,id);
        if(rows.size()!=1) throw missing();
        return rows.get(0);
    }
    private Request own(UUID storeId,UUID id,boolean lock){
        Request r=row(id,lock);
        if(!r.storeId().equals(storeId)) throw missing();
        return r;
    }
    private static String fingerprint(RequestSpec x){
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            // Length-delimited fields avoid accidental collisions such as ["a|b","c"] and ["a","b|c"].
            String[] fields={Integer.toString(x.requestedLimit()),x.reason(),
                x.budgetNote()==null?"":x.budgetNote(),x.requestedFrom().toString(),x.requestedUntil().toString()};
            for(String f:fields){
                byte[] bytes=f.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void validate(RequestSpec x,Instant now){
        if(x==null||x.requestedLimit()<21||x.requestedLimit()>50
           ||x.reason()==null||x.reason().isBlank()||x.reason().length()>500
           ||!x.reason().equals(x.reason().trim())
           ||(x.budgetNote()!=null&&(x.budgetNote().length()>500||!x.budgetNote().equals(x.budgetNote().trim())))
           ||x.requestedFrom()==null||x.requestedUntil()==null||!x.requestedUntil().isAfter(x.requestedFrom())
           ||!x.requestedUntil().isAfter(now)
           ||Duration.between(x.requestedFrom(),x.requestedUntil()).compareTo(Duration.ofDays(30))>0)
            throw bad("Invalid voucher limit request");
    }
    private static void key(String idempotency){
        if(idempotency==null||!idempotency.matches("[!-~]{1,128}"))
            throw bad("Idempotency-Key is required");
    }
    @Transactional(readOnly=true)
    public LimitView limit(){
        Actor.require("SELLER");
        UUID storeId=ownStore();
        Instant t=now(); int used=vouchers.used(storeId,t);
        List<GrantView> grants=db.query("SELECT granted_limit,valid_from,valid_until,source_request_id,"
             +"decided_by,decided_at FROM store_voucher_limits WHERE store_id=?",
            (rs,n)->new GrantView(rs.getInt("granted_limit"),ts(rs,"valid_from"),ts(rs,"valid_until"),
                rs.getObject("source_request_id",UUID.class),rs.getString("decided_by"),ts(rs,"decided_at")),storeId);
        GrantView g=grants.isEmpty()?null:grants.get(0);
        return new LimitView(used,g!=null&&!t.isBefore(g.validFrom())&&t.isBefore(g.validUntil())
            ?g.limit():20,20,g);
    }
    @Transactional(readOnly=true)
    public List<RequestView> mine(int page,int size){
        Actor.require("SELLER");
        checkPage(page,size);
        UUID storeId=ownStore();
        return db.query("SELECT "+FIELDS+" FROM voucher_limit_requests WHERE store_id=?"
            +" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",ROW,storeId,size,page*size)
            .stream().map(VoucherLimitService::view).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CreateResult create(RequestSpec x,String idem){
        key(idem);
        UUID store=ownStore();
        vouchers.lockStore(store); requireEligible(store);
        Instant t=now(); validate(x,t);
        String fp=fingerprint(x);
        List<Request> matches=db.query("SELECT "+FIELDS+" FROM voucher_limit_requests "
            +"WHERE store_id=? AND idempotency_key=?",ROW,store,idem);
        if(!matches.isEmpty()){
            if(!matches.get(0).fingerprint().equals(fp))
                throw conflict("IDEMPOTENCY_KEY_REUSED","Idempotency-Key reused with different payload");
            return new CreateResult(view(matches.get(0)),false);
        }
        Integer count=db.queryForObject("SELECT count(*) FROM voucher_limit_requests "
            +"WHERE store_id=? AND status='PENDING'",Integer.class,store);
        if(count!=null&&count>0)
            throw conflict("VOUCHER_LIMIT_REQUEST_PENDING_EXISTS","A request is already pending");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO voucher_limit_requests (id,store_id,requested_limit,reason,budget_note,"
            +"requested_from,requested_until,idempotency_key,request_fingerprint,status,created_at,updated_at) "
            +"VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?)",id,store,x.requestedLimit(),x.reason(),x.budgetNote(),
            utc(x.requestedFrom()),utc(x.requestedUntil()),idem,fp,utc(t),utc(t));
        history(id,null,"PENDING",null,Actor.current().id(),t);
        return new CreateResult(view(own(store,id,false)),true);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestView cancel(UUID id,long expected){
        UUID store=ownStore();
        vouchers.lockStore(store);
        Request r=own(store,id,true);
        checkVersion(r,expected);
        if(!r.status().equals("PENDING"))
            throw conflict("VOUCHER_LIMIT_REQUEST_INVALID_STATE","Request is no longer pending");
        Instant t=now();
        db.update("UPDATE voucher_limit_requests SET status='CANCELLED',version=version+1,"
            +"updated_at=GREATEST(updated_at,?) WHERE id=? AND store_id=?",utc(t),id,store);
        history(id,"PENDING","CANCELLED",null,Actor.current().id(),t);
        return view(own(store,id,false));
    }
    @Transactional(readOnly=true)
    public List<RequestView> queue(String status,int page,int size){
        Actor.require("ADMIN");checkPage(page,size);
        if(status!=null&&!status.isBlank()&&!List.of("PENDING","APPROVED","REJECTED","CANCELLED").contains(status))
            throw bad("Unknown request status");
        boolean filtered=status!=null&&!status.isBlank();
        String sql="SELECT "+FIELDS+" FROM voucher_limit_requests"
                +(filtered?" WHERE status=?":"")+" ORDER BY created_at ASC,id ASC LIMIT ? OFFSET ?";
        return (filtered?db.query(sql,ROW,status,size,page*size):db.query(sql,ROW,size,page*size))
            .stream().map(VoucherLimitService::view).toList();
    }
    @Transactional(readOnly=true)
    public AdminDetail detail(UUID id){
        Actor.require("ADMIN");
        Request r=row(id,false);
        return new AdminDetail(view(r),vouchers.used(r.storeId(),now()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestView decide(UUID id,String action,Decision input){
        Actor.require("ADMIN");
        if(!List.of("approve","reject").contains(action)) throw bad("Unsupported decision");
        if(input==null) throw bad("Decision body required");
        // Read the immutable Store key without locking request, then lock Store first.
        Request observed=row(id,false);
        vouchers.lockStore(observed.storeId());
        Request r=row(id,true);
        checkVersion(r,input.expectedVersion());
        if(!"PENDING".equals(r.status()))
            throw conflict("VOUCHER_LIMIT_REQUEST_INVALID_STATE","Request is no longer pending");
        Instant t=now();
        if("approve".equals(action)){
            eligibleForAdminApprove(r.storeId());
            if(input.approvedLimit()==null||input.approvedFrom()==null||input.approvedUntil()==null
               ||input.approvedLimit()<21||input.approvedLimit()>r.requestedLimit()
               ||input.approvedFrom().isBefore(r.requestedFrom())
               ||input.approvedUntil().isAfter(r.requestedUntil())
               ||!input.approvedUntil().isAfter(input.approvedFrom())
               ||!input.approvedUntil().isAfter(t)) throw bad("Invalid approved limit or date range");
            boolean modified=input.approvedLimit()<r.requestedLimit()
                ||!input.approvedFrom().equals(r.requestedFrom())
                ||!input.approvedUntil().equals(r.requestedUntil());
            if(modified&&(input.reason()==null||input.reason().isBlank())) throw bad("Decision reason required");
            validateReasonOptional(input.reason());
            db.update("INSERT INTO store_voucher_limits "
                +"(store_id,granted_limit,valid_from,valid_until,source_request_id,decided_by,decided_at,version) "
                +"VALUES (?,?,?,?,?,?,?,0) ON CONFLICT (store_id) DO UPDATE SET "
                +"granted_limit=EXCLUDED.granted_limit,valid_from=EXCLUDED.valid_from,"
                +"valid_until=EXCLUDED.valid_until,source_request_id=EXCLUDED.source_request_id,"
                +"decided_by=EXCLUDED.decided_by,decided_at=EXCLUDED.decided_at,"
                +"version=store_voucher_limits.version+1",
                r.storeId(),input.approvedLimit(),utc(input.approvedFrom()),utc(input.approvedUntil()),
                id,Actor.current().id(),utc(t));
            db.update("UPDATE voucher_limit_requests SET status='APPROVED',approved_limit=?,"
                +"approved_from=?,approved_until=?,decided_by=?,decided_at=?,decision_reason=?,"
                +"version=version+1,updated_at=GREATEST(updated_at,?) WHERE id=?",
                input.approvedLimit(),utc(input.approvedFrom()),utc(input.approvedUntil()),
                Actor.current().id(),utc(t),input.reason(),utc(t),id);
        }else{
            if(input.approvedLimit()!=null||input.approvedFrom()!=null||input.approvedUntil()!=null)
                throw bad("Reject must not contain approval fields");
            if(input.reason()==null||input.reason().isBlank()) throw bad("Rejection reason required");
            validateReasonOptional(input.reason());
            db.update("UPDATE voucher_limit_requests SET status='REJECTED',decided_by=?,"
                +"decided_at=?,decision_reason=?,version=version+1,"
                +"updated_at=GREATEST(updated_at,?) WHERE id=?",
                Actor.current().id(),utc(t),input.reason(),utc(t),id);
        }
        history(id,"PENDING","approve".equals(action)?"APPROVED":"REJECTED",
            input.reason(),Actor.current().id(),t);
        return view(row(id,false));
    }
    private static void validateReasonOptional(String reason){
        if(reason!=null&&(reason.length()>500||!reason.equals(reason.trim())||reason.isBlank()))
            throw bad("Invalid decision reason");
    }
    private static void checkVersion(Request r,long expected){
        if(expected<0||r.version()!=expected)
            throw conflict("VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT","Request version conflict");
    }
    private static void checkPage(int page,int size){
        if(page<0||page>20_000||size<1||size>50) throw bad("Invalid pagination");
    }
    private void history(UUID id,String from,String to,String reason,String actor,Instant t){
        db.update("INSERT INTO voucher_limit_request_history "
                +"(id,request_id,from_status,to_status,reason,actor,created_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),id,from,to,reason,actor,utc(t));
    }
}
