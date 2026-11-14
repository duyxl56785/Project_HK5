package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.ApiException;
import org.springframework.http.HttpStatus;

/** Emits quota details without changing the shared application error protocol. */
public final class VoucherQuotaException extends ApiException {
    private final int used;
    private final int limit;

    public VoucherQuotaException(int used, int limit) {
        super(HttpStatus.CONFLICT, "VOUCHER_QUOTA_EXCEEDED", "Voucher limit reached for this Store");
        this.used = used;
        this.limit = limit;
    }
    public int used() { return used; }
    public int limit() { return limit; }
}
