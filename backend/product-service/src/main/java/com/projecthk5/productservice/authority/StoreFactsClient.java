package com.projecthk5.productservice.authority;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.projecthk5.security.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class StoreFactsClient {
    private final URI endpoint;private final Supplier<String> credential;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public StoreFactsClient(URI endpoint,Supplier<String> credential){
        Objects.requireNonNull(endpoint);String host=endpoint.getHost();
        if(host==null || endpoint.getUserInfo()!=null || endpoint.getRawQuery()!=null || endpoint.getRawFragment()!=null
            || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()) && Set.of("127.0.0.1","localhost","[::1]").contains(host))
            || !"/internal/seller/store-facts/batch".equals(endpoint.getRawPath()))throw new IllegalArgumentException("Private TLS Seller endpoint required");
        this.endpoint=endpoint;this.credential=Objects.requireNonNull(credential);
    }
    public record Fact(UUID storeId,UUID sellerId,String ownerReference,String storeStatus,String sellerStatus,long storeVersion,long sellerVersion){}
    public Map<UUID,Optional<Fact>> read(List<UUID> ids){
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Invalid Store IDs");
        ids=List.copyOf(ids);String nonce=UUID.randomUUID().toString();
        try {
            String secret=credential.get();new InternalPairVerifier.Credentials(secret,null,null);
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json")
                .header("Cache-Control","no-store").header("X-HK5-Service-Credential",secret).header("X-HK5-Facts-Request-Id",nonce)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("storeIds",ids.stream().map(UUID::toString).toList())))).build();
            var response=BoundedHttpResponse.send(http,request,131072);
            if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].equalsIgnoreCase("application/json")
                || !response.headers().firstValue("Cache-Control").orElse("").contains("no-store")
                || response.headers().allValues("X-HK5-Facts-Request-Id").size()!=1
                || !nonce.equals(response.headers().firstValue("X-HK5-Facts-Request-Id").orElse("")))throw unavailable();
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(response.body())).toString();
            var body=json.readTree(text);
            if(body==null || !body.isObject() || body.size()!=1 || !body.has("results") || !body.get("results").isArray() || body.get("results").size()!=ids.size())throw unavailable();
            var results=new LinkedHashMap<UUID,Optional<Fact>>();int index=0;
            for(var row:body.get("results")){
                if(!row.isObject() || !ids.get(index++).equals(uuid(row,"storeId")))throw unavailable();
                UUID id=uuid(row,"storeId");String result=string(row,"result");
                if("ABSENT".equals(result)){if(row.size()!=2)throw unavailable();results.put(id,Optional.empty());continue;}
                if(!"FOUND".equals(result) || row.size()!=8)throw unavailable();
                String owner=string(row,"ownerReference"),store=string(row,"storeStatus"),seller=string(row,"sellerStatus");
                if(owner.isBlank() || owner.codePointCount(0,owner.length())>128 || !Set.of("OPEN","CLOSED").contains(store)
                    || !Set.of("PENDING_REVIEW","APPROVED","REJECTED","SUSPENDED").contains(seller))throw unavailable();
                results.put(id,Optional.of(new Fact(id,uuid(row,"sellerId"),owner,store,seller,version(row,"storeVersion"),version(row,"sellerVersion"))));
            }
            return Collections.unmodifiableMap(results);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw unavailable();}
        catch(Exception invalid){throw unavailable();}
    }
    private String string(JsonNode node,String field){if(!node.has(field) || !node.get(field).isTextual())throw unavailable();return node.get(field).textValue();}
    private UUID uuid(JsonNode node,String field){String value=string(node,field);UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw unavailable();return id;}
    private long version(JsonNode node,String field){String value=string(node,field);if(!value.matches("0|[1-9][0-9]{0,18}"))throw unavailable();return Long.parseLong(value);}
    public static final class Failure extends RuntimeException {private Failure(){super("Current Store facts unavailable");}public int status(){return 503;}}
    private Failure unavailable(){return new Failure();}
}
