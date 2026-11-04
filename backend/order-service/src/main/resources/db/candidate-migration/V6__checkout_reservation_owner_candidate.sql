-- TV3 Order candidate ONLY. Deliberately outside classpath:db/migration.
-- Do not promote to default Flyway or activate Inventory V3 until TV2/TV3/TV4/TV5
-- sign the cross-service owner, auth, recovery and finance contracts.
--
-- This row is committed inside the same proposed Checkout Draft T1 transaction,
-- BEFORE the first owner-bound V3 RESERVE. Never FK to seller_orders because those
-- rows do not exist before the first Inventory HTTP call.
CREATE TABLE checkout_reservation_owner_candidate (
    reservation_id uuid PRIMARY KEY,
    checkout_attempt_id uuid NOT NULL REFERENCES checkout_attempts(id),
    root_order_id uuid NOT NULL,
    seller_order_id uuid NOT NULL,
    customer_id varchar(200) NOT NULL,
    store_id varchar(128) NOT NULL,
    seller_id varchar(128) NOT NULL,
    checkout_request_fingerprint varchar(64) NOT NULL
        CHECK (checkout_request_fingerprint ~ '^[0-9a-f]{64}$'),
    canonical_lines jsonb NOT NULL
        CHECK (jsonb_typeof(canonical_lines) = 'array' AND jsonb_array_length(canonical_lines) > 0),
    provider_owner_fingerprint varchar(64) NOT NULL
        CHECK (provider_owner_fingerprint ~ '^[0-9a-f]{64}$'),
    snapshot_sha256 varchar(64) NOT NULL
        CHECK (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_checkout_reservation_root_attempt
        CHECK (root_order_id = checkout_attempt_id),
    CONSTRAINT uq_checkout_reservation_attempt_seller_order
        UNIQUE (checkout_attempt_id,seller_order_id),
    CONSTRAINT uq_checkout_reservation_attempt_store
        UNIQUE (checkout_attempt_id,store_id),
    CONSTRAINT uq_checkout_reservation_seller_order
        UNIQUE (seller_order_id)
);
CREATE INDEX idx_checkout_reservation_owner_attempt
    ON checkout_reservation_owner_candidate(checkout_attempt_id);

-- Intentional: no trigger schedules recovery, no Order/Inventory HTTP route,
-- no V3 runtime flag, and no automatic deletion/retention policy.
