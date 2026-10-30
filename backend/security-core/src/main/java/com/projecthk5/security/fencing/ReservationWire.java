package com.projecthk5.security.fencing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;

/** Strict, bounded envelopes; authority evidence is only returned by servers. */
public final class ReservationWire {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ReservationWire(){}
    public record Request(UUID nonce,WriteIntent intent,Long epoch){}
    public record Lookup(Optional<Grant> grant,String state){}
    public static String request(String action,UUID nonce,WriteIntent intent,Long epoch){
        boolean needs=epochAction(action);if(needs!=(epoch!=null))throw invalid();
        var n=base(nonce);n.set("intent",intentNode(intent));if(needs)n.put("epoch",positive(epoch));return n.toString();
    }
    public static Request readRequest(String action,byte[] body){
        boolean needs=epochAction(action);var n=parse(body);fields(n,needs?new String[]{"nonce","intent","epoch"}:new String[]{"nonce","intent"});
        return new Request(uuid(n,"nonce"),intent(n.get("intent")),needs?number(n,"epoch",true):null);
    }
    private static boolean epochAction(String action){return switch(action){case "admit","lookup","verify"->false;case "release","receipt"->true;default->throw invalid();};}
    public static String grantReply(UUID nonce,Grant grant){var n=base(nonce);n.set("grant",grantNode(grant));return n.toString();}
    public static Grant readGrant(String text,UUID nonce,WriteIntent intent,Participant expected){var n=parse(text);fields(n,"nonce","grant");nonce(n,nonce);return grant(n.get("grant"),intent,expected);}
    public static String lookupReply(UUID nonce,Grant grant,String state){var n=base(nonce);boolean absent=grant==null&&state==null;if(!absent&&(grant==null||!Set.of("ACTIVE","RELEASED").contains(state)))throw invalid();n.put("result",absent?"ABSENT":"FOUND");if(absent){n.putNull("grant");n.putNull("state");}else{n.set("grant",grantNode(grant));n.put("state",state);}return n.toString();}
    public static Lookup readLookup(String text,UUID nonce,WriteIntent intent,Participant expected){var n=parse(text);fields(n,"nonce","result","grant","state");nonce(n,nonce);if("ABSENT".equals(string(n,"result"))){if(!n.get("grant").isNull()||!n.get("state").isNull())throw invalid();return new Lookup(Optional.empty(),null);}if(!"FOUND".equals(string(n,"result"))||!Set.of("ACTIVE","RELEASED").contains(string(n,"state")))throw invalid();return new Lookup(Optional.of(grant(n.get("grant"),intent,expected)),string(n,"state"));}
    public static String releaseReply(UUID nonce,Grant grant){return grantReply(nonce,grant);}
    public static void validateRelease(String text,UUID nonce,WriteIntent intent,long epoch){var n=parse(text);fields(n,"nonce","grant");nonce(n,nonce);var g=n.get("grant");var result=grant(g,intent,Participant.valueOf(string(g,"participant")));if(result.epoch()!=epoch)throw invalid();}
    public static String receiptReply(UUID nonce,WriteIntent intent,Participant participant,long authorityEpoch,DurableWriteLedger.Receipt receipt){if(!receipt.requestId().equals(intent.requestId())||!receipt.bindingHash().equals(intent.fingerprint())||!Objects.equals(receipt.grants().get(participant),authorityEpoch))throw invalid();var n=base(nonce);n.put("requestId",receipt.requestId().toString());n.put("bindingHash",receipt.bindingHash());n.put("operationEpoch",positive(receipt.epoch()));n.put("state",receipt.state().name());n.put("participant",participant.name());n.put("authorityEpoch",positive(authorityEpoch));return n.toString();}
    public static DurableWriteLedger.Receipt readReceipt(String text,UUID nonce,WriteIntent intent,Participant participant,long authorityEpoch){var n=parse(text);fields(n,"nonce","requestId","bindingHash","operationEpoch","state","participant","authorityEpoch");nonce(n,nonce);binding(n,intent);if(!participant.name().equals(string(n,"participant"))||number(n,"authorityEpoch",true)!=authorityEpoch)throw invalid();var state=DurableWriteLedger.State.valueOf(string(n,"state"));return new DurableWriteLedger.Receipt(intent.requestId(),intent.fingerprint(),number(n,"operationEpoch",true),state,Map.of(participant,authorityEpoch));}
    private static ObjectNode base(UUID nonce){var n=JSON.createObjectNode();n.put("nonce",Objects.requireNonNull(nonce).toString());return n;}
    private static ObjectNode intentNode(WriteIntent i){var n=JSON.createObjectNode();n.put("requestId",i.requestId().toString());n.put("accountId",i.accountId().toString());n.put("storeId",i.storeId().toString());n.put("tokenId",i.tokenId());n.put("mode",i.mode().name());n.put("sellerVersion",Long.toString(i.sellerVersion()));n.put("storeVersion",Long.toString(i.storeVersion()));n.put("productVersion",Long.toString(i.productVersion()));n.put("commandHash",i.commandHash());return n;}
    private static WriteIntent intent(JsonNode n){fields(n,"requestId","accountId","storeId","tokenId","mode","sellerVersion","storeVersion","productVersion","commandHash");return new WriteIntent(uuid(n,"requestId"),uuid(n,"accountId"),uuid(n,"storeId"),string(n,"tokenId"),WriteIntent.Mode.valueOf(string(n,"mode")),number(n,"sellerVersion",false),number(n,"storeVersion",false),number(n,"productVersion",false),string(n,"commandHash"));}
    private static ObjectNode grantNode(Grant g){var n=JSON.createObjectNode();n.put("requestId",g.requestId().toString());n.put("bindingHash",g.bindingHash());n.put("participant",g.participant().name());n.put("epoch",positive(g.epoch()));return n;}
    private static Grant grant(JsonNode n,WriteIntent i,Participant p){fields(n,"requestId","bindingHash","participant","epoch");binding(n,i);if(!p.name().equals(string(n,"participant")))throw invalid();return new Grant(i.requestId(),i.fingerprint(),p,number(n,"epoch",true));}
    private static void binding(JsonNode n,WriteIntent i){if(!uuid(n,"requestId").equals(i.requestId())||!string(n,"bindingHash").equals(i.fingerprint()))throw invalid();}
    private static void nonce(JsonNode n,UUID expected){if(!uuid(n,"nonce").equals(expected))throw invalid();}
    private static UUID uuid(JsonNode n,String key){String s=string(n,key);UUID u=UUID.fromString(s);if(!u.toString().equals(s))throw invalid();return u;}
    private static String string(JsonNode n,String key){if(n==null||!n.has(key)||!n.get(key).isTextual())throw invalid();return n.get(key).textValue();}
    private static long number(JsonNode n,String key,boolean positive){String s=string(n,key);if(!s.matches(positive?"[1-9][0-9]*":"0|[1-9][0-9]*"))throw invalid();try{return Long.parseLong(s);}catch(NumberFormatException e){throw invalid();}}
    private static String positive(long value){if(value<=0)throw invalid();return Long.toString(value);}
    private static void fields(JsonNode n,String... keys){if(n==null||!n.isObject()||n.size()!=keys.length)throw invalid();for(String key:keys)if(!n.has(key))throw invalid();}
    private static JsonNode parse(String text){return parse(text.getBytes(StandardCharsets.UTF_8));}
    private static JsonNode parse(byte[] bytes){try{if(bytes.length>16384)throw invalid();return JSON.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}catch(Exception e){throw invalid();}}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid reservation envelope");}
}
