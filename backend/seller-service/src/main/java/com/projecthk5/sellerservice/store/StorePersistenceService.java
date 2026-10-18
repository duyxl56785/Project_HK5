package com.projecthk5.sellerservice.store;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Explicit wiring requires a verified StoreAccessPolicy; never component-scan a default authorization policy.
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public class StorePersistenceService {
    private final StoreRepository stores;
    private final EntityManager entityManager;
    private final StoreAccessPolicy policy;

    public StorePersistenceService(StoreRepository stores, EntityManager entityManager, StoreAccessPolicy policy) {
        this.stores = Objects.requireNonNull(stores);
        this.entityManager = Objects.requireNonNull(entityManager);
        this.policy = Objects.requireNonNull(policy);
    }

    public record PersistedStore(StoreProfile profile, long version, Instant createdAt, Instant updatedAt) { }

    public PersistedStore create(SellerIdentity actor, String name, String slug) {
        SellerProfile seller = currentSeller(actor);
        StoreProfile created = lifecycle(seller).create(actor, name, slug);
        return save(StoreEntity.create(created, seller));
    }

    public PersistedStore revise(UUID id, long expectedVersion, SellerIdentity actor, String name, String slug) {
        SellerProfile seller = currentSeller(actor);
        return change(id, expectedVersion, seller,
                current -> lifecycle(seller).revise(current, actor, name, slug));
    }

    public PersistedStore setOpen(UUID id, long expectedVersion, SellerIdentity actor, boolean open) {
        SellerProfile seller = currentSeller(actor);
        return change(id, expectedVersion, seller,
                current -> lifecycle(seller).setOpen(current, actor, open));
    }

    // Management read: same verified owner/current APPROVED gate as the existing Store domain.
    public PersistedStore findOwn(SellerIdentity actor) {
        SellerProfile seller = currentSeller(actor);
        StoreEntity store = stores.findBySellerId(seller.getId()).orElseThrow(StorePersistenceService::notFound);
        entityManager.refresh(store);
        return result(store, seller.getOwnerReference());
    }

    private PersistedStore change(UUID id, long expectedVersion, SellerProfile seller,
                                  UnaryOperator<StoreProfile> transition) {
        if (id == null || expectedVersion < 0) {
            throw invalid("Store ID and a nonnegative expected version are required");
        }
        StoreEntity store = stores.findById(id).orElseThrow(StorePersistenceService::notFound);
        // REQUIRED callers may already have cached this entity; compare against a fresh database version.
        entityManager.refresh(store);
        if (!seller.getId().equals(store.getSellerId())) {
            throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Store belongs to another seller");
        }
        if (store.getVersion() != expectedVersion) throw versionConflict(null);
        store.apply(transition.apply(store.toDomain(seller.getOwnerReference())));
        return save(store);
    }

    private SellerProfile currentSeller(SellerIdentity actor) {
        if (actor == null) throw invalid("Store actor is required");
        PersistedSeller verified = policy.requireSellerManagement(actor);
        if (verified == null || verified.id() == null || verified.application() == null) {
            throw unavailable("Verified seller could not be resolved");
        }
        if (!actor.equals(verified.application().owner())) {
            throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Seller identity mismatch");
        }
        // Resolve identity only here: a locking query can version-check a stale managed Seller before refresh.
        SellerProfile seller = entityManager.createQuery(
                        "select s from SellerProfile s where s.ownerReference = :owner", SellerProfile.class)
                .setParameter("owner", actor.reference())
                .getResultList().stream().findFirst().orElseThrow(() -> unavailable("Current seller is unavailable"));
        // Reload and acquire the shared row lock together before authorization; hold it through commit.
        // Seller status UPDATE waits, while competing Store writers still use Store @Version.
        entityManager.refresh(seller, LockModeType.PESSIMISTIC_READ);
        if (!seller.getId().equals(verified.id()) || !seller.getOwnerReference().equals(actor.reference())) {
            throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Current seller identity mismatch");
        }
        if (seller.getStatus() != SellerStatus.APPROVED) {
            throw new StoreRuleException(StoreRuleException.Code.SELLER_NOT_APPROVED, "Seller must be approved");
        }
        return seller;
    }

    private StoreLifecycleService lifecycle(SellerProfile seller) {
        PersistedSeller current = new PersistedSeller(seller.getId(), seller.getVersion(),
                new SellerApplication(new SellerIdentity(seller.getOwnerReference()),
                        seller.getStatus(), seller.getReviewReason()));
        return new StoreLifecycleService(actor -> current);
    }

    private PersistedStore save(StoreEntity store) {
        try {
            StoreEntity saved = stores.saveAndFlush(store);
            SellerProfile seller = entityManager.getReference(SellerProfile.class, saved.getSellerId());
            return result(saved, seller.getOwnerReference());
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(exception);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    if ("uk_stores_seller_id".equals(violation.getConstraintName())) {
                        throw new StorePersistenceException(StorePersistenceException.Code.STORE_ALREADY_EXISTS,
                                "Seller already has a store", exception);
                    }
                    if ("uk_stores_slug".equals(violation.getConstraintName())) {
                        throw new StorePersistenceException(StorePersistenceException.Code.SLUG_ALREADY_EXISTS,
                                "Store slug already exists", exception);
                    }
                }
            }
            throw exception;
        }
    }

    private PersistedStore result(StoreEntity store, String owner) {
        return new PersistedStore(store.toDomain(owner), store.getVersion(), store.getCreatedAt(), store.getUpdatedAt());
    }

    private static StorePersistenceException notFound() {
        return new StorePersistenceException(StorePersistenceException.Code.STORE_NOT_FOUND, "Store does not exist");
    }

    private static StorePersistenceException versionConflict(Throwable cause) {
        return new StorePersistenceException(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                "Store was changed by another request", cause);
    }

    private static StoreRuleException invalid(String message) {
        return new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, message);
    }

    private static StoreRuleException unavailable(String message) {
        return new StoreRuleException(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE, message);
    }
}
