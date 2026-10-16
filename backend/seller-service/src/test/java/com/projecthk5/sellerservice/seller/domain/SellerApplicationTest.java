package com.projecthk5.sellerservice.seller.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SellerApplicationTest {
    private final SellerIdentity owner = new SellerIdentity("verified-owner-reference");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingOrBlankIdentity(String reference) {
        assertEquals(SellerRuleException.Code.INVALID_INPUT, assertThrows(SellerRuleException.class,
                () -> new SellerIdentity(reference)).getCode());
    }

    @Test
    void treatsIdentityAsExactOpaqueReference() {
        assertNotEquals(new SellerIdentity("Owner"), new SellerIdentity("owner"));
        assertNotEquals(new SellerIdentity("owner"), new SellerIdentity(" owner "));
        assertEquals(owner, new SellerIdentity("verified-owner-reference"));
    }

    @Test
    void rejectsMissingOwnerAndStatus() {
        assertThrows(SellerRuleException.class,
                () -> new SellerApplication(null, SellerStatus.PENDING_REVIEW, null));
        assertThrows(SellerRuleException.class, () -> new SellerApplication(owner, null, null));
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"REJECTED", "SUSPENDED"})
    void requiresReviewReasonForRejectedAndSuspended(SellerStatus status) {
        for (String reason : new String[] {null, "", " \t\n", "\u2003"}) {
            assertEquals(SellerRuleException.Code.INVALID_INPUT, assertThrows(SellerRuleException.class,
                    () -> new SellerApplication(owner, status, reason)).getCode());
        }
    }

    @ParameterizedTest
    @EnumSource(SellerStatus.class)
    void representsEachStateWithoutPersistenceOrHttpDependencies(SellerStatus status) {
        SellerApplication application = new SellerApplication(owner, status, " Reviewed ");
        assertEquals(owner, application.owner());
        assertEquals(status, application.status());
        assertEquals("Reviewed", application.reviewReason());
    }
}
