DO $$
BEGIN
    IF current_database() <> 'auth_db' OR current_user <> 'auth_migrator' THEN
        RAISE EXCEPTION 'Auth migration requires auth_migrator on auth_db';
    END IF;
END $$;

CREATE TABLE seller_role_sync_lock (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO seller_role_sync_lock VALUES (1);
CREATE TABLE seller_role_sync_state (
    seller_id uuid PRIMARY KEY,
    account_id uuid NOT NULL UNIQUE REFERENCES accounts(id),
    latest_version bigint NOT NULL CHECK (latest_version >= 0)
);
CREATE TABLE seller_role_sync_versions (
    seller_id uuid NOT NULL REFERENCES seller_role_sync_state(seller_id),
    source_version bigint NOT NULL CHECK (source_version >= 0),
    seller_status varchar(16) NOT NULL CHECK (seller_status IN ('PENDING_REVIEW','APPROVED','REJECTED','SUSPENDED')),
    PRIMARY KEY (seller_id, source_version)
);
CREATE TABLE seller_role_sync_receipts (
    event_id uuid PRIMARY KEY,
    seller_id uuid NOT NULL,
    source_version bigint NOT NULL,
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (seller_id, source_version) REFERENCES seller_role_sync_versions(seller_id, source_version)
);
GRANT SELECT, UPDATE ON seller_role_sync_lock TO auth_app;
GRANT SELECT, INSERT ON seller_role_sync_state TO auth_app;
GRANT UPDATE(latest_version) ON seller_role_sync_state TO auth_app;
GRANT SELECT, INSERT ON seller_role_sync_versions, seller_role_sync_receipts TO auth_app;
