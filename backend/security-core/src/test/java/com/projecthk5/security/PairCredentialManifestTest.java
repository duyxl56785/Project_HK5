package com.projecthk5.security;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PairCredentialManifestTest {
    Map<String,String> secrets() {
        var values=new HashMap<String,String>();
        for(String pair:PairCredentialManifest.PAIRS)values.put(pair+"SECRET","test-only-secret-0123456789-"+pair);
        return values;
    }
    @Test void provisioningRejectsEveryCurrentPreviousAliasIncludingExpiredKeys() {
        for(String a:PairCredentialManifest.PAIRS)for(String b:PairCredentialManifest.PAIRS)if(!a.equals(b))
        for(String x:List.of("SECRET","PREVIOUS_SECRET"))for(String y:List.of("SECRET","PREVIOUS_SECRET")) {
            var values=secrets();values.put(a+x,"shared-test-only-secret-0123456789");values.put(b+y,values.get(a+x));
            assertThrows(IllegalArgumentException.class,()->PairCredentialManifest.provision(values::get));
        }
    }
    @Test void separateEnvironmentsRejectForeignProcessReplayAndRetainRotation() {
        var all=secrets();String own=PairCredentialManifest.PAIRS.getFirst(),foreign=PairCredentialManifest.PAIRS.getLast();
        all.put(own+"PREVIOUS_SECRET","old-test-only-secret-0123456789-rotation");
        var local=new HashMap<String,String>();local.put(own+"SECRET",all.get(own+"SECRET"));
        local.put(own+"PREVIOUS_SECRET",all.get(own+"PREVIOUS_SECRET"));local.put(own+"PREVIOUS_UNTIL","2030-01-01T00:00:00Z");
        local.put(PairCredentialManifest.PROPERTY,PairCredentialManifest.provision(all::get));
        var v=new InternalPairVerifier("seller","auth",Set.of(new InternalPairVerifier.Scope("POST","/internal/auth/seller-role-sync","SELLER_ROLE_SYNC")),
                ()->IsolatedPairCredentials.read(local::get,own),Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"),ZoneOffset.UTC));
        assertNotNull(v.verify(all.get(own+"SECRET"),"POST","/internal/auth/seller-role-sync"));
        assertNotNull(v.verify(all.get(own+"PREVIOUS_SECRET"),"POST","/internal/auth/seller-role-sync"));
        assertEquals(401,assertThrows(InternalPairVerifier.Failure.class,()->v.verify(all.get(foreign+"SECRET"),"POST","/internal/auth/seller-role-sync")).status());
        assertEquals(403,assertThrows(InternalPairVerifier.Failure.class,()->v.verify(all.get(own+"SECRET"),"POST","/internal/seller/store-facts/batch")).status());
        local.put(own+"SECRET",all.get(foreign+"SECRET"));
        assertEquals(503,assertThrows(InternalPairVerifier.Failure.class,()->v.verify(all.get(foreign+"SECRET"),"POST","/internal/auth/seller-role-sync")).status());
    }
    @Test void incompleteMalformedDuplicateAndLocalSlotMismatchFailClosed() {
        var all=secrets();String manifest=PairCredentialManifest.provision(all::get),own=PairCredentialManifest.PAIRS.getFirst();
        for(String bad:List.of("", "v2;",manifest+";unexpected=x",manifest+";"+manifest.substring(3),manifest.replaceFirst("[0-9a-f]{64}","A".repeat(64)))) {
            all.put(PairCredentialManifest.PROPERTY,bad);
            assertThrows(IllegalArgumentException.class,()->IsolatedPairCredentials.read(all::get,own));
        }
        all.put(PairCredentialManifest.PROPERTY,manifest);
        all.put(own+"PREVIOUS_SECRET","unexpected-old-secret-0123456789");
        all.put(own+"PREVIOUS_UNTIL","2030-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->IsolatedPairCredentials.read(all::get,own));
        all.remove(own+"SECRET");assertThrows(IllegalArgumentException.class,()->PairCredentialManifest.provision(all::get));
    }
}
