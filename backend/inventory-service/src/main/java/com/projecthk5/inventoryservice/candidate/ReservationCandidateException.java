package com.projecthk5.inventoryservice.candidate;

/** Candidate provider failure. HTTP mapping is intentionally not exposed until the wire is approved. */
public class ReservationCandidateException extends RuntimeException {
    private final String code;

    public ReservationCandidateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
