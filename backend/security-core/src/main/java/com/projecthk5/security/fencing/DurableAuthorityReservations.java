package com.projecthk5.security.fencing;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class DurableAuthorityReservations {
    public enum Participant { AUTH, SELLER }
    public record Grant(UUID requestId,String bindingHash,Participant participant,long epoch) {
        public Grant {Objects.requireNonNull(requestId);Objects.requireNonNull(participant);
            if(bindingHash==null || !bindingHash.matches("[0-9a-f]{64}") || epoch<=0)throw new IllegalArgumentException("Invalid grant");}
    }
    public record ClosureProof(UUID requestId,String bindingHash,long operationEpoch,DurableWriteLedger.State state,Participant participant,Long authorityEpoch) {
        public ClosureProof {
            Objects.requireNonNull(requestId);Objects.requireNonNull(participant);Objects.requireNonNull(state);
            if(bindingHash==null || !bindingHash.matches("[0-9a-f]{64}") || operationEpoch<=0
                    || (state!=DurableWriteLedger.State.COMMITTED && state!=DurableWriteLedger.State.ABORTED)
                    || (authorityEpoch!=null && authorityEpoch<=0) || (state==DurableWriteLedger.State.COMMITTED && authorityEpoch==null))
                throw new IllegalArgumentException("Invalid closure proof");
        }
    }
    @FunctionalInterface public interface ClosureSource {ClosureProof read(UUID requestId) throws Exception;}
    @FunctionalInterface public interface CurrentAuthority {void verify(Connection connection) throws SQLException;}
    @FunctionalInterface public interface TerminalSource {DurableWriteLedger.Receipt read(UUID requestId) throws Exception;}
    private final DataSource source;
    private final Participant participant;
    public DurableAuthorityReservations(DataSource source,Participant participant) {this.source=Objects.requireNonNull(source);this.participant=Objects.requireNonNull(participant);}

    public Grant admit(WriteIntent intent,CurrentAuthority authority) throws SQLException {
        Objects.requireNonNull(intent);Objects.requireNonNull(authority);
        return FenceSql.transaction(source,c->{
            lock(c);
            try(var closed=c.prepareStatement("SELECT 1 FROM authority_reservation_closures WHERE request_id=?")) {
                closed.setObject(1,intent.requestId());try(var rows=closed.executeQuery()){FenceSql.require(!rows.next());}
            }
            try(var q=c.prepareStatement("SELECT binding_hash,epoch,state FROM authority_write_reservations WHERE request_id=?")) {
                q.setObject(1,intent.requestId());try(var rows=q.executeQuery()){if(rows.next()) {
                    FenceSql.require(intent.fingerprint().equals(rows.getString(1)) && "ACTIVE".equals(rows.getString(3)));
                    return new Grant(intent.requestId(),intent.fingerprint(),participant,rows.getLong(2));
                }}
            }
            try(var pending=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM authority_pending_revocations WHERE state IN ('PENDING','LEASED','BLOCKED'))");var rows=pending.executeQuery()) {
                FenceSql.require(rows.next());
                if(rows.getBoolean(1))throw new IllegalStateException("FENCE_REVOCATION_PENDING");
            }
            authority.verify(c);
            long epoch;
            try(var update=c.prepareStatement("UPDATE authority_fence_clock SET epoch=epoch+1 WHERE id=1 RETURNING epoch");var rows=update.executeQuery()) {
                FenceSql.require(rows.next());epoch=rows.getLong(1);
            }
            try(var insert=c.prepareStatement("INSERT INTO authority_write_reservations(request_id,binding_hash,epoch,state) VALUES(?,?,?,'ACTIVE')")) {
                insert.setObject(1,intent.requestId());insert.setString(2,intent.fingerprint());insert.setLong(3,epoch);insert.executeUpdate();
            }
            return new Grant(intent.requestId(),intent.fingerprint(),participant,epoch);
        });
    }

    public Optional<Grant> lookup(WriteIntent intent) throws SQLException {
        return FenceSql.transaction(source,c->{
            lock(c);
            try(var query=c.prepareStatement("SELECT binding_hash,epoch FROM authority_write_reservations WHERE request_id=?")) {
                query.setObject(1,intent.requestId());try(var rows=query.executeQuery()) {
                    if(!rows.next())return Optional.empty();
                    FenceSql.require(intent.fingerprint().equals(rows.getString(1)));
                    return Optional.of(new Grant(intent.requestId(),intent.fingerprint(),participant,rows.getLong(2)));
                }
            }
        });
    }

    public void release(Grant grant,TerminalSource terminal) throws Exception {
        Objects.requireNonNull(grant);Objects.requireNonNull(terminal);FenceSql.require(grant.participant()==participant);
        // Resolve remote evidence before acquiring any local transaction or lock.
        var receipt=Objects.requireNonNull(terminal.read(grant.requestId()));
        FenceSql.require(receipt.requestId().equals(grant.requestId()) && receipt.bindingHash().equals(grant.bindingHash())
                && Objects.equals(receipt.grants().get(participant),grant.epoch()));
        FenceSql.transaction(source,c->{
            lock(c);
            try(var q=c.prepareStatement("SELECT binding_hash,epoch,state,terminal_state FROM authority_write_reservations WHERE request_id=?")) {
                q.setObject(1,grant.requestId());try(var rows=q.executeQuery()) {
                    FenceSql.require(rows.next());FenceSql.require(grant.bindingHash().equals(rows.getString(1)) && grant.epoch()==rows.getLong(2));
                    if("RELEASED".equals(rows.getString(3))) {FenceSql.require(receipt.state().name().equals(rows.getString(4)));return null;}
                }
            }
            try(var update=c.prepareStatement("UPDATE authority_write_reservations SET state='RELEASED',terminal_state=? WHERE request_id=? AND state='ACTIVE'")) {
                update.setString(1,receipt.state().name());update.setObject(2,grant.requestId());FenceSql.require(update.executeUpdate()==1);
            }
            return null;
        });
    }
    public void seal(WriteIntent intent,long operationEpoch,ClosureSource terminal) throws Exception {
        Objects.requireNonNull(intent);Objects.requireNonNull(terminal);FenceSql.require(operationEpoch>0);
        var proof=Objects.requireNonNull(terminal.read(intent.requestId()));
        FenceSql.require(proof.requestId().equals(intent.requestId()) && proof.bindingHash().equals(intent.fingerprint())
                && proof.operationEpoch()==operationEpoch && proof.participant()==participant);
        FenceSql.transaction(source,c->{
            lock(c);
            try(var q=c.prepareStatement("SELECT binding_hash,operation_epoch,terminal_state FROM authority_reservation_closures WHERE request_id=?")) {
                q.setObject(1,intent.requestId());try(var rows=q.executeQuery()){if(rows.next())
                    FenceSql.require(intent.fingerprint().equals(rows.getString(1)) && operationEpoch==rows.getLong(2) && proof.state().name().equals(rows.getString(3)));}
            }
            boolean found=false;
            try(var q=c.prepareStatement("SELECT binding_hash,epoch,state,terminal_state FROM authority_write_reservations WHERE request_id=?")) {
                q.setObject(1,intent.requestId());try(var rows=q.executeQuery()){if(rows.next()) {
                    found=true;
                    FenceSql.require(intent.fingerprint().equals(rows.getString(1))
                            && (proof.authorityEpoch()==null || proof.authorityEpoch()==rows.getLong(2)));
                    String state=rows.getString(3);
                    FenceSql.require("ACTIVE".equals(state) || ("RELEASED".equals(state) && proof.state().name().equals(rows.getString(4))));
                }}
            }
            FenceSql.require(found || (proof.state()==DurableWriteLedger.State.ABORTED && proof.authorityEpoch()==null));
            try(var insert=c.prepareStatement("INSERT INTO authority_reservation_closures(request_id,binding_hash,operation_epoch,terminal_state) VALUES(?,?,?,?) ON CONFLICT(request_id) DO NOTHING")) {
                insert.setObject(1,intent.requestId());insert.setString(2,intent.fingerprint());insert.setLong(3,operationEpoch);insert.setString(4,proof.state().name());insert.executeUpdate();
            }
            if(found)try(var update=c.prepareStatement("UPDATE authority_write_reservations SET state='RELEASED',terminal_state=? WHERE request_id=? AND state='ACTIVE'")) {
                update.setString(1,proof.state().name());update.setObject(2,intent.requestId());update.executeUpdate();
            }
            return null;
        });
    }
    private static void lock(Connection c) throws SQLException {
        try(var guard=c.prepareStatement("SELECT pg_advisory_xact_lock(72602130)")){guard.execute();}
        try(var query=c.prepareStatement("SELECT epoch FROM authority_fence_clock WHERE id=1 FOR UPDATE");var rows=query.executeQuery()) {FenceSql.require(rows.next());}
    }
}
