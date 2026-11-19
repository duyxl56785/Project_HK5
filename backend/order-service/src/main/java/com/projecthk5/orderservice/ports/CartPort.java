package com.projecthk5.orderservice.ports;

import java.util.List;

public interface CartPort {
    CartSnapshot claim(String bearer,String key,Long expectedRevision);
    void release(String bearer,String key);
    void finalizeCart(String bearer,String key);
    default CartSnapshot view(String bearer) { throw new UnsupportedOperationException("Cart quote unavailable"); }
    default void recover(String customer, String key, long claimedRevision, boolean placed) {
        throw new UnsupportedOperationException("Cart recovery unavailable");
    }
    record Item(String productId,String storeId,String sellerId,int quantity,java.math.BigDecimal unitPriceSnapshot) {}
    record CartSnapshot(String customerId,long revision, boolean claimed,List<Item> items) {}
    /** Cart definitely refused the claim: nothing was claimed, so the attempt needs no reconciliation. */
    final class Rejected extends IllegalStateException {
        public Rejected(String message) { super(message); }
    }
}
