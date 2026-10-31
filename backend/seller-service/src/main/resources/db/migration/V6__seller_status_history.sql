-- Every seller status change. reviewed_by is the Admin account; it is NULL for the seller's own submissions.
CREATE TABLE seller_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES seller_profiles (id),
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    reviewed_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_seller_status_history_seller ON seller_status_history (seller_id, created_at);

-- Sellers that exist before this table start from their current status.
INSERT INTO seller_status_history (seller_id, to_status, reason)
SELECT id, status, review_reason FROM seller_profiles;
