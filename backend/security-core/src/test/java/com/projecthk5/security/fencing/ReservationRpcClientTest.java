package com.projecthk5.security.fencing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;

class ReservationRpcClientTest {
    private HttpServer server;private URI root;private int status=200;private boolean cache=true;private boolean wrongNonce;
    private final AtomicReference<String> supplied=new AtomicReference<>();
    private final WriteIntent intent=new WriteIntent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"token",WriteIntent.Mode.MANAGE,0,0,0,"a".repeat(64));
    @BeforeEach void setup() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);root=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/auth/write-reservations/product/");server.createContext(root.getPath()+"admit",exchange->{var request=ReservationWire.readRequest("admit",exchange.getRequestBody().readAllBytes());supplied.set(exchange.getRequestHeaders().getFirst("X-HK5-Service-Credential"));assertEquals("Bearer token",exchange.getRequestHeaders().getFirst("Authorization"));var grant=new Grant(intent.requestId(),intent.fingerprint(),Participant.AUTH,5);byte[] body=ReservationWire.grantReply(wrongNonce?UUID.randomUUID():request.nonce(),grant).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");if(cache)exchange.getResponseHeaders().add("Cache-Control","no-store");exchange.getResponseHeaders().add("Location",root.resolve("admit").toString());exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();}
    @AfterEach void stop(){server.stop(0);}
    private ReservationRpcClient client(java.util.function.Supplier<String> secret){return new ReservationRpcClient(root,secret,Participant.AUTH,()->"token");}
    @Test void usesCurrentCredentialOnEveryRequest(){var secret=new AtomicReference<>("a".repeat(40));var client=client(secret::get);assertEquals(5,client.admit(intent).epoch());assertEquals(secret.get(),supplied.get());secret.set("b".repeat(40));client.admit(intent);assertEquals(secret.get(),supplied.get());}
    @Test void failsClosedOnReplayCacheOrRedirect(){var client=client(()->"a".repeat(40));wrongNonce=true;assertEquals(503,assertThrows(ReservationRpcClient.Failure.class,()->client.admit(intent)).status());wrongNonce=false;cache=false;assertEquals(503,assertThrows(ReservationRpcClient.Failure.class,()->client.admit(intent)).status());cache=true;status=302;assertEquals(503,assertThrows(ReservationRpcClient.Failure.class,()->client.admit(intent)).status());status=409;assertEquals(409,assertThrows(ReservationRpcClient.Failure.class,()->client.admit(intent)).status());}
    @Test void pinsEndpointAndParticipant(){assertThrows(IllegalArgumentException.class,()->new ReservationRpcClient(URI.create("http://example.com/internal/auth/write-reservations/product/"),()->"a".repeat(40),Participant.AUTH,null));assertThrows(IllegalArgumentException.class,()->new ReservationRpcClient(root,()->"a".repeat(40),Participant.SELLER,null));}
}
