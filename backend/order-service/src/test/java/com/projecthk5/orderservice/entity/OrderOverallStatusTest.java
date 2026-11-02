package com.projecthk5.orderservice.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Action;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOverallStatusTest {
    private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
    private final Order order = new Order(UUID.randomUUID(), "customer");

    private SellerOrder store() {
        SellerOrder so = new SellerOrder(order, "seller", "store", new BigDecimal("200000"), new BigDecimal("10000"));
        order.addSellerOrder(so);
        return so;
    }

    private void deliver(SellerOrder so) {
        for (Action a : new Action[]{Action.ACCEPT, Action.START_PREPARING, Action.READY_FOR_HANDOFF, Action.DISPATCH, Action.DELIVER})
            so.apply(a, null, now);
    }

    @Test
    void staysProcessingWhileAnyStoreOrderIsStillOpen() {
        SellerOrder first = store();
        SellerOrder second = store();
        assertEquals(Order.OverallStatus.PROCESSING, order.overallStatus());
        deliver(first);
        assertEquals(Order.OverallStatus.PROCESSING, order.overallStatus());
        second.apply(Action.ACCEPT, null, now);
        assertEquals(Order.OverallStatus.PROCESSING, order.overallStatus());
    }

    @Test
    void completesWhenEveryStoreOrderEndedAndOneWasDelivered() {
        SellerOrder first = store();
        SellerOrder second = store();
        deliver(first);
        second.apply(Action.REJECT, "Hết hàng", now);
        assertEquals(Order.OverallStatus.COMPLETED, order.overallStatus());
    }

    @Test
    void cancelledWhenEveryStoreOrderWasCancelledOrRejected() {
        SellerOrder first = store();
        SellerOrder second = store();
        first.apply(Action.CANCEL, null, now);
        second.apply(Action.REJECT, "Hết hàng", now);
        assertEquals(Order.OverallStatus.CANCELLED, order.overallStatus());
    }
}
