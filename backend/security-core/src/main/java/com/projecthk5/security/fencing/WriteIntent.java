package com.projecthk5.security.fencing;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public record WriteIntent(UUID requestId, UUID accountId, UUID storeId, String tokenId,
        Mode mode, long sellerVersion, long storeVersion, long productVersion, String commandHash) {
    public enum Mode { MANAGE, MODERATE }
    public WriteIntent {
        Objects.requireNonNull(requestId); Objects.requireNonNull(accountId); Objects.requireNonNull(storeId); Objects.requireNonNull(mode);
        if(tokenId==null || tokenId.isEmpty() || tokenId.length()>256 || tokenId.indexOf('\0')>=0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(tokenId) || sellerVersion<0 || storeVersion<0 || productVersion<0
                || commandHash==null || !commandHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid write intent");
    }
    public String fingerprint() {
        String value=String.join("\0",requestId.toString(),accountId.toString(),storeId.toString(),tokenId,mode.name(),
                Long.toString(sellerVersion),Long.toString(storeVersion),Long.toString(productVersion),commandHash);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
