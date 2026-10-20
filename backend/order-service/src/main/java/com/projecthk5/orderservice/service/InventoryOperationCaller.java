package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.ports.InventoryOperationTransport;
import com.projecthk5.orderservice.ports.InventoryOperationWire;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * One bounded dispatch attempt at a time: caller must persist T1 via Journal.prepare
 * before calling this method. A future approved scheduler may retry UNKNOWN with the
 * same ID and reviewed backoff/deadline; this class never automatically compensates.
 * No positive transport bean is configured in the baseline.
 */
@Service
public class InventoryOperationCaller {
    public enum Outcome { CONFIRMED, UNKNOWN, REJECTED, BLOCKED }
    private final InventoryOperationJournal journal;
    private final InventoryOperationTransport transport;

    public InventoryOperationCaller(InventoryOperationJournal journal, InventoryOperationTransport transport) {
        this.journal = journal;
        this.transport = transport;
    }

    public Outcome dispatchOnce(String operationId) {
        InventoryOperationJournal.Snapshot snapshot = journal.load(operationId);
        if (snapshot.state() == InventoryOperationJournal.State.CONFIRMED) return Outcome.CONFIRMED;
        if (snapshot.state() == InventoryOperationJournal.State.REJECTED)
            return Outcome.REJECTED;
        if (snapshot.state() == InventoryOperationJournal.State.IN_FLIGHT)
            throw new IllegalStateException("Inventory step already in flight; reconcile before retry");
        if (!journal.claimForSend(operationId))
            throw new IllegalStateException("Concurrent Inventory sender claimed step");

        // Only the persisted, fingerprint-checked canonical tuple is sent on every retry.
        InventoryOperationWire.Request request = InventoryOperationWire.from(snapshot.intent());
        final InventoryOperationTransport.Reply reply;
        try {
            reply = transport.send(request);
        } catch (InventoryOperationTransport.NotSent disabled) {
            journal.notSent(operationId, "CALLER_DISABLED");
            return Outcome.BLOCKED;
        } catch (InventoryOperationTransport.DefinitiveFailure rejected) {
            journal.reject(operationId, rejected.code());
            return Outcome.REJECTED;
        } catch (RuntimeException uncertain) {
            // Includes timeouts, 5xx, lost response or unclassified adapter errors.
            journal.unknown(operationId, "TRANSPORT_OUTCOME_UNKNOWN");
            return Outcome.UNKNOWN;
        }

        if (!matchingReply(request, reply)) {
            journal.unknown(operationId, "RESPONSE_MISMATCH");
            return Outcome.UNKNOWN;
        }

        // Separate T2 commit. If this DB update itself fails, the row remains
        // IN_FLIGHT and a stale scanner must flag UNKNOWN; do not mint another ID.
        journal.confirm(operationId);
        return Outcome.CONFIRMED;
    }

    private static boolean matchingReply(InventoryOperationWire.Request request,
                                         InventoryOperationTransport.Reply reply) {
        if (reply == null || !request.operationId().equals(reply.operationId())
                || !request.type().equals(reply.type()) || reply.skus() == null)
            return false;
        Set<String> expected = new HashSet<>();
        request.items().forEach(item -> expected.add(item.sku()));
        Set<String> actual = new HashSet<>(reply.skus());
        return actual.size() == reply.skus().size() && expected.equals(actual);
    }
}
