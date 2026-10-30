package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductReservationRecoveryWorkerTest {
    @Test void crashedClaimsExhaustBudgetWithoutFurtherAuthorityCalls() throws Exception {
        var store=mock(ProductReservationRecoveryStore.class);var ledger=mock(DurableWriteLedger.class);
        var auth=mock(ReservationRpcClient.class);var seller=mock(ReservationRpcClient.class);
        var claim=new ProductReservationRecoveryStore.Claim(mock(WriteIntent.class),7,UUID.randomUUID(),32);
        when(store.claim(Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        assertTrue(new ProductReservationRecoveryWorker(store,ledger,auth,seller,Duration.ofSeconds(30),32).tick());
        verify(store).retry(claim,"ATTEMPTS_EXHAUSTED",32);verifyNoInteractions(ledger,auth,seller);
        verify(store,never()).complete(any());
    }
    @Test void bothSealAcknowledgementsAreRequiredForCompletion() throws Exception {
        var store=mock(ProductReservationRecoveryStore.class);var ledger=mock(DurableWriteLedger.class);
        var auth=mock(ReservationRpcClient.class);var seller=mock(ReservationRpcClient.class);
        var intent=mock(WriteIntent.class);UUID request=UUID.randomUUID();when(intent.requestId()).thenReturn(request);
        var claim=new ProductReservationRecoveryStore.Claim(intent,7,UUID.randomUUID(),0);
        when(store.claim(Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        assertTrue(new ProductReservationRecoveryWorker(store,ledger,auth,seller,Duration.ofSeconds(30),32).tick());
        var order=inOrder(ledger,seller,auth,store);
        order.verify(ledger).abort(request,7);order.verify(seller).seal(intent,7);order.verify(auth).seal(intent,7);order.verify(store).complete(claim);
    }
    @Test void failedSecondParticipantKeepsWorkRecoverable() throws Exception {
        var store=mock(ProductReservationRecoveryStore.class);var ledger=mock(DurableWriteLedger.class);
        var auth=mock(ReservationRpcClient.class);var seller=mock(ReservationRpcClient.class);
        var intent=mock(WriteIntent.class);when(intent.requestId()).thenReturn(UUID.randomUUID());
        var claim=new ProductReservationRecoveryStore.Claim(intent,7,UUID.randomUUID(),0);
        when(store.claim(Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        doThrow(new ReservationRpcClient.Failure(503)).when(auth).seal(intent,7);
        new ProductReservationRecoveryWorker(store,ledger,auth,seller,Duration.ofSeconds(30),32).tick();
        verify(store).retry(claim,"RPC_503",32);verify(store,never()).complete(any());
    }
    @Test void staleParticipantEpochBlocksInsteadOfCompleting() throws Exception {
        var store=mock(ProductReservationRecoveryStore.class);var ledger=mock(DurableWriteLedger.class);
        var auth=mock(ReservationRpcClient.class);var seller=mock(ReservationRpcClient.class);
        var intent=mock(WriteIntent.class);when(intent.requestId()).thenReturn(UUID.randomUUID());
        var claim=new ProductReservationRecoveryStore.Claim(intent,7,UUID.randomUUID(),0);
        when(store.claim(Duration.ofSeconds(30))).thenReturn(Optional.of(claim));
        doThrow(new ReservationRpcClient.Failure(409)).when(seller).seal(intent,7);
        new ProductReservationRecoveryWorker(store,ledger,auth,seller,Duration.ofSeconds(30),32).tick();
        verify(store).retry(claim,"CONFLICT",32);verifyNoInteractions(auth);verify(store,never()).complete(any());
    }
}
