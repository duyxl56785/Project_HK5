package com.projecthk5.sellerservice.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.security.fencing.RevocationBarrier;
import com.projecthk5.security.fencing.RevocationBarrier.*;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.store.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal durable commands, deliberately not a bean or an HTTP write adapter. */
public final class SellerMutationQueue {
    public enum Operation { RESUBMIT, APPROVE, REJECT, SUSPEND, REINSTATE, SET_OPEN }
    public record Command(Operation operation, UUID target, long expectedVersion, String actor,
                          String reason, Boolean open) {
        public Command {
            Objects.requireNonNull(operation); Objects.requireNonNull(target);
            if (expectedVersion < 0 || actor == null || actor.isBlank() || actor.length() > 128)
                throw new IllegalArgumentException("Invalid mutation identity/version");
            if ((operation == Operation.SET_OPEN) != (open != null)
                    || (operation != Operation.REJECT && operation != Operation.SUSPEND && reason != null))
                throw new IllegalArgumentException("Unexpected mutation fields");
            if ((operation == Operation.REJECT || operation == Operation.SUSPEND)
                    && (reason == null || reason.isBlank() || reason.length() > 500))
                throw new IllegalArgumentException("Moderation reason is required and bounded");
        }
    }
    public record Receipt(UUID requestId, UUID resultId, long version, String state) { }
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final RevocationBarrier barrier;
    private final TransactionTemplate transaction;
    private final EntityManager em;
    private final SellerProfileRepository sellers;
    private final StoreRepository stores;
    private final ObjectMapper json = new ObjectMapper();

    public SellerMutationQueue(DataSource source, PlatformTransactionManager manager, EntityManager em,
                               SellerProfileRepository sellers, StoreRepository stores) {
        barrier = new RevocationBarrier(source);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.em = Objects.requireNonNull(em);
        this.sellers = Objects.requireNonNull(sellers);
        this.stores = Objects.requireNonNull(stores);
    }

    public Ticket enqueue(UUID requestId, Command command) throws Exception {
        outsideTransaction();
        String payload = json.writeValueAsString(Objects.requireNonNull(command));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
        return barrier.enqueueMutation(requestId, hash, payload, Kind.SELLER_MUTATION);
    }

    /** Fresh Auth RPC per attempt; no bearer token is stored in the durable command. */
    public Optional<Ticket> recoverOne(CurrentAccountClient auth, Function<Command, String> credential)
            throws Exception {
        outsideTransaction();
        var claimed = barrier.claimMutation(Kind.SELLER_MUTATION, LEASE);
        if (claimed.isEmpty()) return Optional.empty();
        Claim claim = claimed.get();
        try {
            Command command = decode(claim);
            var account = auth.resolve(credential.apply(command));
            account.requireSubject(command.actor());
            if (command.operation() == Operation.SET_OPEN) account.requireRole("SELLER");
            else if (command.operation() != Operation.RESUBMIT) account.requireRole("ADMIN");
            // Current-account lookup is not a commit fence. Never turn its snapshot into write authority.
            barrier.fail(claim, "AUTH_COMMIT_PROOF_UNAVAILABLE");
        } catch (CurrentAccountClient.Failure denied) {
            // Keep the durable barrier: a replacement credential or repaired Auth can resume later.
            barrier.fail(claim, "AUTHORIZATION_UNAVAILABLE");
        } catch (Exception unavailable) {
            barrier.fail(claim, "MUTATION_RECOVERY_UNAVAILABLE");
        }
        return barrier.inspect(claim.ticket().requestId());
    }

    public Optional<Ticket> inspect(UUID requestId) throws SQLException { return barrier.inspect(requestId); }
    public boolean retry(UUID requestId) throws SQLException { return barrier.retry(requestId); }

    /* Local transaction engine only. No production caller until Auth reservation + Seller terminal
       callback exist. Package visibility permits explicit local-policy PostgreSQL proofs. */
    Receipt applyLocal(Claim claim, SellerAccessPolicy sellerPolicy, StoreAccessPolicy storePolicy)
            throws Exception {
        Receipt receipt = applyLocalTransaction(claim, sellerPolicy, storePolicy);
        // Crash before this ACK replays the atomic receipt, never the lifecycle/outbox write.
        barrier.complete(claim);
        return receipt;
    }

    Receipt applyLocalTransaction(Claim claim, SellerAccessPolicy sellerPolicy, StoreAccessPolicy storePolicy)
            throws Exception {
        outsideTransaction();
        Command command = decode(claim);
        return transaction.execute(status -> {
            // Acquire the authority guard before queue/data row locks; ACTIVE never permits a commit.
            em.createNativeQuery("SELECT authority_fence_enter_mutation()").getSingleResult();
            var rows = em.createNativeQuery("SELECT binding_hash FROM authority_pending_revocations "
                    + "WHERE request_id=:id AND state='LEASED' AND claim_token=:token "
                    + "AND lease_until>clock_timestamp() FOR UPDATE")
                    .setParameter("id", claim.ticket().requestId()).setParameter("token", claim.token()).getResultList();
            if (rows.size() != 1 || !claim.ticket().bindingHash().equals(rows.getFirst()))
                throw new IllegalStateException("MUTATION_CLAIM_LOST");
            var existing = em.createNativeQuery("SELECT result_id,result_version,result_state "
                    + "FROM seller_mutation_receipts WHERE request_id=:id AND binding_hash=:hash")
                    .setParameter("id", claim.ticket().requestId())
                    .setParameter("hash", claim.ticket().bindingHash()).getResultList();
            if (!existing.isEmpty()) {
                Object[] row = (Object[]) existing.getFirst();
                return new Receipt(claim.ticket().requestId(), (UUID) row[0], ((Number) row[1]).longValue(), (String) row[2]);
            }
            var actor = new SellerIdentity(command.actor());
            var profiles = new SellerPersistenceService(sellers, sellerPolicy);
            Receipt result;
            if (command.operation() == Operation.SET_OPEN) {
                var store = new StorePersistenceService(stores, em, storePolicy)
                        .setOpen(command.target(), command.expectedVersion(), actor, command.open());
                result = new Receipt(claim.ticket().requestId(), store.profile().id(), store.version(),
                        store.profile().open() ? "OPEN" : "CLOSED");
            } else {
                var seller = switch (command.operation()) {
                    case RESUBMIT -> profiles.resubmit(command.target(), command.expectedVersion(), actor);
                    case APPROVE -> profiles.approve(command.target(), command.expectedVersion(), actor);
                    case REJECT -> profiles.reject(command.target(), command.expectedVersion(), actor, command.reason());
                    case SUSPEND -> profiles.suspend(command.target(), command.expectedVersion(), actor, command.reason());
                    case REINSTATE -> profiles.reinstate(command.target(), command.expectedVersion(), actor);
                    default -> throw new IllegalStateException("Unsupported Seller mutation");
                };
                result = new Receipt(claim.ticket().requestId(), seller.id(), seller.version(), seller.application().status().name());
            }
            em.createNativeQuery("INSERT INTO seller_mutation_receipts(request_id,binding_hash,result_id,result_version,result_state) "
                    + "VALUES(:id,:hash,:result,:version,:state)")
                    .setParameter("id", result.requestId()).setParameter("hash", claim.ticket().bindingHash())
                    .setParameter("result", result.resultId()).setParameter("version", result.version())
                    .setParameter("state", result.state()).executeUpdate();
            return result;
        });
    }

    private Command decode(Claim claim) throws Exception {
        if (claim.ticket().kind() != Kind.SELLER_MUTATION) throw new IllegalArgumentException("Wrong mutation kind");
        return json.readValue(claim.ticket().payload(), Command.class);
    }
    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Mutation orchestration must run outside a transaction");
    }
}
