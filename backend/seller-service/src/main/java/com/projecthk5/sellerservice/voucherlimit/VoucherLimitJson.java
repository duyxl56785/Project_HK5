package com.projecthk5.sellerservice.voucherlimit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projecthk5.security.ApiException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/** Strict DTO boundary: reject unknown/duplicate fields and client-supplied Store identifiers. */
public final class VoucherLimitJson {
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final Set<String> REQUEST=Set.of("requestedLimit","reason","requestedFrom","requestedUntil");

    public VoucherLimitService.RequestSpec request(String body) {
        ObjectNode n=read(body,REQUEST,Set.of("budgetNote"));
        return new VoucherLimitService.RequestSpec(integer(n,"requestedLimit"),text(n,"reason"),
            n.has("budgetNote")?text(n,"budgetNote"):null,instant(n,"requestedFrom"),instant(n,"requestedUntil"));
    }
    public long cancel(String body){return version(read(body,Set.of("expectedVersion"),Set.of()));}
    public VoucherLimitService.Decision approve(String body){
        ObjectNode n=read(body,Set.of("expectedVersion","approvedLimit","approvedFrom","approvedUntil"),
            Set.of("reason"));
        return new VoucherLimitService.Decision(version(n),integer(n,"approvedLimit"),
            instant(n,"approvedFrom"),instant(n,"approvedUntil"),
            n.has("reason")?text(n,"reason"):null);
    }
    public VoucherLimitService.Decision reject(String body){
        ObjectNode n=read(body,Set.of("expectedVersion","reason"),Set.of());
        return new VoucherLimitService.Decision(version(n),null,null,null,text(n,"reason"));
    }
    private ObjectNode read(String body,Set<String> required,Set<String> optional){
        if(body==null) throw invalid();
        try(var parser=mapper.createParser(body)){
            JsonNode n=mapper.readTree(parser);
            if(!(n instanceof ObjectNode node)||parser.nextToken()!=null) throw invalid();
            for(String f:required) if(!node.has(f)) throw invalid();
            var keys=node.fieldNames();
            while(keys.hasNext()){
                String f=keys.next();
                if((!required.contains(f)&&!optional.contains(f))||node.get(f).isNull()) throw invalid();
            }
            return node;
        }catch(java.io.IOException|IllegalArgumentException error){throw invalid();}
    }
    private static String text(ObjectNode n,String key){
        JsonNode v=n.get(key);
        if(v==null||!v.isTextual()) throw invalid();
        return v.textValue();
    }
    private static int integer(ObjectNode n,String key){
        JsonNode v=n.get(key);
        if(v==null||!v.isIntegralNumber()) throw invalid();
        try{return new BigInteger(v.asText()).intValueExact();}
        catch(NumberFormatException|ArithmeticException ex){throw invalid();}
    }
    private static Instant instant(ObjectNode n,String key){
        try{return OffsetDateTime.parse(text(n,key)).toInstant();}
        catch(DateTimeParseException ex){throw invalid();}
    }
    private static long version(ObjectNode n){
        String v=text(n,"expectedVersion");
        if(!v.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try{return Long.parseLong(v);}catch(NumberFormatException ex){throw invalid();}
    }
    private static ApiException invalid(){return ApiException.badRequest("VALIDATION_FAILED","Invalid Voucher limit JSON");}
}
