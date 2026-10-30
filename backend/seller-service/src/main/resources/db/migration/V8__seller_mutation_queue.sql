ALTER TABLE authority_pending_revocations DROP CONSTRAINT authority_pending_revocations_kind_check;
ALTER TABLE authority_pending_revocations ADD CONSTRAINT authority_pending_revocations_kind_check
    CHECK (kind IN ('MANUAL','ROLE_SYNC','AUTH_MUTATION','SELLER_MUTATION'));
ALTER TABLE authority_pending_revocations ADD CONSTRAINT seller_mutation_payload_required
    CHECK (kind <> 'SELLER_MUTATION' OR payload IS NOT NULL);

CREATE TABLE seller_mutation_receipts (
    request_id UUID PRIMARY KEY REFERENCES authority_pending_revocations(request_id),
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    result_id UUID NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version >= 0),
    result_state TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
