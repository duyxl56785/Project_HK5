package com.projecthk5.orderservice.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Action;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerOrderTest {
    private final Instant now = Instant.parse("2026-10-30T10:00:00Z");

    private SellerOrder placed() {
        Order order = new Order(UUID.randomUUID(), "customer");
        SellerOrder so = new SellerOrder(order, "seller", "store", new BigDecimal("200000"), new BigDecimal("10000"));
        order.addSellerOrder(so);
        return so;
    }

    private SellerOrder delivered() {
        SellerOrder so = placed();
        for (Action a : new Action[]{Action.ACCEPT, Action.START_PREPARING, Action.READY_FOR_HANDOFF, Action.DISPATCH, Action.DELIVER})
            so.apply(a, null, now);
        return so;
    }

    @Test
    void fullHappyPathRecordsDeliveryTimeAndNet() {
        SellerOrder so = delivered();
        assertEquals(Status.DELIVERED, so.getStatus());
        assertEquals(now, so.getDeliveredAt());
        assertEquals(new BigDecimal("190000"), so.getSellerNet());
        assertEquals(SellerOrder.CodStatus.PENDING, so.getCodStatus());
    }

    @Test
    void rejectNeedsReasonAndVoidsCod() {
        SellerOrder so = placed();
        assertThrows(IllegalArgumentException.class, () -> so.apply(Action.REJECT, " ", now));
        so.apply(Action.REJECT, "  Het hang  ", now);
        assertEquals(Status.REJECTED, so.getStatus());
        assertEquals("Het hang", so.getStatusReason());
        assertEquals(SellerOrder.CodStatus.VOID, so.getCodStatus());
    }

    @Test
    void cancellingEveryStoreOrderCancelsTheParentOrder() {
        Order order = new Order(UUID.randomUUID(), "customer");
        SellerOrder a = new SellerOrder(order, "s1", "store1", BigDecimal.TEN, BigDecimal.ZERO);
        SellerOrder b = new SellerOrder(order, "s2", "store2", BigDecimal.TEN, BigDecimal.ZERO);
        order.addSellerOrder(a);
        order.addSellerOrder(b);
        a.apply(Action.CANCEL, "doi y", now);
        order.refreshStatus();
        assertEquals(Order.Status.PLACED, order.getStatus());
        b.apply(Action.REJECT, "het hang", now);
        order.refreshStatus();
        assertEquals(Order.Status.CANCELLED, order.getStatus());
    }

    @Test
    void cannotCancelAfterAcceptOrSkipSteps() {
        SellerOrder so = placed();
        so.apply(Action.ACCEPT, null, now);
        assertThrows(IllegalStateException.class, () -> so.apply(Action.CANCEL, "x", now));
        assertThrows(IllegalStateException.class, () -> so.apply(Action.DELIVER, null, now));
    }

    @Test
    void codIsCollectedOnceAndOnlyAfterDelivery() {
        SellerOrder early = placed();
        assertThrows(IllegalStateException.class, () -> early.collectCod("admin", now));
        SellerOrder so = delivered();
        so.collectCod("admin", now);
        assertEquals(SellerOrder.CodStatus.COLLECTED, so.getCodStatus());
        assertEquals(now, so.getCodCollectedAt());
        assertThrows(IllegalStateException.class, () -> so.collectCod("admin", now));
    }

    @Test
    void settlementRequiresCollectedCodAndHappensOnce() {
        SellerOrder so = delivered();
        assertThrows(IllegalStateException.class, () -> so.settle("admin", now));
        so.collectCod("admin", now);
        so.settle("admin", now);
        assertEquals(now, so.getSettledAt());
        assertThrows(IllegalStateException.class, () -> so.settle("admin", now));
    }
}
