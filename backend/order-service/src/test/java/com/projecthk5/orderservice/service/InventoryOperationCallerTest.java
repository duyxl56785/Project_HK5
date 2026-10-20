package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.domain.InventoryOperationIntent.Line;
import com.projecthk5.orderservice.ports.InventoryOperationTransport;
import com.projecthk5.orderservice.ports.InventoryOperationWire;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryOperationCallerTest {
    private final InventoryOperationJournal journal = mock(InventoryOperationJournal.class);
    private final InventoryOperationTransport transport = mock(InventoryOperationTransport.class);
    private final InventoryOperationCaller caller = new InventoryOperationCaller(journal, transport);
    private final InventoryOperationIntent intent = InventoryOperationIntent.prepare(
        UUID.randomUUID(), "reserve-caller-1", InventoryOperationIntent.Type.RESERVE,
        List.of(new Line("sku-b", 1), new Line("sku-a", 2)));
    private InventoryOperationJournal.Snapshot state(InventoryOperationJournal.State st) {
        return new InventoryOperationJournal.Snapshot(intent, st, 1, null);
    }

    @Test void successConfirmsOnlyExactMatchingResponseAndPersistedRequest() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(true);
        when(transport.send(any())).thenReturn(
            new InventoryOperationTransport.Reply(intent.operationId(), "RESERVE", false, List.of("sku-a", "sku-b")));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED, caller.dispatchOnce(intent.operationId()));
        ArgumentCaptor<InventoryOperationWire.Request> cap=ArgumentCaptor.forClass(InventoryOperationWire.Request.class);
        verify(transport).send(cap.capture());
        assertEquals("reserve-caller-1", cap.getValue().operationId());
        assertEquals("RESERVE", cap.getValue().type());
        assertEquals(List.of(new InventoryOperationWire.Item("sku-a",2),
            new InventoryOperationWire.Item("sku-b",1)), cap.getValue().items());
        verify(journal).confirm(intent.operationId());
        verify(journal, never()).unknown(anyString(),anyString());
    }

    @Test void retryAfterUnknownUsesSamePersistedIdTypeAndCanonicalLines() {
        when(journal.load(intent.operationId())).thenReturn(
            state(InventoryOperationJournal.State.PENDING),state(InventoryOperationJournal.State.UNKNOWN));
        when(journal.claimForSend(intent.operationId())).thenReturn(true,true);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.UnknownOutcome("timeout"))
            .thenReturn(new InventoryOperationTransport.Reply(intent.operationId(),"RESERVE",true,List.of("sku-b","sku-a")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,caller.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,caller.dispatchOnce(intent.operationId()));
        ArgumentCaptor<InventoryOperationWire.Request> cap=ArgumentCaptor.forClass(InventoryOperationWire.Request.class);
        verify(transport,times(2)).send(cap.capture());
        assertEquals(cap.getAllValues().get(0),cap.getAllValues().get(1));
        verify(journal).unknown(intent.operationId(),"TRANSPORT_OUTCOME_UNKNOWN");
        verify(journal).confirm(intent.operationId());
    }

    @Test void disabledTransportIsProvablyNotSentAndRemainsPending() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(true);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.NotSent("disabled"));
        assertEquals(InventoryOperationCaller.Outcome.BLOCKED, caller.dispatchOnce(intent.operationId()));
        verify(journal).notSent(intent.operationId(),"CALLER_DISABLED");
        verify(journal,never()).confirm(anyString());
    }

    @Test void mismatchedOrDuplicateProviderResponseIsUnknownNotSuccess() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(true);
        when(transport.send(any())).thenReturn(
            new InventoryOperationTransport.Reply(intent.operationId(),"RESERVE",false,List.of("sku-a","sku-a")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,caller.dispatchOnce(intent.operationId()));
        verify(journal).unknown(intent.operationId(),"RESPONSE_MISMATCH");
        verify(journal,never()).confirm(anyString());
    }

    @Test void knownDefinitiveAtomicRejectionMustNotBeRetried() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(true);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.DefinitiveFailure("INSUFFICIENT_STOCK"));
        assertEquals(InventoryOperationCaller.Outcome.REJECTED,caller.dispatchOnce(intent.operationId()));
        verify(journal).reject(intent.operationId(),"INSUFFICIENT_STOCK");
        verify(journal,never()).confirm(anyString());
    }

    @Test void concurrentInFlightIsNotSentAgainAndConfirmedReplayDoesNotResend() {
        when(journal.load(intent.operationId())).thenReturn(
            state(InventoryOperationJournal.State.IN_FLIGHT), state(InventoryOperationJournal.State.CONFIRMED));
        assertThrows(IllegalStateException.class,()->caller.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,caller.dispatchOnce(intent.operationId()));
        verifyNoInteractions(transport);
    }

    @Test void wireAdapterHasNoBroadCredentialOrExtraOperationType() {
        var wire=InventoryOperationWire.from(intent);
        assertEquals("POST",InventoryOperationWire.METHOD);
        assertEquals("/api/inventory/operations",InventoryOperationWire.PATH);
        assertEquals("X-Internal-Token",InventoryOperationWire.PAIR_HEADER);
        assertEquals("RESERVE",wire.type());
        assertThrows(UnsupportedOperationException.class,()->wire.items().add(new InventoryOperationWire.Item("x",1)));
    }
}
