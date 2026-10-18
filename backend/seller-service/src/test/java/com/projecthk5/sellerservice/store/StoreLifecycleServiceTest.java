package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class StoreLifecycleServiceTest {
    private final SellerIdentity owner = new SellerIdentity("Owner");
    private final UUID sellerId = UUID.randomUUID();
    private StoreAccessPolicy policy;
    private StoreLifecycleService service;

    @BeforeEach void setup() {
        policy = mock(StoreAccessPolicy.class);
        service = new StoreLifecycleService(policy);
    }

    private PersistedSeller seller(SellerIdentity identity, SellerStatus status) {
        String reason = status == SellerStatus.REJECTED || status == SellerStatus.SUSPENDED ? "reason" : null;
        return new PersistedSeller(sellerId, 3, new SellerApplication(identity, status, reason));
    }

    @Test void approvedOwnerCreatesAndRevisesAnImmutableStore() {
        when(policy.requireSellerManagement(owner)).thenReturn(seller(owner, SellerStatus.APPROVED));
        StoreProfile original = service.create(owner, " Shop One ", "shop-one");
        StoreProfile edited = service.revise(original, owner, "New Name", "new-name");
        StoreProfile closed = service.setOpen(edited, owner, false);
        assertEquals("Shop One", original.name());
        assertTrue(original.open());
        assertFalse(closed.open());
        assertEquals(original.id(), closed.id());
        assertEquals(sellerId, closed.sellerId());
        assertEquals(owner.reference(), closed.ownerReference());
        assertTrue(service.setOpen(closed, owner, true).open());
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void nonApprovedSellerCannotCreate(SellerStatus status) {
        when(policy.requireSellerManagement(owner)).thenReturn(seller(owner, status));
        assertEquals(StoreRuleException.Code.SELLER_NOT_APPROVED,
                assertThrows(StoreRuleException.class, () -> service.create(owner, "Shop", "shop")).getCode());
    }

    @Test void suspensionIsRecheckedOnEveryMutation() {
        when(policy.requireSellerManagement(owner)).thenReturn(seller(owner, SellerStatus.APPROVED));
        StoreProfile original = service.create(owner, "Shop", "shop");
        when(policy.requireSellerManagement(owner)).thenReturn(seller(owner, SellerStatus.SUSPENDED));
        assertThrows(StoreRuleException.class, () -> service.revise(original, owner, "Changed", "changed"));
        assertThrows(StoreRuleException.class, () -> service.setOpen(original, owner, false));
        assertTrue(original.open());
    }

    @Test void otherSellerCannotEditOrCloseStore() {
        when(policy.requireSellerManagement(owner)).thenReturn(seller(owner, SellerStatus.APPROVED));
        StoreProfile original = service.create(owner, "Shop", "shop");
        SellerIdentity other = new SellerIdentity("Other");
        when(policy.requireSellerManagement(other)).thenReturn(new PersistedSeller(UUID.randomUUID(), 0,
                new SellerApplication(other, SellerStatus.APPROVED, null)));
        assertEquals(StoreRuleException.Code.ACCESS_DENIED, assertThrows(StoreRuleException.class,
                () -> service.revise(original, other, "Changed", "changed")).getCode());
        assertThrows(StoreRuleException.class, () -> service.setOpen(original, other, false));
    }

    @Test void actorAndPolicyIdentityMustMatchExactly() {
        when(policy.requireSellerManagement(owner)).thenReturn(seller(new SellerIdentity("owner"), SellerStatus.APPROVED));
        assertEquals(StoreRuleException.Code.ACCESS_DENIED,
                assertThrows(StoreRuleException.class, () -> service.create(owner, "Shop", "shop")).getCode());
    }

    @Test void missingOrDeniedPolicyNeverCreatesAStore() {
        assertEquals(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(StoreRuleException.class, () -> service.create(owner, "Shop", "shop")).getCode());
        doThrow(new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Denied"))
                .when(policy).requireSellerManagement(owner);
        assertThrows(StoreRuleException.class, () -> service.create(owner, "Shop", "shop"));
    }

    @Test void missingActorIsRejectedBeforePolicy() {
        assertThrows(StoreRuleException.class, () -> service.create(null, "Shop", "shop"));
        verifyNoInteractions(policy);
    }

    @Test void malformedPolicyResultFailsClosed() {
        when(policy.requireSellerManagement(owner)).thenReturn(new PersistedSeller(sellerId, 0, null));
        assertEquals(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(StoreRuleException.class, () -> service.create(owner, "Shop", "shop")).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Upper", "two words", "-start", "end-", "two--parts", "a/b"})
    void malformedSlugIsRejected(String slug) {
        assertThrows(StoreRuleException.class,
                () -> new StoreProfile(UUID.randomUUID(), sellerId, "Owner", "Shop", slug, true));
    }

    @Test void metadataBoundsAreValidatedWithoutChangingIdentity() {
        assertThrows(StoreRuleException.class,
                () -> new StoreProfile(UUID.randomUUID(), sellerId, "Owner", " ", "shop", true));
        assertThrows(StoreRuleException.class,
                () -> new StoreProfile(UUID.randomUUID(), sellerId, "Owner", "x".repeat(256), "shop", true));
        assertThrows(StoreRuleException.class,
                () -> new StoreProfile(UUID.randomUUID(), sellerId, "Owner", "Shop", "x".repeat(129), true));
        StoreProfile result = new StoreProfile(UUID.randomUUID(), sellerId, "Owner ", "Shop", "shop", true);
        assertEquals("Owner ", result.ownerReference());
        assertEquals("😀".repeat(128), new StoreProfile(UUID.randomUUID(), sellerId,
                "😀".repeat(128), "Shop", "shop", true).ownerReference());
    }
}
