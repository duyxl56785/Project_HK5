package com.projecthk5.security;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

class CurrentAccountClientTest {
    HttpServer server;String reply;int status=200;boolean stall;boolean injectAdmin;
    java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
    JwtAccessVerifier verifier;String token;
    @BeforeEach void setup() throws Exception {
        byte[] key=new byte[32];var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test").type(JOSEObjectType.JWT).build(),
            new JWTClaimsSet.Builder().issuer("https://issuer.example.invalid").audience("aud").subject(UUID.randomUUID().toString()).jwtID("test-jti")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).claim("roles",List.of("CUSTOMER")).build());
        jwt.sign(new MACSigner(key));token=jwt.serialize();
        verifier=new JwtAccessVerifier("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(key,"HmacSHA256")),Clock.systemUTC());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/auth/current-account/seller",exchange->{
            String request=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String requestId=request.substring(request.indexOf(":\"")+2,request.lastIndexOf('"'));
            var principal=verifier.verify(token);
            String body=reply==null?"{\"requestId\":\""+requestId+"\",\"subject\":\""+principal.subject()+"\",\"tokenId\":\"test-jti\",\"effectiveRoles\":[\"CUSTOMER\"]}":reply;
            if(injectAdmin)body=body.replace("CUSTOMER","ADMIN");
            exchange.getResponseHeaders().add("Content-Type","application/json");
            byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            if(stall){try {release.await(6,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void close(){server.stop(0);}
    CurrentAccountClient client(){return new CurrentAccountClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/auth/current-account/seller"),verifier,()->"test-only-scoped-pair-credential-0123456789");}
    @Test void returnsOnlyConfirmedSignedRolesAndDoesNotCache() {
        var result=client().resolve(token);assertEquals(Set.of("CUSTOMER"),result.roles());
        status=403;assertEquals(403,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
    }
    @Test void rejectsReplayedMismatchedAndExtraResponses() {
        reply="{\"requestId\":\""+UUID.randomUUID()+"\",\"subject\":\"other\",\"tokenId\":\"test-jti\",\"effectiveRoles\":[\"ADMIN\"]}";
        assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
    }
    @Test void failsClosedOnAuthUnavailableAndInvalidJwt() {
        status=503;assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
        assertEquals(401,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve("invalid")).status());
        server.stop(0);assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
    }
    @Test void rejectsOversizedResponseWithoutAcceptingAnyAuthority() {
        reply="x".repeat(9000);
        assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
    }    @Test void boundsWholeResponseTimeEvenWhenAuthStallsAfterHeaders() {
        stall=true;long start=System.nanoTime();
        try {
            assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
            assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofSeconds(5))<0);
        }finally {release.countDown();}
    }    @Test void rejectsUnsignedRoleEvenWithMatchingRequestSubjectAndToken() {
        injectAdmin=true;
        assertEquals(503,assertThrows(CurrentAccountClient.Failure.class,()->client().resolve(token)).status());
    }}
