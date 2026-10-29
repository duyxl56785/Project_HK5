package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DurableWriteCoordinator {
    public interface AuthorityPort {
        Grant admit(WriteIntent intent) throws Exception;
        Optional<Grant> lookup(WriteIntent intent) throws Exception;
        void release(Grant grant) throws Exception;
    }
    private final DurableWriteLedger ledger;
    private final AuthorityPort auth,seller;
    public DurableWriteCoordinator(DurableWriteLedger ledger,AuthorityPort auth,AuthorityPort seller) {
        this.ledger=Objects.requireNonNull(ledger);this.auth=Objects.requireNonNull(auth);this.seller=Objects.requireNonNull(seller);
    }
    public DurableWriteLedger.Operation admit(WriteIntent intent) throws Exception {
        outsideTransaction();var operation=ledger.begin(intent);
        if(operation.state()!=DurableWriteLedger.State.PENDING && operation.state()!=DurableWriteLedger.State.ADMITTED)
            throw new IllegalStateException("WRITE_ALREADY_TERMINAL");
        outsideTransaction();var first=auth.admit(intent);requireParticipant(first,Participant.AUTH);ledger.recordGrant(intent.requestId(),operation.epoch(),first);
        outsideTransaction();var second=seller.admit(intent);requireParticipant(second,Participant.SELLER);ledger.recordGrant(intent.requestId(),operation.epoch(),second);
        return ledger.begin(intent);
    }
    public DurableWriteLedger.Receipt commit(UUID request,long epoch,DurableWriteLedger.LocalWrite write) throws Exception {
        outsideTransaction();return ledger.commit(request,epoch,write);
    }
    public DurableWriteLedger.Receipt abort(UUID request,long epoch) throws Exception {
        outsideTransaction();return ledger.abort(request,epoch);
    }
    public void reconcile(WriteIntent intent) throws Exception {
        outsideTransaction();var operation=ledger.begin(intent);
        if(operation.state()!=DurableWriteLedger.State.COMMITTED && operation.state()!=DurableWriteLedger.State.ABORTED)
            throw new IllegalStateException("TERMINAL_RECOVERY_REQUIRED");
        for(var entry:List.of(Map.entry(Participant.SELLER,seller),Map.entry(Participant.AUTH,auth))) {
            var receipt=ledger.terminal(intent.requestId());Long epoch=receipt.grants().get(entry.getKey());
            if(epoch==null) {
                outsideTransaction();var late=entry.getValue().lookup(intent);
                if(late.isEmpty())continue;
                requireParticipant(late.get(),entry.getKey());ledger.recordGrant(intent.requestId(),operation.epoch(),late.get());epoch=late.get().epoch();
            }
            outsideTransaction();entry.getValue().release(new Grant(intent.requestId(),intent.fingerprint(),entry.getKey(),epoch));
        }
    }
    private static void requireParticipant(Grant grant,Participant expected) {
        if(grant.participant()!=expected)throw new IllegalStateException("WRONG_AUTHORITY_PARTICIPANT");
    }
    private static void outsideTransaction() {
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("AUTHORITY_HTTP_INSIDE_TRANSACTION");
    }
}
