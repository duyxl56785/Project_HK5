package com.projecthk5.security;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
class JwtVerifierSettingsTest {
    @Test void noDefaultSecretsAndRotationAliasesRejected(){
        var env=new HashMap<String,String>();assertThrows(IllegalArgumentException.class,()->JwtVerifierSettings.read(env::get,Clock.systemUTC()));
        env.put("HK5_AUTH_JWT_ISSUER","https://issuer.example.invalid");env.put("HK5_AUTH_JWT_AUDIENCE","aud");env.put("HK5_AUTH_JWT_CURRENT_KID","current");env.put("HK5_AUTH_JWT_CURRENT_KEY_B64",Base64.getEncoder().encodeToString(new byte[32]));
        assertNotNull(JwtVerifierSettings.read(env::get,Clock.systemUTC()));
        env.put("HK5_AUTH_JWT_PREVIOUS_KID","previous");env.put("HK5_AUTH_JWT_PREVIOUS_KEY_B64",env.get("HK5_AUTH_JWT_CURRENT_KEY_B64"));
        assertThrows(IllegalArgumentException.class,()->JwtVerifierSettings.read(env::get,Clock.systemUTC()));
        byte[] previous=new byte[32];Arrays.fill(previous,(byte)1);env.put("HK5_AUTH_JWT_PREVIOUS_KEY_B64",Base64.getEncoder().encodeToString(previous));
        assertNotNull(JwtVerifierSettings.read(env::get,Clock.systemUTC()));
    }
}
