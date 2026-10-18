package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.ports.CartPort;
import java.util.*;

public final class CheckoutRules {
    private CheckoutRules() {}

    /**
     * Reject malformed client expectations before creating an idempotency attempt
     * or claiming a Cart. Also used for rechecking the claimed Cart snapshot.
     */
    public static void requireValidExpectedItems(List<ExpectedItem> expected) {
        if (expected == null || expected.isEmpty())
            throw new IllegalStateException("Checkout expectedItems must be nonempty");
        Set<String> ids = new HashSet<>();
        for (ExpectedItem i : expected) {
            if (i == null || invalid(i.productId(), i.quantity()) || !ids.add(i.productId()))
                throw new IllegalStateException("Invalid or duplicate expectedItems");
        }
    }

    /**
     * Fail closed on malformed Cart snapshots as well as invalid client expectations.
     * The API restricts quantities to 1..1000; applying the same invariant here
     * prevents bypasses through internal adapters and test clients.
     */
    public static void requireSameItems(List<ExpectedItem> expected, List<CartPort.Item> actual) {
        requireValidExpectedItems(expected);
        if (actual == null || actual.isEmpty())
            throw new IllegalStateException("Checkout item lists must be nonempty");
        Map<String,Integer> a = new HashMap<>();
        Map<String,Integer> b = new HashMap<>();
        for (ExpectedItem i : expected) {
            a.put(i.productId(), i.quantity());
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
