package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoucherRulesTest {
    private static final Instant T=Instant.parse("2026-11-14T15:00:00Z");
    @Test void storesFourStatesAndDerivesAtServerBoundary() {
        assertEquals(VoucherRules.DisplayStatus.SCHEDULED,VoucherRules.display(
            VoucherRules.StoredStatus.PUBLISHED,T.plusSeconds(1),T.plusSeconds(3600),T));
        assertEquals(VoucherRules.DisplayStatus.ACTIVE,VoucherRules.display(
            VoucherRules.StoredStatus.PUBLISHED,T,T.plusSeconds(3600),T));
        assertEquals(VoucherRules.DisplayStatus.EXPIRED,VoucherRules.display(
            VoucherRules.StoredStatus.PUBLISHED,T.minusSeconds(1),T,T));
        assertEquals(VoucherRules.DisplayStatus.PAUSED,VoucherRules.display(
            VoucherRules.StoredStatus.PAUSED,T.minusSeconds(1),T.plusSeconds(100),T));
        assertEquals(VoucherRules.DisplayStatus.EXPIRED,VoucherRules.display(
            VoucherRules.StoredStatus.PAUSED,T.minusSeconds(1),T,T));
        assertEquals(VoucherRules.DisplayStatus.CANCELLED,VoucherRules.display(
            VoucherRules.StoredStatus.CANCELLED,T.minusSeconds(1),T,T));
    }
    @Test void quotaDefaultGrantExpiryAndGrandfather() {
        var grant=new VoucherRules.Grant(25,T.minusSeconds(2),T.plusSeconds(60));
        assertTrue(VoucherRules.canTakeSlot(19,T,T,null));
        assertFalse(VoucherRules.canTakeSlot(20,T,T,null));
        assertTrue(VoucherRules.canTakeSlot(24,T,T,grant));
        assertFalse(VoucherRules.canTakeSlot(25,T,T,grant));
        assertFalse(VoucherRules.canTakeSlot(24,T.plusSeconds(60),T,grant));
        assertEquals(20,VoucherRules.permittedLimit(24,T,T.plusSeconds(60),grant));
        assertEquals(20,VoucherRules.permittedLimit(24,T,T.minusSeconds(5),grant));
    }
    @Test void validatesValueWindowAndCode() {
        var valid=new VoucherRules.Spec("save20","Giảm 20k",VoucherRules.DiscountType.FIXED,
            20_000,null,100_000,100,T,T.plusSeconds(3600));
        assertEquals("SAVE20",valid.code());
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec(
            "bad code","x",VoucherRules.DiscountType.FIXED,20,null,20,1,T,T.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec(
            "GOOD1","x",VoucherRules.DiscountType.FIXED,20,10L,20,1,T,T.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec(
            "GOOD1","x",VoucherRules.DiscountType.PERCENT,91,10L,0,1,T,T.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec(
            "GOOD1","x",VoucherRules.DiscountType.PERCENT,10,null,0,1,T,T.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec(
            "GOOD1","x",VoucherRules.DiscountType.FIXED,20,null,20,1,T,T.plusSeconds(91L*24*3600)));
    }
}
