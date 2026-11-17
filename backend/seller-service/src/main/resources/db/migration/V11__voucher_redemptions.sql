-- FR-VC-05 C2 (M01-VOUCHER-C2-v2, MD18/MD19/MD22). Usage is counted here, atomically, per SellerOrder.
-- used_count is the number of ACTIVE redemptions; the CHECK makes over-redemption impossible.
ALTER TABLE vouchers
    ADD COLUMN used_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_vouchers_used_count CHECK (used_count >= 0 AND used_count <= usage_limit);

-- One row per Store order (redemption_key = order-<orderId>-store-<storeId>), written by order-service.
-- RELEASED rows are kept: a late redeem with a released key must never consume usage again.
-- voucher_id/customer_id are NULL only for a tombstone written by a release that arrived first.
CREATE TABLE voucher_redemptions (
    redemption_key VARCHAR(120) COLLATE "C" PRIMARY KEY,
    voucher_id UUID REFERENCES vouchers(id) ON DELETE RESTRICT,
    customer_id VARCHAR(200),
    order_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_voucher_redemptions_key CHECK (redemption_key ~ '^order-[0-9a-f-]{36}-store-[0-9A-Za-z-]{1,60}$'),
    CONSTRAINT ck_voucher_redemptions_status CHECK (status IN ('ACTIVE','RELEASED')),
    CONSTRAINT ck_voucher_redemptions_active CHECK (
        (status = 'ACTIVE' AND voucher_id IS NOT NULL AND customer_id IS NOT NULL AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL))
);
-- MD18: one active use per customer per voucher. A released use (MD19) frees the customer again.
CREATE UNIQUE INDEX uq_voucher_redemptions_customer_active
    ON voucher_redemptions(voucher_id, customer_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_voucher_redemptions_voucher ON voucher_redemptions(voucher_id);
