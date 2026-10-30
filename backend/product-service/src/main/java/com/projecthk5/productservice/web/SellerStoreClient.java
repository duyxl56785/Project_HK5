package com.projecthk5.productservice.web;

import com.projecthk5.productservice.catalog.CurrentStoreFactsSource;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.security.Actor;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Product->Seller pair adapter: every call reads current Store/Seller facts (no cache), and the
 * actor's role always comes from the verified JWT in this request.
 */
@Component
public class SellerStoreClient implements ProductAccessPolicy, CurrentStoreFactsSource {
    private final RestClient seller;
    private final String token;

    public SellerStoreClient(@Value("${SELLER_INTERNAL_URL:http://localhost:8103}") String url,
                             @Value("${PRODUCT_SELLER_TOKEN:}") String token) {
        this.seller = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    public record Facts(UUID storeId, UUID sellerId, String ownerReference, String name, String slug,
                        boolean storeOpen, boolean sellerApproved, boolean houseStore) {
        public boolean eligible() { return storeOpen && sellerApproved; }
    }

    public List<Facts> facts(Set<UUID> storeIds) {
        if (storeIds.isEmpty()) return List.of();
        try {
            Facts[] body = seller.post().uri("/internal/stores/facts").header(InternalTokens.HEADER, token)
                    .body(Map.of("storeIds", storeIds)).retrieve().body(Facts[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    /** Stores that are OPEN with an APPROVED Seller, read now. */
    public List<Facts> publicStores() {
        try {
            Facts[] body = seller.get().uri("/internal/stores/eligible").header(InternalTokens.HEADER, token)
                    .retrieve().body(Facts[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public Facts managedBy(String ownerReference) {
        try {
            return seller.get().uri("/internal/stores/managed/{owner}", ownerReference)
                    .header(InternalTokens.HEADER, token).retrieve().body(Facts.class);
        } catch (HttpClientErrorException.NotFound none) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Seller has no Store yet");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    @Override
    public UUID currentManagementStore(String actorReference) {
        requireActor(actorReference, "SELLER");
        return managedBy(actorReference).storeId();
    }

    @Override
    public StoreAccess requireSellerManagement(String actorReference, UUID storeId) {
        requireActor(actorReference, "SELLER");
        return readStore(storeId);
    }

    @Override
    public StoreAccess requireModeration(String actorReference, UUID storeId) {
        requireActor(actorReference, "ADMIN");
        return readStore(storeId);
    }

    @Override
    public StoreAccess readStore(UUID storeId) {
        Facts f = facts(Set.of(storeId)).stream().filter(x -> x.storeId().equals(storeId)).findFirst()
                .orElseThrow(SellerStoreClient::unavailable);
        return new StoreAccess(f.storeId(), f.sellerId(), f.ownerReference(), f.sellerApproved(), f.storeOpen(), f.houseStore());
    }

    @Override
    public List<StoreFacts> loadCurrent(Set<UUID> storeIds) {
        return facts(storeIds).stream().map(f -> new StoreFacts(f.storeId(), f.sellerId(), f.ownerReference(),
                f.sellerApproved(), f.storeOpen(), f.houseStore(), true)).toList();
    }

    private static void requireActor(String actorReference, String role) {
        Actor actor = Actor.current();
        if (!actor.id().equals(actorReference) || !actor.has(role))
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, role + " role required");
    }

    private static ProductRuleException unavailable() {
        return new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Store facts unavailable");
    }
}
