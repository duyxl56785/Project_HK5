package com.projecthk5.security.fencing;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;

/** Durable priority barrier; lease expiry coordinates workers and never grants authority. */
public final class RevocationBarrier {
    public enum State { PENDING, LEASED, APPLIED, REJECTED, BLOCKED }
    public enum Kind { MANUAL, ROLE_SYNC, AUTH_MUTATION, SELLER_MUTATION }
    public record Ticket(UUID requestId,String bindingHash,String payload,State state,int attempts,Kind kind) { }
    public record Claim(Ticket ticket,UUID token) { }
    public static final class Conflict extends IllegalArgumentException {
        public Conflict() {super("REVOCATION_IDENTITY_CONFLICT");}
    }
    private final DataSource source;
    public RevocationBarrier(DataSource source) {this.source=Objects.requireNonNull(source);}
    public Ticket enqueue(UUID id,String hash,String payload) throws SQLException {return enqueue(id,hash,payload,Kind.MANUAL);}
    public Ticket enqueueRoleSync(UUID id,String hash,String payload) throws SQLException {
        return enqueue(id,hash,Objects.requireNonNull(payload),Kind.ROLE_SYNC);
    }
    public Ticket enqueueMutation(UUID id,String hash,String payload,Kind kind) throws SQLException {
        requireMutationKind(kind);
        return enqueue(id,hash,Objects.requireNonNull(payload),kind);
    }
    private Ticket enqueue(UUID id,String hash,String payload,Kind kind) throws SQLException {
        Objects.requireNonNull(id);
        if(hash==null || !hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid binding hash");
        return FenceSql.transaction(source,c->{
            try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(72602130)")){lock.execute();}
            try(var insert=c.prepareStatement("INSERT INTO authority_pending_revocations(request_id,binding_hash,payload,kind) VALUES(?,?,?,?) ON CONFLICT DO NOTHING")) {
                insert.setObject(1,id);insert.setString(2,hash);insert.setString(3,payload);insert.setString(4,kind.name());insert.executeUpdate();
            }
            Ticket ticket=read(c,id).orElseThrow();
            if(ticket.kind()!=kind || !hash.equals(ticket.bindingHash()) || !Objects.equals(payload,ticket.payload()))throw new Conflict();
            return ticket;
        });
    }
    public Optional<Ticket> inspect(UUID id) throws SQLException {return FenceSql.transaction(source,c->read(c,id));}
    private Optional<Ticket> read(Connection c,UUID id) throws SQLException {
        try(var q=c.prepareStatement("SELECT request_id,binding_hash,payload,state,attempts,kind FROM authority_pending_revocations WHERE request_id=?")) {
            q.setObject(1,id);try(var r=q.executeQuery()){return r.next()?Optional.of(ticket(r)):Optional.empty();}
        }
    }
    private Ticket ticket(ResultSet r) throws SQLException {return new Ticket(r.getObject(1,UUID.class),r.getString(2),r.getString(3),State.valueOf(r.getString(4)),r.getInt(5),Kind.valueOf(r.getString(6)));}
    public Optional<Claim> claim(Duration lease) throws SQLException {return claim(null,lease,Kind.MANUAL);}
    public Optional<Claim> claimRoleSync(Duration lease) throws SQLException {return claim(null,lease,Kind.ROLE_SYNC);}
    public Optional<Claim> claimRoleSync(UUID id,Duration lease) throws SQLException {return claim(id,lease,Kind.ROLE_SYNC);}
    public Optional<Claim> claim(UUID id,Duration lease) throws SQLException {return claim(id,lease,Kind.MANUAL);}
    public Optional<Claim> claimMutation(Kind kind,Duration lease) throws SQLException {return claimMutation(kind,null,lease);}
    public Optional<Claim> claimMutation(Kind kind,UUID id,Duration lease) throws SQLException {
        requireMutationKind(kind);return claim(id,lease,kind);
    }
    private static void requireMutationKind(Kind kind) {
        if(kind!=Kind.AUTH_MUTATION && kind!=Kind.SELLER_MUTATION)throw new IllegalArgumentException("Invalid mutation queue kind");
    }
    private Optional<Claim> claim(UUID id,Duration lease,Kind kind) throws SQLException {
        if(lease==null || lease.isNegative() || lease.isZero() || lease.compareTo(Duration.ofMinutes(5))>0)throw new IllegalArgumentException("Invalid lease");
        return FenceSql.transaction(source,c->{
            UUID token=UUID.randomUUID();
            String filter=" AND kind=?"+(id==null?"":" AND request_id=?");
            try(var q=c.prepareStatement("WITH candidate AS (SELECT request_id FROM authority_pending_revocations WHERE ((state='PENDING' AND due_at<=clock_timestamp()) OR (state='LEASED' AND lease_until<=clock_timestamp()))"+filter+" ORDER BY created_at,request_id FOR UPDATE SKIP LOCKED LIMIT 1) UPDATE authority_pending_revocations p SET state='LEASED',claim_token=?,lease_until=clock_timestamp()+(? * interval '1 millisecond'),attempts=attempts+1 FROM candidate c WHERE p.request_id=c.request_id RETURNING p.request_id,p.binding_hash,p.payload,p.state,p.attempts,p.kind")) {
                int n=1;q.setString(n++,kind.name());if(id!=null)q.setObject(n++,id);q.setObject(n++,token);q.setLong(n,lease.toMillis());
                try(var r=q.executeQuery()){return r.next()?Optional.of(new Claim(ticket(r),token)):Optional.empty();}
            }
        });
    }
    public boolean complete(Claim claim) throws SQLException {return finish(claim,"APPLIED",null,0);}
    public boolean reject(Claim claim,String code) throws SQLException {return finish(claim,"REJECTED",code,0);}
    public boolean fail(Claim claim,String code) throws SQLException {
        return finish(claim,claim.ticket().attempts()>=32?"BLOCKED":"PENDING",code,Math.min(60,1L<<Math.min(6,claim.ticket().attempts()-1)));
    }
    private boolean finish(Claim claim,String state,String code,long delay) throws SQLException {
        Objects.requireNonNull(claim);
        if(code!=null && !code.matches("[A-Z0-9_]{1,96}"))throw new IllegalArgumentException("Invalid failure code");
        return FenceSql.transaction(source,c->{
            try(var q=c.prepareStatement("UPDATE authority_pending_revocations SET state=?,last_code=?,claim_token=NULL,lease_until=NULL,due_at=clock_timestamp()+(? * interval '1 second') WHERE request_id=? AND state='LEASED' AND claim_token=? AND lease_until>clock_timestamp()")) {
                q.setString(1,state);q.setString(2,code);q.setLong(3,delay);q.setObject(4,claim.ticket().requestId());q.setObject(5,claim.token());return q.executeUpdate()==1;
            }
        });
    }
    /** Explicit operator retry retains the barrier; it cannot declare an operation applied. */
    public boolean retry(UUID id) throws SQLException {
        return FenceSql.transaction(source,c->{try(var q=c.prepareStatement("UPDATE authority_pending_revocations SET state='PENDING',attempts=0,due_at=clock_timestamp(),last_code=NULL WHERE request_id=? AND state='BLOCKED'")){q.setObject(1,id);return q.executeUpdate()==1;}});
    }
}
