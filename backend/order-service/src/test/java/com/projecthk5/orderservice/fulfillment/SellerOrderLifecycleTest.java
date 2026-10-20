package com.projecthk5.orderservice.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SellerOrderLifecycleTest {

    @Test
    void allowsCandidateHappyPath() {
        SellerOrderLifecycle.Status state = SellerOrderLifecycle.Status.PLACED;
        state = SellerOrderLifecycle.transition(state, SellerOrderLifecycle.Action.ACCEPT, null);
        assertEquals(SellerOrderLifecycle.Status.ACCEPTED, state);
        state = SellerOrderLifecycle.transition(state, SellerOrderLifecycle.Action.START_PREPARING, null);
        assertEquals(SellerOrderLifecycle.Status.PREPARING, state);
        state = SellerOrderLifecycle.transition(state, SellerOrderLifecycle.Action.READY_FOR_HANDOFF, null);
        assertEquals(SellerOrderLifecycle.Status.READY_FOR_HANDOFF, state);
        state = SellerOrderLifecycle.transition(state, SellerOrderLifecycle.Action.DISPATCH, null);
        assertEquals(SellerOrderLifecycle.Status.DISPATCHED, state);
        state = SellerOrderLifecycle.transition(state, SellerOrderLifecycle.Action.DELIVER, null);
        assertEquals(SellerOrderLifecycle.Status.DELIVERED, state);
    }

    @Test
    void allowsCustomerCancelOnlyBeforeAcceptance() {
        assertEquals(
                SellerOrderLifecycle.Status.CANCELLED,
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.CANCEL,
                        null));
        assertThrows(IllegalStateException.class, () ->
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.ACCEPTED,
                        SellerOrderLifecycle.Action.CANCEL,
                        null));
    }

    @Test
    void requiresReasonForSellerRejection() {
        assertThrows(IllegalArgumentException.class, () ->
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.REJECT,
                        "  "));
        assertEquals(
                SellerOrderLifecycle.Status.REJECTED,
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.REJECT,
                        "Out of stock"));
    }

    @Test
    void rejectsSkippingOrRepeatingStates() {
        assertThrows(IllegalStateException.class, () ->
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.PLACED,
                        SellerOrderLifecycle.Action.DISPATCH,
                        null));
        assertThrows(IllegalStateException.class, () ->
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.ACCEPTED,
                        SellerOrderLifecycle.Action.ACCEPT,
                        null));
        assertThrows(IllegalStateException.class, () ->
                SellerOrderLifecycle.transition(
                        SellerOrderLifecycle.Status.DELIVERED,
                        SellerOrderLifecycle.Action.DELIVER,
                        null));
    }

    @Test
    void rejectsAnyActionFromTerminalStates() {
        for (SellerOrderLifecycle.Action action : SellerOrderLifecycle.Action.values()) {
            assertThrows(IllegalStateException.class, () ->
                    SellerOrderLifecycle.transition(
                            SellerOrderLifecycle.Status.CANCELLED, action, "reason"));
            assertThrows(IllegalStateException.class, () ->
                    SellerOrderLifecycle.transition(
                            SellerOrderLifecycle.Status.REJECTED, action, "reason"));
            assertThrows(IllegalStateException.class, () ->
                    SellerOrderLifecycle.transition(
                            SellerOrderLifecycle.Status.DELIVERED, action, "reason"));
        }
    }
}
