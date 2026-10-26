package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoreOwnedFactsTest {
    private static final UUID STORE = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID SELLER = UUID.fromString("50000000-0000-4000-8000-000000000002");

    @ParameterizedTest
    @ValueSource(strings = {" Exact-Owner ", "MixedCASE", "\u00e9", "e\u0301", "owner\u00a0"})
    void persistedOwnerIdentityIsNeverTrimmedCaseFoldedOrNormalized(String owner) {
        assertEquals(owner, facts(owner).ownerReference());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankOwnerCannotBecomeAuthoritative(String owner) {
        unavailable(() -> facts(owner));
    }

    @Test
    void ownerMustBePresentAndRespectPersistedCodePointLimit() {
        unavailable(() -> facts(null));
        unavailable(() -> facts("x".repeat(129)));
        unavailable(() -> facts("\ud83d\ude80".repeat(129)));
        assertEquals("\ud83d\ude80".repeat(128), facts("\ud83d\ude80".repeat(128)).ownerReference());
    }

    @Test
    void incompleteIdentifiersStatusesAndNegativeVersionsCannotBecomeFacts() {
        unavailable(() -> new StoreCurrentSource.OwnedFacts(null, SELLER, StoreStatus.OPEN, SellerStatus.APPROVED, 0, 0, "owner"));
        unavailable(() -> new StoreCurrentSource.OwnedFacts(STORE, null, StoreStatus.OPEN, SellerStatus.APPROVED, 0, 0, "owner"));
        unavailable(() -> new StoreCurrentSource.OwnedFacts(STORE, SELLER, null, SellerStatus.APPROVED, 0, 0, "owner"));
        unavailable(() -> new StoreCurrentSource.OwnedFacts(STORE, SELLER, StoreStatus.OPEN, null, 0, 0, "owner"));
        unavailable(() -> new StoreCurrentSource.OwnedFacts(STORE, SELLER, StoreStatus.OPEN, SellerStatus.APPROVED, -1, 0, "owner"));
        unavailable(() -> new StoreCurrentSource.OwnedFacts(STORE, SELLER, StoreStatus.OPEN, SellerStatus.APPROVED, 0, -1, "owner"));
        assertEquals(Long.MAX_VALUE, new StoreCurrentSource.OwnedFacts(STORE, SELLER, StoreStatus.OPEN,
                SellerStatus.APPROVED, Long.MAX_VALUE, Long.MAX_VALUE, "owner").sellerVersion());
    }

    private StoreCurrentSource.OwnedFacts facts(String owner) {
        return new StoreCurrentSource.OwnedFacts(STORE, SELLER, StoreStatus.OPEN, SellerStatus.APPROVED, 0, 0, owner);
    }

    private void unavailable(Runnable action) {
        assertEquals(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(StoreRuleException.class, action::run).getCode());
    }
}
