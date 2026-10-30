package com.projecthk5.productservice.authority;

import com.projecthk5.security.IsolatedPairCredentials;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit operation factory; does not register a business write policy or capture a user's JWT in a singleton. */
@Component
@ConditionalOnProperty(name="hk5.product.reservations.client.enabled",havingValue="true")
public final class ProductReservationPorts {
    private final DurableWriteLedger ledger;
    private final Environment env;
    private final ProductReservationRecoveryStore recovery;
    public ProductReservationPorts(DurableWriteLedger ledger,Environment env,ProductReservationRecoveryStore recovery){this.ledger=Objects.requireNonNull(ledger);this.env=Objects.requireNonNull(env);this.recovery=Objects.requireNonNull(recovery);}

    public DurableWriteCoordinator coordinatorFor(WriteIntent intent,String jwt) {
        Objects.requireNonNull(intent);
        var auth=client("HK5_PRODUCT_AUTH_RESERVATION_URL","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_",Participant.AUTH,()->jwt);
        var seller=client("HK5_PRODUCT_SELLER_RESERVATION_URL","HK5_PRODUCT_SELLER_STORE_FACTS_",Participant.SELLER,null);
        try {recovery.begin(intent);}
        catch(SQLException failure){throw new IllegalStateException("Durable recovery enlistment unavailable",failure);}
        return new DurableWriteCoordinator(ledger,port(intent,Participant.AUTH,auth),port(intent,Participant.SELLER,seller));
    }
    private ReservationRpcClient client(String url,String prefix,Participant participant,Supplier<String> bearer) {
        return new ReservationRpcClient(URI.create(env.getRequiredProperty(url)),()->{
            IsolatedPairCredentials.read(env::getProperty,prefix);
            return env.getRequiredProperty(prefix+"SECRET");
        },participant,bearer);
    }
    private DurableWriteCoordinator.AuthorityPort port(WriteIntent bound,Participant participant,ReservationRpcClient client) {
        return new DurableWriteCoordinator.AuthorityPort() {
            public Grant admit(WriteIntent intent){requireIntent(intent);return client.admit(bound);}
            public Optional<Grant> lookup(WriteIntent intent){requireIntent(intent);return client.lookup(bound).grant();}
            public void release(Grant grant) throws Exception {
                if(grant==null || !grant.requestId().equals(bound.requestId()) || !grant.bindingHash().equals(bound.fingerprint())
                    || grant.participant()!=participant || grant.epoch()<=0)throw new IllegalArgumentException("Reservation binding mismatch");
                var receipt=ledger.terminal(bound.requestId());
                if(!receipt.bindingHash().equals(bound.fingerprint()) || !Objects.equals(receipt.grants().get(participant),grant.epoch()))
                    throw new IllegalArgumentException("Reservation binding mismatch");
                client.release(bound,grant.epoch());
            }
            private void requireIntent(WriteIntent supplied){
                if(!bound.equals(supplied))throw new IllegalArgumentException("Reservation binding mismatch");
            }
        };
    }
}
