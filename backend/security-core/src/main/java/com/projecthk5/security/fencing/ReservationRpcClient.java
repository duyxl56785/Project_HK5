package com.projecthk5.security.fencing;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;

public final class ReservationRpcClient {
    private final URI root;private final Supplier<String> bearer;private final Participant expected;private final ReservationTransport transport;
    public ReservationRpcClient(URI root,Supplier<String> credential,Participant expected,Supplier<String> bearer){ReservationTransport.endpoint(root,Set.of("/internal/auth/write-reservations/product/","/internal/seller/write-reservations/product/","/internal/auth/write-reservations/seller/"));this.root=root;this.expected=Objects.requireNonNull(expected);if((root.getPath().startsWith("/internal/auth/")?Participant.AUTH:Participant.SELLER)!=expected)throw new IllegalArgumentException("Wrong reservation participant");this.bearer=bearer;this.transport=new ReservationTransport(credential);}
    public Grant admit(WriteIntent intent){if(root.getPath().endsWith("/seller/"))throw new Failure(503);return call(()->{UUID nonce=UUID.randomUUID();String token=bearer==null?null:bearer.get();if(expected==Participant.AUTH&&(token==null||token.isBlank()))throw new Failure(401);return ReservationWire.readGrant(transport.post(root.resolve("admit"),ReservationWire.request("admit",nonce,intent,null),token),nonce,intent,expected);});}
    public ReservationWire.Lookup lookup(WriteIntent intent){return call(()->{UUID nonce=UUID.randomUUID();return ReservationWire.readLookup(transport.post(root.resolve("lookup"),ReservationWire.request("lookup",nonce,intent,null),null),nonce,intent,expected);});}
    public void release(WriteIntent intent,long epoch){if(root.getPath().endsWith("/seller/"))throw new Failure(503);call(()->{UUID nonce=UUID.randomUUID();String response=transport.post(root.resolve("release"),ReservationWire.request("release",nonce,intent,epoch),null);ReservationWire.readGrant(response,nonce,intent,expected);ReservationWire.validateRelease(response,nonce,intent,epoch);return null;});}
    private static <T>T call(Supplier<T> action){try{return action.get();}catch(Failure e){throw e;}catch(Exception e){throw new Failure(503);}}
    public static final class Failure extends RuntimeException {private final int status;public Failure(int status){super("Reservation service request failed");this.status=status;}public int status(){return status;}}
}
