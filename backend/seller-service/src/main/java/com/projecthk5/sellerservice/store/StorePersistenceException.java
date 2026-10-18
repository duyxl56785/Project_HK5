package com.projecthk5.sellerservice.store;

/** Internal persistence errors; HTTP status/envelope remains a separate contract. */
public class StorePersistenceException extends RuntimeException {
    public enum Code { STORE_NOT_FOUND, STORE_ALREADY_EXISTS, SLUG_ALREADY_EXISTS, STORE_VERSION_CONFLICT }
    private final Code code;

    public StorePersistenceException(Code code, String message) {
        this(code, message, null);
    }

    public StorePersistenceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }
}
