package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
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
    private final MoneyRules money=new MoneyRules(true,"0.05","0","true");
    private final CheckoutService svc=new CheckoutService(cart,catalog,inventory,keys,write,repo,new ObjectMapper(),money);
    private final CheckoutRequest request=new CheckoutRequest(List.of(new ExpectedItem("p1",2),new ExpectedItem("p2",1)),null);
    private final String customer="customer1", bearer="Bearer jwt", key="key-1";

    private CheckoutAttempt fresh() {
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,true));
        return attempt;
    }
    private void catalogAndCart() {
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller1","store1",true,true,true,false,new BigDecimal("10000")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2","seller2","store2",true,true,true,true,new BigDecimal("15000")));
    }
    @Test void twoStoresGenerateOneOrderWithTwoSellerOrdersAndSnapshot() {
        CheckoutAttempt attempt=fresh();catalogAndCart();
        when(write.savePlaced(any(Order.class),eq(attempt.getId()),anyString())).thenAnswer(inv -> inv.getArgument(0));
        CheckoutResult result=svc.checkout(customer,bearer,key,request);
        assertEquals("PLACED",result.status());
        assertEquals(2,result.sellerOrderCount());
        assertEquals(0,new BigDecimal("35000").compareTo(result.total()));
        assertEquals(attempt.getId(),result.orderId());
        verify(inventory).reserve(eq(attempt.getId()),argThat(items -> items.size()==2));
        verify(cart).finalizeCart(bearer,key);
        verify(inventory,never()).release(any());
    }
    @Test void sameKeyAndExistingSuccessfulOrderReplaysWithoutCreating() {
        Order previous=new Order(UUID.randomUUID(),customer);
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        attempt.succeed(previous.getId());
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,false));
        when(repo.findByIdAndCustomerId(previous.getId(), customer)).thenReturn(Optional.of(previous));
        CheckoutResult replay=svc.checkout(customer,bearer,key,request);
        assertEquals(previous.getId(),replay.orderId());
        verifyNoInteractions(catalog,inventory,write);
    }
    @Test void inventoryTimeoutPreservesCartAndReservationForReconciliation() {
        CheckoutAttempt attempt=fresh();catalogAndCart();
        doThrow(new IllegalStateException("Inventory timeout")).when(inventory).reserve(any(),anyList());
        assertThrows(ResponseStatusException.class,()->svc.checkout(customer,bearer,key,request));
        verify(cart,never()).release(anyString(),anyString());
        verify(inventory,never()).release(any());
        verify(keys).fail(attempt.getId(),true);
        verifyNoInteractions(write);
    }
    @Test void commitUnknownDoesNotCompensateAndFlagsForReconciliation() {
        CheckoutAttempt attempt=fresh();catalogAndCart();
        doThrow(new IllegalStateException("DB timeout after commit?")).when(write)
            .savePlaced(any(Order.class),eq(attempt.getId()),anyString());
        when(repo.findByIdAndCustomerId(attempt.getId(), customer)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,()->svc.checkout(customer,bearer,key,request));
        verify(cart,never()).release(anyString(),anyString());
        verify(inventory,never()).release(any());
        verify(keys).fail(attempt.getId(),true);
    }
    @Test void commitConfirmedByReadNeverCompensates() {
        CheckoutAttempt attempt=fresh();catalogAndCart();
        final Order[] saved=new Order[1];
        doAnswer(inv -> { saved[0]=inv.getArgument(0);throw new IllegalStateException("Client lost COMMIT response"); })
            .when(write).savePlaced(any(Order.class),eq(attempt.getId()),anyString());
        when(repo.findByIdAndCustomerId(attempt.getId(), customer)).thenAnswer(inv -> Optional.of(saved[0]));
        assertEquals(attempt.getId(),svc.checkout(customer,bearer,key,request).orderId());
        verify(inventory,never()).release(any());
        verify(cart,never()).release(anyString(),anyString());
    }
    @Test void changedPriceRejectsCheckoutBeforeStockReservation() {
        CheckoutAttempt attempt=fresh();catalogAndCart();
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller1","store1",true,true,true,false,new BigDecimal("12000")));
        assertThrows(IllegalStateException.class,()->svc.checkout(customer,bearer,key,request));
        verify(inventory,never()).reserve(any(),anyList());
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
    }
    @Test void malformedClaimedCartNullLineReleasesClaimWithoutExternalSideEffects() {
        CheckoutAttempt attempt=fresh();
        List<CartPort.Item> malformed = new ArrayList<>();
        malformed.add(new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")));
        malformed.add(null);
        when(cart.claim(bearer,key,null)).thenReturn(
            new CartPort.CartSnapshot(customer,1,true,malformed));

        assertThrows(IllegalStateException.class, () -> svc.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verifyNoInteractions(catalog,inventory,write);
    }

    @Test void invalidClaimedCartQuantityCannotReachReservationOrWriter() {
        CheckoutAttempt attempt=fresh();
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",0,new BigDecimal("10000")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));

        assertThrows(IllegalStateException.class, () -> svc.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verifyNoInteractions(catalog,inventory,write);
    }

    @Test void nonFinalProcessingReplayNeverCallsInventory() {
        CheckoutAttempt attempt=new CheckoutAttempt(customer,key,RequestFingerprint.compute(request));
        when(keys.begin(eq(customer),eq(key),anyString())).thenReturn(new IdempotencyCoordinator.BeginResult(attempt,false));
        assertThrows(ResponseStatusException.class,()->svc.checkout(customer,bearer,key,request));
        verifyNoInteractions(cart,catalog,inventory,write);
    }
    @Test void malformedRequestCannotCreateAttemptOrCallRemotePorts() {
        List<CheckoutRequest> invalid = java.util.Arrays.asList(
            null,
            new CheckoutRequest(null, null),
            new CheckoutRequest(List.of(), null),
            new CheckoutRequest(java.util.Arrays.asList((ExpectedItem) null), null),
            new CheckoutRequest(List.of(new ExpectedItem("p1", 0)), null),
            new CheckoutRequest(List.of(new ExpectedItem("p1", 1001)), null),
            new CheckoutRequest(List.of(new ExpectedItem("p1", 1), new ExpectedItem("p1", 2)), null));
        for (CheckoutRequest bad : invalid) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> svc.checkout(customer, bearer, key, bad));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(keys, cart, catalog, inventory, write, repo);
    }
    @Test void unsupportedUnitPrecisionNeverReachesInventoryOrWriter() {
        CheckoutAttempt attempt=fresh();
        catalogAndCart();
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("1.001")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot(
            "p1","seller1","store1",true,true,true,false,new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> svc.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verifyNoInteractions(inventory,write,repo);
    }

    @Test void parentOverflowAcrossTwoIndividuallyValidStoresFailsBeforeReserve() {
        CheckoutAttempt attempt=fresh();
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("30000000000000000.00")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("60000000000000000.00")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot(
            "p1","seller1","store1",true,true,true,false,new BigDecimal("30000000000000000.00")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot(
            "p2","seller2","store2",true,true,true,true,new BigDecimal("60000000000000000.00")));
        assertThrows(IllegalArgumentException.class, () -> svc.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verifyNoInteractions(inventory,write,repo);
    }

    @Test void unrepresentableFeeUnderSyntheticPolicyFailsBeforeReserve() {
        CheckoutAttempt attempt=fresh();
        when(cart.claim(bearer,key,null)).thenReturn(new CartPort.CartSnapshot(customer,1,true,List.of(
            new CartPort.Item("p1","store1","seller1",2,new BigDecimal("0.01")),
            new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot(
            "p1","seller1","store1",true,true,true,false,new BigDecimal("0.01")));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot(
            "p2","seller2","store2",true,true,true,true,new BigDecimal("15000")));
        CheckoutService synthetic=new CheckoutService(cart,catalog,inventory,keys,write,repo,
            new ObjectMapper(),new MoneyRules(true,"0.05","3","true"));
        assertThrows(IllegalArgumentException.class, () -> synthetic.checkout(customer,bearer,key,request));
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verifyNoInteractions(inventory,write,repo);
    }
    @Test void nullCartClaimReplyIsUnknownAndNeverBlindlyReleasesOrReserves() {
        CheckoutAttempt attempt=fresh();
        when(cart.claim(bearer,key,null)).thenReturn(null);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
            () -> svc.checkout(customer,bearer,key,request));
        assertEquals(503,error.getStatusCode().value());
        verify(keys).fail(attempt.getId(),true);
        verify(cart,never()).release(anyString(),anyString());
        verifyNoInteractions(catalog,inventory,write,repo);
    }

    @Test void explicitlyUnclaimedCartCannotProceedOrReleaseAnotherClaim() {
        CheckoutAttempt attempt=fresh();
        when(cart.claim(bearer,key,null)).thenReturn(
            new CartPort.CartSnapshot(customer,1,false,List.of(
                new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")),
                new CartPort.Item("p2","store2","seller2",1,new BigDecimal("15000")))));
        assertThrows(IllegalStateException.class, () -> svc.checkout(customer,bearer,key,request));
        verify(keys).fail(attempt.getId(),false);
        verify(cart,never()).release(anyString(),anyString());
        verifyNoInteractions(catalog,inventory,write,repo);
    }

    @Test void outboxSerializationFailureCannotReserveStockOrWriteOrder() throws Exception {
        CheckoutAttempt attempt=fresh();
        catalogAndCart();
        ObjectMapper brokenJson=mock(ObjectMapper.class);
        when(brokenJson.writeValueAsString(any())).thenThrow(
            new com.fasterxml.jackson.core.JsonProcessingException("Synthetic serialization failure") {});
        CheckoutService subject=new CheckoutService(cart,catalog,inventory,keys,write,repo,brokenJson,money);

        IllegalStateException error=assertThrows(IllegalStateException.class,
            () -> subject.checkout(customer,bearer,key,request));
        assertEquals("Unable to write outbox event",error.getMessage());
        verify(cart).release(bearer,key);
        verify(keys).fail(attempt.getId(),false);
        verify(cart,never()).finalizeCart(anyString(),anyString());
        verifyNoInteractions(inventory,write,repo);
    }
}
