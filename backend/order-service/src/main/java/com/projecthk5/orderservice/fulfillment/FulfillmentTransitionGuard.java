package com.projecthk5.orderservice.fulfillment;

import java.util.Objects;

/**
 * Fail-closed precondition for one SellerOrder fulfillment transition.
 *
 * <p>This is deliberately a pure boundary: the caller must obtain the Store
 * identity from an approved trusted authority and must atomically persist the
 * returned state with the SellerOrder. It does not authenticate a caller,
 * perform a database update, publish an event, call Inventory, or record COD.
 * Checking ownership before the lifecycle rule prevents an unauthorized Store
 * from learning whether a foreign SellerOrder is in a transitionable state.</p>
 */
public final class FulfillmentTransitionGuard {
    private FulfillmentTransitionGuard() {
    }

    /**
     * Validates exact trusted Store ownership and the existing lifecycle rule,
     * returning the only permitted next state. A repeated action is rejected
     * once the caller has durably stored the first resulting state.
     */
    public static SellerOrderLifecycle.Status requireAllowed(
            String verifiedStoreId,
            String sellerOrderStoreId,
            SellerOrderLifecycle.Status currentStatus,
            SellerOrderLifecycle.Action action,
            String rejectionReason) {
        FulfillmentStoreOwnership.requireOwner(verifiedStoreId, sellerOrderStoreId);
        return SellerOrderLifecycle.transition(
                Objects.requireNonNull(currentStatus, "current status is required"),
                Objects.requireNonNull(action, "action is required"),
                rejectionReason);
    }
}
