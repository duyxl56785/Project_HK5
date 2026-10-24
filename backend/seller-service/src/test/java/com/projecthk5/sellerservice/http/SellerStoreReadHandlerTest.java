package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceException;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.*;
import com.projecthk5.sellerservice.store.StorePersistenceService.PersistedStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Authority doubles only; these tests do not verify JWT or expose HTTP. */
class SellerStoreReadHandlerTest {
    private final SellerStoreReadAuthority authority = mock(SellerStoreReadAuthority.class);
    private final SellerPersistenceService sellers = mock(SellerPersistenceService.class);
    private final StorePersistenceService stores = mock(StorePersistenceService.class);
    private final SellerIdentity owner = new SellerIdentity(" Exact-Owner ");
    private final UUID sellerId = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private SellerStoreReadHandler handler;

    @BeforeEach
    void setup() { handler = new SellerStoreReadHandler(authority, sellers, stores); }

    @ParameterizedTest
    @EnumSource(SellerStatus.class)
    void sellerOwnUsesRoleNeutralAccountAndPreservesAllOwnStatuses(SellerStatus status) throws Exception {
        when(authority.requireCurrentAccount()).thenReturn(owner);
        when(authority.requireCurrentSeller()).thenThrow(new AssertionError("Seller role must not be required"));
        String reason = status == SellerStatus.REJECTED || status == SellerStatus.SUSPENDED ? "Private reason" : null;
        when(sellers.findOwn(owner)).thenReturn(new PersistedSeller(sellerId, 0,
                new SellerApplication(owner, status, reason)));
        var dto = handler.sellerOwn();
        var json = new ObjectMapper().readTree(new SellerStoreJson().writeSellerPrivate(dto));
        assertEquals(sellerId.toString(), json.get("id").textValue());
        assertEquals(status.name(), json.get("status").textValue());
        assertEquals("0", json.get("version").textValue());
        if (reason == null) assertTrue(json.get("reviewReason").isNull());
        else assertEquals("Private reason", json.get("reviewReason").textValue());
        assertEquals(Set.of("id", "version", "status", "reviewReason"), fields(json));
        assertFalse(json.toString().contains(owner.reference()));
        verify(sellers).findOwn(owner);
        verifyNoInteractions(stores);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 9007199254740993L, Long.MAX_VALUE})
    void storeOwnUsesSellerScopeAndExistingPrivateJsonWithoutVersionPrecisionLoss(long version) throws Exception {
        when(authority.requireCurrentSeller()).thenReturn(owner);
        when(authority.requireCurrentAccount()).thenThrow(new AssertionError("Store needs SELLER scope"));
        var id = UUID.fromString("50000000-0000-4000-8000-000000000001");
        when(stores.findOwn(owner)).thenReturn(new PersistedStore(
                new StoreProfile(id, sellerId, owner.reference(), "Own store", "own-store", false),
                version, Instant.parse("2026-10-20T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")));
        var json = new ObjectMapper().readTree(new SellerStoreJson().writeStoreOwner(handler.storeOwn()));
        assertEquals(id.toString(), json.get("id").textValue());
        assertEquals(sellerId.toString(), json.get("sellerId").textValue());
        assertFalse(json.get("open").booleanValue());
        assertEquals(Long.toString(version), json.get("version").textValue());
        assertTrue(json.get("version").isTextual());
        assertEquals(Set.of("id", "sellerId", "name", "slug", "open", "version", "createdAt", "updatedAt"), fields(json));
        assertFalse(json.toString().contains(owner.reference()));
        verify(stores).findOwn(owner);
        verifyNoInteractions(sellers);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAuthorityResultCannotReachPersistence(boolean store) {
        when(authority.requireCurrentAccount()).thenReturn(null);
        when(authority.requireCurrentSeller()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> read(store));
        verifyNoInteractions(sellers, stores);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void outOfRangeVerifiedIdentityCannotReachPersistence(boolean store) {
        var invalid = new SellerIdentity("x".repeat(129));
        when(authority.requireCurrentAccount()).thenReturn(invalid);
        when(authority.requireCurrentSeller()).thenReturn(invalid);
        assertThrows(IllegalStateException.class, () -> read(store));
        verifyNoInteractions(sellers, stores);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deniedOrUnavailableAuthorityStopsBeforePrivateLookup(boolean store) {
        var unavailable = new IllegalStateException("Authority unavailable");
        when(authority.requireCurrentAccount()).thenThrow(unavailable);
        when(authority.requireCurrentSeller()).thenThrow(unavailable);
        assertSame(unavailable, assertThrows(IllegalStateException.class, () -> read(store)));
        verifyNoInteractions(sellers, stores);
    }

    @Test
    void sellerAbsenceIsNotTranslatedToEmptySuccess() {
        when(authority.requireCurrentAccount()).thenReturn(owner);
        var missing = new SellerPersistenceException(SellerPersistenceException.Code.SELLER_NOT_FOUND, "Missing");
        when(sellers.findOwn(owner)).thenThrow(missing);
        assertSame(missing, assertThrows(SellerPersistenceException.class, handler::sellerOwn));
    }

    @Test
    void storeAbsenceIsNotTranslatedToNullOrEmptySuccess() {
        when(authority.requireCurrentSeller()).thenReturn(owner);
        var missing = new StorePersistenceException(StorePersistenceException.Code.STORE_NOT_FOUND, "Missing");
        when(stores.findOwn(owner)).thenThrow(missing);
        assertSame(missing, assertThrows(StorePersistenceException.class, handler::storeOwn));
    }

    @Test
    void existingStorePolicyDenialIsNotSwallowedByPresentation() {
        when(authority.requireCurrentSeller()).thenReturn(owner);
        var denied = new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Denied");
        when(stores.findOwn(owner)).thenThrow(denied);
        assertSame(denied, assertThrows(StoreRuleException.class, handler::storeOwn));
    }

    private Object read(boolean store) { return store ? handler.storeOwn() : handler.sellerOwn(); }

    private static Set<String> fields(com.fasterxml.jackson.databind.JsonNode node) {
        return StreamSupport.stream(((Iterable<String>) () -> node.fieldNames()).spliterator(), false)
                .collect(Collectors.toSet());
    }
}
