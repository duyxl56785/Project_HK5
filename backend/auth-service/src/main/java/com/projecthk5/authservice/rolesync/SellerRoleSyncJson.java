package com.projecthk5.authservice.rolesync;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import com.projecthk5.authservice.rolesync.SellerRoleSyncService.*;

/** Proposed wire codec only, not a registered HTTP binding or a contract signature. */
public final class SellerRoleSyncJson {
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public Command read(String payload) {
        if(payload == null || payload.length()>16384) throw invalid();
        try {
            JsonNode node=mapper.readTree(payload);
            if(node==null || !node.isObject() || node.size()!=5) throw invalid();
            var fields=new HashSet<String>(); node.fieldNames().forEachRemaining(fields::add);
            if(!fields.equals(Set.of("eventId","sellerId","accountId","sellerStatus","sourceVersion")))throw invalid();
            for(String field:fields)if(!node.get(field).isTextual())throw invalid();
            String version=node.get("sourceVersion").textValue();
            if(!version.matches("0|[1-9][0-9]{0,18}"))throw invalid();
            return new Command(uuid(node.get("eventId").textValue()),uuid(node.get("sellerId").textValue()),
                    node.get("accountId").textValue(),Status.valueOf(node.get("sellerStatus").textValue()),Long.parseLong(version));
        } catch(Exception failure) {throw invalid();}
    }
    public String ack(Command command,Result result) {
        Objects.requireNonNull(command);Objects.requireNonNull(result);
        try {return mapper.writeValueAsString(Map.of("eventId",command.eventId().toString(),"sellerId",command.sellerId().toString(),
                "sourceVersion",Long.toString(command.sourceVersion()),"result",result.name()));}
        catch(Exception failure){throw new IllegalStateException("Role sync acknowledgment could not be encoded");}
    }
    private UUID uuid(String value) {UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid();return id;}
    private IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid role sync payload");}
}
