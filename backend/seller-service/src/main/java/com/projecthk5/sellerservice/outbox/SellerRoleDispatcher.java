package com.projecthk5.sellerservice.outbox;
import java.util.Objects;

/** Explicit tick only; the opt-in host is disabled by default. No HTTP during DB claims/completions. */
public final class SellerRoleDispatcher {
    public enum Ack { APPLIED,DUPLICATE,STALE_IGNORED }
    @FunctionalInterface public interface Sender {Ack send(DeliveryStore.Claim event);}
    private final DeliveryStore store;private final Sender sender;
    public SellerRoleDispatcher(DeliveryStore store,Sender sender){this.store=Objects.requireNonNull(store);this.sender=Objects.requireNonNull(sender);}
    public boolean runOnce(){
        DeliveryStore.outsideTransaction();var claimed=store.claim();if(claimed.isEmpty())return false;var event=claimed.get();
        Ack ack;
        try{ack=Objects.requireNonNull(sender.send(event));}
        catch(RoleSyncClient.Conflict conflict){store.failed(event,true);return true;}
        catch(RuntimeException unavailable){store.failed(event,false);return true;}
        store.delivered(event,ack.name());return true;
    }
}
