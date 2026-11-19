package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.entity.Order;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;

public final class RequestFingerprint {
    private RequestFingerprint() {}
    public static String compute(CheckoutRequest request) {
        return compute(request, null);
    }
    public static String compute(CheckoutRequest request, Order.Shipping shipping) {
        StringBuilder canonical = new StringBuilder("revision=").append(request.expectedRevision()).append(";");
        request.expectedItems().stream().sorted(Comparator.comparing(ExpectedItem::productId))
            .forEach(it -> canonical.append(it.productId().length()).append(':').append(it.productId())
                .append(':').append(it.quantity()).append(';'));
        if (!request.vouchers().isEmpty()) {
            canonical.append("voucher-c2;");
            request.vouchers().stream().sorted(Comparator.comparing(VoucherSelection::storeId))
                    .forEach(voucher -> {
                        append(canonical, voucher.storeId());
                        append(canonical, voucher.code().toUpperCase(Locale.ROOT));
                        append(canonical, voucher.quoteFingerprint());
                    });
            if (shipping != null) {
                append(canonical, shipping.name());
                append(canonical, shipping.phone());
                append(canonical, shipping.address());
            }
        }
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha);
        } catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }
}
