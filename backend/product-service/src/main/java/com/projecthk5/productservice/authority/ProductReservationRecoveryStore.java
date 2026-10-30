package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/** Durable work leases fence workers, never the participant reservations. */
public final class ProductReservationRecoveryStore {
    public record Claim(WriteIntent intent,long operationEpoch,UUID token,int attempts) {}
    public record Status(UUID requestId,long operationEpoch,String state,int attempts,String lastCode) {}
    public record Orphan(UUID requestId,String bindingHash,long operationEpoch,String state) {}
    private final DataSource source;
    private final Duration grace;
    public ProductReservationRecoveryStore(DataSource source,Duration grace) {
        this.source=Objects.requireNonNull(source);this.grace=Objects.requireNonNull(grace);
        if(grace.isNegative() || grace.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("Invalid recovery grace");
    }
    public DurableWriteLedger.Operation begin(WriteIntent intent) throws SQLException {
        return new DurableWriteLedger(source).begin(intent,(connection,operation)->enlist(connection,intent,operation.epoch()));
    }
    public void enlist(WriteIntent intent,long epoch) throws SQLException {
        transaction(c->{enlist(c,intent,epoch);return null;});
    }
    private void enlist(Connection c,WriteIntent intent,long epoch) throws SQLException {
        String wire=ReservationWire.request("lookup",intent.requestId(),intent,null);
        try(var q=c.prepareStatement("INSERT INTO product_reservation_recovery(request_id,binding_hash,operation_epoch,intent,due_at) SELECT request_id,binding_hash,epoch,?,clock_timestamp()+(? * interval '1 millisecond') FROM authority_write_operations WHERE request_id=? AND binding_hash=? AND epoch=? ON CONFLICT DO NOTHING")) {
            q.setString(1,wire);q.setLong(2,grace.toMillis());q.setObject(3,intent.requestId());q.setString(4,intent.fingerprint());q.setLong(5,epoch);q.executeUpdate();
        }
        try(var q=c.prepareStatement("SELECT binding_hash,operation_epoch,intent FROM product_reservation_recovery WHERE request_id=?")) {
            q.setObject(1,intent.requestId());try(var r=q.executeQuery()) {
                require(r.next() && r.getString(1).equals(intent.fingerprint()) && r.getLong(2)==epoch && r.getString(3).equals(wire));
            }
        }
    }
    public Optional<Claim> claim(Duration lease) throws SQLException {
        if(lease.isNegative() || lease.isZero() || lease.compareTo(Duration.ofMinutes(5))>0)throw new IllegalArgumentException("Invalid recovery lease");
        return transaction(c->{
            try(var q=c.prepareStatement("SELECT request_id,intent,operation_epoch,attempts FROM product_reservation_recovery WHERE (state='READY' AND due_at<=clock_timestamp()) OR (state='LEASED' AND lease_until<=clock_timestamp()) ORDER BY due_at,created_at,request_id FOR UPDATE SKIP LOCKED LIMIT 1");var r=q.executeQuery()) {
                if(!r.next())return Optional.empty();
                UUID request=r.getObject(1,UUID.class);var intent=ReservationWire.readRequest("lookup",r.getString(2).getBytes(StandardCharsets.UTF_8)).intent();
                long epoch=r.getLong(3);int attempts=r.getInt(4);UUID token=UUID.randomUUID();
                try(var update=c.prepareStatement("UPDATE product_reservation_recovery SET state='LEASED',attempts=attempts+1,token=?,lease_until=clock_timestamp()+(? * interval '1 millisecond') WHERE request_id=?")) {
                    update.setObject(1,token);update.setLong(2,lease.toMillis());update.setObject(3,request);require(update.executeUpdate()==1);
                }return Optional.of(new Claim(intent,epoch,token,attempts));
            }
        });
    }
    public boolean complete(Claim claim) throws SQLException {return finish(claim,"COMPLETE",null,0);}
    public boolean retry(Claim claim,String code,int maxAttempts) throws SQLException {
        if(maxAttempts<1 || maxAttempts>32)throw new IllegalArgumentException("Invalid attempts");
        boolean blocked="CONFLICT".equals(code) || claim.attempts()+1>=maxAttempts;
        return finish(claim,blocked?"BLOCKED":"READY",code,Math.min(60,1L<<Math.min(6,claim.attempts())));
    }
    private boolean finish(Claim claim,String state,String code,long delay) throws SQLException {
        try(var c=source.getConnection();var q=c.prepareStatement("UPDATE product_reservation_recovery SET state=?,last_code=?,token=NULL,lease_until=NULL,due_at=clock_timestamp()+(? * interval '1 second') WHERE request_id=? AND operation_epoch=? AND state='LEASED' AND token=? AND lease_until>clock_timestamp()")) {
            q.setString(1,state);q.setString(2,code);q.setLong(3,delay);q.setObject(4,claim.intent().requestId());q.setLong(5,claim.operationEpoch());q.setObject(6,claim.token());return q.executeUpdate()==1;
        }
    }
    public Optional<Status> inspect(UUID request) throws SQLException {
        try(var c=source.getConnection();var q=c.prepareStatement("SELECT operation_epoch,state,attempts,last_code FROM product_reservation_recovery WHERE request_id=?")) {
            q.setObject(1,request);try(var r=q.executeQuery()){return r.next()?Optional.of(new Status(request,r.getLong(1),r.getString(2),r.getInt(3),r.getString(4))):Optional.empty();}
        }
    }
    public List<Orphan> orphans() throws SQLException {
        return orphans(0);
    }
    public List<Orphan> orphans(long afterEpoch) throws SQLException {
        if(afterEpoch<0)throw new IllegalArgumentException("Invalid orphan cursor");
        try(var c=source.getConnection();var q=c.prepareStatement("SELECT o.request_id,o.binding_hash,o.epoch,o.state FROM authority_write_operations o WHERE o.epoch>? AND NOT EXISTS (SELECT 1 FROM product_reservation_recovery r WHERE r.request_id=o.request_id) ORDER BY o.epoch,o.request_id LIMIT 100")) {
            q.setLong(1,afterEpoch);
            try(var rows=q.executeQuery()) {
            var result=new ArrayList<Orphan>();
            while(rows.next())result.add(new Orphan(rows.getObject(1,UUID.class),rows.getString(2),rows.getLong(3),rows.getString(4)));
            return List.copyOf(result);
            }
        }
    }
    public void requestRetry(UUID request,long expectedEpoch,String actor) throws SQLException {
        if(actor==null || actor.isBlank() || actor.length()>200)throw new IllegalArgumentException("Invalid actor");
        transaction(c->{
            try(var q=c.prepareStatement("SELECT operation_epoch,state,lease_until>clock_timestamp() FROM product_reservation_recovery WHERE request_id=? FOR UPDATE")) {
                q.setObject(1,request);try(var r=q.executeQuery()){require(r.next() && r.getLong(1)==expectedEpoch && !"COMPLETE".equals(r.getString(2)) && !("LEASED".equals(r.getString(2)) && r.getBoolean(3)));}
            }
            try(var q=c.prepareStatement("UPDATE product_reservation_recovery SET state='READY',attempts=0,token=NULL,lease_until=NULL,due_at=clock_timestamp() WHERE request_id=?")){q.setObject(1,request);q.executeUpdate();}
            try(var q=c.prepareStatement("INSERT INTO product_reservation_recovery_audit(request_id,operation_epoch,actor,action) VALUES(?,?,?,'REQUEST_RETRY')")){q.setObject(1,request);q.setLong(2,expectedEpoch);q.setString(3,actor);q.executeUpdate();}return null;
        });
    }
    private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work)throws SQLException {
        try(var c=source.getConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
            try{T result=work.run(c);c.commit();return result;}
            catch(SQLException|RuntimeException|Error failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
        }
    }
    private static void require(boolean condition){if(!condition)throw new IllegalStateException("Recovery binding or state conflict");}
}
