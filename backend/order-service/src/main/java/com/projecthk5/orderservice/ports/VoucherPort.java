package com.projecthk5.orderservice.ports;

import com.projecthk5.orderservice.domain.VoucherSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface VoucherPort {
    enum State { RESERVED, COMMITTED, RELEASED }
    enum ReleaseReason { CHECKOUT_FAILED, CANCELLED_BEFORE_DISPATCH, REJECTED_BEFORE_DISPATCH }
    record Intent(UUID sellerOrderId, UUID storeId, String customerId, VoucherSnapshot quote) { }
    record Reservation(UUID sellerOrderId, UUID storeId, String customerId, VoucherSnapshot quote,
                       State state, ReleaseReason releaseReason) { }

    VoucherSnapshot quote(UUID store, String customer, String code, long merchandise);
    Reservation redeem(Intent intent);
    Optional<Reservation> status(Intent intent);
    Reservation commit(Intent intent);
    Reservation abort(Intent intent);
    Reservation release(Intent intent, ReleaseReason reason);

    /** Authenticated and classified Seller response, distinct from ambiguous transport failure. */
    final class Rejected extends IllegalStateException {
        private final String code;
        public Rejected(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
