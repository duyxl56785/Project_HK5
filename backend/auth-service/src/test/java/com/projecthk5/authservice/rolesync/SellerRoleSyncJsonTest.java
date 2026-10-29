package com.projecthk5.authservice.rolesync;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.projecthk5.authservice.rolesync.SellerRoleSyncService.*;

class SellerRoleSyncJsonTest {
    final SellerRoleSyncJson codec=new SellerRoleSyncJson();
    final String event=UUID.randomUUID().toString(),seller=UUID.randomUUID().toString(),account=UUID.randomUUID().toString();
    String input(String version){return "{\"eventId\":\""+event+"\",\"sellerId\":\""+seller+"\",\"accountId\":\""+account+"\",\"sellerStatus\":\"APPROVED\",\"sourceVersion\":"+version+"}";}
    @Test void exactCommandAndAckHaveOnlyFourPublicFields()throws Exception {
        var command=codec.read(input("\"9223372036854775807\""));
        assertEquals(Long.MAX_VALUE,command.sourceVersion());
        var ack=new ObjectMapper().readTree(codec.ack(command,Result.APPLIED));
        assertEquals(4,ack.size());assertEquals("9223372036854775807",ack.get("sourceVersion").textValue());
        assertFalse(ack.has("accountId"));assertEquals("APPLIED",ack.get("result").textValue());
    }
    @ParameterizedTest @ValueSource(strings={"0","null","true","\"00\"","\"-1\"","\"+1\"","\" 1\"","\"1.0\"","\"1e1\"","\"9223372036854775808\""})
    void rejectsCoercionAndNoncanonicalVersion(String version){assertThrows(IllegalArgumentException.class,()->codec.read(input(version)));}
    @Test void unknownDuplicateNullUuidAndTrailingDataAreRejected() {
        String valid=input("\"0\"");
        for(String bad:List.of(valid.replace("\"eventId\":", "\"unknown\":"),
                valid.replace("\"sellerStatus\":", "\"accountId\":\"extra\",\"sellerStatus\":"),
                valid.replace(event,"1-1-1-1-1"),valid.replace("\"APPROVED\"","null"),valid+" {}", "[]"))
            assertThrows(IllegalArgumentException.class,()->codec.read(bad));
    }
}
