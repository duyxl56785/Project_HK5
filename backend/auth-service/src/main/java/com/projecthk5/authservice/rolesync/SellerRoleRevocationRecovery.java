package com.projecthk5.authservice.rolesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.fencing.RevocationBarrier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Enqueues before the proxied mutation transaction, and acknowledges completion after its commit. */
public final class SellerRoleRevocationRecovery {
    private final RevocationBarrier barrier;
    private final SellerRoleSyncService service;
    private final SellerRoleSyncJson codec=new SellerRoleSyncJson();
    private static final Duration LEASE=Duration.ofSeconds(30);
    public SellerRoleRevocationRecovery(RevocationBarrier barrier,SellerRoleSyncService service) {
        this.barrier=Objects.requireNonNull(barrier);this.service=Objects.requireNonNull(service);
    }
    public SellerRoleSyncService.Result apply(SellerRoleSyncService.Command command) throws Exception {
        requireNoTransaction();
        if(command.sellerStatus()==SellerRoleSyncService.Status.APPROVED)return service.apply(command);
        String payload=canonical(command);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        var ticket=barrier.enqueueRoleSync(command.eventId(),hash,payload);
        if(ticket.state()==RevocationBarrier.State.APPLIED)return SellerRoleSyncService.Result.DUPLICATE;
        if(ticket.state()==RevocationBarrier.State.REJECTED) {
            // Re-evaluate only for its permanent conflict response; the terminal ticket cannot be requeued.
            throw new RevocationBarrier.Conflict();
        }
        var claim=barrier.claimRoleSync(command.eventId(),LEASE).orElseThrow(()->new IllegalStateException("REVOCATION_PENDING"));
        return execute(claim,command);
    }
    public boolean tick() throws Exception {
        requireNoTransaction();
        var claim=barrier.claimRoleSync(LEASE);
        if(claim.isEmpty())return false;
        SellerRoleSyncService.Command command;
        try {
            command=codec.read(claim.get().ticket().payload());
            if(command.sellerStatus()==SellerRoleSyncService.Status.APPROVED)throw new IllegalArgumentException();
        } catch(IllegalArgumentException invalid) {
            barrier.reject(claim.get(),"VALIDATION_FAILED");return true;
        }
        try {execute(claim.get(),command);}
        catch(Exception retryScheduled) { /* failure was durably classified by execute */ }
        return true;
    }
    private SellerRoleSyncService.Result execute(RevocationBarrier.Claim claim,SellerRoleSyncService.Command command) throws Exception {
        if(claim.ticket().attempts()>32) {
            barrier.fail(claim,"RETRY_EXHAUSTED");
            throw new IllegalStateException("REVOCATION_RETRY_EXHAUSTED");
        }
        try {
            var result=service.apply(command);
            if(!barrier.complete(claim))throw new IllegalStateException("REVOCATION_LEASE_LOST");
            return result;
        } catch(SellerRoleSyncService.Conflict permanent) {
            barrier.reject(claim,permanent.code());throw permanent;
        } catch(Exception unavailable) {
            barrier.fail(claim,"ROLE_SYNC_UNAVAILABLE");throw unavailable;
        }
    }
    private static void requireNoTransaction() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Revocation orchestration requires no ambient transaction");
    }
    private String canonical(SellerRoleSyncService.Command c) throws Exception {
        var fields=new LinkedHashMap<String,String>();
        fields.put("eventId",c.eventId().toString());fields.put("sellerId",c.sellerId().toString());
        fields.put("accountId",c.accountId());fields.put("sellerStatus",c.sellerStatus().name());fields.put("sourceVersion",Long.toString(c.sourceVersion()));
        return new ObjectMapper().writeValueAsString(fields);
    }
}
