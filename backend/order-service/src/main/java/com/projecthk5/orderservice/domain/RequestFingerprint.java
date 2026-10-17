package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

public final class RequestFingerprint {
    private RequestFingerprint() {}
    public static String compute(CheckoutRequest request) {
        StringBuilder canonical = new StringBuilder("revision=").append(request.expectedRevision()).append(";");
        request.expectedItems().stream().sorted(Comparator.comparing(ExpectedItem::productId))
            .forEach(it -> canonical.append(it.productId().length()).append(':').append(it.productId())
                .append(':').append(it.quantity()).append(';'));
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha);
        } catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
