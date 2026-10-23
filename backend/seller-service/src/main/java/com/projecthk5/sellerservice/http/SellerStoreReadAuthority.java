package com.projecthk5.sellerservice.http;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;

/**
 * Trusted request-context boundary; no production implementation or default bean.
 * TV1 must verify signature, kid, issuer, audience and token times, bind exact sub,
 * and check the live account before returning. Never accept client-declared identity.
 */
public interface SellerStoreReadAuthority {
    /** Active authenticated account; no invented CUSTOMER/SELLER role requirement. */
    SellerIdentity requireCurrentAccount();

    /** Active account with SELLER in both verified token and current grants. */
    SellerIdentity requireCurrentSeller();
}
