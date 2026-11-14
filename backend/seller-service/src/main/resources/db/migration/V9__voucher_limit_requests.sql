-- FR-VC-05: one current grant and one pending request per Store, with immutable decision audit.
CREATE TABLE voucher_limit_requests (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) COLLATE "C" NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    requested_limit SMALLINT NOT NULL CHECK (requested_limit BETWEEN 21 AND 50),
    reason VARCHAR(500) NOT NULL CHECK (reason = btrim(reason) AND reason ~ '[^[:space:]]'),
    budget_note VARCHAR(500),
    requested_from TIMESTAMPTZ NOT NULL,
    requested_until TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    approved_limit SMALLINT,
    approved_from TIMESTAMPTZ,
    approved_until TIMESTAMPTZ,
    decided_by VARCHAR(128),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_voucher_limit_request_key UNIQUE(store_id,idempotency_key),
    CONSTRAINT ck_voucher_limit_request_hash CHECK(request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_voucher_limit_request_window CHECK (
        isfinite(requested_from) AND isfinite(requested_until)
        AND requested_until > requested_from
        AND requested_until <= requested_from + INTERVAL '30 days'),
    CONSTRAINT ck_voucher_limit_request_decision CHECK (
        (status = 'APPROVED' AND approved_limit BETWEEN 21 AND requested_limit
            AND approved_from >= requested_from AND approved_until <= requested_until
            AND approved_until > approved_from AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR (status = 'REJECTED' AND approved_limit IS NULL AND approved_from IS NULL
            AND approved_until IS NULL AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND decision_reason IS NOT NULL AND decision_reason ~ '[^[:space:]]')
        OR (status IN ('PENDING','CANCELLED') AND approved_limit IS NULL AND approved_from IS NULL
            AND approved_until IS NULL AND decided_by IS NULL AND decided_at IS NULL)
    )
);
CREATE UNIQUE INDEX uq_voucher_limit_one_pending ON voucher_limit_requests(store_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_voucher_limit_request_state_created ON voucher_limit_requests(status,created_at,id);
CREATE INDEX idx_voucher_limit_request_store_created ON voucher_limit_requests(store_id,created_at DESC,id);

CREATE TABLE store_voucher_limits (
    store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE RESTRICT,
    granted_limit SMALLINT NOT NULL CHECK (granted_limit BETWEEN 21 AND 50),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    source_request_id UUID NOT NULL REFERENCES voucher_limit_requests(id) ON DELETE RESTRICT,
    decided_by VARCHAR(128) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_voucher_limit_grant_window CHECK (
        isfinite(valid_from) AND isfinite(valid_until) AND valid_until > valid_from)
);

CREATE TABLE voucher_limit_request_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES voucher_limit_requests(id) ON DELETE RESTRICT,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL CHECK(to_status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    reason VARCHAR(500),
    actor VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_voucher_limit_history_request ON voucher_limit_request_history(request_id,created_at,id);
