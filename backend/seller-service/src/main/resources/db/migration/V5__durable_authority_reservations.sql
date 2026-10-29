CREATE TABLE authority_fence_clock (
    id INTEGER PRIMARY KEY CHECK (id=1),
    epoch BIGINT NOT NULL CHECK (epoch>=0)
);
INSERT INTO authority_fence_clock VALUES(1,0);
CREATE TABLE authority_write_reservations (
    request_id UUID PRIMARY KEY,
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    epoch BIGINT NOT NULL UNIQUE CHECK (epoch>0),
    state TEXT NOT NULL CHECK (state IN ('ACTIVE','RELEASED')),
    terminal_state TEXT CHECK (terminal_state IN ('COMMITTED','ABORTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((state='ACTIVE' AND terminal_state IS NULL) OR (state='RELEASED' AND terminal_state IS NOT NULL))
);
CREATE INDEX authority_active_reservations ON authority_write_reservations(state) WHERE state='ACTIVE';
CREATE FUNCTION authority_fence_enter_mutation() RETURNS BIGINT
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE next_epoch BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock_shared(72602130);
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION USING ERRCODE='40001', MESSAGE='Authority mutation requires READ COMMITTED';
    END IF;
    SELECT epoch INTO STRICT next_epoch FROM authority_fence_clock WHERE id=1;
    IF EXISTS(SELECT 1 FROM authority_write_reservations WHERE state='ACTIVE') THEN
        RAISE EXCEPTION USING ERRCODE='55P03', MESSAGE='Authority reservation is active';
    END IF;
    RETURN next_epoch;
END $$;
CREATE FUNCTION authority_fence_mutation_trigger() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM authority_fence_enter_mutation();
    RETURN NULL;
END $$;
CREATE TRIGGER seller_profiles_authority_fence BEFORE INSERT OR UPDATE OR DELETE ON seller_profiles FOR EACH STATEMENT EXECUTE FUNCTION authority_fence_mutation_trigger();
CREATE TRIGGER stores_authority_fence BEFORE INSERT OR UPDATE OR DELETE ON stores FOR EACH STATEMENT EXECUTE FUNCTION authority_fence_mutation_trigger();
CREATE FUNCTION authority_fence_truncate_trigger() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NOT pg_try_advisory_xact_lock_shared(72602130) THEN
        RAISE EXCEPTION USING ERRCODE='55P03', MESSAGE='Authority admission is active';
    END IF;
    PERFORM authority_fence_enter_mutation();
    RETURN NULL;
END $$;
CREATE TRIGGER seller_profiles_authority_truncate BEFORE TRUNCATE ON seller_profiles FOR EACH STATEMENT EXECUTE FUNCTION authority_fence_truncate_trigger();
CREATE TRIGGER stores_authority_truncate BEFORE TRUNCATE ON stores FOR EACH STATEMENT EXECUTE FUNCTION authority_fence_truncate_trigger();
