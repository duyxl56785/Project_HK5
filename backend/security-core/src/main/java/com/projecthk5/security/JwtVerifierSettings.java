package com.projecthk5.security;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
/** Existing HS256 verifier configuration only, no issuing or default keys. */
public final class JwtVerifierSettings {
    private JwtVerifierSettings(){}
    public static JwtAccessVerifier read(Function<String,String> values,Clock clock){
        String prefix="HK5_AUTH_JWT_",kid=required(values,prefix+"CURRENT_KID"),previous=optional(values,prefix+"PREVIOUS_KID"),old=optional(values,prefix+"PREVIOUS_KEY_B64");
        if((previous==null)!=(old==null) || kid.equals(previous))throw invalid();
        var keys=new LinkedHashMap<String,SecretKey>();keys.put(kid,key(required(values,prefix+"CURRENT_KEY_B64")));
        if(previous!=null){var previousKey=key(old);if(Arrays.equals(keys.get(kid).getEncoded(),previousKey.getEncoded()))throw invalid();keys.put(previous,previousKey);}
        return new JwtAccessVerifier(required(values,prefix+"ISSUER"),required(values,prefix+"AUDIENCE"),kid,keys,clock);
    }
    private static String optional(Function<String,String> source,String name){String value=source.apply(name);if(value!=null && (value.isBlank() || !value.equals(value.strip())))throw invalid();return value;}
    private static String required(Function<String,String> source,String name){String value=optional(source,name);if(value==null)throw invalid();return value;}
    private static SecretKey key(String value){
        byte[] bytes;try{bytes=Base64.getDecoder().decode(value);}catch(IllegalArgumentException invalid){throw invalid();}
        try{if(bytes.length<32 || bytes.length>4096 || !Base64.getEncoder().encodeToString(bytes).equals(value))throw invalid();return new SecretKeySpec(bytes,"HmacSHA256");}
        finally{Arrays.fill(bytes,(byte)0);}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid JWT verifier settings");}
}
