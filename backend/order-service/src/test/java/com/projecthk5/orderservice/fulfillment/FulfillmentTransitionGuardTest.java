package com.projecthk5.orderservice.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FulfillmentTransitionGuardTest {
    private static final String STORE_A = "store-a";
    private static final String STORE_B = "store-b";

    @Test
    void allowsOwnerToPerformAnExistingValidTransition() {
        assertEquals(SellerOrderLifecycle.Status.DISPATCHED,
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_A, STORE_A, SellerOrderLifecycle.Status.READY_FOR_HANDOFF,
                        SellerOrderLifecycle.Action.DISPATCH, null));
    }

    @Test
    void rejectsForeignStoreBeforeDisclosingLifecycleEligibility() {
        SecurityException failure = assertThrows(SecurityException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_B, STORE_A, SellerOrderLifecycle.Status.READY_FOR_HANDOFF,
                        SellerOrderLifecycle.Action.DISPATCH, null));

        assertEquals("Fulfillment access requires Store ownership", failure.getMessage());
    }

    @Test
    void rejectsPaddedStoreIdsEvenWhenBothInputsContainTheSameWhitespace() {
        assertThrows(SecurityException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        " store-a", " store-a", SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.ACCEPT, null));
        assertThrows(SecurityException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        "store-a ", "store-a ", SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.ACCEPT, null));
    }

    @Test
    void rejectsInvalidAndRepeatedDeliveryActions() {
        assertThrows(IllegalStateException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_A, STORE_A, SellerOrderLifecycle.Status.READY_FOR_HANDOFF,
                        SellerOrderLifecycle.Action.DELIVER, null));
        assertThrows(IllegalStateException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_A, STORE_A, SellerOrderLifecycle.Status.DELIVERED,
                        SellerOrderLifecycle.Action.DELIVER, null));
    }

    @Test
    void keepsExistingRejectionReasonValidationAfterOwnershipCheck() {
        assertThrows(IllegalArgumentException.class, () ->
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_A, STORE_A, SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.REJECT, " "));
        assertEquals(SellerOrderLifecycle.Status.REJECTED,
                FulfillmentTransitionGuard.requireAllowed(
                        STORE_A, STORE_A, SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.REJECT, "Out of stock"));
    }
}
