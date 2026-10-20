-- Order-owned, immutable Inventory operation intent. No HTTP provider is enabled by this migration.
-- T1 INSERT commits before network I/O; T2 outcome UPDATE commits independently.
CREATE TABLE inventory_operation_journal (
    operation_id varchar(128) PRIMARY KEY,
    checkout_attempt_id uuid NOT NULL REFERENCES checkout_attempts(id),
    operation_type varchar(16) NOT NULL CHECK (operation_type IN ('RESERVE', 'CONSUME', 'RELEASE')),
    canonical_lines jsonb NOT NULL CHECK (jsonb_typeof(canonical_lines) = 'array'),
    payload_fingerprint varchar(64) NOT NULL,
    state varchar(24) NOT NULL CHECK (state IN ('PENDING', 'IN_FLIGHT', 'UNKNOWN', 'CONFIRMED', 'REJECTED')),
    send_attempts integer NOT NULL DEFAULT 0 CHECK (send_attempts >= 0),
    last_error_code varchar(96),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_journal_recovery ON inventory_operation_journal(state, updated_at);
CREATE INDEX idx_inventory_journal_checkout ON inventory_operation_journal(checkout_attempt_id);
