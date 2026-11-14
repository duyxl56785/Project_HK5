-- Replay-safe for disposable V7 upgrade rehearsal: Flyway history from V7 onward is reset without dropping later tables.
-- Existing objects retain their constraints and data; fresh installs still apply every constraint normally.
-- FR-VC-05 C1 / TV3 (Refs #199). Must run AFTER TV2's V9; no Checkout/Finance changes.
CREATE TABLE IF NOT EXISTS vouchers (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    code VARCHAR(20) COLLATE "C" NOT NULL,
    name VARCHAR(120) NOT NULL,
    discount_type VARCHAR(8) NOT NULL,
    discount_value NUMERIC(19,0) NOT NULL,
    max_discount NUMERIC(19,0),
    min_order_amount NUMERIC(19,0) NOT NULL,
    usage_limit INTEGER NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vouchers_store_code UNIQUE (store_id, code),
    CONSTRAINT ck_vouchers_code CHECK (code ~ '^[A-Z0-9]{4,20}$'),
    CONSTRAINT ck_vouchers_name CHECK (char_length(name) BETWEEN 1 AND 120 AND btrim(name) = name AND name ~ '[^[:space:]]'),
    CONSTRAINT ck_vouchers_type CHECK (discount_type IN ('FIXED','PERCENT')),
    CONSTRAINT ck_vouchers_amount CHECK (
        discount_value > 0 AND discount_value <= 99999999999999999
        AND min_order_amount >= 0 AND min_order_amount <= 99999999999999999
        AND ((discount_type = 'FIXED' AND max_discount IS NULL AND min_order_amount >= discount_value)
          OR (discount_type = 'PERCENT' AND discount_value BETWEEN 1 AND 90
              AND max_discount > 0 AND max_discount <= 99999999999999999))
    ),
    CONSTRAINT ck_vouchers_usage CHECK (usage_limit BETWEEN 1 AND 100000),
    CONSTRAINT ck_vouchers_window CHECK (
        isfinite(starts_at) AND isfinite(ends_at) AND ends_at > starts_at
        AND ends_at <= starts_at + INTERVAL '90 days'
    ),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('DRAFT','PUBLISHED','PAUSED','CANCELLED')),
    CONSTRAINT ck_vouchers_version CHECK (version >= 0),
    CONSTRAINT ck_vouchers_timestamps CHECK (isfinite(created_at) AND isfinite(updated_at) AND updated_at >= created_at)
);
CREATE INDEX IF NOT EXISTS idx_vouchers_store_published_end ON vouchers (store_id, ends_at)
    WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_vouchers_store_created ON vouchers (store_id, created_at DESC, id);

CREATE TABLE IF NOT EXISTS voucher_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id UUID NOT NULL REFERENCES vouchers(id) ON DELETE RESTRICT,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_voucher_history_status CHECK (
        from_status IN ('DRAFT','PUBLISHED','PAUSED','CANCELLED')
        AND to_status IN ('DRAFT','PUBLISHED','PAUSED','CANCELLED')
        AND from_status <> to_status
    )
);
CREATE INDEX IF NOT EXISTS idx_voucher_history_voucher ON voucher_status_history (voucher_id, created_at);
