package com.projecthk5.productservice.promotion;

import com.projecthk5.productservice.product.ProductPrice;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure, read-only pricing rules for future product promotions. Not connected to Catalog,
 * Cart or Checkout. Its input MUST come from an authoritative, already-approved campaign
 * projection after Store/Seller/Product eligibility and ownership checks.
 */
public final class PromotionPriceRules {
    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime FLASH_START = LocalTime.of(22, 0);

    private PromotionPriceRules() { }

    public enum Kind { FLASH_SALE, SUPER_SALE, SELLER_CLEARANCE }

    public enum CampaignStatus { DRAFT, APPROVED, PAUSED, CANCELLED }

    /** Facts supplied by the caller. Authorization and freshness are the provider's responsibility. */
    public record ProductFacts(UUID productId, UUID storeId, UUID sellerId, BigDecimal baseUnitPrice,
                               String currency, boolean productActive, boolean storeOpen, boolean sellerApproved) {
        public ProductFacts {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(storeId, "storeId");
            Objects.requireNonNull(sellerId, "sellerId");
            ProductPrice.validate(baseUnitPrice, currency);
        }
    }

    /** Product-specific campaign projection supplied by a trusted server-side caller. */
    public record Offer(UUID campaignId, UUID productId, UUID storeId, UUID sellerId, Kind kind,
                        CampaignStatus status, BigDecimal promotionalUnitPrice,
                        Instant startsAt, Instant endsAt, LocalDate superSaleDate) {
        public Offer {
            Objects.requireNonNull(campaignId, "campaignId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(storeId, "storeId");
            Objects.requireNonNull(sellerId, "sellerId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            if ((kind == Kind.SUPER_SALE) != (superSaleDate != null)) {
                throw new IllegalArgumentException("Only Super Sale requires an Admin-selected date");
            }
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            ProductPrice.validate(promotionalUnitPrice, "VND");
            if (!startsAt.isBefore(endsAt)) {
                throw new IllegalArgumentException("Promotion window must have positive duration");
            }
        }

        public boolean appliesAt(Instant now) {
            Objects.requireNonNull(now, "now");
            if (status != CampaignStatus.APPROVED || now.isBefore(startsAt) || !now.isBefore(endsAt)) {
                return false;
            }
            return switch (kind) {
                case FLASH_SALE -> isWeeklyFlashWindow(now);
                case SUPER_SALE -> isChosenSuperSaleDay(now, superSaleDate);
                case SELLER_CLEARANCE -> true;
            };
        }
    }

    /** A quote is NOT an authorization to charge this amount at Checkout. */
    public record PriceQuote(BigDecimal baseUnitPrice, BigDecimal effectiveUnitPrice,
                             UUID campaignId, Kind kind, Instant evaluatedAt) {
        public boolean discounted() { return campaignId != null; }
    }

    /**
     * Resolve at one authoritative server timestamp. Overlapping active promotions are a
     * configuration error: the Admin must choose the winner before making them effective.
     * Never silently stack discounts, choose the lowest price, or mutate Product.unitPrice.
     */
    public static PriceQuote resolve(ProductFacts product, Instant now, Collection<Offer> offers) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(offers, "offers");
        BigDecimal baseUnitPrice = product.baseUnitPrice();
        boolean eligible = product.productActive() && product.storeOpen() && product.sellerApproved();

        Offer winner = null;
        for (Offer offer : offers) {
            Objects.requireNonNull(offer, "Null promotion offer");
            if (!product.productId().equals(offer.productId()) || !product.storeId().equals(offer.storeId())
                    || !product.sellerId().equals(offer.sellerId())) {
                throw new IllegalArgumentException("Promotion does not match Product, Store and Seller");
            }
            if (!eligible || !offer.appliesAt(now)) continue;
            if (offer.promotionalUnitPrice().compareTo(baseUnitPrice) >= 0) {
                throw new IllegalArgumentException("Promotion price must be lower than the normal price");
            }
            if (winner != null) {
                throw new ConflictingPromotionsException("More than one active promotion for Product");
            }
            winner = offer;
        }
        return winner == null
                ? new PriceQuote(baseUnitPrice, baseUnitPrice, null, null, now)
                : new PriceQuote(baseUnitPrice, winner.promotionalUnitPrice(), winner.campaignId(), winner.kind(), now);
    }

    /** Saturday 22:00 inclusive to Sunday 00:00 exclusive, Vietnam local time. */
    public static boolean isWeeklyFlashWindow(Instant now) {
        ZonedDateTime local = Objects.requireNonNull(now, "now").atZone(VIETNAM_ZONE);
        return local.getDayOfWeek() == DayOfWeek.SATURDAY && !local.toLocalTime().isBefore(FLASH_START);
    }

    /** Super Sale date is chosen by Admin; the engine never hard-codes a day of month. */
    public static boolean isChosenSuperSaleDay(Instant now, LocalDate adminChosenDate) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(adminChosenDate, "adminChosenDate");
        return now.atZone(VIETNAM_ZONE).toLocalDate().equals(adminChosenDate);
    }

    /** Raw interval overlap for validation. Schedule and status are evaluated separately. */
    public static boolean windowsOverlap(Offer a, Offer b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (!a.productId().equals(b.productId()) || !a.storeId().equals(b.storeId())) return false;
        return a.startsAt().isBefore(b.endsAt()) && b.startsAt().isBefore(a.endsAt());
    }

    public static final class ConflictingPromotionsException extends IllegalStateException {
        public ConflictingPromotionsException(String message) { super(message); }
    }
}
