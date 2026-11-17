package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Order-to-Seller only. Settlement of existing reservations remains available with new C2 admissions disabled. */
@RestController
@RequestMapping("/internal/vouchers/redemptions")
class VoucherRedemptionController {
    private final VoucherRedemptionService vouchers;
    private final String orderToken;

    VoucherRedemptionController(VoucherRedemptionService vouchers,
            @Value("${ORDER_SELLER_TOKEN:}") String orderToken) {
        this.vouchers = vouchers;
        this.orderToken = orderToken;
    }

    record PriceRequest(UUID storeId, String customerId, String code, Long merchandiseAmount) { }
    record AbortRequest(VoucherRedemptionService.Redeem redeem, VoucherRedemptionService.Quote quote) { }
    record ReleaseRequest(VoucherRedemptionService.ReleaseReason reason) { }

    @PostMapping("/quote")
    VoucherRedemptionService.Quote quote(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
            @RequestBody PriceRequest request) {
        InternalTokens.require(orderToken, token);
        if (request.storeId() == null || request.merchandiseAmount() == null)
            throw ApiException.badRequest("VALIDATION_FAILED", "Store and amount required");
        return vouchers.quote(request.storeId(), request.customerId(), request.code(), request.merchandiseAmount());
    }

    @PostMapping("/redeem")
    VoucherRedemptionService.Reservation redeem(
            @RequestHeader(value = InternalTokens.HEADER, required = false) String token,
            @RequestBody VoucherRedemptionService.Redeem request) {
        InternalTokens.require(orderToken, token);
        return vouchers.redeem(request);
    }

    @PostMapping("/abort")
    VoucherRedemptionService.Reservation abort(
            @RequestHeader(value = InternalTokens.HEADER, required = false) String token, @RequestBody AbortRequest request) {
        InternalTokens.require(orderToken, token);
        return vouchers.abortCheckout(request.redeem(), request.quote());
    }

    @GetMapping("/{storeId}/{sellerOrderId}")
    VoucherRedemptionService.Reservation status(
            @RequestHeader(value = InternalTokens.HEADER, required = false) String token,
            @PathVariable UUID storeId, @PathVariable UUID sellerOrderId) {
        InternalTokens.require(orderToken, token);
        return vouchers.status(storeId, sellerOrderId);
    }

    @PostMapping("/{storeId}/{sellerOrderId}/commit")
    VoucherRedemptionService.Reservation commit(
            @RequestHeader(value = InternalTokens.HEADER, required = false) String token,
            @PathVariable UUID storeId, @PathVariable UUID sellerOrderId) {
        InternalTokens.require(orderToken, token);
        return vouchers.commit(storeId, sellerOrderId);
    }

    @PostMapping("/{storeId}/{sellerOrderId}/release")
    VoucherRedemptionService.Reservation release(
            @RequestHeader(value = InternalTokens.HEADER, required = false) String token,
            @PathVariable UUID storeId, @PathVariable UUID sellerOrderId, @RequestBody ReleaseRequest request) {
        InternalTokens.require(orderToken, token);
        if (request.reason() == null || request.reason() == VoucherRedemptionService.ReleaseReason.CHECKOUT_FAILED)
            throw ApiException.badRequest("VALIDATION_FAILED", "Confirmed pre-dispatch order closure required");
        return vouchers.release(storeId, sellerOrderId, request.reason());
    }
}
