DO $$
BEGIN
    IF current_database() <> 'auth_db' OR current_user <> 'auth_migrator' THEN
        RAISE EXCEPTION 'Auth migration requires auth_migrator on auth_db';
    END IF;
END $$;

-- Writes in flight at the business services. A row is removed on release, or once the service has
-- fenced the write, never by age alone, so a revocation can always reach every write it must stop.
CREATE TABLE write_reservations (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id),
    service_url varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX write_reservations_account_idx ON write_reservations (account_id);
CREATE INDEX write_reservations_created_idx ON write_reservations (created_at);

GRANT SELECT, INSERT, DELETE ON write_reservations TO auth_app;
