package com.projecthk5.cartservice.api;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CartDtos {
    private CartDtos() {}
    public record AddItem(@NotBlank String productId, @Min(1) @Max(1000) int quantity) {}
    public record UpdateItem(@Min(1) @Max(1000) int quantity) {}
    public record Claim(@NotBlank @Size(max=128) String key, Long expectedRevision) {}
    public record Item(String productId, String storeId, String sellerId, int quantity, java.math.BigDecimal unitPriceSnapshot) {}
    public record StoreGroup(String storeId,String sellerId,List<Item> items) {}
    public record CartView(String customerId,long revision,boolean claimed,List<Item> items,List<StoreGroup> groups) {}
}
