package com.projecthk5.orderservice.service;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Action;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinanceReportTest {
    private final Instant now = Instant.parse("2026-10-30T10:00:00Z");

    private SellerOrder delivered(String gross, String fee) {
        Order order = new Order(UUID.randomUUID(), "c");
        SellerOrder so = new SellerOrder(order, "s", "store", new BigDecimal(gross), new BigDecimal(fee));
        for (Action a : new Action[]{Action.ACCEPT, Action.START_PREPARING, Action.READY_FOR_HANDOFF, Action.DISPATCH, Action.DELIVER})
            so.apply(a, null, now);
        return so;
    }

    @Test
    void totalsSplitPendingCollectedSettledAndPayable() {
        SellerOrder pending = delivered("100000", "5000");
        SellerOrder collected = delivered("200000", "10000");
        collected.collectCod("admin", now);
        SellerOrder settled = delivered("40000", "0");
        settled.collectCod("admin", now);
        settled.settle("admin", now);

        FinanceReport.Totals t = FinanceReport.totals(List.of(pending, collected, settled));
        assertEquals(3, t.deliveredOrders());
        assertEquals(new BigDecimal("340000"), t.gross());
        assertEquals(new BigDecimal("15000"), t.platformFee());
        assertEquals(new BigDecimal("325000"), t.sellerNet());
        assertEquals(new BigDecimal("100000"), t.codPending());
        assertEquals(new BigDecimal("240000"), t.codCollected());
        assertEquals(new BigDecimal("40000"), t.settledNet());
        assertEquals(new BigDecimal("190000"), t.payableNet());
    }

    @Test
    void emptyStoreHasZeroTotals() {
        FinanceReport.Totals t = FinanceReport.totals(List.of());
        assertEquals(0, t.deliveredOrders());
        assertEquals(BigDecimal.ZERO, t.gross());
    }
}
