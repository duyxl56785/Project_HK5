ALTER TABLE authority_write_operations ADD CONSTRAINT authority_operation_binding UNIQUE(request_id,binding_hash,epoch);
CREATE TABLE product_reservation_recovery (
    request_id UUID PRIMARY KEY REFERENCES authority_write_operations(request_id),
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    operation_epoch BIGINT NOT NULL CHECK (operation_epoch > 0),
    intent TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    state TEXT NOT NULL DEFAULT 'READY' CHECK (state IN ('READY','LEASED','COMPLETE','BLOCKED')),
    token UUID,
    lease_until TIMESTAMPTZ,
    due_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_code TEXT,
    FOREIGN KEY(request_id,binding_hash,operation_epoch) REFERENCES authority_write_operations(request_id,binding_hash,epoch),
    CHECK ((state='LEASED') = (token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX product_reservation_recovery_due ON product_reservation_recovery(due_at,created_at) WHERE state IN ('READY','LEASED');
CREATE TABLE product_reservation_recovery_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES product_reservation_recovery(request_id),
    operation_epoch BIGINT NOT NULL,
    actor TEXT NOT NULL,
    database_actor TEXT NOT NULL DEFAULT current_user,
    action TEXT NOT NULL CHECK (action='REQUEST_RETRY'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
