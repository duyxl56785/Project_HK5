package com.projecthk5.sellerservice.store;

public class StoreRuleException extends RuntimeException {
    public enum Code { INVALID_INPUT, ACCESS_DENIED, SELLER_NOT_APPROVED, AUTHORIZATION_UNAVAILABLE }
    private final Code code;

    public StoreRuleException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
