package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {
    private final CartPort cart=mock(CartPort.class);
    private final CatalogPort catalog=mock(CatalogPort.class);
    private final InventoryPort inventory=mock(InventoryPort.class);
    private final IdempotencyCoordinator keys=mock(IdempotencyCoordinator.class);
    private final OrderWriteService write=mock(OrderWriteService.class);
    private final OrderRepository repo=mock(OrderRepository.class);
    private final CheckoutService svc=new CheckoutService(cart,catalog,inventory,keys,write,repo,new ObjectMapper());
    private final CheckoutRequest request=new CheckoutRequest(List.of(new ExpectedItem("p1",2),new ExpectedItem("p2",1)),null);
    private final String customer="customer1", bearer="Bearer jwt", key="key-1";

    @Test void twoStoresGenerateOneOrderWithTwoSellerOrdersAndSnapshot() {
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,true));
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")),new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller1","store1",true,true,true,false,new BigDecimal("10000")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2","seller2","store2",true,true,true,true,new BigDecimal("15000")));
        when(write.savePlaced(any(Order.class),eq(attempt.getId()),anyString())).thenAnswer(inv -> inv.getArgument(0));
        CheckoutResult result=svc.checkout(customer,bearer,key,request);
        assertEquals("PLACED",result.status());
        assertEquals(2,result.sellerOrderCount());
        assertEquals(0,new BigDecimal("35000").compareTo(result.total()));
        verify(inventory).reserve(eq(result.orderId()),argThat(items -> items.size()==2));
        verify(cart).finalizeCart(bearer,key);
        verify(inventory,never()).release(any());
    }
    @Test void sameKeyAndExistingSuccessfulOrderReplaysWithoutCreating() {
        Order previous=new Order(UUID.randomUUID(),customer);
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        attempt.succeed(previous.getId());
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,false));
        when(repo.findById(previous.getId())).thenReturn(Optional.of(previous));
        CheckoutResult replay=svc.checkout(customer,bearer,key,request);
        assertEquals(previous.getId(),replay.orderId());
        verifyNoInteractions(catalog,inventory,write);
    }
    @Test void inventoryOutageReleasesCartAndMarksFailed() {
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,true));
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")),new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller1","store1",true,true,true,false,new BigDecimal("10000")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2","seller2","store2",true,true,true,true,new BigDecimal("15000")));
        doThrow(new IllegalStateException("Inventory timeout")).when(inventory).reserve(any(),anyList());
        assertThrows(IllegalStateException.class,()->svc.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(eq(attempt.getId()),eq(false));
        verifyNoInteractions(write);
    }
    @Test void changedPriceRejectsCheckoutBeforeStockReservation() {
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,true));
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller1","store1",true,true,true,false,new BigDecimal("12000")));
        assertThrows(IllegalStateException.class,()->svc.checkout(customer,bearer,key,request));
        verify(inventory,never()).reserve(any(),anyList());
        verify(cart).release(bearer,key);
        verifyNoInteractions(write);
    }
}
