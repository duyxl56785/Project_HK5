package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.StorePersistenceService.PersistedStore;
import com.projecthk5.sellerservice.store.StoreProfile;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerStoreResponsesTest {
    private final SellerStoreJson json = new SellerStoreJson();
    private final ObjectMapper inspection = new ObjectMapper();
    private final UUID sellerId = UUID.randomUUID();
    private final String privateOwner = "private-account-reference";

    @Test
    void privateSellerRepresentationHasOnlyAllowedFieldsAndExactStringVersion() throws Exception {
        var source = new PersistedSeller(sellerId, Long.MAX_VALUE,
                new SellerApplication(new SellerIdentity(privateOwner), SellerStatus.REJECTED, "Review details"));
        var response = SellerStoreResponses.sellerPrivate(source);
        JsonNode tree = inspection.readTree(json.writeSellerPrivate(response));
        assertEquals(Set.of("id", "version", "status", "reviewReason"), fields(tree));
        assertEquals(sellerId.toString(), tree.get("id").textValue());
        assertTrue(tree.get("version").isTextual());
        assertEquals("9223372036854775807", tree.get("version").textValue());
        assertEquals("REJECTED", tree.get("status").textValue());
        assertEquals("Review details", tree.get("reviewReason").textValue());
        assertFalse(tree.toString().contains(privateOwner));
    }

    @Test
    void clearedReasonStaysExplicitNullInPrivateResponse() throws Exception {
        var source = new PersistedSeller(sellerId, 0,
                new SellerApplication(new SellerIdentity(privateOwner), SellerStatus.APPROVED, null));
        JsonNode tree = inspection.readTree(json.writeSellerPrivate(SellerStoreResponses.sellerPrivate(source)));
        assertEquals("0", tree.get("version").textValue());
        assertTrue(tree.has("reviewReason"));
        assertTrue(tree.get("reviewReason").isNull());
    }

    @Test
    void ownerStoreResponsePreservesIdentityOpenAndUtcTimestampsWithoutOwnerOrReason() throws Exception {
        UUID storeId = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-21T01:02:03.123456Z");
        Instant updated = created.plusSeconds(1);
        var source = new PersistedStore(new StoreProfile(storeId, sellerId, privateOwner, "Store", "store", false),
                9007199254740993L, created, updated);
        JsonNode tree = inspection.readTree(json.writeStoreOwner(SellerStoreResponses.storeOwner(source)));
        assertEquals(Set.of("id", "sellerId", "name", "slug", "open", "version", "createdAt", "updatedAt"), fields(tree));
        assertEquals(storeId.toString(), tree.get("id").textValue());
        assertEquals(sellerId.toString(), tree.get("sellerId").textValue());
        assertEquals("Store", tree.get("name").textValue());
        assertEquals("store", tree.get("slug").textValue());
        assertFalse(tree.get("open").booleanValue());
        assertEquals("9007199254740993", tree.get("version").textValue());
        assertEquals(created.toString(), tree.get("createdAt").textValue());
        assertEquals(updated.toString(), tree.get("updatedAt").textValue());
        assertFalse(tree.toString().contains(privateOwner));
        assertFalse(tree.has("reviewReason"));
    }

    @Test
    void invalidSourceCannotProduceAnApparentlyValidResponse() {
        assertThrows(IllegalArgumentException.class, () -> SellerStoreResponses.sellerPrivate(null));
        assertThrows(IllegalArgumentException.class, () -> SellerStoreResponses.storeOwner(null));
        assertThrows(IllegalArgumentException.class, () -> SellerStoreResponses.sellerPrivate(new PersistedSeller(
                sellerId, -1, new SellerApplication(new SellerIdentity(privateOwner), SellerStatus.APPROVED, null))));
        assertThrows(IllegalArgumentException.class, () -> json.writeSellerPrivate(null));
        assertThrows(IllegalArgumentException.class, () -> json.writeStoreOwner(null));
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
