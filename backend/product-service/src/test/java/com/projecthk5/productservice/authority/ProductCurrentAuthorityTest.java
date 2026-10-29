package com.projecthk5.productservice.authority;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.projecthk5.security.*;
import com.projecthk5.productservice.product.*;
import org.junit.jupiter.api.*;

class ProductCurrentAuthorityTest {
    HttpServer server;ObjectMapper json=new ObjectMapper();String token;
    UUID owner=UUID.randomUUID(),store=UUID.randomUUID(),seller=UUID.randomUUID();
    String storeOwner;String storeStatus="OPEN",sellerStatus="APPROVED";boolean absent,wrongNonce;int dependencyStatus=200;
    Set<String> currentRoles=Set.of("SELLER");AtomicInteger calls=new AtomicInteger();
    JwtAccessVerifier verifier;ProductCurrentAuthority factory;
    java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> tamper=root->{};
    MutableClock clock=new MutableClock();
    static final String KEY="test-only-product-authority-pair-credential-0123456789";
    static class MutableClock extends Clock {Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
    @BeforeEach void start() throws Exception {
        storeOwner=owner.toString();byte[] key=new byte[32];var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test").type(JOSEObjectType.JWT).build(),
            new JWTClaimsSet.Builder().issuer("https://issuer.example.invalid").audience("aud").subject(owner.toString()).jwtID("test-jti")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).claim("roles",List.of("SELLER")).build());
        jwt.sign(new MACSigner(key));token=jwt.serialize();
        verifier=new JwtAccessVerifier("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(key,"HmacSHA256")),Clock.systemUTC());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/auth/current-account/product",exchange->{
            calls.incrementAndGet();var body=json.readTree(exchange.getRequestBody());
            byte[] response=json.writeValueAsBytes(Map.of("requestId",body.get("requestId").textValue(),"subject",owner.toString(),"tokenId","test-jti","effectiveRoles",currentRoles));
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(currentRoles.isEmpty()?403:200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.createContext("/internal/seller/store-facts/batch",exchange->{
            calls.incrementAndGet();var request=json.readTree(exchange.getRequestBody());
            var rows=new ArrayList<Map<String,Object>>();for(var id:request.get("storeIds")) {
                if(absent)rows.add(Map.of("storeId",id.textValue(),"result","ABSENT"));
                else rows.add(Map.of("storeId",id.textValue(),"result","FOUND","sellerId",seller.toString(),"ownerReference",storeOwner,
                    "storeStatus",storeStatus,"sellerStatus",sellerStatus,"storeVersion","9","sellerVersion","7"));
            }
            var responseNode=json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(Map.of("results",rows));tamper.accept(responseNode);
            byte[] response=json.writeValueAsBytes(responseNode);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.getResponseHeaders().add("Cache-Control","no-store");
            exchange.getResponseHeaders().add("X-HK5-Facts-Request-Id",wrongNonce?UUID.randomUUID().toString():exchange.getRequestHeaders().getFirst("X-HK5-Facts-Request-Id"));
            exchange.sendResponseHeaders(dependencyStatus,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        factory=new ProductCurrentAuthority(new CurrentAccountClient(URI.create(base+"/internal/auth/current-account/product"),verifier,()->KEY),
            new StoreFactsClient(URI.create(base+"/internal/seller/store-facts/batch"),()->KEY),clock);
    }
    @AfterEach void close(){server.stop(0);org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
    @Test void connectsAuthorityWithoutInventingHouseAndDoesNoNetworkInBusinessTransaction() {
        var prepared=factory.prepareSellerManagement(token,store);
        int before=calls.get();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        var access=prepared.readStore(store);
        assertEquals(owner.toString(),access.ownerReference());assertEquals(before,calls.get());
        assertThrows(ProductRuleException.class,access::houseStore);
        assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));assertEquals(before,calls.get());
    }
    @Test void preflightCannotAuthorizeWritesAfterConcurrentRemoteRevocation() throws Exception {
        var prepared=factory.prepareSellerManagement(token,store);
        var changed=new java.util.concurrent.CountDownLatch(1);
        var worker=new Thread(()->{currentRoles=Set.of();sellerStatus="SUSPENDED";storeStatus="CLOSED";changed.countDown();});
        worker.start();assertTrue(changed.await(3,java.util.concurrent.TimeUnit.SECONDS));worker.join();
        int before=calls.get();
        for(boolean transaction:List.of(false,true)) {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(transaction);
            assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(ProductRuleException.class,()->prepared.requireSellerManagement(owner.toString(),store)).getCode());
            assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(ProductRuleException.class,()->prepared.requireModeration(owner.toString(),store)).getCode());
        }
        assertEquals(before,calls.get());
    }
    @Test void rejectsWrongOwnerAndUnapprovedClosedMissingStores() {
        storeOwner="someone-else";assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));
        storeOwner=owner.toString();storeStatus="CLOSED";assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));
        storeStatus="OPEN";sellerStatus="SUSPENDED";assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));
        sellerStatus="APPROVED";absent=true;assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));
    }
    @Test void rejectsStaleProofForgedActorAndDependencyFailure() {
        var prepared=factory.prepareSellerManagement(token,store);
        assertThrows(ProductRuleException.class,()->prepared.requireSellerManagement("forged",store));
        clock.now=clock.now.plusSeconds(6);assertThrows(ProductRuleException.class,()->prepared.requireSellerManagement(owner.toString(),store));
        dependencyStatus=503;assertThrows(StoreFactsClient.Failure.class,()->factory.prepareSellerManagement(token,store));
    }
    @Test void rejectsReplayAndWrongRolesBeforeStoreRequest() {
        wrongNonce=true;assertThrows(StoreFactsClient.Failure.class,()->factory.prepareSellerManagement(token,store));
        wrongNonce=false;currentRoles=Set.of();int before=calls.get();
        assertThrows(CurrentAccountClient.Failure.class,()->factory.prepareSellerManagement(token,store));assertEquals(before+1,calls.get());
    }
    @Test void strictResponseValidationRejectsIncompleteExtraMalformedAndInconsistentFacts() {
        List<java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode>> cases=List.of(
            root->root.remove("results"),root->root.put("extra",true),root->((com.fasterxml.jackson.databind.node.ArrayNode)root.get("results")).removeAll(),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("sellerVersion","07"),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("storeVersion",1),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("storeId",UUID.randomUUID().toString()),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("sellerStatus","UNKNOWN"),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("ownerReference",""),
            root->((com.fasterxml.jackson.databind.node.ObjectNode)root.get("results").get(0)).put("houseStore",false));
        for(var change:cases){tamper=change;assertEquals(503,assertThrows(StoreFactsClient.Failure.class,()->factory.prepareSellerManagement(token,store)).status());}
    }
}
