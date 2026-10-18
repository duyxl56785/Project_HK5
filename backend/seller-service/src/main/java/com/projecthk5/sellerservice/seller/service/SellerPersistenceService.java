package com.projecthk5.sellerservice.seller.service;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Wire explicitly as a Spring bean with a verified policy in the future Auth integration.
@Transactional(propagation = Propagation.REQUIRED)
public class SellerPersistenceService {
    private final SellerProfileRepository repository;
    private final SellerAccessPolicy accessPolicy;
    private final SellerLifecycleService lifecycle;

    public SellerPersistenceService(SellerProfileRepository repository, SellerAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
        this.lifecycle = new SellerLifecycleService(accessPolicy);
    }

    // Internal result, not a shared HTTP DTO or an exposed JPA entity.
    public record PersistedSeller(UUID id, long version, SellerApplication application) { }

    public PersistedSeller register(SellerIdentity applicant) {
        SellerProfile profile = SellerProfile.registration(lifecycle.register(applicant));
        try {
            return result(repository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_seller_profiles_owner_reference".equals(violation.getConstraintName())) {
                    throw new SellerPersistenceException(SellerPersistenceException.Code.SELLER_ALREADY_EXISTS,
                            "Account already has a seller profile", exception);
                }
            }
            throw exception;
        }
    }

    public PersistedSeller resubmit(UUID id, long expectedVersion, SellerIdentity actor) {
        requireActor(actor, false);
        return change(id, expectedVersion, current -> lifecycle.resubmit(current, actor));
    }

    public PersistedSeller approve(UUID id, long expectedVersion, SellerIdentity actor) {
        requireActor(actor, true);
        return change(id, expectedVersion, current -> lifecycle.approve(current, actor));
    }

    public PersistedSeller reject(UUID id, long expectedVersion, SellerIdentity actor, String reason) {
        requireActor(actor, true);
        return change(id, expectedVersion, current -> lifecycle.reject(current, actor, reason));
    }

    public PersistedSeller suspend(UUID id, long expectedVersion, SellerIdentity actor, String reason) {
        requireActor(actor, true);
        return change(id, expectedVersion, current -> lifecycle.suspend(current, actor, reason));
    }

    public PersistedSeller reinstate(UUID id, long expectedVersion, SellerIdentity actor) {
        requireActor(actor, true);
        return change(id, expectedVersion, current -> lifecycle.reinstate(current, actor));
    }

    private PersistedSeller change(UUID id, long expectedVersion, UnaryOperator<SellerApplication> transition) {
        if (id == null || expectedVersion < 0) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT,
                    "Seller ID and a nonnegative expected version are required");
        }
        SellerProfile profile = repository.findById(id).orElseThrow(() -> new SellerPersistenceException(
                SellerPersistenceException.Code.SELLER_NOT_FOUND, "Seller profile does not exist"));
        if (profile.getVersion() != expectedVersion) {
            throw versionConflict(null);
        }
        profile.applyLifecycleResult(transition.apply(application(profile)));
        try {
            return result(repository.saveAndFlush(profile));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(exception);
        }
    }

    private void requireActor(SellerIdentity actor, boolean moderation) {
        if (actor == null) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, "Seller actor is required");
        }
        accessPolicy.requireAuthenticated(actor);
        if (moderation) {
            accessPolicy.requireSellerModerationPermission(actor);
        }
    }

    private PersistedSeller result(SellerProfile profile) {
        return new PersistedSeller(profile.getId(), profile.getVersion(), application(profile));
    }

    private SellerApplication application(SellerProfile profile) {
        return new SellerApplication(new SellerIdentity(profile.getOwnerReference()),
                profile.getStatus(), profile.getReviewReason());
    }

    private SellerPersistenceException versionConflict(Throwable cause) {
        return new SellerPersistenceException(SellerPersistenceException.Code.SELLER_VERSION_CONFLICT,
                "Seller profile was changed by another request", cause);
    }
}
