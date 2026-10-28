package com.projecthk5.productservice.image;

/** Internal fixed diagnostics; never retain filesystem paths in causes or suppressed exceptions. */
public final class ImageStorageException extends RuntimeException {
    public enum Code { INVALID_INPUT, INVALID_KEY, NOT_FOUND, KEY_CONFLICT, UNSAFE_STORAGE, IO_FAILURE }
    private final Code code;

    public ImageStorageException(Code code) {
        super("Image storage operation failed: " + code);
        this.code = code;
    }

    public Code getCode() { return code; }
}
