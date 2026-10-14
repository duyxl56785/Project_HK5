package com.projecthk5.sellerservice.seller.domain;

public class SellerRuleException extends RuntimeException {
    public enum Code {
        INVALID_INPUT,
        INVALID_STATE,
        NOT_OWNER,
        SELF_MODERATION,
        ACCESS_DENIED
    }

    private final Code code;

    public SellerRuleException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
