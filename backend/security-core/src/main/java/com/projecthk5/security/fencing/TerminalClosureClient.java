package com.projecthk5.security.fencing;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;

public final class TerminalClosureClient {
    private final URI endpoint;private final Participant expected;private final ReservationTransport transport;
    public TerminalClosureClient(URI endpoint,Supplier<String> credential,Participant expected){ReservationTransport.endpoint(endpoint,Set.of("/internal/product/write-receipts/auth/closure","/internal/product/write-receipts/seller/closure"));this.expected=Objects.requireNonNull(expected);if(!endpoint.getPath().endsWith("/"+expected.name().toLowerCase(Locale.ROOT)+"/closure"))throw new IllegalArgumentException("Wrong receipt participant");this.endpoint=endpoint;transport=new ReservationTransport(credential);}
    public DurableAuthorityReservations.ClosureProof read(WriteIntent intent,long operationEpoch){try{UUID nonce=UUID.randomUUID();return ReservationWire.readClosure(transport.post(endpoint,ReservationWire.request("closure",nonce,intent,operationEpoch),null),nonce,intent,expected,operationEpoch);}catch(ReservationRpcClient.Failure e){throw e;}catch(Exception e){throw new ReservationRpcClient.Failure(503);}}
}
