package com.projecthk5.security.fencing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;

class ReservationWireTest {
    private final UUID nonce=UUID.randomUUID();
    private final WriteIntent intent=new WriteIntent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"token",WriteIntent.Mode.MANAGE,0,1,2,"a".repeat(64));
    private byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    @Test void requestRoundTripAndStrictAuthorityBoundary(){String text=ReservationWire.request("admit",nonce,intent,null);assertEquals(intent,ReservationWire.readRequest("admit",bytes(text)).intent());assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("admit",bytes(text.replace("\"intent\":","\"grant\":{},\"intent\":"))));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("release",bytes(text)));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("admit",bytes(text.replace("\"sellerVersion\":\"0\"","\"sellerVersion\":0"))));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("admit",bytes(text.replace("\"sellerVersion\":\"0\"","\"sellerVersion\":\"00\""))));}
    @Test void rejectsDuplicateTrailingMalformedAndOversized(){String text=ReservationWire.request("lookup",nonce,intent,null);assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("lookup",bytes(text+"{}")));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("lookup",bytes(text.replace("\"nonce\":","\"nonce\":\""+nonce+"\",\"nonce\":"))));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("lookup",new byte[]{(byte)0xc3,0x28}));assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("lookup",new byte[16385]));}
    @Test void grantRequiresNonceBindingParticipantAndEpoch(){Grant g=new Grant(intent.requestId(),intent.fingerprint(),Participant.AUTH,3);String text=ReservationWire.grantReply(nonce,g);assertEquals(g,ReservationWire.readGrant(text,nonce,intent,Participant.AUTH));assertThrows(IllegalArgumentException.class,()->ReservationWire.readGrant(text,UUID.randomUUID(),intent,Participant.AUTH));assertThrows(IllegalArgumentException.class,()->ReservationWire.readGrant(text,nonce,intent,Participant.SELLER));assertThrows(IllegalArgumentException.class,()->ReservationWire.validateRelease(text,nonce,intent,4));assertThrows(IllegalArgumentException.class,()->ReservationWire.readGrant(text.replace(intent.fingerprint(),"b".repeat(64)),nonce,intent,Participant.AUTH));}
    @Test void absentAndTerminalReceiptAreStrict(){assertTrue(ReservationWire.readLookup(ReservationWire.lookupReply(nonce,null,null),nonce,intent,Participant.AUTH).grant().isEmpty());assertThrows(IllegalArgumentException.class,()->ReservationWire.lookupReply(nonce,null,"ACTIVE"));var receipt=new DurableWriteLedger.Receipt(intent.requestId(),intent.fingerprint(),8,DurableWriteLedger.State.ABORTED,Map.of(Participant.AUTH,3L,Participant.SELLER,5L));String text=ReservationWire.receiptReply(nonce,intent,Participant.AUTH,3,receipt);assertEquals(Map.of(Participant.AUTH,3L),ReservationWire.readReceipt(text,nonce,intent,Participant.AUTH,3).grants());assertThrows(IllegalArgumentException.class,()->ReservationWire.readReceipt(text.replace("ABORTED","PENDING"),nonce,intent,Participant.AUTH,3));assertThrows(IllegalArgumentException.class,()->ReservationWire.readReceipt(text,nonce,intent,Participant.AUTH,4));}
    @Test void closureBindsOperationAndAllowsOnlyAbortedMissingAuthority() {
        var aborted=new ClosureProof(intent.requestId(),intent.fingerprint(),8,DurableWriteLedger.State.ABORTED,Participant.AUTH,null);
        String text=ReservationWire.closureReply(nonce,intent,aborted);
        assertEquals(aborted,ReservationWire.readClosure(text,nonce,intent,Participant.AUTH,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text,nonce,intent,Participant.AUTH,9));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text,UUID.randomUUID(),intent,Participant.AUTH,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text,nonce,intent,Participant.SELLER,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text.replace("ABORTED","COMMITTED"),nonce,intent,Participant.AUTH,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text.replace("ABORTED","PENDING"),nonce,intent,Participant.AUTH,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readClosure(text.replace("\"authorityEpoch\":null","\"authorityEpoch\":\"0\""),nonce,intent,Participant.AUTH,8));
        var committed=new ClosureProof(intent.requestId(),intent.fingerprint(),8,DurableWriteLedger.State.COMMITTED,Participant.AUTH,3L);
        assertEquals(committed,ReservationWire.readClosure(ReservationWire.closureReply(nonce,intent,committed),nonce,intent,Participant.AUTH,8));
    }
    @Test void sealAcknowledgementIsBoundAndRequestCannotSupplyProof() {
        String ack=ReservationWire.sealReply(nonce,intent,Participant.AUTH,8);
        ReservationWire.validateSeal(ack,nonce,intent,Participant.AUTH,8);
        assertThrows(IllegalArgumentException.class,()->ReservationWire.validateSeal(ack,nonce,intent,Participant.SELLER,8));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.validateSeal(ack,nonce,intent,Participant.AUTH,9));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.validateSeal(ack.replace("SEALED","RELEASED"),nonce,intent,Participant.AUTH,8));
        String request=ReservationWire.request("seal",nonce,intent,8L);
        assertEquals(8L,ReservationWire.readRequest("seal",bytes(request)).epoch());
        assertThrows(IllegalArgumentException.class,()->ReservationWire.readRequest("seal",bytes(request.replace("\"intent\":","\"proof\":{},\"intent\":"))));
        assertThrows(IllegalArgumentException.class,()->ReservationWire.request("closure",nonce,intent,0L));
    }
}
