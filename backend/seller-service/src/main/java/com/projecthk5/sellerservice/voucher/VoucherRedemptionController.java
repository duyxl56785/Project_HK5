package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.InternalTokens;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Voucher C2: order-service only (ORDER_SELLER_TOKEN). Customers never call these routes directly. */
@RestController
@RequestMapping("/internal/vouchers")
public class VoucherRedemptionController {
    private final VoucherRedemptionService service;
    private final String orderToken;

    public VoucherRedemptionController(VoucherRedemptionService service,
                                       @Value("${ORDER_SELLER_TOKEN:}") String orderToken) {
        this.service = service;
        this.orderToken = orderToken;
    }

    public record QuoteRequest(UUID storeId, String code, String customerId) {}
    public record RedeemRequest(String redemptionKey, UUID voucherId, UUID storeId, String code,
                                String customerId, UUID orderId) {}
    public record ReleaseRequest(String redemptionKey, UUID orderId) {}

    @PostMapping("/quote")
    public VoucherRedemptionService.Terms quote(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                @RequestBody QuoteRequest body) {
        InternalTokens.require(orderToken, token);
        return service.quote(body.storeId(), body.code(), body.customerId());
    }

    @PostMapping("/redeem")
    public VoucherRedemptionService.Redemption redeem(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                      @RequestBody RedeemRequest body) {
        InternalTokens.require(orderToken, token);
        return service.redeem(body.redemptionKey(), body.voucherId(), body.storeId(), body.code(),
                body.customerId(), body.orderId());
    }

    @PostMapping("/release")
    public VoucherRedemptionService.Redemption release(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                       @RequestBody ReleaseRequest body) {
        InternalTokens.require(orderToken, token);
        return service.release(body.redemptionKey(), body.orderId());
    }
}
