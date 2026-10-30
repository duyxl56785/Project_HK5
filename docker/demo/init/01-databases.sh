#!/bin/sh
# Runs once on an empty demo volume. Auth/User keep their TV1 migrator/app role split;
# the other services own one database each (no cross-database foreign keys).
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -v auth_app="$(cat /run/secrets/AUTH_DB_PASSWORD)" -v auth_mig="$(cat /run/secrets/AUTH_MIGRATION_PASSWORD)" \
  -v user_app="$(cat /run/secrets/USER_DB_PASSWORD)" -v user_mig="$(cat /run/secrets/USER_MIGRATION_PASSWORD)" <<'SQL'
CREATE ROLE auth_migrator LOGIN PASSWORD :'auth_mig';
CREATE ROLE auth_app LOGIN PASSWORD :'auth_app';
CREATE DATABASE auth_db OWNER auth_migrator;
CREATE ROLE user_migrator LOGIN PASSWORD :'user_mig';
CREATE ROLE user_app LOGIN PASSWORD :'user_app';
CREATE DATABASE user_db OWNER user_migrator;
CREATE DATABASE seller_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE cart_db;
CREATE DATABASE order_db;
CREATE DATABASE notification_db;
SQL
