DO $$
BEGIN
    IF current_database() <> 'auth_db' OR current_user <> 'auth_migrator' THEN
        RAISE EXCEPTION 'Auth migration requires auth_migrator on auth_db';
    END IF;
END $$;

-- Highest Seller status revision applied, so stale role events are ignored.
ALTER TABLE accounts ADD COLUMN seller_role_revision bigint NOT NULL DEFAULT -1;
