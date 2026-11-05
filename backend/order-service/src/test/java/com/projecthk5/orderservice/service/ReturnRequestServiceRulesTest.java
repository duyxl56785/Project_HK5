package com.projecthk5.orderservice.service;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.security.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReturnRequestServiceRulesTest {
    private static final Instant DELIVERED = Instant.parse("2026-11-05T00:00:00Z");

    private boolean eligible(Instant now, Instant settled, SellerOrder.CodStatus cod,
                             SellerOrderLifecycle.Status status, String payment) {
        return ReturnRequestService.eligible(payment, status, cod, DELIVERED, settled, now);
    }

    @Test
    void buyerReturnWindowIsInclusiveAtExactlySevenUtcDays() {
        assertTrue(eligible(DELIVERED, null, SellerOrder.CodStatus.PENDING,
                SellerOrderLifecycle.Status.DELIVERED, "COD"));
        assertTrue(eligible(DELIVERED.plus(Duration.ofDays(7)), null, SellerOrder.CodStatus.COLLECTED,
                SellerOrderLifecycle.Status.DELIVERED, "COD"));
        assertFalse(eligible(DELIVERED.plus(Duration.ofDays(7)).plusNanos(1), null,
                SellerOrder.CodStatus.PENDING, SellerOrderLifecycle.Status.DELIVERED, "COD"));
        assertFalse(eligible(DELIVERED.minusMillis(1), null,
                SellerOrder.CodStatus.PENDING, SellerOrderLifecycle.Status.DELIVERED, "COD"));
    }

    @Test
    void onlyDeliveredUnsettledCodStoreOrdersAreEligible() {
        assertFalse(eligible(DELIVERED.plusSeconds(3), DELIVERED.plusSeconds(1),
                SellerOrder.CodStatus.COLLECTED, SellerOrderLifecycle.Status.DELIVERED, "COD"));
        assertFalse(eligible(DELIVERED.plusSeconds(3), null,
                SellerOrder.CodStatus.VOID, SellerOrderLifecycle.Status.DELIVERED, "COD"));
        assertFalse(eligible(DELIVERED.plusSeconds(3), null,
                SellerOrder.CodStatus.PENDING, SellerOrderLifecycle.Status.DISPATCHED, "COD"));
        assertFalse(eligible(DELIVERED.plusSeconds(3), null,
                SellerOrder.CodStatus.PENDING, SellerOrderLifecycle.Status.DELIVERED, "CARD"));
        assertFalse(ReturnRequestService.eligible("COD", SellerOrderLifecycle.Status.DELIVERED,
                SellerOrder.CodStatus.PENDING, null, null, DELIVERED));
        assertFalse(ReturnRequestService.eligible("COD", SellerOrderLifecycle.Status.DELIVERED,
                SellerOrder.CodStatus.PENDING, DELIVERED, null, null));
    }

    @Test
    void keyValidationAndCanonicalFingerprintAreStableAndNotColliding() {
        ReturnRequestService.requireKey("my-key:2");
        assertThrows(ApiException.class, () -> ReturnRequestService.requireKey(null));
        assertThrows(ApiException.class, () -> ReturnRequestService.requireKey("  "));
        assertThrows(ApiException.class, () -> ReturnRequestService.requireKey(" leading"));
        assertThrows(ApiException.class, () -> ReturnRequestService.requireKey("a".repeat(129)));
        assertThrows(ApiException.class, () -> ReturnRequestService.requireKey("abc\nxyz"));

        UUID o = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID s1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID s2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String a = ReturnRequestService.fingerprint(o, s1, ReturnRequestService.normalizeReason("  Hàng lỗi  "));
        String b = ReturnRequestService.fingerprint(o, s1, "Hàng lỗi");
        assertEquals(a, b);
        assertTrue(a.matches("[0-9a-f]{64}"));
        assertNotEquals(a, ReturnRequestService.fingerprint(o, s2, "Hàng lỗi"));
        assertNotEquals(a, ReturnRequestService.fingerprint(o, s1, "Hàng không lỗi"));
    }

    @Test
    void reasonLengthUsesJavaUtf16UnitsAndAllowsMultiline() {
        assertEquals("Hàng bị lỗi\nCần đổi", ReturnRequestService.normalizeReason("  Hàng bị lỗi\nCần đổi  "));
        assertEquals(500, ReturnRequestService.normalizeReason("😀".repeat(250)).length());
        assertThrows(ApiException.class, () -> ReturnRequestService.normalizeReason("😀".repeat(251)));
        assertThrows(ApiException.class, () -> ReturnRequestService.normalizeReason("  "));
        assertThrows(ApiException.class, () -> ReturnRequestService.normalizeReason("a".repeat(501)));
    }
}
