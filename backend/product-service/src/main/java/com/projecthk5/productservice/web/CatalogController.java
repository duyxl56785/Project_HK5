package com.projecthk5.productservice.web;

import com.projecthk5.productservice.catalog.CatalogSnapshotSource;
import com.projecthk5.productservice.category.Category;
import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.image.ImageStorage;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductEntity;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Public Catalog: only ACTIVE Products whose Store is OPEN and Seller is APPROVED right now.
 * Also serves the internal per-Product snapshot used by Cart and Order.
 */
@RestController
public class CatalogController {
    private final EntityManager em;
    private final SellerStoreClient stores;
    private final CategoryRepository categories;
    private final DetailsRepository details;
    private final ImageRepository images;
    private final ReviewRepository reviews;
    private final ImageStorage storage;
    private final DependencyClients dependencies;
    private final CatalogSnapshotSource snapshots;
    private final String cartToken;
    private final String orderToken;

    public CatalogController(EntityManager em, SellerStoreClient stores, CategoryRepository categories, DetailsRepository details,
                             ImageRepository images, ReviewRepository reviews, ImageStorage storage,
                             DependencyClients dependencies, CatalogSnapshotSource snapshots,
                             @Value("${CART_PRODUCT_TOKEN:}") String cartToken,
                             @Value("${ORDER_PRODUCT_TOKEN:}") String orderToken) {
        this.em = em;
        this.stores = stores;
        this.categories = categories;
        this.details = details;
        this.images = images;
        this.reviews = reviews;
        this.storage = storage;
        this.dependencies = dependencies;
        this.snapshots = snapshots;
        this.cartToken = cartToken;
        this.orderToken = orderToken;
    }

    public record StoreRef(UUID id, String name, String slug) {}
    public record Summary(UUID id, String name, String unitPrice, String currency, UUID categoryId, StoreRef store, String imageUrl) {}
    public record Page(List<Summary> items, long total, int page, int size) {}
    public record Rating(long count, BigDecimal average) {}
    public record Detail(UUID id, String name, String sku, String unitPrice, String currency, UUID categoryId, String description,
                         StoreRef store, List<String> images, long available, Rating rating) {}
    public record ReviewView(UUID id, int rating, String comment, java.time.Instant createdAt) {}
    public record NameRef(UUID id, String name, String imageUrl) {}
    /** Internal snapshot for Cart/Order; sellerId is the Seller profile id. */
    public record Snapshot(String productId, String sellerId, String storeId, String sku, String name, boolean available,
                           boolean sellerApproved, boolean storeOpen, boolean houseStore, BigDecimal unitPrice) {}

    @GetMapping("/api/catalog/products")
    @Transactional(readOnly = true)
    public Page search(@RequestParam(required = false) String q, @RequestParam(required = false) UUID categoryId,
                       @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                       @RequestParam(required = false) UUID storeId, @RequestParam(defaultValue = "NEWEST") String sort,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        if (page < 0 || size < 1 || size > 60 || (q != null && q.length() > 100))
            throw ApiException.badRequest("VALIDATION_FAILED", "Invalid search parameters");
        Map<UUID, SellerStoreClient.Facts> eligible = eligibleStores();
        Set<UUID> storeIds = storeId == null ? eligible.keySet() : eligible.containsKey(storeId) ? Set.of(storeId) : Set.of();
        if (storeIds.isEmpty()) return new Page(List.of(), 0, page, size);
        StringBuilder where = new StringBuilder(" from ProductEntity p where p.status = :status and p.storeId in :stores");
        Map<String, Object> params = new HashMap<>(Map.of("status", ProductStatus.ACTIVE, "stores", storeIds));
        if (q != null && !q.isBlank()) { where.append(" and lower(p.name) like :q"); params.put("q", "%" + q.strip().toLowerCase() + "%"); }
        if (categoryId != null) { where.append(" and p.categoryId in :cats"); params.put("cats", withDescendants(categoryId)); }
        if (minPrice != null) { where.append(" and p.unitPrice >= :min"); params.put("min", minPrice); }
        if (maxPrice != null) { where.append(" and p.unitPrice <= :max"); params.put("max", maxPrice); }
        String order = switch (sort) {
            case "PRICE_ASC" -> " order by p.unitPrice asc, p.id";
            case "PRICE_DESC" -> " order by p.unitPrice desc, p.id";
            case "NAME_ASC" -> " order by p.name asc, p.id";
            default -> " order by p.version desc, p.id";
        };
        TypedQuery<Long> count = em.createQuery("select count(p)" + where, Long.class);
        TypedQuery<ProductEntity> rows = em.createQuery("select p" + where + order, ProductEntity.class);
        params.forEach((k, v) -> { count.setParameter(k, v); rows.setParameter(k, v); });
        List<ProductEntity> found = rows.setFirstResult(page * size).setMaxResults(size).getResultList();
        return new Page(found.stream().map(p -> summary(p, eligible.get(p.getStoreId()))).toList(), count.getSingleResult(), page, size);
    }

    @GetMapping("/api/catalog/products/{id}")
    @Transactional(readOnly = true)
    public Detail detail(@PathVariable UUID id) {
        ProductEntity p = em.find(ProductEntity.class, id);
        SellerStoreClient.Facts store = p == null ? null : eligibleStores().get(p.getStoreId());
        if (p == null || p.getStatus() != ProductStatus.ACTIVE || store == null)
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found");
        DependencyClients.Balance balance = dependencies.balance(p.getInventorySku());
        Object[] summary = reviews.publicSummary(id).get(0);
        BigDecimal avg = summary[1] == null ? BigDecimal.ZERO : new BigDecimal(summary[1].toString()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new Detail(p.getId(), p.getName(), p.getInventorySku(), price(p), p.getCurrency(), p.getCategoryId(),
                details.findById(id).map(ProductExtras.Details::getDescription).orElse(""),
                new StoreRef(store.storeId(), store.name(), store.slug()),
                images.findByProductIdOrderByDisplayOrder(id).stream().map(i -> "/api/catalog/images/" + i.getId()).toList(),
                balance == null ? 0 : balance.availableQuantity(), new Rating((Long) summary[0], avg));
    }

    @GetMapping("/api/catalog/products/{id}/reviews")
    public List<ReviewView> reviews(@PathVariable UUID id) {
        return reviews.findByProductIdAndHiddenFalseOrderByCreatedAtDesc(id).stream()
                .map(r -> new ReviewView(r.getId(), r.getRating(), r.getComment(), r.getCreatedAt())).toList();
    }

    /** Names for order history/seller order screens; names are not secret, status is not disclosed. */
    @GetMapping("/api/catalog/products/names")
    public List<NameRef> names(@RequestParam List<UUID> ids) {
        if (ids.size() > 100) throw ApiException.badRequest("VALIDATION_FAILED", "At most 100 ids");
        return em.createQuery("select p from ProductEntity p where p.id in :ids", ProductEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .map(p -> new NameRef(p.getId(), p.getName(), firstImage(p.getId()))).toList();
    }

    @GetMapping("/api/catalog/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable UUID imageId) {
        ProductExtras.Image image = images.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "Image not found"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getMediaType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .header("X-Content-Type-Options", "nosniff").body(storage.read(image.getStorageKey()));
    }

    @GetMapping("/internal/catalog/products/{id}")
    public Snapshot snapshot(@RequestHeader(value = InternalTokens.HEADER, required = false) String token, @PathVariable UUID id) {
        if (!InternalTokens.matchesAny(token, cartToken, orderToken)) InternalTokens.require(cartToken, token);
        CatalogSnapshotSource.LookupResult result = snapshots.load(Set.of(id)).get(0);
        if (result.status() == CatalogSnapshotSource.LookupStatus.ABSENT)
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found");
        if (result.status() != CatalogSnapshotSource.LookupStatus.FOUND)
            throw ApiException.unavailable("CATALOG_UNAVAILABLE", "Current catalog facts unavailable");
        var facts = result.facts();
        var product = facts.product();
        return new Snapshot(product.id().toString(), facts.store().sellerId().toString(), product.storeId().toString(),
                product.sku(), product.name(), product.status() == ProductStatus.ACTIVE && facts.categoryExists(),
                facts.store().sellerApproved(), facts.store().storeOpen(), facts.store().houseStore(), product.unitPrice());
    }

    private Map<UUID, SellerStoreClient.Facts> eligibleStores() {
        return stores.publicStores().stream().collect(Collectors.toMap(SellerStoreClient.Facts::storeId, Function.identity()));
    }

    private Set<UUID> withDescendants(UUID root) {
        List<Category> all = categories.findAll();
        Set<UUID> result = new HashSet<>(Set.of(root));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Category c : all)
                if (c.getParent() != null && result.contains(c.getParent().getId()) && result.add(c.getId())) grew = true;
        }
        return result;
    }

    private Summary summary(ProductEntity p, SellerStoreClient.Facts store) {
        return new Summary(p.getId(), p.getName(), price(p), p.getCurrency(), p.getCategoryId(),
                new StoreRef(store.storeId(), store.name(), store.slug()), firstImage(p.getId()));
    }

    private String firstImage(UUID productId) {
        List<ProductExtras.Image> list = images.findByProductIdOrderByDisplayOrder(productId);
        return list.isEmpty() ? null : "/api/catalog/images/" + list.get(0).getId();
    }

    private static String price(ProductEntity p) {
        return p.getUnitPrice().toBigIntegerExact().toString();
    }
}
