package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

public final class ProductReservationRecoveryWorker {
    private final ProductReservationRecoveryStore store;
    private final DurableWriteLedger ledger;
    private final ReservationRpcClient auth,seller;
    private final Duration lease;
    private final int maxAttempts;
    public ProductReservationRecoveryWorker(ProductReservationRecoveryStore store,DurableWriteLedger ledger,
            ReservationRpcClient auth,ReservationRpcClient seller,Duration lease,int maxAttempts) {
        this.store=Objects.requireNonNull(store);this.ledger=Objects.requireNonNull(ledger);
        this.auth=Objects.requireNonNull(auth);this.seller=Objects.requireNonNull(seller);this.lease=Objects.requireNonNull(lease);
        if(maxAttempts<1 || maxAttempts>32)throw new IllegalArgumentException("Invalid attempts");this.maxAttempts=maxAttempts;
    }
    public boolean tick() throws SQLException {
        var next=store.claim(lease);if(next.isEmpty())return false;var claim=next.get();
        if(claim.attempts()>=maxAttempts){store.retry(claim,"ATTEMPTS_EXHAUSTED",maxAttempts);return true;}
        try {
            ledger.abort(claim.intent().requestId(),claim.operationEpoch());
            seller.seal(claim.intent(),claim.operationEpoch());
            auth.seal(claim.intent(),claim.operationEpoch());
            store.complete(claim);
        } catch(ReservationRpcClient.Failure failure) {
            store.retry(claim,failure.status()==409?"CONFLICT":"RPC_"+failure.status(),maxAttempts);
        } catch(IllegalArgumentException|IllegalStateException conflict) {
            store.retry(claim,"CONFLICT",maxAttempts);
        } catch(SQLException unavailable) {store.retry(claim,"DATABASE_UNAVAILABLE",maxAttempts);}
        return true;
    }
}
