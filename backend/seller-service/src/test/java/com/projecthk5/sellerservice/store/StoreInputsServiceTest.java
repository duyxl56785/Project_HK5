package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreInputsServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final StoreAccessPolicy policy = mock(StoreAccessPolicy.class);
    private final StorePersistenceService service = new StorePersistenceService(stores, entities, policy);
    private final SellerIdentity actor = new SellerIdentity("Exact-Owner");

    @Test
    void typedInputsNeverReplaceThePolicyOrTurnItsDenialIntoAWrite() {
        when(policy.requireSellerManagement(actor)).thenThrow(
                new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Denied fixture"));
        UUID id = UUID.randomUUID();
        assertThrows(StoreRuleException.class, () -> service.create(actor, new StoreInputs.Create("Store", "store")));
        assertThrows(StoreRuleException.class,
                () -> service.revise(id, actor, new StoreInputs.Update("Store", "store", 0L)));
        assertThrows(StoreRuleException.class,
                () -> service.setOpen(id, actor, new StoreInputs.SetOpen(false, 0L)));
        verify(policy, times(3)).requireSellerManagement(actor);
        verifyNoInteractions(stores, entities);
    }

    @Test
    void missingTypedInputsNeverReachPersistence() {
        UUID id = UUID.randomUUID();
        assertInvalid(() -> service.create(actor, null));
        assertInvalid(() -> service.revise(id, actor, null));
        assertInvalid(() -> service.setOpen(id, actor, null));
        verifyNoInteractions(stores, entities);
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(StoreRuleException.Code.INVALID_INPUT,
                assertThrows(StoreRuleException.class, action).getCode());
    }
}
