package com.projecthk5.sellerservice.seller.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SellerProfileTest {
    private final SellerIdentity owner = new SellerIdentity("Owner Reference ");

    @Test
    void createsSeparateSellerIdAndPreservesOpaqueOwner() {
        SellerProfile profile = SellerProfile.registration(pending(owner));
        assertNotNull(profile.getId());
        assertEquals(owner.reference(), profile.getOwnerReference());
        assertEquals(SellerStatus.PENDING_REVIEW, profile.getStatus());
        assertNull(profile.getReviewReason());
    }

    @Test
    void enforcesOwnerLengthWithoutTruncating() {
        String exact = "X".repeat(128);
        assertEquals(exact, SellerProfile.registration(pending(new SellerIdentity(exact))).getOwnerReference());
        assertThrows(SellerRuleException.class,
                () -> SellerProfile.registration(pending(new SellerIdentity(exact + "X"))));
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"APPROVED", "REJECTED", "SUSPENDED"})
    void cannotCreateAnAlreadyModeratedProfile(SellerStatus status) {
        assertThrows(SellerRuleException.class, () -> SellerProfile.registration(
                new SellerApplication(owner, status, "reason")));
    }

    @Test
    void enforcesReasonLimitBeforeMutatingTheEntity() {
        SellerProfile profile = SellerProfile.registration(pending(owner));
        profile.applyLifecycleResult(new SellerApplication(owner, SellerStatus.REJECTED, "R".repeat(500)));
        assertEquals(500, profile.getReviewReason().length());
        assertThrows(SellerRuleException.class, () -> profile.applyLifecycleResult(
                new SellerApplication(owner, SellerStatus.SUSPENDED, "R".repeat(501))));
        assertEquals(SellerStatus.REJECTED, profile.getStatus());
        assertEquals(500, profile.getReviewReason().length());
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"PENDING_REVIEW", "APPROVED"})
    void clearsReasonWhenStateDoesNotRequireIt(SellerStatus status) {
        SellerProfile profile = SellerProfile.registration(pending(owner));
        profile.applyLifecycleResult(new SellerApplication(owner, SellerStatus.REJECTED, "reason"));
        profile.applyLifecycleResult(new SellerApplication(owner, status, "obsolete reason"));
        assertNull(profile.getReviewReason());
    }

    @Test
    void reasonLengthMatchesPostgresqlCharactersForSupplementaryUnicode() {
        SellerProfile profile = SellerProfile.registration(pending(owner));
        String reason = "\uD83D\uDE00".repeat(500);
        profile.applyLifecycleResult(new SellerApplication(owner, SellerStatus.REJECTED, reason));
        assertEquals(reason, profile.getReviewReason());
        assertThrows(SellerRuleException.class, () -> profile.applyLifecycleResult(
                new SellerApplication(owner, SellerStatus.REJECTED, reason + "X")));
    }

    @Test
    void rejectsOwnershipChangesWithoutMutatingTheEntity() {
        SellerProfile profile = SellerProfile.registration(pending(owner));
        assertThrows(SellerRuleException.class,
                () -> profile.applyLifecycleResult(pending(new SellerIdentity("different owner"))));
        assertEquals(owner.reference(), profile.getOwnerReference());
        assertEquals(SellerStatus.PENDING_REVIEW, profile.getStatus());
    }

    private SellerApplication pending(SellerIdentity identity) {
        return new SellerApplication(identity, SellerStatus.PENDING_REVIEW, null);
    }
}
