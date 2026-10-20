package com.projecthk5.orderservice.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SellerOrderLifecycleTest {

    @Test
    void rejectsNullStatusOrAction() {
        assertThrows(NullPointerException.class, () ->
                SellerOrderLifecycle.transition(null, SellerOrderLifecycle.Action.ACCEPT, null));
        assertThrows(NullPointerException.class, () ->
                SellerOrderLifecycle.transition(SellerOrderLifecycle.Status.PLACED, null, null));
    }

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
    void transitionTableRejectsEveryUnlistedStateActionPair() {
        var allowed = java.util.Map.of(
                SellerOrderLifecycle.Status.PLACED, java.util.Map.of(
                        SellerOrderLifecycle.Action.ACCEPT, SellerOrderLifecycle.Status.ACCEPTED,
                        SellerOrderLifecycle.Action.CANCEL, SellerOrderLifecycle.Status.CANCELLED,
                        SellerOrderLifecycle.Action.REJECT, SellerOrderLifecycle.Status.REJECTED),
                SellerOrderLifecycle.Status.ACCEPTED, java.util.Map.of(
                        SellerOrderLifecycle.Action.START_PREPARING, SellerOrderLifecycle.Status.PREPARING),
                SellerOrderLifecycle.Status.PREPARING, java.util.Map.of(
                        SellerOrderLifecycle.Action.READY_FOR_HANDOFF, SellerOrderLifecycle.Status.READY_FOR_HANDOFF),
                SellerOrderLifecycle.Status.READY_FOR_HANDOFF, java.util.Map.of(
                        SellerOrderLifecycle.Action.DISPATCH, SellerOrderLifecycle.Status.DISPATCHED),
                SellerOrderLifecycle.Status.DISPATCHED, java.util.Map.of(
                        SellerOrderLifecycle.Action.DELIVER, SellerOrderLifecycle.Status.DELIVERED));

        for (SellerOrderLifecycle.Status current : SellerOrderLifecycle.Status.values()) {
            for (SellerOrderLifecycle.Action action : SellerOrderLifecycle.Action.values()) {
                SellerOrderLifecycle.Status expected = allowed
                        .getOrDefault(current, java.util.Map.of())
                        .get(action);
                if (expected == null) {
                    assertThrows(IllegalStateException.class, () ->
                            SellerOrderLifecycle.transition(current, action, "reason"),
                            current + " must reject " + action);
                } else {
                    assertEquals(expected, SellerOrderLifecycle.transition(current, action, "reason"),
                            current + " + " + action);
                }
            }
        }
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
