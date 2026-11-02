package com.projecthk5.orderservice.finance;

import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutRequest;
import com.projecthk5.orderservice.api.CheckoutDtos.ExpectedItem;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import java.math.BigDecimal;
import java.util.List;

/** Test-only stored amounts/identities, never an approved commission or current-authority source. */
final class FinanceTwoStoreFixture {
    static final String PRODUCT_A = "f1000000-0000-4000-8000-000000000001";
    static final String PRODUCT_B = "f1000000-0000-4000-8000-000000000002";
    static final String STORE_A = "f2000000-0000-4000-8000-000000000001";
    static final String STORE_B = "f2000000-0000-4000-8000-000000000002";

    private FinanceTwoStoreFixture() { }

    static CheckoutRequest request() {
        return new CheckoutRequest(List.of(new ExpectedItem(PRODUCT_A, 1), new ExpectedItem(PRODUCT_B, 1)), null);
    }

    static Order order(CheckoutAttempt attempt, String firstGross, String secondGross) {
        var order = new Order(attempt.getId(), attempt.getCustomerId());
        // Deliberately supplied historical amounts, not a calculation of rates/House policy.
        add(order, STORE_A, "f3000000-0000-4000-8000-000000000001", PRODUCT_A, firstGross, "1.23");
        add(order, STORE_B, "f3000000-0000-4000-8000-000000000002", PRODUCT_B, secondGross, "4.56");
        return order;
    }

    private static void add(Order order, String store, String seller, String product, String gross, String fee) {
        var amount = new BigDecimal(gross);
        var child = new SellerOrder(order, seller, store, amount, new BigDecimal(fee));
        child.getItems().add(new OrderItem(child, product, 1, amount, amount));
        order.addSellerOrder(child);
    }
}
