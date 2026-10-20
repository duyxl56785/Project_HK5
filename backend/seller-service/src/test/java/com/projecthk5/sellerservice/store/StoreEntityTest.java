package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreEntityTest {
    private final String ownerReference = " Exact-Owner ";
    private final SellerProfile seller = SellerProfile.registration(new SellerApplication(
            new SellerIdentity(ownerReference), SellerStatus.PENDING_REVIEW, null));

    @Test
    void mapsExistingDomainWithoutChangingItsIdentityOrOpaqueOwner() {
        StoreProfile domain = domain(UUID.randomUUID(), seller.getId(), ownerReference, true);
        StoreEntity entity = StoreEntity.create(domain, seller);
        assertEquals(domain, entity.toDomain(ownerReference));
        assertEquals(StoreStatus.OPEN, entity.getStatus());
        assertEquals(domain.id(), entity.getId());
        assertEquals(seller.getId(), entity.getSellerId());
        assertEquals("Store", entity.getName());
        assertEquals("store", entity.getSlug());
    }

    @Test
    void appliesMetadataAndExplicitOpenClosedStatusWithoutTransferringOwnership() {
        StoreEntity entity = StoreEntity.create(domain(UUID.randomUUID(), seller.getId(), ownerReference, true), seller);
        StoreProfile revised = new StoreProfile(entity.getId(), seller.getId(), ownerReference,
                "Revised", "revised-store", false);
        entity.apply(revised);
        assertEquals(StoreStatus.CLOSED, entity.getStatus());
        assertEquals(revised, entity.toDomain(ownerReference));
        StoreProfile reopened = new StoreProfile(entity.getId(), seller.getId(), ownerReference,
                revised.name(), revised.slug(), true);
        entity.apply(reopened);
        assertEquals(StoreStatus.OPEN, entity.getStatus());
        assertEquals(reopened, entity.toDomain(ownerReference));
    }

    @Test
    void rejectsMissingOrMismatchedPersistedSeller() {
        StoreProfile domain = domain(UUID.randomUUID(), seller.getId(), ownerReference, true);
        assertThrows(StoreRuleException.class, () -> StoreEntity.create(null, seller));
        assertThrows(StoreRuleException.class, () -> StoreEntity.create(domain, null));
        assertThrows(StoreRuleException.class, () -> StoreEntity.create(
                domain(domain.id(), UUID.randomUUID(), ownerReference, true), seller));
        assertThrows(StoreRuleException.class, () -> StoreEntity.create(
                domain(domain.id(), seller.getId(), "exact-owner", true), seller));
    }

    @Test
    void identityChangeAndForgedOwnerNeverMutateTheEntity() {
        StoreProfile original = domain(UUID.randomUUID(), seller.getId(), ownerReference, true);
        StoreEntity entity = StoreEntity.create(original, seller);
        assertThrows(StoreRuleException.class, () -> entity.apply(null));
        assertThrows(StoreRuleException.class,
                () -> entity.apply(domain(UUID.randomUUID(), seller.getId(), ownerReference, false)));
        assertThrows(StoreRuleException.class,
                () -> entity.apply(domain(original.id(), UUID.randomUUID(), ownerReference, false)));
        assertThrows(StoreRuleException.class,
                () -> entity.apply(domain(original.id(), seller.getId(), "Exact-Owner", false)));
        assertThrows(StoreRuleException.class, () -> entity.toDomain("Exact-Owner"));
        assertThrows(StoreRuleException.class, () -> entity.toDomain(null));
        assertEquals(original, entity.toDomain(ownerReference));
    }

    private StoreProfile domain(UUID id, UUID sellerId, String owner, boolean open) {
        return new StoreProfile(id, sellerId, owner, " Store ", "store", open);
    }
}
