package com.projecthk5.sellerservice.seller.entity;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.service.SellerInputs;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(length = 20)
    private String phone;

    @Column(name = "pickup_address", length = 255)
    private String pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 16)
    private SellerInputs.BusinessType businessType;

    @Column(name = "id_number", length = 12)
    private String idNumber;

    @Column(name = "tax_code", length = 14)
    private String taxCode;

    @Column(name = "contact_email", length = 254)
    private String email;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 19)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 100)
    private String bankAccountHolder;

    @Column(name = "terms_version", length = 16)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

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

    /** Every apply or resubmit re-records rule acceptance, so the stored version is the one last agreed to. */
    public void updateApplication(SellerInputs.Application application) {
        SellerInputs.Contact contact = application.contact();
        representativeName = contact.representativeName();
        phone = contact.phone();
        pickupAddress = contact.pickupAddress();
        businessType = contact.businessType();
        idNumber = contact.idNumber();
        taxCode = contact.taxCode();
        email = contact.email();
        bankName = contact.bankName();
        bankAccountNumber = contact.bankAccountNumber();
        bankAccountHolder = contact.bankAccountHolder();
        termsVersion = application.termsVersion();
        termsAcceptedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getOwnerReference() { return ownerReference; }
    public SellerStatus getStatus() { return status; }
    public String getReviewReason() { return reviewReason; }
    public String getRepresentativeName() { return representativeName; }
    public String getPhone() { return phone; }
    public String getPickupAddress() { return pickupAddress; }
    public SellerInputs.BusinessType getBusinessType() { return businessType; }
    public String getIdNumber() { return idNumber; }
    public String getTaxCode() { return taxCode; }
    public String getEmail() { return email; }
    public String getBankName() { return bankName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getBankAccountHolder() { return bankAccountHolder; }
    public String getTermsVersion() { return termsVersion; }
    public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
    public Long getVersion() { return version; }

    private static SellerRuleException invalid(String message) {
        return new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, message);
    }
}
