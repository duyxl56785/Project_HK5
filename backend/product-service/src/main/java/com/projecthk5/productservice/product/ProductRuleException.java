package com.projecthk5.productservice.product;

public class ProductRuleException extends RuntimeException {
    public enum Code { INVALID_INPUT, INVALID_STATE, ACCESS_DENIED, CATEGORY_NOT_FOUND, AUTHORIZATION_UNAVAILABLE }
    private final Code code;

    public ProductRuleException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
