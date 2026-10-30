package com.projecthk5.orderservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CheckoutDtos {
    private CheckoutDtos() {}
    public record ExpectedItem(@NotBlank String productId, @Min(1) @Max(1000) int quantity) {}
    public record CheckoutRequest(@NotEmpty List<@Valid ExpectedItem> expectedItems, Long expectedRevision) {}
    public record ShippingAddress(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Pattern(regexp = "\\+?[0-9 .-]{8,20}") String phone,
                                  @NotBlank @Size(max = 500) String address) {}
    /** Wire body of POST /api/orders: COD is the only payment method in this release. */
    public record PlaceOrderRequest(@NotEmpty List<@Valid ExpectedItem> expectedItems, Long expectedRevision,
                                    @NotNull @Valid ShippingAddress shipping) {}
    public record CheckoutResult(UUID orderId, String status, BigDecimal total, int sellerOrderCount) {}
}
