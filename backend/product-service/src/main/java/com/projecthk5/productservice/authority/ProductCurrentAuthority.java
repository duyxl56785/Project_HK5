package com.projecthk5.productservice.authority;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.productservice.product.*;
import com.projecthk5.productservice.security.CurrentAccountProductPolicy;
import java.time.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Preflight only, unregistered: all network authority checks precede business transactions. */
public final class ProductCurrentAuthority {
    private final CurrentAccountClient accounts;private final StoreFactsClient stores;private final Clock clock;
    public ProductCurrentAuthority(CurrentAccountClient accounts,StoreFactsClient stores,Clock clock){this.accounts=Objects.requireNonNull(accounts);this.stores=Objects.requireNonNull(stores);this.clock=Objects.requireNonNull(clock);}
    public ProductAccessPolicy prepareSellerManagement(String token,UUID store){return prepare(token,store,false);}
    public ProductAccessPolicy prepareModeration(String token,UUID store){return prepare(token,store,true);}
    private ProductAccessPolicy prepare(String token,UUID store,boolean moderation){
        if(store==null)throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT,"Store ID required");
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive())throw unavailable();
        Instant started=clock.instant();var current=accounts.resolve(token);current.requireRole(moderation?"ADMIN":"SELLER");
        var fact=stores.read(List.of(store)).get(store).orElseThrow(ProductCurrentAuthority::unavailable);
        var access=new ProductAccessPolicy.StoreAccess(store,fact.sellerId(),fact.ownerReference(),"APPROVED".equals(fact.sellerStatus()),"OPEN".equals(fact.storeStatus()),(Boolean)null);
        ProductAccessPolicy snapshot=new ProductAccessPolicy(){
            private StoreAccess require(UUID id){
                var now=clock.instant();if(!store.equals(id) || now.isBefore(started) || !now.isBefore(started.plusSeconds(5)))throw unavailable();return access;
            }
            public StoreAccess requireSellerManagement(String actor,UUID id){return require(id);}
            public StoreAccess requireModeration(String actor,UUID id){return require(id);}
            public StoreAccess readStore(UUID id){return require(id);}
        };
        var policy=new CurrentAccountProductPolicy(current,snapshot);
        if(moderation)policy.requireModeration(current.subject(),store);else policy.requireSellerManagement(current.subject(),store);
        return policy;
    }
    private static ProductRuleException unavailable(){return new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,"Fresh pre-transaction authority is required");}
}
