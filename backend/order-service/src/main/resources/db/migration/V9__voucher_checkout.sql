ALTER TABLE seller_orders ADD COLUMN voucher_snapshot jsonb;
ALTER TABLE seller_orders ADD CONSTRAINT ck_seller_order_voucher_snapshot CHECK (
    voucher_snapshot IS NULL OR (
        jsonb_typeof(voucher_snapshot) = 'object'
        AND voucher_snapshot ?& ARRAY['voucherId','version','code','type','discountValue','merchandiseAmount','discountAmount']
        AND (voucher_snapshot->>'merchandiseAmount')::bigint >= 1
        AND (voucher_snapshot->>'discountAmount')::bigint BETWEEN 0 AND (voucher_snapshot->>'merchandiseAmount')::bigint - 1
        AND gross = (voucher_snapshot->>'merchandiseAmount')::bigint - (voucher_snapshot->>'discountAmount')::bigint
    ) IS TRUE
);

ALTER TABLE checkout_order_drafts DROP CONSTRAINT checkout_order_drafts_snapshot_version_check;
ALTER TABLE checkout_order_drafts ADD CONSTRAINT checkout_order_drafts_snapshot_version_check
    CHECK (snapshot_version IN (1, 2));

-- Seller calls are journaled independently of Inventory and share the CheckoutAttempt fence.
CREATE TABLE checkout_voucher_sagas (
    attempt_id uuid PRIMARY KEY REFERENCES checkout_attempts(id),
    claimed_revision bigint NOT NULL CHECK (claimed_revision >= 0),
    phase text NOT NULL CHECK (phase IN ('REDEEM','INVENTORY','PLACED','ABORTED')),
    lease_token uuid,
    lease_until timestamptz NOT NULL DEFAULT now(),
    retry_at timestamptz NOT NULL DEFAULT now(),
    cart_done boolean NOT NULL DEFAULT false,
    failure_code text,
    CHECK ((phase = 'ABORTED') = (failure_code IS NOT NULL))
);

CREATE INDEX ix_checkout_voucher_sagas_retry ON checkout_voucher_sagas(retry_at);

CREATE TABLE checkout_voucher_intents (
    seller_order_id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES checkout_voucher_sagas(attempt_id),
    store_id uuid NOT NULL,
    quote jsonb NOT NULL CHECK (jsonb_typeof(quote) = 'object'),
    state text NOT NULL CHECK (state IN ('PENDING','REDEEMING','RESERVED','COMMITTED','RELEASED')),
    release_reason text,
    UNIQUE (attempt_id, store_id),
    CHECK ((state = 'RELEASED') = (release_reason IS NOT NULL))
);

CREATE INDEX ix_checkout_voucher_intents_attempt ON checkout_voucher_intents(attempt_id);
