CREATE TABLE seller_role_delivery (
    event_id UUID PRIMARY KEY REFERENCES seller_status_outbox(event_id) ON DELETE RESTRICT,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','LEASED','DELIVERED','BLOCKED','EXHAUSTED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 8),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    ack_result VARCHAR(16) CHECK (ack_result IN ('APPLIED','DUPLICATE','STALE_IGNORED')),
    last_error VARCHAR(32) CHECK (last_error ~ '^[A-Z_]+$'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT delivery_lease_valid CHECK ((state='LEASED' AND claim_token IS NOT NULL AND lease_until IS NOT NULL) OR (state<>'LEASED' AND claim_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT delivery_ack_valid CHECK ((state='DELIVERED') = (ack_result IS NOT NULL)),
    CONSTRAINT delivery_time_valid CHECK (isfinite(next_attempt_at) AND isfinite(updated_at) AND (lease_until IS NULL OR isfinite(lease_until)))
);
CREATE INDEX seller_role_delivery_due ON seller_role_delivery(state,next_attempt_at,lease_until);
INSERT INTO seller_role_delivery(event_id) SELECT event_id FROM seller_status_outbox;
CREATE FUNCTION forbid_seller_snapshot_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Seller status snapshots are immutable' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER seller_snapshot_immutable BEFORE UPDATE ON seller_status_outbox
FOR EACH ROW EXECUTE FUNCTION forbid_seller_snapshot_update();
