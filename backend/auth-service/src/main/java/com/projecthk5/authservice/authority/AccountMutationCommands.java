package com.projecthk5.authservice.authority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.security.Hk5JwtCodec;
import com.projecthk5.security.fencing.RevocationBarrier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal only: no bean or HTTP adapter until an authenticated owner-approved caller exists. */
public final class AccountMutationCommands {
    private final RevocationBarrier barrier;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();
    private final String activeStatus;
    private static final Duration LEASE = Duration.ofSeconds(30);

    public record Change(UUID requestId, UUID accountId, long expectedVersion, String expectedStatus, Set<Account.Role> expectedRoles,
                         String status, Set<Account.Role> roles) {
        public Change {
            Objects.requireNonNull(requestId); Objects.requireNonNull(accountId);
            if(expectedVersion<0)throw new IllegalArgumentException("Expected authority version required");
            if (expectedStatus == null || expectedStatus.isBlank() || status == null || status.isBlank() || status.length()>32)
                throw new IllegalArgumentException("Explicit account status required");
            expectedRoles = Set.copyOf(expectedRoles); roles = Set.copyOf(roles);
            if (roles.isEmpty()) throw new IllegalArgumentException("Account roles required");
        }
    }
    private record Command(UUID actorId, Instant expiresAt, Change change) { }
    public static final class Denied extends RuntimeException {
        Denied(String code) { super(code); }
    }
    public AccountMutationCommands(RevocationBarrier barrier, JdbcTemplate jdbc, TransactionTemplate transaction, String activeStatus) {
        this.barrier=Objects.requireNonNull(barrier); this.jdbc=Objects.requireNonNull(jdbc);
        Objects.requireNonNull(transaction);
        if(!(transaction.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource()!=jdbc.getDataSource())
            throw new IllegalArgumentException("Mutation requires the same JDBC transaction data source");
        this.transaction=new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        if(!"ACTIVE".equals(activeStatus))throw new IllegalArgumentException("Reviewed ACTIVE account status required");
        this.activeStatus=activeStatus;
    }
    /** Principal is unforgeable outside the signature-validating codec; never accept caller-supplied actor IDs. */
    public RevocationBarrier.Ticket enqueue(Hk5JwtCodec.VerifiedPrincipal principal, Change change) throws Exception {
        noTransaction();
        if(principal==null || !principal.tokenRoles().contains(Account.Role.ADMIN))throw new Denied("AUTH_MUTATION_FORBIDDEN");
        UUID actor=UUID.fromString(principal.subject());
        if(!actor.toString().equals(principal.subject()))throw new Denied("AUTH_MUTATION_FORBIDDEN");
        // Sorted role lists make set iteration order irrelevant to the immutable queue identity.
        var fields=new LinkedHashMap<String,Object>();
        fields.put("actorId",actor); fields.put("requestId",change.requestId()); fields.put("accountId",change.accountId());
        fields.put("expiresAt",principal.expiresAt().toString());
        fields.put("expectedVersion",change.expectedVersion());
        fields.put("expectedStatus",change.expectedStatus()); fields.put("expectedRoles",change.expectedRoles().stream().map(Enum::name).sorted().toList());
        fields.put("status",change.status()); fields.put("roles",change.roles().stream().map(Enum::name).sorted().toList());
        String payload=json.writeValueAsString(fields);
        return barrier.enqueueMutation(change.requestId(),hash(payload),payload,RevocationBarrier.Kind.AUTH_MUTATION);
    }
    public boolean tick() throws Exception {
        noTransaction();
        var claimed=barrier.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,LEASE);
        if(claimed.isEmpty())return false;
        var claim=claimed.get();
        try {
            executeClaim(claim);
            if(!barrier.complete(claim))throw new IllegalStateException("AUTH_MUTATION_LEASE_LOST");
        } catch(Denied | IllegalArgumentException invalid) {
            barrier.reject(claim,invalid instanceof Denied ? invalid.getMessage() : "AUTH_MUTATION_INVALID");
        } catch(Exception unavailable) {
            barrier.fail(claim,"AUTH_MUTATION_UNAVAILABLE"); throw unavailable;
        }
        return true;
    }
    void executeClaim(RevocationBarrier.Claim claim) throws Exception {
        noTransaction();
        Command command;
        try {command=decode(claim.ticket().payload());}
        catch(Exception invalid) {throw new Denied("AUTH_MUTATION_INVALID");}
        if(claim.ticket().kind()!=RevocationBarrier.Kind.AUTH_MUTATION || !command.change().requestId().equals(claim.ticket().requestId())
                || !hash(claim.ticket().payload()).equals(claim.ticket().bindingHash()))throw new Denied("AUTH_MUTATION_BINDING_INVALID");
        transaction.executeWithoutResult(tx -> apply(command,claim));
    }
    private void apply(Command command,RevocationBarrier.Claim claim) {
        // Exclude every trigger-guarded writer, including direct role DELETE, while checking current actor authority.
        jdbc.execute("SELECT pg_advisory_xact_lock(72602130)");
        var owned=jdbc.queryForList("SELECT request_id FROM authority_pending_revocations WHERE request_id=? AND kind='AUTH_MUTATION' "
                +"AND state='LEASED' AND claim_token=? AND lease_until>clock_timestamp() AND binding_hash=? AND payload=? FOR UPDATE",
                claim.ticket().requestId(),claim.token(),claim.ticket().bindingHash(),claim.ticket().payload());
        if(owned.isEmpty())throw new IllegalStateException("AUTH_MUTATION_LEASE_LOST");
        jdbc.queryForObject("SELECT authority_fence_enter_mutation()",Long.class);
        String hash=claim.ticket().bindingHash();
        // Serialize identity replay and lock actor/target in deterministic order before current authority reads.
        jdbc.queryForObject("SELECT id FROM seller_role_sync_lock WHERE id=1 FOR UPDATE",Integer.class);
        var receipt=jdbc.queryForList("SELECT binding_hash FROM account_mutation_receipts WHERE request_id=?",command.change().requestId());
        if(!receipt.isEmpty()) {
            if(!hash.equals(receipt.getFirst().get("binding_hash")))throw new Denied("AUTH_MUTATION_BINDING_INVALID");
            return;
        }
        jdbc.queryForList("SELECT id FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",command.actorId(),command.change().accountId());
        var actor=jdbc.queryForList("SELECT status FROM accounts WHERE id=?",command.actorId());
        if(actor.isEmpty() || !activeStatus.equals(actor.getFirst().get("status")) || !roles(command.actorId()).contains(Account.Role.ADMIN))
            throw new Denied("AUTH_MUTATION_FORBIDDEN");
        Change c=command.change();
        var target=jdbc.queryForList("SELECT status FROM accounts WHERE id=?",c.accountId());
        var versions=jdbc.queryForList("SELECT version FROM account_authority_versions WHERE account_id=?",c.accountId());
        if(target.isEmpty() || versions.isEmpty() || c.expectedVersion()!=((Number)versions.getFirst().get("version")).longValue()
                || !c.expectedStatus().equals(target.getFirst().get("status")) || !c.expectedRoles().equals(roles(c.accountId())))
            throw new Denied("AUTH_MUTATION_PRECONDITION_FAILED");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT clock_timestamp() < ?::timestamptz",Boolean.class,command.expiresAt().toString())))
            throw new Denied("AUTH_MUTATION_AUTHORIZATION_EXPIRED");
        jdbc.update("UPDATE accounts SET status=? WHERE id=?",c.status(),c.accountId());
        jdbc.update("DELETE FROM account_role_grants WHERE account_id=?",c.accountId());
        for(var role:c.roles())jdbc.update("INSERT INTO account_role_grants(account_id,role) VALUES(?,?)",c.accountId(),role.name());
        jdbc.update("INSERT INTO account_mutation_receipts(request_id,binding_hash) VALUES(?,?)",c.requestId(),hash);
    }
    private Set<Account.Role> roles(UUID id) {
        return new HashSet<>(jdbc.query("SELECT role FROM account_role_grants WHERE account_id=?",(r,n)->Account.Role.valueOf(r.getString(1)),id));
    }
    private Command decode(String payload) throws Exception {
        var n=json.readTree(payload);
        Set<Account.Role> expected=new HashSet<>(),roles=new HashSet<>();
        n.required("expectedRoles").forEach(v->expected.add(Account.Role.valueOf(v.asText())));
        n.required("roles").forEach(v->roles.add(Account.Role.valueOf(v.asText())));
        return new Command(UUID.fromString(n.required("actorId").asText()),Instant.parse(n.required("expiresAt").asText()),new Change(UUID.fromString(n.required("requestId").asText()),
            UUID.fromString(n.required("accountId").asText()),n.required("expectedVersion").longValue(),n.required("expectedStatus").asText(),expected,n.required("status").asText(),roles));
    }
    private static String hash(String payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
    }
    private static void noTransaction() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Mutation orchestration requires no ambient transaction");
    }
}
