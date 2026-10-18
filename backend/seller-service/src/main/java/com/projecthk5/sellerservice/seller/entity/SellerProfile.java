package com.projecthk5.sellerservice.seller.entity;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "seller_profiles", uniqueConstraints =
        @UniqueConstraint(name = "uk_seller_profiles_owner_reference", columnNames = "owner_reference"))
public class SellerProfile {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_reference", nullable = false, length = 128, updatable = false)
    private String ownerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SellerStatus status;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected SellerProfile() { }

    public static SellerProfile registration(SellerApplication application) {
        if (application == null || application.status() != SellerStatus.PENDING_REVIEW) {
            throw invalid("A new seller must be pending review");
        }
        SellerProfile profile = new SellerProfile();
        profile.id = UUID.randomUUID();
        profile.ownerReference = application.owner().reference();
        profile.applyLifecycleResult(application);
        return profile;
    }

    public void applyLifecycleResult(SellerApplication application) {
        if (application == null || !ownerReference.equals(application.owner().reference())
                || ownerReference.codePointCount(0, ownerReference.length()) > 128) {
            throw invalid("Seller owner must be unchanged and contain at most 128 characters");
        }
        String reason = application.reviewReason();
        if (reason != null && reason.codePointCount(0, reason.length()) > 500) {
            throw invalid("Seller review reason must contain at most 500 characters");
        }
        status = application.status();
        reviewReason = status == SellerStatus.REJECTED || status == SellerStatus.SUSPENDED ? reason : null;
    }

    public UUID getId() { return id; }
    public String getOwnerReference() { return ownerReference; }
    public SellerStatus getStatus() { return status; }
    public String getReviewReason() { return reviewReason; }
    public Long getVersion() { return version; }

    private static SellerRuleException invalid(String message) {
        return new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, message);
    }
}
