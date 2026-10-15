package com.projecthk5.productservice.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Internal rules only. Purchase facts must come from a trusted Order adapter, never a request body. */
public final class ReviewRules {
    private ReviewRules() { }

    public record Purchase(String buyerId, boolean delivered, boolean containsProduct) {
        public Purchase {
            requireIdentity(buyerId);
        }
    }

    public record Rating(int stars, boolean hidden) {
        public Rating {
            if (stars < 1 || stars > 5) {
                throw new IllegalArgumentException("Rating must be between 1 and 5");
            }
        }
    }

    /** Production adapter must verify JWT and Admin permission or throw; no default implementation. */
    @FunctionalInterface
    public interface AdminAccess {
        void requireAdmin();
    }

    public static Rating moderate(Rating current, boolean hidden, AdminAccess access) {
        Objects.requireNonNull(access, "Admin access policy is required").requireAdmin();
        Objects.requireNonNull(current, "Stored rating is required");
        return new Rating(current.stars(), hidden);
    }

    public static void requireEligible(String verifiedBuyerId, Purchase purchase,
            int stars, boolean alreadyReviewed) {
        requireIdentity(verifiedBuyerId);
        Objects.requireNonNull(purchase, "Trusted purchase is required");
        new Rating(stars, false);
        if (!verifiedBuyerId.equals(purchase.buyerId())) {
            throw new SecurityException("Only the buyer may review this purchase");
        }
        if (!purchase.delivered() || !purchase.containsProduct()) {
            throw new IllegalStateException("Review requires a purchased product in a delivered order");
        }
        if (alreadyReviewed) {
            throw new IllegalStateException("Product already reviewed in this order");
        }
    }

    /** Empty means no public ratings; hidden ratings affect neither sum nor count. */
    public static Optional<BigDecimal> publicAverage(Collection<Rating> ratings) {
        Objects.requireNonNull(ratings, "Ratings are required");
        long sum = 0;
        long count = 0;
        for (Rating rating : ratings) {
            Objects.requireNonNull(rating, "Rating is required");
            if (!rating.hidden()) {
                sum += rating.stars();
                count++;
            }
        }
        return count == 0 ? Optional.empty() : Optional.of(BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }

    private static void requireIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Verified buyer identity is required");
        }
    }
}
