DO $$
BEGIN
    IF current_database() <> 'auth_db' OR current_user <> 'auth_migrator' THEN
        RAISE EXCEPTION 'Auth migration requires auth_migrator on auth_db';
    END IF;
END $$;

CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash text NOT NULL CHECK (length(btrim(password_hash)) > 0),
    -- Status vocabulary belongs to the Auth policy PR; there is no implicit active default.
    status varchar(32) NOT NULL CHECK (length(btrim(status)) > 0),
    created_at timestamptz NOT NULL,
    CONSTRAINT accounts_email_normalized CHECK (
        length(email) > 0 AND email = lower(email COLLATE "C")
        AND email !~ '[[:space:]]'
    )
);

CREATE TABLE account_role_grants (
    account_id uuid NOT NULL REFERENCES accounts(id),
    role varchar(16) NOT NULL CHECK (role IN ('CUSTOMER', 'SELLER', 'ADMIN')),
    PRIMARY KEY (account_id, role)
);

CREATE TABLE refresh_families (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT refresh_family_lifetime CHECK (
        expires_at > created_at AND expires_at <= created_at + interval '168 hours'
    ),
    CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    UNIQUE (id, expires_at)
);
CREATE INDEX refresh_families_account_idx ON refresh_families(account_id);
CREATE INDEX refresh_families_expiry_idx ON refresh_families(expires_at);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    parent_id uuid UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    UNIQUE (id, family_id),
    FOREIGN KEY (family_id, expires_at) REFERENCES refresh_families(id, expires_at),
    FOREIGN KEY (parent_id, family_id) REFERENCES refresh_tokens(id, family_id),
    CHECK (parent_id IS NULL OR parent_id <> id),
    CHECK (expires_at > created_at),
    CHECK (used_at IS NULL OR (used_at >= created_at AND used_at < expires_at)),
    CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE UNIQUE INDEX refresh_tokens_one_root_idx ON refresh_tokens(family_id) WHERE parent_id IS NULL;
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens(family_id);

CREATE TABLE profile_provisioning_tasks (
    account_id uuid PRIMARY KEY REFERENCES accounts(id),
    created_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    completed_at timestamptz,
    CHECK (next_attempt_at >= created_at),
    CHECK (completed_at IS NULL OR completed_at >= created_at)
);
CREATE INDEX profile_provisioning_due_idx ON profile_provisioning_tasks(next_attempt_at)
    WHERE completed_at IS NULL;

GRANT SELECT, INSERT, UPDATE ON accounts, refresh_families, refresh_tokens,
    profile_provisioning_tasks TO auth_app;
GRANT SELECT, INSERT, DELETE ON account_role_grants TO auth_app;
-- No runtime DELETE of refresh history before expiry, schema-wide grants, or Flyway history grants.
