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
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final BigDecimal BASE = new BigDecimal("100000");
    private static final Instant SATURDAY_2200 = Instant.parse("2026-11-14T15:00:00Z");
    private static final Instant SATURDAY_215959 = Instant.parse("2026-11-14T14:59:59Z");
    private static final Instant SUNDAY_0000 = Instant.parse("2026-11-14T17:00:00Z");

    private PromotionPriceRules.Offer offer(PromotionPriceRules.Kind kind, String price,
                                             Instant from, Instant until) {
        return new PromotionPriceRules.Offer(UUID.randomUUID(), PRODUCT, STORE, SELLER, kind,
                PromotionPriceRules.CampaignStatus.APPROVED, new BigDecimal(price), from, until,
                kind == PromotionPriceRules.Kind.SUPER_SALE ? LocalDate.of(2026, 11, 14) : null);
    }

    private PromotionPriceRules.ProductFacts facts(BigDecimal base) {
        return new PromotionPriceRules.ProductFacts(PRODUCT, STORE, SELLER, base, "VND", true, true, true);
    }

    private PromotionPriceRules.PriceQuote at(Instant now, PromotionPriceRules.Offer... offers) {
        return PromotionPriceRules.resolve(facts(BASE), now, List.of(offers));
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
        assertThrows(PromotionPriceRules.ConflictingPromotionsException.class,
                () -> at(SATURDAY_2200, superSale, flash));
    }

    @Test void priceAboveCurrentBaseDoesNotMaskAnUnresolvedOverlap() {
        var first = offer(PromotionPriceRules.Kind.FLASH_SALE, "60000", SATURDAY_2200, SUNDAY_0000);
        var second = offer(PromotionPriceRules.Kind.SUPER_SALE, "70000", SATURDAY_2200, SUNDAY_0000);
        var current = facts(new BigDecimal("65000"));
        for (var offers : List.of(List.of(first, second), List.of(second, first))) {
            assertThrows(PromotionPriceRules.ConflictingPromotionsException.class,
                    () -> PromotionPriceRules.resolve(current, SATURDAY_2200, offers));
        }
    }

    @Test void mismatchedOfferIsRejectedEvenWhenOtherOffersConflict() {
        var first = offer(PromotionPriceRules.Kind.FLASH_SALE, "80000", SATURDAY_2200, SUNDAY_0000);
        var second = offer(PromotionPriceRules.Kind.SUPER_SALE, "70000", SATURDAY_2200, SUNDAY_0000);
        var wrongSeller = new PromotionPriceRules.Offer(UUID.randomUUID(), PRODUCT, STORE, UUID.randomUUID(),
                PromotionPriceRules.Kind.SELLER_CLEARANCE, PromotionPriceRules.CampaignStatus.APPROVED,
                new BigDecimal("60000"), SATURDAY_2200, SUNDAY_0000, null);
        for (var offers : List.of(List.of(first, second, wrongSeller), List.of(wrongSeller, second, first))) {
            assertThrows(IllegalArgumentException.class,
                    () -> PromotionPriceRules.resolve(facts(BASE), SATURDAY_2200, offers));
        }
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
                new PromotionPriceRules.ProductFacts(PRODUCT, UUID.randomUUID(), SELLER, BASE, "VND", true, true, true),
                SATURDAY_2200, List.of(offer)));
        assertThrows(IllegalArgumentException.class, () -> PromotionPriceRules.resolve(
                new PromotionPriceRules.ProductFacts(UUID.randomUUID(), STORE, SELLER, BASE, "VND", true, true, true),
                SATURDAY_2200, List.of(offer)));
        assertThrows(IllegalArgumentException.class, () -> PromotionPriceRules.resolve(
                new PromotionPriceRules.ProductFacts(PRODUCT, STORE, UUID.randomUUID(), BASE, "VND", true, true, true),
                SATURDAY_2200, List.of(offer)));
    }

    @Test void invalidIntervalsFailBeforeAnyPricing() {
        assertThrows(IllegalArgumentException.class, () -> offer(
                PromotionPriceRules.Kind.SUPER_SALE, "80000", SATURDAY_2200, SATURDAY_2200));
        assertThrows(IllegalArgumentException.class, () -> offer(
                PromotionPriceRules.Kind.SUPER_SALE, "80000", SUNDAY_0000, SATURDAY_2200));
    }

    @Test void superSaleQuoteUsesSelectedVietnamDateAndCampaignWindow() {
        var start = Instant.parse("2026-11-17T17:00:00Z");
        var end = Instant.parse("2026-11-18T17:00:00Z");
        var offer = new PromotionPriceRules.Offer(UUID.randomUUID(), PRODUCT, STORE, SELLER,
                PromotionPriceRules.Kind.SUPER_SALE, PromotionPriceRules.CampaignStatus.APPROVED,
                new BigDecimal("80000"), start.minusSeconds(1), end.plusSeconds(1), LocalDate.of(2026, 11, 18));
        assertFalse(at(start.minusNanos(1), offer).discounted());
        assertTrue(at(start, offer).discounted());
        assertTrue(at(end.minusNanos(1), offer).discounted());
        assertFalse(at(end, offer).discounted());
        assertThrows(IllegalArgumentException.class, () -> new PromotionPriceRules.Offer(
                UUID.randomUUID(), PRODUCT, STORE, SELLER, PromotionPriceRules.Kind.SUPER_SALE,
                PromotionPriceRules.CampaignStatus.APPROVED, new BigDecimal("80000"), start, end, null));
    }

    @Test void ineligibleProductStoreOrSellerDoesNotProduceDiscount() {
        var offer = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "80000", SATURDAY_2200, SUNDAY_0000);
        for (var product : List.of(
                new PromotionPriceRules.ProductFacts(PRODUCT, STORE, SELLER, BASE, "VND", false, true, true),
                new PromotionPriceRules.ProductFacts(PRODUCT, STORE, SELLER, BASE, "VND", true, false, true),
                new PromotionPriceRules.ProductFacts(PRODUCT, STORE, SELLER, BASE, "VND", true, true, false))) {
            assertEquals(BASE, PromotionPriceRules.resolve(product, SATURDAY_2200, List.of(offer)).effectiveUnitPrice());
        }
    }

    @Test void onlyApprovedOffersParticipateInPricingAndConflicts() {
        var active = offer(PromotionPriceRules.Kind.FLASH_SALE, "80000", SATURDAY_2200, SUNDAY_0000);
        for (var status : List.of(PromotionPriceRules.CampaignStatus.DRAFT,
                PromotionPriceRules.CampaignStatus.PAUSED, PromotionPriceRules.CampaignStatus.CANCELLED)) {
            var inactive = new PromotionPriceRules.Offer(UUID.randomUUID(), PRODUCT, STORE, SELLER,
                    PromotionPriceRules.Kind.SELLER_CLEARANCE, status, new BigDecimal("70000"),
                    SATURDAY_2200, SUNDAY_0000, null);
            assertFalse(at(SATURDAY_2200, inactive).discounted());
            assertEquals(active.campaignId(), at(SATURDAY_2200, inactive, active).campaignId());
        }
    }

    @Test void requoteValidatesAgainstCurrentBaseWithoutMutatingPreviousQuote() {
        var offer = offer(PromotionPriceRules.Kind.SELLER_CLEARANCE, "80000", SATURDAY_2200, SUNDAY_0000);
        var original = at(SATURDAY_2200, offer);
        var updated = facts(new BigDecimal("90000"));
        assertSame(updated.baseUnitPrice(),
                PromotionPriceRules.resolve(updated, SATURDAY_2200, List.of(offer)).baseUnitPrice());
        assertSame(BASE, original.baseUnitPrice());
        for (var invalid : List.of("80000", "79999")) {
            assertThrows(IllegalArgumentException.class, () -> PromotionPriceRules.resolve(
                    facts(new BigDecimal(invalid)), SATURDAY_2200, List.of(offer)));
        }
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
