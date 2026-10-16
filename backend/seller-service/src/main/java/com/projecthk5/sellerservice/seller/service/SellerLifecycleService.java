package com.projecthk5.sellerservice.seller.service;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import java.util.Objects;

// Not a Spring bean: no production Auth policy/persistence adapter exists until contracts are agreed.
public final class SellerLifecycleService {
    private final SellerAccessPolicy accessPolicy;

    public SellerLifecycleService(SellerAccessPolicy accessPolicy) {
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "Seller access policy is required");
    }

    public SellerApplication register(SellerIdentity applicant) {
        requireAuthenticated(applicant);
        return new SellerApplication(applicant, SellerStatus.PENDING_REVIEW, null);
    }

    public SellerApplication resubmit(SellerApplication application, SellerIdentity actor) {
        requireApplication(application);
        requireAuthenticated(actor);
        if (!application.owner().equals(actor)) {
            throw new SellerRuleException(SellerRuleException.Code.NOT_OWNER,
                    "Only the owner can resubmit a seller application");
        }
        requireState(application, SellerStatus.REJECTED);
        return new SellerApplication(application.owner(), SellerStatus.PENDING_REVIEW, null);
    }

    public SellerApplication approve(SellerApplication application, SellerIdentity actor) {
        return moderate(application, actor, SellerStatus.PENDING_REVIEW, SellerStatus.APPROVED, null);
    }

    public SellerApplication reject(SellerApplication application, SellerIdentity actor, String reason) {
        return moderate(application, actor, SellerStatus.PENDING_REVIEW, SellerStatus.REJECTED, reason);
    }

    public SellerApplication suspend(SellerApplication application, SellerIdentity actor, String reason) {
        return moderate(application, actor, SellerStatus.APPROVED, SellerStatus.SUSPENDED, reason);
    }

    public SellerApplication reinstate(SellerApplication application, SellerIdentity actor) {
        return moderate(application, actor, SellerStatus.SUSPENDED, SellerStatus.APPROVED, null);
    }

    private SellerApplication moderate(SellerApplication application, SellerIdentity actor,
            SellerStatus expected, SellerStatus target, String reason) {
        requireApplication(application);
        requireAuthenticated(actor);
        accessPolicy.requireSellerModerationPermission(actor);
        if (application.owner().equals(actor)) {
            throw new SellerRuleException(SellerRuleException.Code.SELF_MODERATION,
                    "Seller owners cannot moderate their own application");
        }
        requireState(application, expected);
        return new SellerApplication(application.owner(), target, reason);
    }

    private void requireAuthenticated(SellerIdentity actor) {
        if (actor == null) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, "Seller actor is required");
        }
        accessPolicy.requireAuthenticated(actor);
    }

    private void requireApplication(SellerApplication application) {
        if (application == null) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_INPUT,
                    "Seller application is required");
        }
    }

    private void requireState(SellerApplication application, SellerStatus expected) {
        if (application.status() != expected) {
            throw new SellerRuleException(SellerRuleException.Code.INVALID_STATE,
                    "Seller operation is not allowed in the current state");
        }
    }
}
