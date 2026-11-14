package com.projecthk5.productservice.promotion;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionPriceRulesTest {
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BigDecimal BASE = new BigDecimal("100000");
    private static final Instant SATURDAY_2200 = Instant.parse("2026-11-14T15:00:00Z");
    private static final Instant SATURDAY_215959 = Instant.parse("2026-11-14T14:59:59Z");
    private static final Instant SUNDAY_0000 = Instant.parse("2026-11-14T17:00:00Z");

    private PromotionPriceRules.ApprovedOffer offer(PromotionPriceRules.Kind kind, String price,
                                                    Instant from, Instant until) {
        return new PromotionPriceRules.ApprovedOffer(UUID.randomUUID(), PRODUCT, STORE, kind,
                new BigDecimal(price), from, until);
    }

    private PromotionPriceRules.PriceQuote at(Instant now, PromotionPriceRules.ApprovedOffer... offers) {
        return PromotionPriceRules.resolve(PRODUCT, STORE, BASE, "VND", now, List.of(offers));
    }

    @Test void fs01TimeBoundariesUseVietnamServerTime() {
        var flash = offer(PromotionPriceRules.Kind.FLASH_SALE, "80000",
                SATURDAY_215959.minusSeconds(3600), SUNDAY_0000.plusSeconds(3600));
        assertFalse(PromotionPriceRules.isWeeklyFlashWindow(SATURDAY_215959));
        assertFalse(at(SATURDAY_215959, flash).discounted());
        assertTrue(PromotionPriceRules.isWeeklyFlashWindow(SATURDAY_2200));
        assertEquals(new BigDecimal("80000"), at(SATURDAY_2200, flash).effectiveUnitPrice());
        assertTrue(PromotionPriceRules.isWeeklyFlashWindow(SUNDAY_0000.minusSeconds(1)));
        assertFalse(PromotionPriceRules.isWeeklyFlashWindow(SUNDAY_0000));
        assertFalse(at(SUNDAY_0000, flash).discounted());
    }

    @Test void arbitrarySuperSaleDayIsAdminChosenNotHardcoded15th() {
        LocalDate chosen = LocalDate.of(2026, 11, 18);
        assertTrue(PromotionPriceRules.isChosenSuperSaleDay(Instant.parse("2026-11-17T17:00:00Z"), chosen));
        assertFalse(PromotionPriceRules.isChosenSuperSaleDay(Instant.parse("2026-11-18T17:00:00Z"), chosen));
    }

    @Test void sellerClearanceHasHalfOpenWindowAndProvenance() {
        var clearance = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "70000",
                SATURDAY_2200, SUNDAY_0000);
        assertFalse(at(SATURDAY_215959, clearance).discounted());
        var quote = at(SATURDAY_2200, clearance);
        assertTrue(quote.discounted());
        assertEquals(clearance.campaignId(), quote.campaignId());
        assertEquals(PromotionPriceRules.Kind.SELLER_CLEARANCE, quote.kind());
        assertEquals(0, quote.effectiveUnitPrice().compareTo(new BigDecimal("70000")));
        assertFalse(at(SUNDAY_0000, clearance).discounted());
    }

    @Test void fs02TwoConcurrentEligibleCampaignsFailClosedWithoutStacking() {
        var flash = offer(PromotionPriceRules.Kind.FLASH_SALE, "80000",
                SATURDAY_2200, SUNDAY_0000);
        var superSale = offer(PromotionPriceRules.Kind.SUPER_SALE, "65000",
                SATURDAY_2200, SUNDAY_0000);
        assertTrue(PromotionPriceRules.windowsOverlap(flash, superSale));
        assertThrows(PromotionPriceRules.ConflictingPromotionsException.class,
                () -> at(SATURDAY_2200, flash, superSale));
    }

    @Test void nonOverlappingCampaignsDoNotConflict() {
        var first = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "80000",
                SATURDAY_2200.minusSeconds(500), SATURDAY_2200);
        var second = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "70000",
                SATURDAY_2200, SUNDAY_0000);
        assertFalse(PromotionPriceRules.windowsOverlap(first, second));
        assertEquals(new BigDecimal("70000"), at(SATURDAY_2200, first, second).effectiveUnitPrice());
    }

    @Test void fs04RejectsFractionalZeroAndOutOfRangePromotionPrices() {
        for (String invalid : List.of("0", "0.50", "1000000001", "-1")) {
            assertThrows(RuntimeException.class, () -> offer(PromotionPriceRules.Kind.FLASH_SALE,
                    invalid, SATURDAY_2200, SUNDAY_0000), invalid);
        }
        var sameAsNormal = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "100000",
                SATURDAY_2200, SUNDAY_0000);
        var aboveNormal = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "100001",
                SATURDAY_2200, SUNDAY_0000);
        assertThrows(IllegalArgumentException.class, () -> at(SATURDAY_2200, sameAsNormal));
        assertThrows(IllegalArgumentException.class, () -> at(SATURDAY_2200, aboveNormal));
    }

    @Test void wrongStoreOrProductNeverReceivesAnOffer() {
        var offer = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "80000",
                SATURDAY_2200, SUNDAY_0000);
        assertThrows(IllegalArgumentException.class, () -> PromotionPriceRules.resolve(
                PRODUCT, UUID.randomUUID(), BASE, "VND", SATURDAY_2200, List.of(offer)));
        assertThrows(IllegalArgumentException.class, () -> PromotionPriceRules.resolve(
                UUID.randomUUID(), STORE, BASE, "VND", SATURDAY_2200, List.of(offer)));
    }

    @Test void invalidIntervalsFailBeforeAnyPricing() {
        assertThrows(IllegalArgumentException.class, () -> offer(
                PromotionPriceRules.Kind.SUPER_SALE, "80000", SATURDAY_2200, SATURDAY_2200));
        assertThrows(IllegalArgumentException.class, () -> offer(
                PromotionPriceRules.Kind.SUPER_SALE, "80000", SUNDAY_0000, SATURDAY_2200));
    }

    @Test void quoteDoesNotChangeProductBasePrice() {
        var offer = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "80000",
                SATURDAY_2200, SUNDAY_0000);
        var quote = at(SATURDAY_2200, offer);
        assertSame(BASE, quote.baseUnitPrice());
        assertEquals(BASE, at(SUNDAY_0000, offer).effectiveUnitPrice());
        assertFalse(at(SUNDAY_0000, offer).discounted());
    }
}
