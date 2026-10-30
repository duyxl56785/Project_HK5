package com.projecthk5.sellerservice.web;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.sellerservice.http.SellerStoreJson;
import com.projecthk5.sellerservice.http.SellerStoreResponses;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import com.projecthk5.sellerservice.store.StoreEntity;
import com.projecthk5.sellerservice.store.StorePersistenceService;
import com.projecthk5.sellerservice.store.StoreStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class StoreController {
    private final StorePersistenceService stores;
    private final SellerStoreJson json;
    private final EntityManager em;
    private final String productToken;
    private final String orderToken;

    public StoreController(StorePersistenceService stores, SellerStoreJson json, EntityManager em,
                           @Value("${PRODUCT_SELLER_TOKEN:}") String productToken,
                           @Value("${ORDER_SELLER_TOKEN:}") String orderToken) {
        this.stores = stores;
        this.json = json;
        this.em = em;
        this.productToken = productToken;
        this.orderToken = orderToken;
    }

    public record PublicStore(UUID id, String name, String slug) {}
    /** Current authoritative facts for trusted internal callers (Product, Order). */
    public record StoreFacts(UUID storeId, UUID sellerId, String ownerReference, String name, String slug,
                             boolean storeOpen, boolean sellerApproved, boolean houseStore) {}
    public record FactsRequest(@NotEmpty @Size(max = 100) Set<UUID> storeIds) {}

    @PostMapping("/api/stores")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerStoreResponses.StoreOwner create(@RequestBody String body) {
        return SellerStoreResponses.storeOwner(stores.create(me(), json.createStore(body)));
    }

    @GetMapping("/api/stores/me")
    public SellerStoreResponses.StoreOwner mine() {
        return SellerStoreResponses.storeOwner(stores.findOwn(me()));
    }

    @PutMapping("/api/stores/{id}")
    public SellerStoreResponses.StoreOwner update(@PathVariable UUID id, @RequestBody String body) {
        return SellerStoreResponses.storeOwner(stores.revise(id, me(), json.updateStore(body)));
    }

    @PatchMapping("/api/stores/{id}/open")
    public SellerStoreResponses.StoreOwner setOpen(@PathVariable UUID id, @RequestBody String body) {
        return SellerStoreResponses.storeOwner(stores.setOpen(id, me(), json.setOpen(body)));
    }

    @GetMapping("/api/stores")
    @Transactional(readOnly = true)
    public List<PublicStore> publicStores() {
        return visible().stream().map(f -> new PublicStore(f.storeId(), f.name(), f.slug())).toList();
    }

    @GetMapping("/api/stores/by-slug/{slug}")
    @Transactional(readOnly = true)
    public PublicStore bySlug(@PathVariable String slug) {
        return visible().stream().filter(f -> f.slug().equals(slug)).findFirst()
                .map(f -> new PublicStore(f.storeId(), f.name(), f.slug()))
                .orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "Store not found"));
    }

    @PostMapping("/internal/stores/facts")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<StoreFacts> facts(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                  @Valid @RequestBody FactsRequest request) {
        requirePair(token);
        return query("where st.id in :ids", request.storeIds());
    }

    @GetMapping("/internal/stores/eligible")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<StoreFacts> eligible(@RequestHeader(value = InternalTokens.HEADER, required = false) String token) {
        requirePair(token);
        return visible();
    }

    @GetMapping("/internal/stores/managed/{ownerReference}")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public StoreFacts managed(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                              @PathVariable String ownerReference) {
        requirePair(token);
        return query("where s.ownerReference = :ids", ownerReference).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("STORE_NOT_FOUND", "No Store for this account"));
    }

    private void requirePair(String token) {
        if (!InternalTokens.matchesAny(token, productToken, orderToken)) InternalTokens.require(productToken, token);
    }

    private List<StoreFacts> visible() {
        return query("where st.status = :ids and s.status = com.projecthk5.sellerservice.seller.domain.SellerStatus.APPROVED",
                StoreStatus.OPEN);
    }

    private List<StoreFacts> query(String where, Object param) {
        List<Object[]> rows = em.createQuery("select st, s from StoreEntity st join SellerProfile s on s.id = st.seller.id "
                + where + " order by st.name", Object[].class).setParameter("ids", param).getResultList();
        return rows.stream().map(r -> {
            StoreEntity st = (StoreEntity) r[0];
            SellerProfile s = (SellerProfile) r[1];
            return new StoreFacts(st.getId(), s.getId(), s.getOwnerReference(), st.getName(), st.getSlug(),
                    st.getStatus() == StoreStatus.OPEN, s.getStatus() == SellerStatus.APPROVED, false);
        }).toList();
    }

    private static SellerIdentity me() {
        return new SellerIdentity(Actor.current().id());
    }
}
