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
        String current=source.apply(prefix+"SECRET"),previous=source.apply(prefix+"PREVIOUS_SECRET"),until=source.apply(prefix+"PREVIOUS_UNTIL");
        var credentials=new InternalPairVerifier.Credentials(current,previous,until==null?null:Instant.parse(until));
        for(String pair:PAIRS)if(!pair.equals(prefix))for(String field:List.of("SECRET","PREVIOUS_SECRET")){
            String other=source.apply(pair+field);
            if(other!=null && (other.equals(current) || other.equals(previous)))throw new IllegalArgumentException("Internal pair credentials must be distinct");
        }
        return credentials;
    }
}
