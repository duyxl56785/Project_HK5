package com.projecthk5.security;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class IsolatedPairCredentialsTest {
    @Test void rejectsAllCrossPairDirectionsAndRetainsDistinctRotation(){
        var pairs=List.of("HK5_SELLER_AUTH_ROLE_SYNC_","HK5_SELLER_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_SELLER_STORE_FACTS_");
        for(String own:pairs)for(String other:pairs)if(!own.equals(other))for(String ownSlot:List.of("SECRET","PREVIOUS_SECRET"))for(String otherSlot:List.of("SECRET","PREVIOUS_SECRET")){
            var values=new HashMap<String,String>();values.put(own+"SECRET","current-test-only-credential-0123456789");values.put(own+"PREVIOUS_SECRET","previous-test-only-credential-0123456789");values.put(own+"PREVIOUS_UNTIL","2099-01-01T00:00:00Z");
            assertNotNull(IsolatedPairCredentials.read(values::get,own));values.put(other+otherSlot,values.get(own+ownSlot));
            assertThrows(IllegalArgumentException.class,()->IsolatedPairCredentials.read(values::get,own));
        }
    }
}
