package com.projecthk5.orderservice.ports;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CartHttpAdapter implements CartPort {
    private final RestClient client;
    private final String secret;
    public CartHttpAdapter(RestClient.Builder builder,@Value("${app.cart.internal-url:http://localhost:8106}") String url,
                           @Value("${app.cart.internal-token:}") String secret) {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client=builder.requestFactory(factory).baseUrl(url).build(); this.secret=secret;
    }
    private void configured() {
        if (secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cart internal contract not configured");
    }
    @Override public CartSnapshot claim(String bearer,String key,Long revision) {
        configured();
        try { return client.post().uri("/internal/cart/claim")
            .header("Authorization",bearer).header("X-Internal-Token",secret)
            .body(claimBody(key,revision))
            .retrieve().body(CartSnapshot.class);
        } catch (HttpClientErrorException.Conflict refused) {
            throw new CartPort.Rejected("Cart changed or is reserved by another checkout");
        } catch (RestClientException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cart claim unavailable",ex); }
    }
    private Map<String,Object> claimBody(String key,Long revision) {
        if (revision == null) return Map.of("key",key);
        return Map.of("key",key,"expectedRevision",revision);
    }
    @Override public void release(String bearer,String key) { call("release",bearer,key); }
    @Override public void finalizeCart(String bearer,String key) { call("finalize",bearer,key); }
    private void call(String operation,String bearer,String key) {
        configured();
        try { client.post().uri("/internal/cart/"+operation)
            .header("Authorization",bearer).header("X-Internal-Token",secret)
            .body(Map.of("key",key)).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cart "+operation+" unavailable",ex);}
    }
}
