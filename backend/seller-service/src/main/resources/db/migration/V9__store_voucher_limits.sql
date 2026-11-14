-- FR-VC-05 C1: Store-scoped grant and approval workflow. Refs #198 #199.
-- The Store row is the shared serialization lock for approve, voucher publish and resume.
CREATE TABLE voucher_limit_requests (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    requested_limit SMALLINT NOT NULL CHECK (requested_limit BETWEEN 21 AND 50),
    reason VARCHAR(500) NOT NULL,
    budget_note VARCHAR(500),
    requested_from TIMESTAMPTZ NOT NULL,
    requested_until TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(128) COLLATE "C" NOT NULL,
    request_fingerprint CHAR(64) COLLATE "C" NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approved_limit SMALLINT,
    approved_from TIMESTAMPTZ,
    approved_until TIMESTAMPTZ,
    decided_by VARCHAR(128),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_voucher_limit_request_key UNIQUE (store_id,idempotency_key),
    CONSTRAINT ck_vlr_reason CHECK (reason = btrim(reason) AND reason ~ '[^[:space:]]'),
    CONSTRAINT ck_vlr_budget CHECK (budget_note IS NULL OR budget_note = btrim(budget_note)),
    CONSTRAINT ck_vlr_key CHECK (idempotency_key = btrim(idempotency_key) AND idempotency_key ~ '^[!-~]{1,128}$'),
    CONSTRAINT ck_vlr_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_vlr_window CHECK (isfinite(requested_from) AND isfinite(requested_until)
         AND requested_until > requested_from AND requested_until <= requested_from + INTERVAL '30 days'),
    CONSTRAINT ck_vlr_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_vlr_approval CHECK (
        (status='APPROVED' AND approved_limit BETWEEN 21 AND requested_limit
         AND approved_from >= requested_from AND approved_until <= requested_until
         AND approved_until > approved_from AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR (status <> 'APPROVED' AND approved_limit IS NULL AND approved_from IS NULL AND approved_until IS NULL)
    ),
    CONSTRAINT ck_vlr_decision CHECK (
        (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR (status IN ('PENDING','CANCELLED') AND decided_by IS NULL AND decided_at IS NULL)
    ),
    CONSTRAINT ck_vlr_version CHECK (version >= 0),
    CONSTRAINT ck_vlr_timestamps CHECK (isfinite(created_at) AND isfinite(updated_at) AND updated_at >= created_at)
);
CREATE UNIQUE INDEX uq_voucher_limit_one_pending ON voucher_limit_requests(store_id) WHERE status='PENDING';
CREATE INDEX idx_vlr_admin_queue ON voucher_limit_requests(status,created_at,id);
CREATE INDEX idx_vlr_store_created ON voucher_limit_requests(store_id,created_at DESC,id DESC);

CREATE TABLE voucher_limit_request_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES voucher_limit_requests(id) ON DELETE RESTRICT,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    actor VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_vlr_hist_status CHECK (
        (from_status IS NULL OR from_status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
        AND to_status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
CREATE INDEX idx_vlr_history_request ON voucher_limit_request_history(request_id,created_at);

CREATE TABLE store_voucher_limits (
    store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE RESTRICT,
    granted_limit SMALLINT NOT NULL CHECK (granted_limit BETWEEN 21 AND 50),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    source_request_id UUID NOT NULL REFERENCES voucher_limit_requests(id) ON DELETE RESTRICT,
    decided_by VARCHAR(128) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_svl_window CHECK (isfinite(valid_from) AND isfinite(valid_until) AND valid_until > valid_from),
    CONSTRAINT ck_svl_version CHECK (version >= 0)
);
