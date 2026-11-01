-- Durable side effects of committed Seller transitions (Auth role sync, owner notifications).
-- Written in the same transaction as the status change; a relay retries until delivered.
CREATE TABLE seller_outbox (
    id UUID PRIMARY KEY,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('ROLE_SYNC', 'NOTIFY')),
    seller_id UUID NOT NULL REFERENCES seller_profiles(id),
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX seller_outbox_pending_idx ON seller_outbox(created_at) WHERE delivered_at IS NULL;
