package com.projecthk5.sellerservice.seller.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerPersistenceServiceTest {
    @Mock private SellerProfileRepository repository;
    @Mock private SellerAccessPolicy policy;
    private SellerPersistenceService service;
    private final SellerIdentity actor = new SellerIdentity("unverified");

    @BeforeEach
    void setUp() {
        service = new SellerPersistenceService(repository, policy);
    }

    @Test
    void deniedRegistrationCannotWriteToRepository() {
        doThrow(denied()).when(policy).requireAuthenticated(actor);
        assertThrows(SellerRuleException.class, () -> service.register(actor));
        verifyNoInteractions(repository);
    }

    @Test
    void deniedResubmissionCannotProbeStoredProfiles() {
        doThrow(denied()).when(policy).requireAuthenticated(actor);
        assertThrows(SellerRuleException.class, () -> service.resubmit(UUID.randomUUID(), 0, actor));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVE", "REJECT", "SUSPEND", "REINSTATE"})
    void deniedModeratorCannotProbeStoredProfiles(String operation) {
        doThrow(denied()).when(policy).requireSellerModerationPermission(actor);
        UUID id = UUID.randomUUID();
        assertThrows(SellerRuleException.class, () -> {
            switch (operation) {
                case "APPROVE" -> service.approve(id, 0, actor);
                case "REJECT" -> service.reject(id, 0, actor, "reason");
                case "SUSPEND" -> service.suspend(id, 0, actor, "reason");
                case "REINSTATE" -> service.reinstate(id, 0, actor);
                default -> fail("Unknown operation");
            }
        });
        verifyNoInteractions(repository);
    }

    @Test
    void missingActorCannotReachPolicyOrRepository() {
        assertThrows(SellerRuleException.class, () -> service.approve(UUID.randomUUID(), 0, null));
        assertThrows(SellerRuleException.class, () -> service.register(null));
        verifyNoInteractions(repository, policy);
    }

    private SellerRuleException denied() {
        return new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Denied fixture");
    }

    @Test
    void deniedReadsCannotProbeProfiles() {
        doThrow(denied()).when(policy).requireAuthenticated(actor);
        assertThrows(SellerRuleException.class, () -> service.findOwn(actor));
        assertThrows(SellerRuleException.class, () -> service.findForModeration(UUID.randomUUID(), actor));
        verifyNoInteractions(repository);
    }

    @Test
    void nonModeratorCannotReadAnotherProfile() {
        doThrow(denied()).when(policy).requireSellerModerationPermission(actor);
        assertThrows(SellerRuleException.class, () -> service.findForModeration(UUID.randomUUID(), actor));
        verifyNoInteractions(repository);
    }

    @Test
    void missingReadActorIsRejectedBeforePolicyOrLookup() {
        assertThrows(SellerRuleException.class, () -> service.findOwn(null));
        assertThrows(SellerRuleException.class, () -> service.findForModeration(UUID.randomUUID(), null));
        verifyNoInteractions(repository, policy);
    }

    @Test
    void missingModerationIdDoesNotReachRepository() {
        assertThrows(SellerRuleException.class, () -> service.findForModeration(null, actor));
        verifyNoInteractions(repository);
    }
}
