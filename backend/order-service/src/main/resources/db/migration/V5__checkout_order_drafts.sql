-- Private TV3 checkout draft, never exposed through customer Order History.
-- No change to existing Order/Finance/Fulfillment tables, status, events or Inventory protocol.
-- Contains the same shipping PII as the eventual Order: apply Order DB access/retention controls.
CREATE TABLE checkout_order_drafts (
    checkout_attempt_id uuid PRIMARY KEY REFERENCES checkout_attempts(id),
    snapshot_version smallint NOT NULL CHECK (snapshot_version = 1),
    payload text NOT NULL CHECK (jsonb_typeof(payload::jsonb) = 'object'),
    payload_sha256 varchar(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now()
);
