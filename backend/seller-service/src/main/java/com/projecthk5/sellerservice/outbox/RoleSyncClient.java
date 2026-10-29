package com.projecthk5.sellerservice.outbox;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.projecthk5.security.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class RoleSyncClient implements SellerRoleDispatcher.Sender {
    private final URI endpoint;private final Supplier<String> credential;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public RoleSyncClient(URI endpoint,Supplier<String> credential){
        Objects.requireNonNull(endpoint);String host=endpoint.getHost();
        if(host==null || endpoint.getUserInfo()!=null || endpoint.getRawQuery()!=null || endpoint.getRawFragment()!=null
            || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()) && Set.of("127.0.0.1","localhost","[::1]").contains(host))
            || !"/internal/auth/seller-role-sync".equals(endpoint.getRawPath()))throw new IllegalArgumentException("Private TLS Auth role sync endpoint required");
        this.endpoint=endpoint;this.credential=Objects.requireNonNull(credential);
    }
    public URI endpoint(){return endpoint;}
    public SellerRoleDispatcher.Ack send(DeliveryStore.Claim event){
        DeliveryStore.outsideTransaction();
        try {
            String secret=credential.get();new InternalPairVerifier.Credentials(secret,null,null);
            String body=json.writeValueAsString(Map.of("eventId",event.eventId().toString(),"sellerId",event.sellerId().toString(),"accountId",event.accountId(),"sellerStatus",event.status(),"sourceVersion",Long.toString(event.version())));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").header("X-HK5-Service-Credential",secret).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=BoundedHttpResponse.send(http,request,8192);
            if(response.statusCode()==409 || Set.of(400,404,405,413,415).contains(response.statusCode()))throw new Conflict();
            if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].equalsIgnoreCase("application/json")
                || !response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))throw new IllegalStateException();
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(response.body())).toString();
            var ack=json.readTree(text);
            if(ack==null || !ack.isObject() || ack.size()!=4)throw new IllegalArgumentException();
            for(String field:List.of("eventId","sellerId","sourceVersion","result"))if(!ack.has(field) || !ack.get(field).isTextual())throw new IllegalArgumentException();
            if(!event.eventId().toString().equals(ack.get("eventId").textValue()) || !event.sellerId().toString().equals(ack.get("sellerId").textValue()) || !Long.toString(event.version()).equals(ack.get("sourceVersion").textValue()))throw new IllegalArgumentException();
            return SellerRoleDispatcher.Ack.valueOf(ack.get("result").textValue());
        }catch(Conflict conflict){throw conflict;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Role sync unavailable");}
        catch(Exception unavailable){throw new IllegalStateException("Role sync unavailable");}
    }
    public static final class Conflict extends RuntimeException {private Conflict(){super("Role sync requires reconciliation");}}
}
