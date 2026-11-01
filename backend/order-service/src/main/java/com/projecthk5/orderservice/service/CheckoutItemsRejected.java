package com.projecthk5.orderservice.service;

import java.util.List;

/** Checkout refused the listed Cart lines; nothing was created and no line was dropped. */
public final class CheckoutItemsRejected extends IllegalStateException {
    public enum Reason { PRODUCT_UNAVAILABLE, STORE_UNAVAILABLE, CATALOG_CHANGED, PRICE_CHANGED, OUT_OF_STOCK }
    public record Item(String productId, Reason reason) {}

    private final transient List<Item> items;

    public CheckoutItemsRejected(List<Item> items) {
        super("Some Cart items cannot be ordered");
        this.items = List.copyOf(items);
    }

    public List<Item> items() { return items; }
}
