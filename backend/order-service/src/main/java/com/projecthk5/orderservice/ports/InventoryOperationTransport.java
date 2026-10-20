package com.projecthk5.orderservice.ports;

import java.util.List;

/**
 * Future per-pair verified HTTP adapter; MUST enforce actor/system provenance
 * independently of X-Internal-Token before it can send a positive operation.
 * Distinguish definite no-send, definite provider rejection, and unknown outcome.
 */
public interface InventoryOperationTransport {
    Reply send(InventoryOperationWire.Request request);

    record Reply(String operationId, String type, boolean replayed, List<String> skus) {
        public Reply { skus = List.copyOf(skus); }
    }

    /** Auth/adapter disabled: guaranteed request never left Order. */
    final class NotSent extends RuntimeException {
        public NotSent(String message) { super(message); }
    }

    /** Provider verified an atomic non-commit response (e.g. insufficient stock). */
    final class DefinitiveFailure extends RuntimeException {
        private final String code;
        public DefinitiveFailure(String code) {
            super(code);
            this.code = code;
        }
        public String code() { return code; }
    }

    /** Timeout, connection loss, 5xx or 409 claim-conflict: commit status unknown. */
    final class UnknownOutcome extends RuntimeException {
        public UnknownOutcome(String message) { super(message); }
    }
}
