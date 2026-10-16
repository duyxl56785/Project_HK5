package com.projecthk5.sellerservice.seller.service;

public class SellerPersistenceException extends RuntimeException {
    public enum Code { SELLER_NOT_FOUND, SELLER_ALREADY_EXISTS, SELLER_VERSION_CONFLICT }

    private final Code code;

    public SellerPersistenceException(Code code, String message) {
        this(code, message, null);
    }

    public SellerPersistenceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }
}
