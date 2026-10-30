package com.projecthk5.security.fencing;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;

public final class TerminalReceiptClient {
    private final URI endpoint;private final Participant expected;private final ReservationTransport transport;
    public TerminalReceiptClient(URI endpoint,Supplier<String> credential,Participant expected){ReservationTransport.endpoint(endpoint,Set.of("/internal/product/write-receipts/auth","/internal/product/write-receipts/seller"));this.expected=Objects.requireNonNull(expected);if(!endpoint.getPath().endsWith("/"+expected.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Wrong receipt participant");this.endpoint=endpoint;transport=new ReservationTransport(credential);}
    public DurableWriteLedger.Receipt read(WriteIntent intent,long authorityEpoch){try{UUID nonce=UUID.randomUUID();return ReservationWire.readReceipt(transport.post(endpoint,ReservationWire.request("receipt",nonce,intent,authorityEpoch),null),nonce,intent,expected,authorityEpoch);}catch(ReservationRpcClient.Failure e){throw e;}catch(Exception e){throw new ReservationRpcClient.Failure(503);}}
}
