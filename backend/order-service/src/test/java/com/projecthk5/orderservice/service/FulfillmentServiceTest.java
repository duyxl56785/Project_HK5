package com.projecthk5.orderservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Status;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.security.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FulfillmentServiceTest {
    private final SellerOrderRepository repo = mock(SellerOrderRepository.class);
    private final OrderEvents events = mock(OrderEvents.class);
    private final FulfillmentService service = new FulfillmentService(repo, events,
            Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));
    private final Order order = new Order(UUID.randomUUID(), "customer-1");
    private final SellerOrder so = new SellerOrder(order, "seller-1", "store-1", new BigDecimal("100000"), new BigDecimal("5000"));

    FulfillmentServiceTest() {
        so.getItems().add(new OrderItem(so, "p1", 2, new BigDecimal("50000"), new BigDecimal("100000"), "SKU-1", "Ao"));
        order.addSellerOrder(so);
        when(repo.lockById(so.getId())).thenReturn(Optional.of(so));
    }

    private static HttpStatus status(Runnable call) {
        return assertThrows(ApiException.class, call::run).status();
    }

    @Test
    void sellerCannotTouchAnotherStoresOrder() {
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.sellerAction("store-2", so.getId(), "accept", null)));
        assertEquals(Status.PLACED, so.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void unknownActionAndMissingOrderAre404() {
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.sellerAction("store-1", so.getId(), "teleport", null)));
        when(repo.lockById(any())).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.sellerAction("store-1", UUID.randomUUID(), "accept", null)));
    }

    @Test
    void acceptNotifiesBuyer() {
        service.sellerAction("store-1", so.getId(), "accept", null);
        assertEquals(Status.ACCEPTED, so.getStatus());
        verify(events).notifyCustomer(eq(so), eq("ORDER_ACCEPTED"), anyString(), anyString(), eq("accepted"), eq("PLACED"), eq("SELLER"));
        verify(events, never()).inventory(any(), anyString());
    }

    @Test
    void dispatchConsumesReservedStockExactlyOnce() {
        for (String a : new String[]{"accept", "prepare", "ready"})
            service.sellerAction("store-1", so.getId(), a, null);
        verify(events, never()).inventory(so, "CONSUME");
        service.sellerAction("store-1", so.getId(), "dispatch", null);
        assertEquals(Status.DISPATCHED, so.getStatus());
        verify(events, times(1)).inventory(so, "CONSUME");
        service.sellerAction("store-1", so.getId(), "deliver", null);
        assertEquals(Status.DELIVERED, so.getStatus());
        verify(events, times(1)).inventory(so, "CONSUME");
        verify(events, never()).inventory(so, "RELEASE");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.sellerAction("store-1", so.getId(), "deliver", null)));
        verify(events, times(1)).inventory(so, "CONSUME");
    }

    @Test
    void rejectRequiresReasonThenReleasesStock() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.sellerAction("store-1", so.getId(), "reject", null)));
        service.sellerAction("store-1", so.getId(), "reject", "Het hang");
        assertEquals(Status.REJECTED, so.getStatus());
        assertEquals(Order.Status.CANCELLED, order.getStatus());
        verify(events).inventory(so, "RELEASE");
    }

    @Test
    void customerCancelIsScopedToBuyerAndOnlyBeforeAcceptance() {
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.customerCancel("intruder", order.getId(), so.getId(), null)));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.customerCancel("customer-1", UUID.randomUUID(), so.getId(), null)));
        service.customerCancel("customer-1", order.getId(), so.getId(), null);
        assertEquals(Status.CANCELLED, so.getStatus());
        verify(events).inventory(so, "RELEASE");
        verify(events).notifyStore(eq(so), eq("ORDER_CANCELLED"), anyString(), anyString(), eq("cancelled"), eq("PLACED"), eq("CUSTOMER"));
        assertEquals(HttpStatus.CONFLICT, status(() -> service.customerCancel("customer-1", order.getId(), so.getId(), null)));
    }

    @Test
    void codAndSettlementFollowDeliveryAndHappenOnce() {
        assertEquals(HttpStatus.CONFLICT, status(() -> service.collectCod("admin", so.getId())));
        for (String a : new String[]{"accept", "prepare", "ready", "dispatch", "deliver"})
            service.sellerAction("store-1", so.getId(), a, null);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.settle("admin", so.getId())));
        service.collectCod("admin", so.getId());
        assertEquals(HttpStatus.CONFLICT, status(() -> service.collectCod("admin", so.getId())));
        service.settle("admin", so.getId());
        assertNotNull(so.getSettledAt());
        assertEquals(HttpStatus.CONFLICT, status(() -> service.settle("admin", so.getId())));
        verify(events).notifyStore(eq(so), eq("SETTLED"), anyString(), contains("95000"), eq("settled"), anyString(), eq("ADMIN"));
    }
}
