CREATE TABLE authority_write_operations (
    request_id UUID PRIMARY KEY,
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    epoch BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    state TEXT NOT NULL CHECK (state IN ('PENDING','ADMITTED','COMMITTED','ABORTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE authority_write_grants (
    request_id UUID NOT NULL REFERENCES authority_write_operations(request_id),
    participant TEXT NOT NULL CHECK (participant IN ('AUTH','SELLER')),
    authority_epoch BIGINT NOT NULL CHECK (authority_epoch>0),
    PRIMARY KEY(request_id,participant)
);
