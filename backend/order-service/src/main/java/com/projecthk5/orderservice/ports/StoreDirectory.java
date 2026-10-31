package com.projecthk5.orderservice.ports;

import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Order->Seller pair: current Store ownership is read per request, never trusted from the client. */
@Component
public class StoreDirectory {
    private final RestClient seller;
    private final String token;

    public StoreDirectory(@Value("${SELLER_INTERNAL_URL:http://localhost:8103}") String url,
                          @Value("${ORDER_SELLER_TOKEN:}") String token) {
        this.seller = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    public record Store(String storeId, String sellerId, String ownerReference, String name, String slug,
                        boolean storeOpen, boolean sellerApproved, boolean houseStore) {}

    /** The Store managed by this verified account, or 403 when the account has none. */
    public Store managedBy(String ownerReference) {
        configured();
        try {
            Store store = seller.get().uri("/internal/stores/managed/{owner}", ownerReference)
                    .header(InternalTokens.HEADER, token).retrieve().body(Store.class);
            if (store == null) throw unavailable();
            return store;
        } catch (HttpClientErrorException.NotFound none) {
            throw ApiException.forbidden("This account does not manage a Store");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public List<Store> facts(Set<String> storeIds) {
        if (storeIds.isEmpty()) return List.of();
        configured();
        try {
            Store[] body = seller.post().uri("/internal/stores/facts").header(InternalTokens.HEADER, token)
                    .body(Map.of("storeIds", storeIds)).retrieve().body(Store[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private void configured() {
        if (token.isBlank()) throw ApiException.unavailable("STORE_DIRECTORY_NOT_CONFIGURED", "Seller internal contract not configured");
    }

    private static ApiException unavailable() {
        return ApiException.unavailable("STORE_DIRECTORY_UNAVAILABLE", "Store facts unavailable");
    }
}
