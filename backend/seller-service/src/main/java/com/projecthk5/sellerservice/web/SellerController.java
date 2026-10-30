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
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.StoreEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Transactional
public class SellerController {
    private final SellerPersistenceService sellers;
    private final SellerStoreJson json;
    private final SellerOutboxRepository outbox;
    private final EntityManager em;
    private final ObjectMapper mapper = new ObjectMapper();

    public SellerController(SellerPersistenceService sellers, SellerStoreJson json, SellerOutboxRepository outbox, EntityManager em) {
        this.sellers = sellers;
        this.json = json;
        this.outbox = outbox;
        this.em = em;
    }

    public record SellerView(SellerStoreResponses.SellerPrivate seller, StoreSummary store) {}
    public record StoreSummary(UUID id, String name, String slug, boolean open) {}
    public record AdminSellerRow(UUID id, String ownerReference, SellerStatus status, String reviewReason, String version,
                                 StoreSummary store) {}

    /** Any signed-in CUSTOMER may apply; owner comes from the JWT subject only. */
    @PostMapping("/api/sellers")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerStoreResponses.SellerPrivate register(@RequestBody(required = false) String body) {
        json.registration(body == null ? "{}" : body);
        Actor actor = Actor.require("CUSTOMER");
        PersistedSeller seller = sellers.register(new SellerIdentity(actor.id()));
        return SellerStoreResponses.sellerPrivate(seller);
    }

    @GetMapping("/api/sellers/me")
    public SellerView me() {
        PersistedSeller seller = sellers.findOwn(new SellerIdentity(Actor.current().id()));
        return new SellerView(SellerStoreResponses.sellerPrivate(seller), storeOf(seller.id()));
    }

    @PostMapping("/api/sellers/me/resubmit")
    public SellerStoreResponses.SellerPrivate resubmit(@RequestBody String body) {
        SellerIdentity me = new SellerIdentity(Actor.current().id());
        PersistedSeller own = sellers.findOwn(me);
        return SellerStoreResponses.sellerPrivate(sellers.resubmit(own.id(), json.version(body), me));
    }

    @GetMapping("/api/admin/sellers")
    public List<AdminSellerRow> list(@RequestParam(required = false) SellerStatus status) {
        List<SellerProfile> rows = em.createQuery("select s from SellerProfile s where (:status is null or s.status = :status)"
                        + " order by s.status, s.id", SellerProfile.class)
                .setParameter("status", status).setMaxResults(500).getResultList();
        return rows.stream().map(s -> new AdminSellerRow(s.getId(), s.getOwnerReference(), s.getStatus(), s.getReviewReason(),
                Long.toString(s.getVersion()), storeOf(s.getId()))).toList();
    }

    @PostMapping("/api/admin/sellers/{id}/{action}")
    public SellerStoreResponses.SellerPrivate moderate(@PathVariable UUID id, @PathVariable String action, @RequestBody String body) {
        SellerIdentity admin = new SellerIdentity(Actor.current().id());
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
        enqueue(SellerOutbox.Kind.ROLE_SYNC, result.id(), Map.of("accountId", owner, "granted", status == SellerStatus.APPROVED));
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
