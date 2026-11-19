package com.projecthk5.orderservice.ports;

import com.fasterxml.jackson.databind.JsonNode;
import com.projecthk5.orderservice.domain.VoucherSnapshot;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Component
public class VoucherHttpAdapter implements VoucherPort {
    private static final String ROOT = "/internal/vouchers/redemptions";
    private static final Set<String> REJECTIONS = Set.of("VOUCHER_CHANGED", "VOUCHER_NOT_FOUND",
            "VOUCHER_MIN_ORDER_AMOUNT", "VOUCHER_GROSS_TOO_LOW", "VOUCHER_ALREADY_USED", "VOUCHER_USAGE_LIMIT_REACHED",
            "STORE_NOT_FOUND", "VOUCHER_C2_DISABLED");
    private final RestClient seller;
    private final String token;

    public VoucherHttpAdapter(@Value("${SELLER_INTERNAL_URL:http://localhost:8103}") String url,
                              @Value("${ORDER_SELLER_TOKEN:}") String token) {
        this.seller = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    @Override
    public VoucherSnapshot quote(UUID store, String customer, String code, long merchandise) {
        VoucherSnapshot quote = post("/quote", Map.of("storeId", store, "customerId", customer,
                "code", code, "merchandiseAmount", merchandise), VoucherSnapshot.class);
        if (quote.merchandiseAmount() != merchandise || !quote.code().equals(code))
            throw unavailable();
        return quote;
    }

    @Override public Reservation redeem(Intent i) {
        return checked(i, post("/redeem", request(i), Reservation.class));
    }

    @Override public Optional<Reservation> status(Intent i) {
        configured();
        try {
            var reply = seller.get().uri(ROOT + path(i)).header(InternalTokens.HEADER, token)
                    .retrieve().body(Reservation.class);
            return Optional.of(checked(i, reply));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404 && "VOUCHER_REDEMPTION_NOT_FOUND".equals(code(error)))
                return Optional.empty();
            throw unavailable();
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    @Override public Reservation commit(Intent i) {
        return checked(i, post(path(i) + "/commit", null, Reservation.class));
    }

    @Override public Reservation abort(Intent i) {
        return checked(i, post("/abort", Map.of("redeem", request(i), "quote", i.quote()), Reservation.class));
    }

    @Override public Reservation release(Intent i, ReleaseReason reason) {
        return checked(i, post(path(i) + "/release", Map.of("reason", reason), Reservation.class));
    }

    private <T> T post(String path, Object payload, Class<T> response) {
        configured();
        try {
            var request = seller.post().uri(ROOT + path).header(InternalTokens.HEADER, token);
            if (payload != null) request.body(payload);
            T value = request.retrieve().body(response);
            if (value == null) throw unavailable();
            return value;
        } catch (RestClientResponseException error) {
            String code = code(error);
            if (error.getStatusCode().is4xxClientError() && REJECTIONS.contains(code)) throw new Rejected(code);
            throw unavailable();
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private static String code(RestClientResponseException error) {
        try {
            var body = error.getResponseBodyAs(JsonNode.class);
            return body == null ? "" : body.path("code").asText();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static Reservation checked(Intent i, Reservation reply) {
        if (reply == null || !i.sellerOrderId().equals(reply.sellerOrderId())
                || !i.storeId().equals(reply.storeId()) || !i.customerId().equals(reply.customerId())
                || !i.quote().equals(reply.quote()) || reply.state() == null)
            throw unavailable();
        return reply;
    }

    private static Map<String, Object> request(Intent i) {
        VoucherSnapshot quote = i.quote();
        return Map.of("sellerOrderId", i.sellerOrderId(), "storeId", i.storeId(), "customerId", i.customerId(),
                "voucherId", quote.voucherId(), "expectedVersion", quote.version(),
                "merchandiseAmount", quote.merchandiseAmount(), "expectedDiscount", quote.discountAmount());
    }

    private static String path(Intent i) { return "/" + i.storeId() + "/" + i.sellerOrderId(); }

    private void configured() { if (token.isBlank()) throw unavailable(); }

    private static ApiException unavailable() {
        return ApiException.unavailable("VOUCHER_SERVICE_UNAVAILABLE", "Voucher outcome could not be verified");
    }
}
