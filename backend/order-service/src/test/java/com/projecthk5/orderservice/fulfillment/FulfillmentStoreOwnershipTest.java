package com.projecthk5.orderservice.fulfillment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FulfillmentStoreOwnershipTest {

    @Test
    void allowsOnlyExactVerifiedStoreMatch() {
        assertDoesNotThrow(() ->
                FulfillmentStoreOwnership.requireOwner("store-1", "store-1"));
    }

    @Test
    void rejectsDifferentStoreWithoutRevealingOrderDetails() {
        SecurityException error = assertThrows(SecurityException.class, () ->
                FulfillmentStoreOwnership.requireOwner("store-1", "store-2"));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Fulfillment access requires Store ownership", error.getMessage());
    }

    @Test
    void rejectsMissingOrBlankStoreIds() {
        assertThrows(SecurityException.class, () ->
                FulfillmentStoreOwnership.requireOwner(null, "store-1"));
        assertThrows(SecurityException.class, () ->
                FulfillmentStoreOwnership.requireOwner(" ", "store-1"));
        assertThrows(SecurityException.class, () ->
                FulfillmentStoreOwnership.requireOwner("store-1", null));
        assertThrows(SecurityException.class, () ->
                FulfillmentStoreOwnership.requireOwner("store-1", "\t"));
    }
}
