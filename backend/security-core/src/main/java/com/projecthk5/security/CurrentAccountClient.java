package com.projecthk5.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.JwtException;

/** Per-request read; no role cache, redirects or Auth database access. */
public final class CurrentAccountClient {
    private final URI endpoint;
    private final JwtAccessVerifier verifier;
    private final Supplier<String> credential;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public CurrentAccountClient(URI endpoint,JwtAccessVerifier verifier,Supplier<String> credential) {
        Objects.requireNonNull(endpoint);
        boolean loopback=Set.of("127.0.0.1","localhost","[::1]").contains(endpoint.getHost()==null?"":endpoint.getHost());
        if(endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getRawQuery()!=null || endpoint.getRawFragment()!=null
            || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()) && loopback)
            || !Set.of("/internal/auth/current-account/seller","/internal/auth/current-account/product").contains(endpoint.getRawPath()))
            throw new IllegalArgumentException("Explicit private Auth endpoint with TLS required");
        this.endpoint=endpoint;this.verifier=Objects.requireNonNull(verifier);this.credential=Objects.requireNonNull(credential);
    }
    public CurrentAccount resolve(String token) {
        JwtAccessVerifier.VerifiedPrincipal signed;
        try {signed=verifier.verify(token);}catch(JwtException invalid){throw failure(401);}
        String nonce=UUID.randomUUID().toString();
        try {
            String secret=credential.get();new InternalPairVerifier.Credentials(secret,null,null);
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .header("X-HK5-Service-Credential",secret).POST(HttpRequest.BodyPublishers.ofString("{\"requestId\":\""+nonce+"\"}")).build();
            var pending=http.sendAsync(request,info->new BoundedBody());
            HttpResponse<byte[]> response;
            try {response=pending.get(3,java.util.concurrent.TimeUnit.SECONDS);}
            catch(Exception failure){pending.cancel(true);throw failure;}
            {
                int status=response.statusCode();
                if(status==401 || status==403)throw failure(status);
                if(status!=200 || !response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].equalsIgnoreCase("application/json"))throw failure(503);
                byte[] bytes=response.body();
                var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                var node=json.readTree(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString());
                if(node==null || !node.isObject() || node.size()!=4)throw failure(503);
                for(String field:List.of("requestId","subject","tokenId"))if(!node.has(field) || !node.get(field).isTextual())throw failure(503);
                if(!nonce.equals(node.get("requestId").textValue()) || !signed.subject().equals(node.get("subject").textValue())
                    || !signed.tokenId().equals(node.get("tokenId").textValue()) || !node.has("effectiveRoles") || !node.get("effectiveRoles").isArray())throw failure(503);
                var roles=new HashSet<String>();
                for(var role:node.get("effectiveRoles"))if(!role.isTextual() || !signed.tokenRoles().contains(role.textValue()) || !roles.add(role.textValue()))throw failure(503);
                if(roles.isEmpty())throw failure(503);
                return new CurrentAccount(signed.subject(),Set.copyOf(roles));
            }
        }catch(Failure denied){throw denied;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw failure(503);}
        catch(Exception unavailable){throw failure(503);}
    }
    public static final class CurrentAccount {
        private final String subject;private final Set<String> roles;
        private CurrentAccount(String subject,Set<String> roles){this.subject=subject;this.roles=roles;}
        public String subject(){return subject;} public Set<String> roles(){return roles;}
        public void requireRole(String role){if(!roles.contains(role))throw failure(403);}
        public void requireSubject(String expected){if(!subject.equals(expected))throw failure(403);}
    }
    public static final class Failure extends RuntimeException {
        private final int status;private Failure(int status){super("Current account authorization failed");this.status=status;}
        public int status(){return status;}
    }
    private static Failure failure(int status){return new Failure(status);}
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;value.request(Long.MAX_VALUE);}
        public void onNext(List<java.nio.ByteBuffer> buffers){
            for(var buffer:buffers){
                if(bytes.size()+buffer.remaining()>8192){subscription.cancel();result.completeExceptionally(new IllegalStateException("Authority response too large"));return;}
                byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);
            }
        }
        public void onError(Throwable failure){result.completeExceptionally(failure);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }}
