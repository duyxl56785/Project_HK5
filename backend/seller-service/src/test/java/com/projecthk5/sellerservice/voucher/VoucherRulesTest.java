package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoucherRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    @Test void quotaAndGrantExpiryRespectGrandfatherRule() {
        var grant = new VoucherRules.Grant(25, NOW.minusSeconds(60), NOW.plusSeconds(60));
        assertTrue(VoucherRules.canTakeSlot(19,NOW,NOW,null));
        assertFalse(VoucherRules.canTakeSlot(20,NOW,NOW,null));
        assertTrue(VoucherRules.canTakeSlot(24,NOW,NOW,grant));
        assertFalse(VoucherRules.canTakeSlot(25,NOW,NOW,grant));
        assertFalse(VoucherRules.canTakeSlot(24,NOW,NOW.plusSeconds(60),grant));
        // endsAt can exceed grant validity: quota only checks startsAt vs grant end.
        assertFalse(VoucherRules.canTakeSlot(20,NOW.plusSeconds(60),NOW,grant));
    }

    @Test void derivedStatesAreStableOnExactTimeBoundaries() {
        Instant end=NOW.plusSeconds(3600);
        assertEquals(VoucherRules.DisplayStatus.SCHEDULED,
                VoucherRules.display(VoucherRules.StoredStatus.PUBLISHED,NOW,end,NOW.minusNanos(1)));
        assertEquals(VoucherRules.DisplayStatus.ACTIVE,
                VoucherRules.display(VoucherRules.StoredStatus.PUBLISHED,NOW,end,NOW));
        assertEquals(VoucherRules.DisplayStatus.EXPIRED,
                VoucherRules.display(VoucherRules.StoredStatus.PUBLISHED,NOW,end,end));
        assertEquals(VoucherRules.DisplayStatus.EXPIRED,
                VoucherRules.display(VoucherRules.StoredStatus.PAUSED,NOW,end,end));
    }

    @Test void moneyAndLifetimeAreValidatedBeforePersistence() {
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec("ab1", "V",VoucherRules.DiscountType.FIXED,
                10,null,10,1,NOW,NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec("AB12", "V",VoucherRules.DiscountType.PERCENT,
                91,100L,10,1,NOW,NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class,()->new VoucherRules.Spec("AB12", "V",VoucherRules.DiscountType.FIXED,
                10,null,10,1,NOW,NOW.plus(Duration.ofDays(91))));
        assertEquals("AB12",new VoucherRules.Spec("ab12", "Voucher",VoucherRules.DiscountType.FIXED,
                10,null,10,1,NOW,NOW.plusSeconds(30)).code());
    }
}
