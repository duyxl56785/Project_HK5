package com.projecthk5.security.fencing;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;

public final class DurableWriteLedger {
    public enum State { PENDING, ADMITTED, COMMITTED, ABORTED }
    public record Operation(UUID requestId,String bindingHash,long epoch,State state) { }
    public record Receipt(UUID requestId,String bindingHash,long epoch,State state,Map<Participant,Long> grants) {
        public Receipt { Objects.requireNonNull(requestId);Objects.requireNonNull(bindingHash);grants=Map.copyOf(grants);
            if(epoch<=0 || state!=State.COMMITTED && state!=State.ABORTED)throw new IllegalArgumentException("Nonterminal receipt"); }
    }
    @FunctionalInterface public interface LocalWrite {void apply(Connection connection) throws SQLException;}
    @FunctionalInterface public interface BeginWork {void apply(Connection connection,Operation operation) throws SQLException;}
    private final DataSource source;
    public DurableWriteLedger(DataSource source) {this.source=Objects.requireNonNull(source);}

    public Operation begin(WriteIntent intent) throws SQLException {
        return begin(intent,(connection,operation)->{});
    }
    public Operation begin(WriteIntent intent,BeginWork work) throws SQLException {
        Objects.requireNonNull(work);
        return FenceSql.transaction(source,c->{
            try(var insert=c.prepareStatement("INSERT INTO authority_write_operations(request_id,binding_hash,state) VALUES(?,?,'PENDING') ON CONFLICT DO NOTHING")) {
                insert.setObject(1,intent.requestId());insert.setString(2,intent.fingerprint());insert.executeUpdate();
            }
            var operation=lock(c,intent.requestId());FenceSql.require(operation.bindingHash().equals(intent.fingerprint()));
            work.apply(c,operation);return operation;
        });
    }
    public void recordGrant(UUID request,long epoch,Grant grant) throws SQLException {
        FenceSql.transaction(source,c->{
            var operation=lock(c,request);check(operation,epoch);FenceSql.require(request.equals(grant.requestId()) && operation.bindingHash().equals(grant.bindingHash()));
            try(var insert=c.prepareStatement("INSERT INTO authority_write_grants VALUES(?,?,?) ON CONFLICT DO NOTHING")) {
                insert.setObject(1,request);insert.setString(2,grant.participant().name());insert.setLong(3,grant.epoch());insert.executeUpdate();
            }
            var grants=grants(c,request);FenceSql.require(Objects.equals(grants.get(grant.participant()),grant.epoch()));
            if(grants.size()==2 && operation.state()==State.PENDING)state(c,request,State.ADMITTED);
            return null;
        });
    }
    public Receipt commit(UUID request,long epoch,LocalWrite write) throws SQLException {
        Objects.requireNonNull(write);
        return FenceSql.transaction(source,c->{
            var operation=lock(c,request);check(operation,epoch);
            if(operation.state()!=State.COMMITTED) {
                FenceSql.require(operation.state()==State.ADMITTED && grants(c,request).size()==2);
                write.apply(c);state(c,request,State.COMMITTED);
            }
            return receipt(c,lock(c,request));
        });
    }
    public Receipt abort(UUID request,long epoch) throws SQLException {
        return FenceSql.transaction(source,c->{
            var operation=lock(c,request);check(operation,epoch);
            if(operation.state()!=State.COMMITTED)state(c,request,State.ABORTED);
            return receipt(c,lock(c,request));
        });
    }
    public Receipt terminal(UUID request) throws SQLException {
        return FenceSql.transaction(source,c->receipt(c,lock(c,request)));
    }
    private static void check(Operation operation,long epoch){FenceSql.require(operation.epoch()==epoch);}
    private static Operation lock(Connection c,UUID request) throws SQLException {
        try(var q=c.prepareStatement("SELECT binding_hash,epoch,state FROM authority_write_operations WHERE request_id=? FOR UPDATE")) {
            q.setObject(1,request);try(var rows=q.executeQuery()){FenceSql.require(rows.next());return new Operation(request,rows.getString(1),rows.getLong(2),State.valueOf(rows.getString(3)));}
        }
    }
    private static Map<Participant,Long> grants(Connection c,UUID request) throws SQLException {
        var result=new EnumMap<Participant,Long>(Participant.class);
        try(var q=c.prepareStatement("SELECT participant,authority_epoch FROM authority_write_grants WHERE request_id=?")) {
            q.setObject(1,request);try(var rows=q.executeQuery()){while(rows.next())result.put(Participant.valueOf(rows.getString(1)),rows.getLong(2));}
        }return result;
    }
    private static Receipt receipt(Connection c,Operation operation) throws SQLException {
        return new Receipt(operation.requestId(),operation.bindingHash(),operation.epoch(),operation.state(),grants(c,operation.requestId()));
    }
    private static void state(Connection c,UUID request,State state) throws SQLException {
        try(var update=c.prepareStatement("UPDATE authority_write_operations SET state=? WHERE request_id=?")) {
            update.setString(1,state.name());update.setObject(2,request);FenceSql.require(update.executeUpdate()==1);
        }
    }
}
