-- FR-VC-05 C2. A redemption is retained after release for idempotent replay.
ALTER TABLE vouchers ADD CONSTRAINT uq_vouchers_id_store UNIQUE (id, store_id);

CREATE TABLE voucher_redemptions (
    seller_order_id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL,
    store_id UUID NOT NULL,
    customer_id VARCHAR(200) COLLATE "C" NOT NULL CHECK (btrim(customer_id) <> ''),
    voucher_version BIGINT NOT NULL CHECK (voucher_version >= 0),
    voucher_code VARCHAR(20) NOT NULL,
    discount_type VARCHAR(8) NOT NULL,
    discount_value BIGINT NOT NULL,
    max_discount BIGINT,
    merchandise_amount BIGINT NOT NULL CHECK (merchandise_amount BETWEEN 1 AND 99999999999999999),
    discount_amount BIGINT NOT NULL CHECK (discount_amount >= 0 AND discount_amount < merchandise_amount),
    state VARCHAR(12) NOT NULL,
    release_reason VARCHAR(32),
    CONSTRAINT fk_voucher_redemptions_voucher_store FOREIGN KEY (voucher_id, store_id)
        REFERENCES vouchers(id, store_id) ON DELETE RESTRICT,
    CONSTRAINT ck_voucher_redemptions_spec CHECK (
        (discount_type = 'FIXED' AND max_discount IS NULL
            AND discount_value BETWEEN 1 AND 99999999999999999 AND discount_amount = discount_value)
        OR (discount_type = 'PERCENT' AND discount_value BETWEEN 1 AND 90
            AND max_discount IS NOT NULL AND max_discount BETWEEN 1 AND 99999999999999999
            AND discount_amount <= max_discount)
    ),
    CONSTRAINT ck_voucher_redemptions_state CHECK (
        (state IN ('RESERVED', 'COMMITTED') AND release_reason IS NULL)
        OR (state = 'RELEASED' AND release_reason IS NOT NULL
            AND release_reason IN ('CHECKOUT_FAILED', 'CANCELLED_BEFORE_DISPATCH', 'REJECTED_BEFORE_DISPATCH'))
    )
);

CREATE UNIQUE INDEX uq_voucher_redemptions_active_customer
    ON voucher_redemptions(voucher_id, customer_id) WHERE state IN ('RESERVED', 'COMMITTED');
