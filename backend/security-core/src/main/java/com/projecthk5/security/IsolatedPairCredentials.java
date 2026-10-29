package com.projecthk5.security;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** One invariant for incoming pair credentials; labels alone never isolate reused secrets. */
public final class IsolatedPairCredentials {
    private IsolatedPairCredentials(){}
    private static final Set<String> PAIRS=Set.of("HK5_SELLER_AUTH_ROLE_SYNC_","HK5_SELLER_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_SELLER_STORE_FACTS_");
    public static InternalPairVerifier.Credentials read(Function<String,String> source,String prefix){
        if(!PAIRS.contains(prefix))throw new IllegalArgumentException("Unknown internal pair");
        var snapshot=new HashMap<String,String>();
        for(String pair:PAIRS)for(String slot:List.of("SECRET","PREVIOUS_SECRET"))snapshot.put(pair+slot,source.apply(pair+slot));
        snapshot.put(prefix+"PREVIOUS_UNTIL",source.apply(prefix+"PREVIOUS_UNTIL"));
        snapshot.put(PairCredentialManifest.PROPERTY,source.apply(PairCredentialManifest.PROPERTY));
        PairCredentialManifest.verify(snapshot::get);
        PairCredentialManifest.requireLocalSlots(snapshot::get,prefix);
        String current=snapshot.get(prefix+"SECRET"),previous=snapshot.get(prefix+"PREVIOUS_SECRET"),until=snapshot.get(prefix+"PREVIOUS_UNTIL");
        var credentials=new InternalPairVerifier.Credentials(current,previous,until==null?null:Instant.parse(until));
        for(String pair:PAIRS)if(!pair.equals(prefix))for(String field:List.of("SECRET","PREVIOUS_SECRET")){
            String other=snapshot.get(pair+field);
            if(other!=null && (other.equals(current) || other.equals(previous)))throw new IllegalArgumentException("Internal pair credentials must be distinct");
        }
        return credentials;
    }
}
