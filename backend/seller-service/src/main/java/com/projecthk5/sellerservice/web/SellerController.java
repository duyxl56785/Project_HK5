package com.projecthk5.sellerservice.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.sellerservice.http.SellerStoreJson;
import com.projecthk5.sellerservice.http.SellerStoreResponses;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import com.projecthk5.sellerservice.seller.service.SellerInputs;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.StoreEntity;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Transactional
public class SellerController {
    private final SellerPersistenceService sellers;
    private final SellerStoreJson json;
    private final SellerOutboxRepository outbox;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SellerController(SellerPersistenceService sellers, SellerStoreJson json, SellerOutboxRepository outbox, EntityManager em,
                            JdbcTemplate jdbc) {
        this.sellers = sellers;
        this.json = json;
        this.outbox = outbox;
        this.em = em;
        this.jdbc = jdbc;
    }

    public record SellerView(SellerStoreResponses.SellerPrivate seller, Contact contact, StoreSummary store) {}
    public record Contact(String representativeName, String phone, String pickupAddress) {}
    public record StoreSummary(UUID id, String name, String slug, boolean open) {}
    public record AdminSellerRow(UUID id, String ownerReference, SellerStatus status, String reviewReason, String version,
                                 Contact contact, StoreSummary store) {}
    public record HistoryRow(String fromStatus, String toStatus, String reason, String reviewedBy, OffsetDateTime at) {}

    /** Any signed-in CUSTOMER may apply; owner comes from the JWT subject only. */
    @PostMapping("/api/sellers")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerStoreResponses.SellerPrivate register(@RequestBody String body) {
        SellerInputs.Contact contact = json.registration(body);
        SellerIdentity me = new SellerIdentity(Actor.require("CUSTOMER").id());
        PersistedSeller created = sellers.register(me);
        SellerStoreResponses.SellerPrivate saved = saveContact(created, contact, me);
        recordStatus(created.id(), null, SellerStatus.PENDING_REVIEW, null, null);
        return saved;
    }

    @GetMapping("/api/sellers/me")
    public SellerView me() {
        PersistedSeller seller = sellers.findOwn(new SellerIdentity(Actor.current().id()));
        return new SellerView(SellerStoreResponses.sellerPrivate(seller), contactOf(em.find(SellerProfile.class, seller.id())),
                storeOf(seller.id()));
    }

    @PostMapping("/api/sellers/me/resubmit")
    public SellerStoreResponses.SellerPrivate resubmit(@RequestBody String body) {
        SellerIdentity me = new SellerIdentity(Actor.current().id());
        PersistedSeller own = sellers.findOwn(me);
        SellerInputs.Resubmission input = json.resubmission(body);
        SellerStoreResponses.SellerPrivate saved = saveContact(sellers.resubmit(own.id(), input.expectedVersion(), me), input.contact(), me);
        recordStatus(own.id(), own.application().status(), SellerStatus.PENDING_REVIEW, null, null);
        return saved;
    }

    @GetMapping("/api/admin/sellers")
    public List<AdminSellerRow> list(@RequestParam(required = false) SellerStatus status) {
        List<SellerProfile> rows = em.createQuery("select s from SellerProfile s where (:status is null or s.status = :status)"
                        + " order by s.status, s.id", SellerProfile.class)
                .setParameter("status", status).setMaxResults(500).getResultList();
        return rows.stream().map(s -> new AdminSellerRow(s.getId(), s.getOwnerReference(), s.getStatus(), s.getReviewReason(),
                Long.toString(s.getVersion()), contactOf(s), storeOf(s.getId()))).toList();
    }

    @GetMapping("/api/admin/sellers/{id}/history")
    public List<HistoryRow> history(@PathVariable UUID id) {
        return jdbc.query("select from_status, to_status, reason, reviewed_by, created_at from seller_status_history"
                        + " where seller_id = ? order by created_at, id",
                (row, n) -> new HistoryRow(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getObject(5, OffsetDateTime.class)), id);
    }

    @PostMapping("/api/admin/sellers/{id}/{action}")
    public SellerStoreResponses.SellerPrivate moderate(@PathVariable UUID id, @PathVariable String action, @RequestBody String body) {
        SellerIdentity admin = new SellerIdentity(Actor.current().id());
        SellerProfile before = em.find(SellerProfile.class, id);
        SellerStatus from = before == null ? null : before.getStatus();
        PersistedSeller result = switch (action) {
            case "approve" -> sellers.approve(id, json.version(body), admin);
            case "reject" -> sellers.reject(id, json.moderation(body), admin);
            case "suspend" -> sellers.suspend(id, json.moderation(body), admin);
            case "reinstate" -> sellers.reinstate(id, json.version(body), admin);
            default -> throw ApiException.notFound("ROUTE_NOT_FOUND", "Unknown moderation action");
        };
        // Same transaction as the status change: the relay delivers these after commit, with retries.
        SellerStatus status = result.application().status();
        String owner = result.application().owner().reference();
        recordStatus(id, from, status, result.application().reviewReason(), admin.reference());
        enqueue(SellerOutbox.Kind.ROLE_SYNC, result.id(), Map.of("accountId", owner, "granted", status == SellerStatus.APPROVED,
                "revision", result.version()));
        enqueue(SellerOutbox.Kind.NOTIFY, result.id(), Map.of("recipient", owner, "type", "SELLER_" + status.name(),
                "title", switch (status) {
                    case APPROVED -> "Hồ sơ người bán đã được duyệt";
                    case REJECTED -> "Hồ sơ người bán bị từ chối";
                    case SUSPENDED -> "Tài khoản người bán bị tạm ngừng";
                    default -> "Hồ sơ người bán đã cập nhật";
                },
                "message", result.application().reviewReason() == null ? "Trạng thái hồ sơ người bán đã được cập nhật."
                        : "Lý do: " + result.application().reviewReason(),
                "link", "/seller"));
        return SellerStoreResponses.sellerPrivate(result);
    }

    /** Flushes so the returned version is the one the next write must send. */
    private SellerStoreResponses.SellerPrivate saveContact(PersistedSeller seller, SellerInputs.Contact contact, SellerIdentity me) {
        em.find(SellerProfile.class, seller.id()).updateContact(contact.representativeName(), contact.phone(), contact.pickupAddress());
        em.flush();
        return SellerStoreResponses.sellerPrivate(sellers.findOwn(me));
    }

    private void recordStatus(UUID sellerId, SellerStatus from, SellerStatus to, String reason, String reviewedBy) {
        em.flush();
        jdbc.update("insert into seller_status_history (seller_id, from_status, to_status, reason, reviewed_by) values (?, ?, ?, ?, ?)",
                sellerId, from == null ? null : from.name(), to.name(), reason, reviewedBy);
    }

    private static Contact contactOf(SellerProfile seller) {
        return new Contact(seller.getRepresentativeName(), seller.getPhone(), seller.getPickupAddress());
    }

    private void enqueue(SellerOutbox.Kind kind, UUID sellerId, Map<String, Object> payload) {
        try {
            outbox.save(new SellerOutbox(kind, sellerId, mapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private StoreSummary storeOf(UUID sellerId) {
        return em.createQuery("select st from StoreEntity st where st.seller.id = :id", StoreEntity.class)
                .setParameter("id", sellerId).getResultList().stream().findFirst()
                .map(st -> new StoreSummary(st.getId(), st.getName(), st.getSlug(),
                        st.getStatus() == com.projecthk5.sellerservice.store.StoreStatus.OPEN))
                .orElse(null);
    }
}
