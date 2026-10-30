package com.projecthk5.productservice.web;

import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.http.ProductManagementResponse;
import com.projecthk5.productservice.product.persistence.ProductEntity;
import com.projecthk5.productservice.review.ReviewRules;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** ADMIN Product moderation, ADMIN review visibility, and CUSTOMER reviews of delivered purchases. */
@RestController
public class ModerationController {
    private final ProductApplicationService application;
    private final EntityManager em;
    private final SellerStoreClient stores;
    private final ReviewRepository reviews;
    private final DependencyClients dependencies;

    public ModerationController(ProductApplicationService application, EntityManager em, SellerStoreClient stores,
                                ReviewRepository reviews, DependencyClients dependencies) {
        this.application = application;
        this.em = em;
        this.stores = stores;
        this.reviews = reviews;
        this.dependencies = dependencies;
    }

    public record ModerationRequest(@NotBlank String expectedVersion, @Size(max = 500) String reason) {}
    public record AdminProduct(ProductManagementResponse product, String storeName, String reviewReason) {}
    public record ReviewRequest(@NotNull UUID orderId, @NotNull UUID productId, @Min(1) @Max(5) int rating,
                                @Size(max = 2000) String comment) {}
    public record ReviewView(UUID id, UUID orderId, UUID productId, String productName, int rating, String comment,
                             boolean hidden, Instant createdAt) {}

    @GetMapping("/api/admin/products")
    @Transactional(readOnly = true)
    public List<AdminProduct> list(@RequestParam(defaultValue = "PENDING_REVIEW") ProductStatus status) {
        List<ProductEntity> rows = em.createQuery("select p from ProductEntity p where p.status = :s order by p.name", ProductEntity.class)
                .setParameter("s", status).setMaxResults(200).getResultList();
        Set<UUID> storeIds = rows.stream().map(ProductEntity::getStoreId).collect(Collectors.toSet());
        Map<UUID, String> names = stores.facts(storeIds).stream()
                .collect(Collectors.toMap(SellerStoreClient.Facts::storeId, SellerStoreClient.Facts::name));
        return rows.stream().map(p -> new AdminProduct(new ProductManagementResponse(p.getId(), p.getStoreId(), p.getCategoryId(),
                p.getInventorySku(), p.getName(), p.getUnitPrice().toBigIntegerExact().toString(), p.getCurrency(), p.getStatus(),
                Long.toString(p.getVersion())), names.getOrDefault(p.getStoreId(), "?"), p.getReviewReason())).toList();
    }

    @PostMapping("/api/admin/products/{id}/{action}")
    public ProductManagementResponse moderate(@PathVariable UUID id, @PathVariable String action, @Valid @RequestBody ModerationRequest req) {
        String admin = Actor.current().id();
        long version = SellerProductController.version(req.expectedVersion());
        return ProductManagementResponse.from(switch (action) {
            case "approve" -> application.approve(admin, id, version);
            case "reject" -> application.reject(admin, id, version, req.reason());
            case "suspend" -> application.suspend(admin, id, version, req.reason());
            default -> throw ApiException.notFound("ROUTE_NOT_FOUND", "Unknown moderation action");
        });
    }

    @GetMapping("/api/admin/reviews")
    public List<ReviewView> allReviews() {
        return views(reviews.findTop200ByOrderByCreatedAtDesc());
    }

    @PostMapping("/api/admin/reviews/{id}/{action}")
    @Transactional
    public ReviewView setVisibility(@PathVariable UUID id, @PathVariable String action) {
        if (!Set.of("hide", "show").contains(action)) throw ApiException.notFound("ROUTE_NOT_FOUND", "Unknown action");
        ProductExtras.Review review = reviews.findById(id).orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Review not found"));
        ReviewRules.Rating next = ReviewRules.moderate(new ReviewRules.Rating(review.getRating(), review.isHidden()),
                "hide".equals(action), () -> Actor.require("ADMIN"));
        review.setHidden(next.hidden());
        return views(List.of(review)).get(0);
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView review(@Valid @RequestBody ReviewRequest req) {
        String buyer = Actor.require("CUSTOMER").id();
        DependencyClients.Purchase purchase = dependencies.purchase(req.orderId(), req.productId());
        try {
            ReviewRules.requireEligible(buyer, new ReviewRules.Purchase(purchase.customerId(), purchase.delivered(),
                    purchase.containsProduct()), req.rating(), reviews.existsByOrderIdAndProductId(req.orderId(), req.productId()));
        } catch (SecurityException notBuyer) {
            throw ApiException.forbidden("Only the buyer may review this purchase");
        } catch (IllegalStateException ineligible) {
            throw ApiException.conflict("REVIEW_NOT_ALLOWED", ineligible.getMessage());
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().strip();
        try {
            return views(List.of(reviews.saveAndFlush(new ProductExtras.Review(req.orderId(), req.productId(), buyer, req.rating(), comment)))).get(0);
        } catch (DataIntegrityViolationException duplicate) {
            // Unique (order_id, product_id) settles concurrent duplicates.
            throw ApiException.conflict("REVIEW_NOT_ALLOWED", "Product already reviewed in this order");
        }
    }

    @GetMapping("/api/reviews/mine")
    public List<ReviewView> mine() {
        return views(reviews.findByCustomerId(Actor.current().id()));
    }

    private List<ReviewView> views(List<ProductExtras.Review> list) {
        Set<UUID> ids = list.stream().map(ProductExtras.Review::getProductId).collect(Collectors.toSet());
        Map<UUID, String> names = ids.isEmpty() ? Map.of() : em.createQuery("select p from ProductEntity p where p.id in :ids", ProductEntity.class)
                .setParameter("ids", ids).getResultList().stream().collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
        return list.stream().map(r -> new ReviewView(r.getId(), r.getOrderId(), r.getProductId(), names.get(r.getProductId()),
                r.getRating(), r.getComment(), r.isHidden(), r.getCreatedAt())).toList();
    }
}
