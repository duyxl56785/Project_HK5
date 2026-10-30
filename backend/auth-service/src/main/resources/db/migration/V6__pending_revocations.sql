CREATE TABLE authority_pending_revocations (
    request_id UUID PRIMARY KEY,
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    payload TEXT,
    kind TEXT NOT NULL DEFAULT 'MANUAL' CHECK (kind IN ('MANUAL','ROLE_SYNC')),
    CHECK (kind <> 'ROLE_SYNC' OR payload IS NOT NULL),
    state TEXT NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','LEASED','APPLIED','REJECTED','BLOCKED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    due_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_code TEXT,
    CHECK ((state='LEASED') = (claim_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX authority_pending_revocations_due ON authority_pending_revocations(created_at,request_id)
    WHERE state IN ('PENDING','LEASED');
CREATE FUNCTION authority_pending_revocation_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.request_id IS DISTINCT FROM OLD.request_id OR NEW.binding_hash IS DISTINCT FROM OLD.binding_hash
       OR NEW.kind IS DISTINCT FROM OLD.kind OR NEW.payload IS DISTINCT FROM OLD.payload OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'immutable revocation binding' USING ERRCODE='23514';
    END IF;
    IF OLD.state IN ('APPLIED','REJECTED') AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'terminal revocation' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER authority_pending_revocation_immutable BEFORE UPDATE ON authority_pending_revocations
    FOR EACH ROW EXECUTE FUNCTION authority_pending_revocation_immutable();
GRANT SELECT, INSERT, UPDATE ON authority_pending_revocations TO auth_app;
