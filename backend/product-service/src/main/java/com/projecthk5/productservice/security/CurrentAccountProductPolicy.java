package com.projecthk5.productservice.security;
import java.util.*;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.productservice.product.*;

/** Request-scoped verified authority; real Store/Seller policy remains mandatory and unregistered. */
public final class CurrentAccountProductPolicy implements ProductAccessPolicy {
    private final CurrentAccountClient.CurrentAccount current;
    private final ProductAccessPolicy stores;
    public CurrentAccountProductPolicy(CurrentAccountClient.CurrentAccount current,ProductAccessPolicy stores){
        this.current=Objects.requireNonNull(current);this.stores=Objects.requireNonNull(stores);
    }
    private void require(String actor,String role){
        if(!current.subject().equals(actor) || !current.roles().contains(role))throw denied();
    }
    @Override public StoreAccess requireSellerManagement(String actor,UUID id){
        require(actor,"SELLER");var facts=stores.requireSellerManagement(actor,id);
        if(facts==null || !id.equals(facts.storeId()) || !actor.equals(facts.ownerReference()) || !facts.sellerApproved() || !facts.storeOpen())throw denied();
        return facts;
    }
    @Override public StoreAccess requireModeration(String actor,UUID id){
        require(actor,"ADMIN");var facts=stores.requireModeration(actor,id);
        if(facts==null || !id.equals(facts.storeId()) || actor.equals(facts.ownerReference()))throw denied();
        return facts;
    }
    @Override public StoreAccess readStore(UUID id){
        var facts=stores.readStore(id);if(facts==null || !id.equals(facts.storeId()))throw denied();return facts;
    }
    private ProductRuleException denied(){return new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED,"Current verified account and Store authority required");}
}
