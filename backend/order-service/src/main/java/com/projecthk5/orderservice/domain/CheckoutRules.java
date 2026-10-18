package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.ports.CartPort;
import java.util.*;

public final class CheckoutRules {
    private CheckoutRules() {}

    /**
     * Fail closed on malformed Cart snapshots as well as invalid client expectations.
     * The API restricts quantities to 1..1000; applying the same invariant here
     * prevents bypasses through internal adapters and test clients.
     */
    public static void requireSameItems(List<ExpectedItem> expected, List<CartPort.Item> actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty())
            throw new IllegalStateException("Checkout item lists must be nonempty");
        Map<String,Integer> a = new HashMap<>();
        Map<String,Integer> b = new HashMap<>();
        for (ExpectedItem i : expected) {
            if (i == null || invalid(i.productId(), i.quantity())
                    || a.putIfAbsent(i.productId(), i.quantity()) != null)
                throw new IllegalStateException("Invalid or duplicate expectedItems");
        }
        for (CartPort.Item i : actual) {
            if (i == null || invalid(i.productId(), i.quantity())
                    || b.putIfAbsent(i.productId(), i.quantity()) != null)
                throw new IllegalStateException("Invalid or duplicate items in cart");
        }
        if (!a.equals(b)) throw new IllegalStateException("Cart and expectedItems differ");
    }

    private static boolean invalid(String productId, int quantity) {
        return productId == null || productId.isBlank() || quantity < 1 || quantity > 1000;
    }
}
