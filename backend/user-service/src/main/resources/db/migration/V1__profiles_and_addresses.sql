DO $$
BEGIN
    IF current_database() <> 'user_db' OR current_user <> 'user_migrator' THEN
        RAISE EXCEPTION 'User migration requires user_migrator on user_db';
    END IF;
END $$;

-- account_id is Auth's logical accountId (JWT sub); no cross-database foreign key.
CREATE TABLE user_profiles (
    account_id varchar(128) COLLATE "C" PRIMARY KEY,
    display_name varchar(100),
    phone varchar(16) CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{1,14}$'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE user_addresses (
    id uuid PRIMARY KEY,
    account_id varchar(128) COLLATE "C" NOT NULL REFERENCES user_profiles(account_id),
    recipient_name varchar(100) NOT NULL CHECK (length(btrim(recipient_name)) > 0),
    phone varchar(16) NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{1,14}$'),
    line varchar(255) NOT NULL CHECK (length(btrim(line)) > 0),
    city varchar(100) NOT NULL CHECK (length(btrim(city)) > 0),
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL
);
CREATE INDEX user_addresses_account_idx ON user_addresses(account_id);
CREATE UNIQUE INDEX user_addresses_one_default ON user_addresses(account_id) WHERE is_default;

GRANT SELECT, INSERT, UPDATE ON user_profiles TO user_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_addresses TO user_app;
