package com.projecthk5.sellerservice.security;
import java.util.Objects;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.SellerAccessPolicy;

/** Construct per request after RPC, before entering business transactions; intentionally not a bean. */
public final class CurrentAccountSellerPolicy implements SellerAccessPolicy {
    private final CurrentAccountClient.CurrentAccount current;
    public CurrentAccountSellerPolicy(CurrentAccountClient.CurrentAccount current){this.current=Objects.requireNonNull(current);}
    @Override public void requireAuthenticated(SellerIdentity actor) {
        if(actor==null || !current.subject().equals(actor.reference()) || current.roles().isEmpty())throw denied();
    }
    @Override public void requireSellerModerationPermission(SellerIdentity actor) {
        requireAuthenticated(actor);if(!current.roles().contains("ADMIN"))throw denied();
    }
    public SellerIdentity requireCurrentAccount(){return new SellerIdentity(current.subject());}
    public SellerIdentity requireCurrentSeller(){
        if(!current.roles().contains("SELLER"))throw denied();return requireCurrentAccount();
    }
    private SellerRuleException denied(){return new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED,"Current verified account permission required");}
}
