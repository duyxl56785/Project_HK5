package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.ports.CartPort;
import java.util.*;

public final class CheckoutRules {
    private CheckoutRules() {}
    public static void requireSameItems(List<ExpectedItem> expected,List<CartPort.Item> actual) {
        Map<String,Integer> a = new HashMap<>();
        Map<String,Integer> b = new HashMap<>();
        for (ExpectedItem i : expected) {
            if (i.productId() == null || i.productId().isBlank() || i.quantity() < 1 || a.putIfAbsent(i.productId(),i.quantity()) != null)
                throw new IllegalStateException("Invalid or duplicate expectedItems");
        }
        for (CartPort.Item i : actual) {
            if (b.putIfAbsent(i.productId(),i.quantity()) != null) throw new IllegalStateException("Duplicate items in cart");
        }
        if (!a.equals(b)) throw new IllegalStateException("Cart and expectedItems differ");
    }
}
