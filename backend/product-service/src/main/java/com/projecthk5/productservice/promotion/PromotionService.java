package com.projecthk5.productservice.promotion;

import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.productservice.web.SellerStoreClient;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromotionService {
    private static final ZoneId ZONE = PromotionPriceRules.VIETNAM_ZONE;
    private final JdbcTemplate db;
    private final SellerStoreClient stores;

    public PromotionService(JdbcTemplate db, SellerStoreClient stores) {
        this.db = db;
        this.stores = stores;
    }

    public record Campaign(UUID id, String name, String kind, String status, UUID storeId,
                           Instant startsAt, Instant endsAt, Instant submittedAt, long version) {}
    public record Registration(UUID productId, String productName, UUID storeId, String storeName,
                               BigDecimal baseUnitPrice, BigDecimal promotionalUnitPrice,
                               String status, String reason, long version) {}

    private static final String REGISTRATION_SELECT = """
            SELECT r.*,p.name AS product_name,p.store_id,p.unit_price AS base_unit_price
            FROM promotion_campaign_products r JOIN products p ON p.id=r.product_id
            """;
    private record Product(UUID id, UUID store, String status, BigDecimal base) {}

    private static Instant time(ResultSet row, String name) throws SQLException {
        OffsetDateTime value = row.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Campaign campaign(ResultSet row, int index) throws SQLException {
        return new Campaign(row.getObject("id", UUID.class), row.getString("name"), row.getString("kind"),
                row.getString("status"), row.getObject("store_id", UUID.class), time(row, "starts_at"),
                time(row, "ends_at"), time(row, "submitted_at"), row.getLong("version"));
    }

    private static Registration registration(ResultSet row, int index) throws SQLException {
        return new Registration(row.getObject("product_id", UUID.class), row.getString("product_name"),
                row.getObject("store_id", UUID.class), null, row.getBigDecimal("base_unit_price"),
                row.getBigDecimal("promotional_unit_price"),
                row.getString("status"), row.getString("reason"), row.getLong("version"));
    }

    private Campaign campaign(UUID id, boolean lock) {
        return db.query("SELECT * FROM promotion_campaigns WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                PromotionService::campaign, id).stream().findFirst().orElseThrow(PromotionService::missing);
    }

    private Product lockProduct(UUID id) {
        return db.query("SELECT id,store_id,status,unit_price FROM products WHERE id=? FOR UPDATE",
                (row, index) -> new Product(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                        row.getString(3), row.getBigDecimal(4)), id)
                .stream().findFirst().orElseThrow(PromotionService::missing);
    }

    private List<Registration> registrations(UUID id) {
        return db.query(REGISTRATION_SELECT + " WHERE r.campaign_id=? ORDER BY r.product_id",
                PromotionService::registration, id);
    }

    @Transactional(readOnly = true)
    public List<Campaign> list(boolean admin) {
        Actor actor = admin ? Actor.require("ADMIN") : Actor.current();
        if (admin) return db.query("SELECT * FROM promotion_campaigns ORDER BY starts_at,id", PromotionService::campaign);
        UUID store = stores.managedBy(actor.id()).storeId();
        if (actor.has("SELLER")) {
            return db.query("SELECT * FROM promotion_campaigns WHERE store_id IS NULL OR store_id=? ORDER BY starts_at,id",
                    PromotionService::campaign, store);
        }
        return db.query("""
                SELECT DISTINCT c.* FROM promotion_campaigns c
                JOIN promotion_campaign_products r ON r.campaign_id=c.id
                JOIN products p ON p.id=r.product_id
                WHERE p.store_id=? AND r.status IN ('PENDING','APPROVED') AND c.status<>'CANCELLED'
                  AND c.ends_at>clock_timestamp()
                ORDER BY c.starts_at,c.id
                """, PromotionService::campaign, store);
    }

    @Transactional(readOnly = true)
    public List<Registration> listProducts(UUID id, boolean admin) {
        Actor actor = admin ? Actor.require("ADMIN") : Actor.current();
        Campaign c = campaign(id, false);
        if (admin) {
            List<Registration> rows = registrations(id);
            var storeIds = rows.stream().map(Registration::storeId).collect(java.util.stream.Collectors.toSet());
            var names = stores.facts(storeIds).stream().collect(java.util.stream.Collectors.toMap(
                    SellerStoreClient.Facts::storeId, SellerStoreClient.Facts::name));
            return rows.stream().map(r -> new Registration(r.productId(), r.productName(), r.storeId(),
                    names.get(r.storeId()), r.baseUnitPrice(), r.promotionalUnitPrice(),
                    r.status(), r.reason(), r.version())).toList();
        }
        UUID store = stores.managedBy(actor.id()).storeId();
        if (c.storeId() != null && !store.equals(c.storeId())) throw missing();
        List<Registration> owned = db.query(REGISTRATION_SELECT
                + " WHERE r.campaign_id=? AND p.store_id=? ORDER BY r.product_id",
                PromotionService::registration, id, store);
        if (!actor.has("SELLER")) {
            ongoing(c, Instant.now());
            owned = owned.stream().filter(r -> List.of("PENDING", "APPROVED").contains(r.status())).toList();
            if (owned.isEmpty()) throw missing();
        }
        return owned;
    }

    public Campaign create(String name, PromotionPriceRules.Kind kind, LocalDate day, Instant start, Instant end) {
        boolean clearance = kind == PromotionPriceRules.Kind.SELLER_CLEARANCE;
        Actor actor = Actor.require(clearance ? "SELLER" : "ADMIN");
        UUID store = clearance ? stores.currentManagementStore(actor.id()) : null;
        if (clearance) requireStoreEligible(store);
        if (name.isBlank() || name.length()>120) throw invalid("Invalid campaign name");
        if (clearance) {
            var span = Duration.between(start, end);
            if (span.compareTo(Duration.ofHours(1)) < 0 || span.compareTo(Duration.ofDays(30)) > 0)
                throw invalid("Clearance duration must be between one hour and 30 days");
        } else {
            if (kind == PromotionPriceRules.Kind.FLASH_SALE && day.getDayOfWeek() != DayOfWeek.SATURDAY)
                throw invalid("Flash Sale date must be Saturday");
            start = day.atTime(kind == PromotionPriceRules.Kind.FLASH_SALE ? 22 : 0, 0).atZone(ZONE).toInstant();
            end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        }
        if (!Instant.now().isBefore(start)) throw invalid("Campaign must start in the future");
        UUID id = UUID.randomUUID();
        try {
            db.update("""
                    INSERT INTO promotion_campaigns(id,name,kind,store_id,flash_date,super_sale_date,starts_at,ends_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, id, name, kind.name(), store, kind == PromotionPriceRules.Kind.FLASH_SALE ? day : null,
                    kind == PromotionPriceRules.Kind.SUPER_SALE ? day : null, utc(start), utc(end));
        } catch (DuplicateKeyException duplicate) {
            throw ApiException.conflict("PROMOTION_SCHEDULE_CONFLICT", "A campaign already occupies the schedule");
        }
        audit(id, null, actor, "CREATE", null, "DRAFT", 0, null);
        return campaign(id, false);
    }

    public Campaign reschedule(UUID id, LocalDate day, long expected) {
        Actor actor = Actor.require("ADMIN");
        Campaign c = campaign(id, true);
        version(c.version(), expected);
        Instant now = Instant.now();
        beforeStart(c, now);
        if (!c.status().equals("DRAFT") || c.kind().equals("SELLER_CLEARANCE")) throw state();
        boolean flash = c.kind().equals("FLASH_SALE");
        if (flash && day.getDayOfWeek() != DayOfWeek.SATURDAY) throw invalid("Flash Sale date must be Saturday");
        Instant start = day.atTime(flash ? 22 : 0, 0).atZone(ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        if (!now.isBefore(start)) throw invalid("Campaign must start in the future");
        try {
            db.update("""
                    UPDATE promotion_campaigns SET flash_date=?,super_sale_date=?,starts_at=?,ends_at=?,version=version+1
                    WHERE id=?
                    """, flash ? day : null, flash ? null : day, utc(start), utc(end), id);
        } catch (DuplicateKeyException duplicate) {
            throw ApiException.conflict("PROMOTION_SCHEDULE_CONFLICT", "A campaign already occupies the schedule");
        }
        audit(id, null, actor, "RESCHEDULE", c.status(), c.status(), expected+1, null);
        return campaign(id, false);
    }

    public Campaign action(UUID id, String action, long expected, String reason) {
        Actor actor = Actor.require(action.equals("submit") ? "SELLER" : "ADMIN");
        Campaign c = campaign(id, true);
        if (action.equals("submit") && (!"SELLER_CLEARANCE".equals(c.kind())
                || !stores.currentManagementStore(actor.id()).equals(c.storeId()))) throw missing();
        version(c.version(), expected);
        Instant now = Instant.now();
        ongoing(c, now);
        String next = switch (action) {
            case "submit" -> {
                beforeStart(c, now);
                requireStoreEligible(c.storeId());
                if (!c.status().equals("DRAFT") || c.submittedAt()!=null) throw state();
                yield "DRAFT";
            }
            case "approve", "resume" -> {
                boolean approve = action.equals("approve");
                if (approve) beforeStart(c, now);
                if (!c.status().equals(approve ? "DRAFT" : "PAUSED")
                        || "SELLER_CLEARANCE".equals(c.kind()) && c.submittedAt()==null) throw state();
                validateApprovedProducts(c, actor);
                yield "APPROVED";
            }
            case "pause" -> {
                if (!c.status().equals("APPROVED")) throw state();
                yield "PAUSED";
            }
            case "cancel" -> {
                beforeStart(c, now);
                yield "CANCELLED";
            }
            default -> throw invalid("Unknown campaign action");
        };
        db.update("""
                UPDATE promotion_campaigns SET status=?, version=version+1,
                    submitted_at=COALESCE(?,submitted_at) WHERE id=?
                """, next, action.equals("submit") ? utc(now) : null, id);
        audit(id, null, actor, action, c.status(), next, expected+1, reason);
        return campaign(id, false);
    }

    private void validateApprovedProducts(Campaign c, Actor actor) {
        for (Registration r : registrations(c.id())) {
            if (!r.status().equals("APPROVED")) continue;
            Product product = lockProduct(r.productId());
            checkApproval(c, product, r.promotionalUnitPrice(), actor);
        }
    }

    public Registration register(UUID id, UUID productId, BigDecimal price, long expectedCampaign, Long expectedOffer) {
        Actor actor = Actor.require("SELLER");
        Campaign c = campaign(id, true);
        Product p = lockProduct(productId);
        var facts = stores.readStore(p.store());
        if (!actor.id().equals(facts.ownerReference()) || c.storeId()!=null && !c.storeId().equals(p.store()))
            throw missing();
        version(c.version(), expectedCampaign);
        beforeStart(c, Instant.now());
        eligible(p, facts, price);
        Registration old = findRegistration(id, productId);
        if (old == null) {
            if (expectedOffer != null) throw state();
            db.update("INSERT INTO promotion_campaign_products(campaign_id,product_id,promotional_unit_price) VALUES (?,?,?)",
                    id, productId, price);
            audit(id, productId, actor, "REGISTER", null, "PENDING", 0, null);
        } else {
            if (expectedOffer == null || !old.status().equals("WITHDRAWN")) throw state();
            version(old.version(), expectedOffer);
            db.update("""
                    UPDATE promotion_campaign_products SET status='PENDING', promotional_unit_price=?,reason=NULL,
                    version=version+1 WHERE campaign_id=? AND product_id=?
                    """, price, id, productId);
            audit(id, productId, actor, "REGISTER", old.status(), "PENDING", old.version()+1, null);
        }
        return findRegistration(id, productId);
    }

    public Registration decide(UUID id, UUID productId, String action, long expected, String reason) {
        Actor actor = action.equals("withdraw") ? Actor.current() : Actor.require("ADMIN");
        Campaign c = campaign(id, true);
        Product p = lockProduct(productId);
        Registration r = findRegistration(id, productId);
        if (r == null) throw missing();
        if (action.equals("withdraw") && !actor.id().equals(stores.readStore(p.store()).ownerReference())) throw missing();
        version(r.version(), expected);
        Instant now = Instant.now();
        ongoing(c, now);
        String next;
        if (action.equals("withdraw")) {
            if (!List.of("PENDING", "APPROVED").contains(r.status())) throw state();
            next = "WITHDRAWN";
        } else {
            beforeStart(c, now);
            if (!r.status().equals("PENDING")) throw state();
            next = switch (action) {
                case "approve" -> {
                    if (!c.status().equals("APPROVED")) throw state();
                    checkApproval(c, p, r.promotionalUnitPrice(), actor);
                    yield "APPROVED";
                }
                case "reject" -> {
                    if (reason == null || reason.isBlank() || reason.length()>500) throw invalid("Rejection reason required");
                    yield "REJECTED";
                }
                default -> throw invalid("Unknown decision");
            };
        }
        db.update("UPDATE promotion_campaign_products SET status=?,reason=?,version=version+1 WHERE campaign_id=? AND product_id=?",
                next, reason, id, productId);
        audit(id, productId, actor, action, r.status(), next, expected+1, reason);
        return findRegistration(id, productId);
    }

    private Registration findRegistration(UUID id, UUID productId) {
        return db.query(REGISTRATION_SELECT + " WHERE r.campaign_id=? AND r.product_id=?",
                PromotionService::registration, id, productId).stream().findFirst().orElse(null);
    }

    private void checkApproval(Campaign c, Product p, BigDecimal price, Actor actor) {
        var facts = stores.readStore(p.store());
        if (actor.id().equals(facts.ownerReference())) throw ApiException.forbidden("Admin cannot approve own Store");
        eligible(p, facts, price);
        if (Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM promotion_campaign_products r JOIN promotion_campaigns c ON c.id=r.campaign_id
                    WHERE r.product_id=? AND r.campaign_id<>? AND r.status='APPROVED' AND c.status='APPROVED'
                    AND c.starts_at < ? AND c.ends_at > ?)
                """, Boolean.class, p.id(), c.id(), utc(c.endsAt()), utc(c.startsAt()))))
            throw ApiException.conflict("PROMOTION_OVERLAP", "Another approved offer overlaps this campaign");
    }

    private void requireStoreEligible(UUID store) {
        var facts = stores.readStore(store);
        if (!facts.storeOpen() || !facts.sellerApproved()) throw state();
    }

    private static void eligible(Product p, com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess facts,
                                 BigDecimal price) {
        if (!p.status().equals("ACTIVE") || !facts.storeOpen() || !facts.sellerApproved()) throw state();
        ProductPrice.validate(price, "VND");
        if (price.compareTo(p.base()) >= 0) throw ApiException.conflict(
                "PROMOTION_PRICE_CONFLICT", "Promotion price must be lower than the base price");
    }

    private void audit(UUID campaign, UUID product, Actor actor, String action, String before, String after,
                       long version, String reason) {
        db.update("""
                INSERT INTO promotion_campaign_audit(campaign_id,product_id,actor,action,from_status,to_status,version,reason)
                VALUES (?,?,?,?,?,?,?,?)
                """, campaign, product, actor.id(), action, before, after, version, reason);
    }

    private static void version(long current, long expected) {
        if (current!=expected) throw ApiException.conflict("PROMOTION_VERSION_CONFLICT", "Promotion version changed");
    }

    private static void ongoing(Campaign c, Instant now) {
        if (c.status().equals("CANCELLED") || !now.isBefore(c.endsAt())) throw state();
    }

    private static void beforeStart(Campaign c, Instant now) {
        ongoing(c, now);
        if (!now.isBefore(c.startsAt())) throw state();
    }

    public record Promotion(UUID campaignId, String kind, Instant endsAt) {}
    public record Quote(BigDecimal unitPrice, Promotion promotion) {}

    @Transactional(readOnly = true)
    public Quote quote(UUID product, UUID store, UUID seller, BigDecimal base, boolean eligible, Instant now) {
        if (!eligible) return new Quote(base, null);
        var offers = db.query("""
                SELECT c.id,c.kind,c.starts_at,c.ends_at,c.super_sale_date,r.promotional_unit_price
                FROM promotion_campaign_products r JOIN promotion_campaigns c ON c.id=r.campaign_id
                WHERE r.product_id=? AND r.status='APPROVED' AND c.status='APPROVED'
                  AND c.starts_at<=? AND c.ends_at>?
                """, (row, index) -> new PromotionPriceRules.Offer(row.getObject("id", UUID.class), product,
                store, seller, PromotionPriceRules.Kind.valueOf(row.getString("kind")),
                PromotionPriceRules.CampaignStatus.APPROVED, row.getBigDecimal("promotional_unit_price"),
                time(row,"starts_at"), time(row,"ends_at"), row.getObject("super_sale_date",LocalDate.class)),
                product, utc(now), utc(now));
        var quote = PromotionPriceRules.resolve(
                new PromotionPriceRules.ProductFacts(product, store, seller, base, "VND", true, true, true), now, offers);
        if (!quote.discounted()) return new Quote(base, null);
        var offer = offers.stream().filter(x -> x.campaignId().equals(quote.campaignId())).findFirst().orElseThrow();
        return new Quote(quote.effectiveUnitPrice(), new Promotion(offer.campaignId(), offer.kind().name(), offer.endsAt()));
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("VALIDATION_FAILED", message);
    }

    private static ApiException missing() {
        return ApiException.notFound("PROMOTION_NOT_FOUND", "Promotion or Product was not found");
    }

    private static ApiException state() {
        return ApiException.conflict("PROMOTION_INVALID_STATE", "Promotion action is not allowed in this state");
    }
}
