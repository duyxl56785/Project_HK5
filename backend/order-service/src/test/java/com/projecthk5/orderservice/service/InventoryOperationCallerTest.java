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
        when(journal.claimForSend(intent.operationId())).thenReturn(1);
        when(transport.send(any())).thenReturn(
            new InventoryOperationTransport.Reply(intent.operationId(), "RESERVE", false, List.of("sku-a", "sku-b")));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED, caller.dispatchOnce(intent.operationId()));
        ArgumentCaptor<InventoryOperationWire.Request> cap=ArgumentCaptor.forClass(InventoryOperationWire.Request.class);
        verify(transport).send(cap.capture());
        assertEquals("reserve-caller-1", cap.getValue().operationId());
        assertEquals("RESERVE", cap.getValue().type());
        assertEquals(List.of(new InventoryOperationWire.Item("sku-a",2),
            new InventoryOperationWire.Item("sku-b",1)), cap.getValue().items());
        verify(journal).confirm(intent.operationId(),1);
        verify(journal, never()).unknown(anyString(),anyInt(),anyString());
    }

    @Test void retryAfterUnknownUsesSamePersistedIdTypeAndCanonicalLines() {
        when(journal.load(intent.operationId())).thenReturn(
            state(InventoryOperationJournal.State.PENDING),state(InventoryOperationJournal.State.UNKNOWN));
        when(journal.claimForSend(intent.operationId())).thenReturn(1,2);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.UnknownOutcome("timeout"))
            .thenReturn(new InventoryOperationTransport.Reply(intent.operationId(),"RESERVE",true,List.of("sku-b","sku-a")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,caller.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,caller.dispatchOnce(intent.operationId()));
        ArgumentCaptor<InventoryOperationWire.Request> cap=ArgumentCaptor.forClass(InventoryOperationWire.Request.class);
        verify(transport,times(2)).send(cap.capture());
        assertEquals(cap.getAllValues().get(0),cap.getAllValues().get(1));
        verify(journal).unknown(intent.operationId(),1,"TRANSPORT_OUTCOME_UNKNOWN");
        verify(journal).confirm(intent.operationId(),2);
    }

    @Test void disabledTransportIsProvablyNotSentAndRemainsPending() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(1);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.NotSent("disabled"));
        assertEquals(InventoryOperationCaller.Outcome.BLOCKED, caller.dispatchOnce(intent.operationId()));
        verify(journal).notSent(intent.operationId(),1,"CALLER_DISABLED");
        verify(journal,never()).confirm(anyString(),anyInt());
    }

    @Test void mismatchedOrDuplicateProviderResponseIsUnknownNotSuccess() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(1);
        when(transport.send(any())).thenReturn(
            new InventoryOperationTransport.Reply(intent.operationId(),"RESERVE",false,List.of("sku-a","sku-a")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,caller.dispatchOnce(intent.operationId()));
        verify(journal).unknown(intent.operationId(),1,"RESPONSE_MISMATCH");
        verify(journal,never()).confirm(anyString(),anyInt());
    }

    @Test void knownDefinitiveAtomicRejectionMustNotBeRetried() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.PENDING));
        when(journal.claimForSend(intent.operationId())).thenReturn(1);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.DefinitiveFailure("INSUFFICIENT_STOCK"));
        assertEquals(InventoryOperationCaller.Outcome.REJECTED,caller.dispatchOnce(intent.operationId()));
        verify(journal).reject(intent.operationId(),1,"INSUFFICIENT_STOCK");
        verify(journal,never()).confirm(anyString(),anyInt());
    }

    @Test void concurrentInFlightIsNotSentAgainAndConfirmedReplayDoesNotResend() {
        when(journal.load(intent.operationId())).thenReturn(
            state(InventoryOperationJournal.State.IN_FLIGHT), state(InventoryOperationJournal.State.CONFIRMED));
        assertThrows(IllegalStateException.class,()->caller.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,caller.dispatchOnce(intent.operationId()));
        verifyNoInteractions(transport);
    }


    @Test void staleReplyCannotReportConfirmedAfterRetryWasReclaimed() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.UNKNOWN));
        when(journal.claimForSend(intent.operationId())).thenReturn(1);
        when(transport.send(any())).thenReturn(new InventoryOperationTransport.Reply(
                intent.operationId(),"RESERVE",false,List.of("sku-a","sku-b")));
        doThrow(new IllegalStateException("Stale or conflicting Inventory journal transition"))
                .when(journal).confirm(intent.operationId(),1);
        assertThrows(IllegalStateException.class, () -> caller.dispatchOnce(intent.operationId()));
        verify(journal).confirm(intent.operationId(),1);
        verify(journal,never()).reject(anyString(),anyInt(),anyString());
    }

    @Test void staleTimeoutCannotOverwriteNewerAttemptState() {
        when(journal.load(intent.operationId())).thenReturn(state(InventoryOperationJournal.State.UNKNOWN));
        when(journal.claimForSend(intent.operationId())).thenReturn(2);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.UnknownOutcome("old timeout"));
        doThrow(new IllegalStateException("Stale or conflicting Inventory journal transition"))
                .when(journal).unknown(intent.operationId(),2,"TRANSPORT_OUTCOME_UNKNOWN");
        assertThrows(IllegalStateException.class, () -> caller.dispatchOnce(intent.operationId()));
        verify(journal,never()).confirm(anyString(),anyInt());
    }


    @Test void multiSkuTimeoutAndRetryKeepExactlySameRequestAndNoImplicitRelease() {
        var multiple = InventoryOperationIntent.prepare(UUID.randomUUID(), "reserve-three-skus",
                InventoryOperationIntent.Type.RESERVE, List.of(
                        new Line("SKU-Z", 3L),
                        new Line("SKU-A", 2_147_483_648L),
                        new Line("SKU-M", 17L)));
        when(journal.load(multiple.operationId())).thenReturn(
                new InventoryOperationJournal.Snapshot(multiple,
                        InventoryOperationJournal.State.PENDING, 0, null),
                new InventoryOperationJournal.Snapshot(multiple,
                        InventoryOperationJournal.State.UNKNOWN, 1, "TRANSPORT_OUTCOME_UNKNOWN"));
        when(journal.claimForSend(multiple.operationId())).thenReturn(1, 2);
        when(transport.send(any())).thenThrow(new InventoryOperationTransport.UnknownOutcome("timeout"))
                .thenReturn(new InventoryOperationTransport.Reply(multiple.operationId(),
                        "RESERVE", true, List.of("SKU-M", "SKU-Z", "SKU-A")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,
                caller.dispatchOnce(multiple.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,
                caller.dispatchOnce(multiple.operationId()));
        ArgumentCaptor<InventoryOperationWire.Request> payload =
                ArgumentCaptor.forClass(InventoryOperationWire.Request.class);
        verify(transport, times(2)).send(payload.capture());
        assertEquals(payload.getAllValues().get(0), payload.getAllValues().get(1));
        assertEquals("reserve-three-skus", payload.getValue().operationId());
        assertEquals("RESERVE", payload.getValue().type());
        assertEquals(List.of(
                new InventoryOperationWire.Item("SKU-A", 2_147_483_648L),
                new InventoryOperationWire.Item("SKU-M", 17L),
                new InventoryOperationWire.Item("SKU-Z", 3L)), payload.getValue().items());
        verify(journal).unknown(multiple.operationId(), 1, "TRANSPORT_OUTCOME_UNKNOWN");
        verify(journal).confirm(multiple.operationId(), 2);
        verify(journal, never()).prepare(any());
        verify(journal, never()).notSent(anyString(), anyInt(), anyString());
        verify(journal, never()).reject(anyString(), anyInt(), anyString());
    }

    @Test void multiSkuMismatchedProviderSkuSetRecordsUnknownRatherThanConfirming() {
        var multiple = InventoryOperationIntent.prepare(UUID.randomUUID(), "reserve-mismatched-three",
                InventoryOperationIntent.Type.RESERVE, List.of(
                        new Line("SKU-A", 2), new Line("SKU-B", 7), new Line("SKU-C", 1)));
        when(journal.load(multiple.operationId())).thenReturn(
                new InventoryOperationJournal.Snapshot(multiple,
                        InventoryOperationJournal.State.PENDING, 0, null));
        when(journal.claimForSend(multiple.operationId())).thenReturn(1);
        when(transport.send(any())).thenReturn(new InventoryOperationTransport.Reply(
                multiple.operationId(), "RESERVE", false, List.of("SKU-A", "SKU-B", "SKU-D")));
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,
                caller.dispatchOnce(multiple.operationId()));
        verify(journal).unknown(multiple.operationId(), 1, "RESPONSE_MISMATCH");
        verify(journal, never()).confirm(anyString(), anyInt());
        verify(journal, never()).prepare(any());
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
