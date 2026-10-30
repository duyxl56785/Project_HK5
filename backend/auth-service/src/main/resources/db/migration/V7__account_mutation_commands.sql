ALTER TABLE authority_pending_revocations DROP CONSTRAINT authority_pending_revocations_kind_check;
ALTER TABLE authority_pending_revocations ADD CONSTRAINT authority_pending_revocations_kind_check
    CHECK (kind IN ('MANUAL','ROLE_SYNC','AUTH_MUTATION'));
ALTER TABLE authority_pending_revocations ADD CONSTRAINT account_mutation_payload_required
    CHECK (kind <> 'AUTH_MUTATION' OR payload IS NOT NULL);
CREATE TABLE account_mutation_receipts (
    request_id UUID PRIMARY KEY REFERENCES authority_pending_revocations(request_id),
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
GRANT SELECT, INSERT ON account_mutation_receipts TO auth_app;
CREATE TABLE account_authority_versions (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    version BIGINT NOT NULL CHECK (version >= 0)
);
INSERT INTO account_authority_versions SELECT id,0 FROM accounts;
CREATE FUNCTION account_authority_version_change() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE account UUID;
BEGIN
    IF TG_TABLE_NAME='accounts' THEN
        IF TG_OP='DELETE' THEN RETURN OLD; END IF;
        account := NEW.id;
    ELSE
        IF TG_OP='DELETE' THEN account := OLD.account_id; ELSE account := NEW.account_id; END IF;
        IF TG_OP='UPDATE' THEN
            IF OLD.account_id IS DISTINCT FROM NEW.account_id THEN
                UPDATE account_authority_versions SET version=version+1 WHERE account_id=OLD.account_id;
            END IF;
        END IF;
    END IF;
    INSERT INTO account_authority_versions SELECT account,1 WHERE EXISTS(SELECT 1 FROM accounts WHERE id=account)
        ON CONFLICT(account_id) DO UPDATE SET version=account_authority_versions.version+1;
    RETURN NULL;
END $$;
DO $$
BEGIN
    EXECUTE format('ALTER FUNCTION %I.account_authority_version_change() SET search_path = pg_catalog, %I, pg_temp',current_schema(),current_schema());
END $$;
REVOKE ALL ON FUNCTION account_authority_version_change() FROM PUBLIC;
CREATE TRIGGER accounts_authority_version AFTER INSERT OR UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION account_authority_version_change();
CREATE TRIGGER account_roles_authority_version AFTER INSERT OR UPDATE OR DELETE ON account_role_grants
    FOR EACH ROW EXECUTE FUNCTION account_authority_version_change();
GRANT SELECT ON account_authority_versions TO auth_app;
