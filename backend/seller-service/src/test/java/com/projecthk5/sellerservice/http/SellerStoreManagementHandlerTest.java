package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.store.StorePersistenceService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SellerStoreManagementHandlerTest {
    private final SellerStoreReadAuthority authority = mock(SellerStoreReadAuthority.class);
    private final SellerPersistenceService sellers = mock(SellerPersistenceService.class);
    private final StorePersistenceService stores = mock(StorePersistenceService.class);
    private SellerStoreManagementHandler handler;

    @BeforeEach void setup() {
        handler = new SellerStoreManagementHandler(authority, sellers, stores, new SellerStoreJson());
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "resubmit", "createStore", "updateStore", "setStoreOpen",
            "sellerForModeration", "approve", "reject", "suspend", "reinstate"})
    void unavailableIdentityStopsBeforeParsingOrPersistence(String operation) {
        when(authority.requireCurrentAccount()).thenThrow(new IllegalStateException("Fixture unavailable"));
        when(authority.requireCurrentSeller()).thenThrow(new IllegalStateException("Fixture unavailable"));
        assertThrows(IllegalStateException.class, () -> invoke(operation, "{"));
        verifyNoInteractions(sellers, stores);
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "resubmit", "createStore", "updateStore", "setStoreOpen",
            "sellerForModeration", "approve", "reject", "suspend", "reinstate"})
    void nullTrustedIdentityIsNeverTreatedAsAnonymousSuccess(String operation) {
        assertThrows(IllegalStateException.class, () -> invoke(operation, "{}"));
        verifyNoInteractions(sellers, stores);
    }

    @Test void accountOnlyRegistrationKeepsExactIdentityAndPrivateResponseShape() {
        var exact = new SellerIdentity(" Exact-Subject ");
        when(authority.requireCurrentAccount()).thenReturn(exact);
        var created = new SellerPersistenceService.PersistedSeller(UUID.randomUUID(), 0,
                new SellerApplication(exact, SellerStatus.PENDING_REVIEW, null));
        when(sellers.register(exact)).thenReturn(created);
        var result = handler.register("{}");
        assertEquals(created.id(), result.id());
        assertEquals("0", result.version());
        assertEquals(SellerStatus.PENDING_REVIEW, result.status());
        verify(authority, never()).requireCurrentSeller();
        verify(sellers).register(exact);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"ownerReference\":\"spoofed\"}", "{\"role\":\"ADMIN\"}",
            "{\"sellerId\":\"spoofed\"}", "{}{}", "{\"expectedVersion\":0}"})
    void registrationNeverAcceptsIdentityOrExtraFields(String payload) {
        when(authority.requireCurrentAccount()).thenReturn(new SellerIdentity("owner"));
        assertThrows(SellerStoreJson.InvalidInput.class, () -> handler.register(payload));
        verifyNoInteractions(sellers, stores);
    }

    private Object invoke(String operation, String payload) {
        UUID target = UUID.fromString("a1000000-0000-4000-8000-000000000001");
        return switch (operation) {
            case "register" -> handler.register(payload);
            case "resubmit" -> handler.resubmit(payload);
            case "createStore" -> handler.createStore(payload);
            case "updateStore" -> handler.updateStore(payload);
            case "setStoreOpen" -> handler.setStoreOpen(payload);
            case "sellerForModeration" -> handler.sellerForModeration(target);
            case "approve" -> handler.approve(target, payload);
            case "reject" -> handler.reject(target, payload);
            case "suspend" -> handler.suspend(target, payload);
            case "reinstate" -> handler.reinstate(target, payload);
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
