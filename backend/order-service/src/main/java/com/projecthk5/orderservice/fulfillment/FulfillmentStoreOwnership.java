package com.projecthk5.orderservice.fulfillment;

/**
 * Fail-closed Store boundary for SellerOrder fulfillment policy checks.
 *
 * <p>The caller must obtain {@code verifiedStoreId} from an approved trusted
 * authentication/ownership boundary. This class does not authenticate an
 * actor, resolve current Seller/Store ownership, or perform persistence/API
 * authorization. It only compares that trusted Store ID with the Store ID
 * persisted on the SellerOrder.</p>
 */
public final class FulfillmentStoreOwnership {
    private FulfillmentStoreOwnership() {
    }

    /**
     * Requires an exact Store ID match before a fulfillment operation.
     *
     * @param verifiedStoreId Store ID verified by the caller's trusted boundary
     * @param sellerOrderStoreId Store ID persisted on the target SellerOrder
     * @throws SecurityException if either ID is absent/blank or they differ
     */
    public static void requireOwner(String verifiedStoreId, String sellerOrderStoreId) {
        if (verifiedStoreId == null || verifiedStoreId.isBlank()
                || sellerOrderStoreId == null || sellerOrderStoreId.isBlank()
                || !verifiedStoreId.equals(sellerOrderStoreId)) {
            throw new SecurityException("Fulfillment access requires Store ownership");
        }
    }
}
