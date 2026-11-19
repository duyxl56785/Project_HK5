package com.projecthk5.orderservice.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Immutable, integral-VND quote persisted for Checkout replay and the SellerOrder. */
public record VoucherSnapshot(UUID voucherId, long version, String code, String type, long discountValue,
                              Long maxDiscount, long merchandiseAmount, long discountAmount) {
    public VoucherSnapshot {
        if (voucherId == null || version < 0 || code == null || !code.matches("[A-Z0-9]{4,20}")
                || merchandiseAmount < 1 || merchandiseAmount > 99_999_999_999_999_999L
                || discountAmount < 0 || discountAmount >= merchandiseAmount)
            throw new IllegalArgumentException("Invalid voucher snapshot");
        if ("FIXED".equals(type)) {
            if (discountValue < 1 || maxDiscount != null || discountAmount != discountValue)
                throw new IllegalArgumentException("Invalid fixed voucher snapshot");
        } else if (!"PERCENT".equals(type) || discountValue < 1 || discountValue > 90
                || maxDiscount == null || maxDiscount < 1 || discountAmount > maxDiscount) {
            throw new IllegalArgumentException("Invalid percentage voucher snapshot");
        }
    }

    public BigDecimal gross() {
        return BigDecimal.valueOf(merchandiseAmount - discountAmount);
    }

    public String quoteFingerprint(String customer, String store) {
        String value = customer.length() + ":" + customer + store.length() + ":" + store
                + ":" + voucherId + ":" + version + ":" + code + ":" + type
                + ":" + discountValue + ":" + maxDiscount + ":" + merchandiseAmount + ":" + discountAmount;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
