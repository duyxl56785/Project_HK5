package com.projecthk5.orderservice.fulfillment;

import java.util.Objects;

/**
 * Pure domain transition rules for SellerOrder fulfillment.
 *
 * <p>The vocabulary is the TV3 candidate documented in the TV4 fulfillment
 * contract. It is intentionally isolated from JPA entities and HTTP APIs until
 * the cross-owner state/event contract is approved. This class does not perform
 * authentication, Store ownership checks, persistence, inventory operations,
 * COD settlement, or event publication; callers must enforce those boundaries.</p>
 */
public final class SellerOrderLifecycle {
    private SellerOrderLifecycle() {
    }

    public enum Status {
        PLACED,
        ACCEPTED,
        PREPARING,
        READY_FOR_HANDOFF,
        DISPATCHED,
        DELIVERED,
        CANCELLED,
        REJECTED
    }

    public enum Action {
        ACCEPT,
        START_PREPARING,
        READY_FOR_HANDOFF,
        DISPATCH,
        DELIVER,
        CANCEL,
        REJECT
    }

    /**
     * Returns the next state for a valid action.
     *
     * @param current current persisted state
     * @param action requested transition
     * @param rejectionReason required for REJECT; ignored for other actions
     * @throws IllegalStateException if the action is not valid from current
     * @throws NullPointerException if current status or action is absent
     * @throws IllegalArgumentException if a required rejection reason is absent
     */
    public static Status transition(Status current, Action action, String rejectionReason) {
        Objects.requireNonNull(current, "current status is required");
        Objects.requireNonNull(action, "action is required");

        return switch (action) {
            case ACCEPT -> requireCurrent(current, Status.PLACED, Status.ACCEPTED);
            case START_PREPARING -> requireCurrent(current, Status.ACCEPTED, Status.PREPARING);
            case READY_FOR_HANDOFF -> requireCurrent(current, Status.PREPARING, Status.READY_FOR_HANDOFF);
            case DISPATCH -> requireCurrent(current, Status.READY_FOR_HANDOFF, Status.DISPATCHED);
            case DELIVER -> requireCurrent(current, Status.DISPATCHED, Status.DELIVERED);
            case CANCEL -> requireCurrent(current, Status.PLACED, Status.CANCELLED);
            case REJECT -> {
                requireCurrent(current, Status.PLACED, Status.REJECTED);
                if (rejectionReason == null || rejectionReason.isBlank()) {
                    throw new IllegalArgumentException("rejection reason is required");
                }
                yield Status.REJECTED;
            }
        };
    }

    private static Status requireCurrent(Status actual, Status expected, Status next) {
        if (actual != expected) {
            throw new IllegalStateException("action is not allowed from " + actual);
        }
        return next;
    }
}
