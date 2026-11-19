package com.projecthk5.orderservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CheckoutDtos {
    private CheckoutDtos() {}
    public record ExpectedItem(@NotBlank String productId, @Min(1) @Max(1000) int quantity) {}
    public record VoucherCode(@NotBlank @Size(max = 36) String storeId,
                              @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4,20}") String code) {}
    public record VoucherSelection(@NotBlank @Size(max = 36) String storeId,
                                   @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4,20}") String code,
                                   @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String quoteFingerprint) {}
    public record VoucherQuoteRequest(@NotEmpty List<@Valid ExpectedItem> expectedItems,
                                      @NotNull @PositiveOrZero Long expectedRevision,
                                      @NotEmpty @Size(max = 100) List<@NotNull @Valid VoucherCode> vouchers) {}
    public record CheckoutRequest(@NotEmpty List<@Valid ExpectedItem> expectedItems, Long expectedRevision,
                                  @Size(max = 100) List<@Valid VoucherSelection> vouchers) {
        public CheckoutRequest(List<ExpectedItem> expectedItems, Long expectedRevision) {
            this(expectedItems, expectedRevision, List.of());
        }
        public CheckoutRequest { vouchers = vouchers == null ? List.of() : List.copyOf(vouchers); }
    }
    public record ShippingAddress(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Pattern(regexp = "\\+?[0-9 .-]{8,20}") String phone,
                                  @NotBlank @Size(max = 500) String address) {}
    /** Wire body of POST /api/orders: COD is the only payment method in this release. */
    public record PlaceOrderRequest(@NotEmpty List<@Valid ExpectedItem> expectedItems, Long expectedRevision,
                                    @NotNull @Valid ShippingAddress shipping,
                                    @Size(max = 100) List<@NotNull @Valid VoucherSelection> vouchers) {
        public PlaceOrderRequest(List<ExpectedItem> expectedItems, Long expectedRevision, ShippingAddress shipping) {
            this(expectedItems, expectedRevision, shipping, List.of());
        }
    }
    public record StorePrice(String storeId, String voucherCode, String quoteFingerprint,
                             String merchandiseAmount, String discountAmount, String gross, String platformFee) {}
    public record CheckoutQuote(long revision, String total, List<StorePrice> stores) {}
    public record CheckoutResult(UUID orderId, String status, BigDecimal total, int sellerOrderCount,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            List<StorePrice> prices) {
        public CheckoutResult(UUID orderId, String status, BigDecimal total, int sellerOrderCount) {
            this(orderId, status, total, sellerOrderCount, null);
        }
    }
}
