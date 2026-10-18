# Run from the repository with credentials already in the process environment.
$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.dev.yml'
$secretNames = @('AUTH_DB_PASSWORD', 'AUTH_MIGRATION_PASSWORD', 'USER_DB_PASSWORD', 'USER_MIGRATION_PASSWORD')
foreach ($name in $secretNames) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 32) {
        throw "$name must contain an independently generated secret of at least 32 characters."
    }
}
if (@($secretNames | ForEach-Object { [Environment]::GetEnvironmentVariable($_) } | Select-Object -Unique).Count -ne 4) {
    throw 'Use four different application/migration passwords.'
}
$container = docker compose -f $composeFile ps -q postgres
if ($LASTEXITCODE -ne 0 -or !$container) { throw 'Start this repository PostgreSQL service first.' }
$metadata = (docker inspect $container | ConvertFrom-Json)[0]
$expectedDirectory = $PSScriptRoot.Replace('\', '/').TrimEnd('/')
$actualDirectory = $metadata.Config.Labels.'com.docker.compose.project.working_dir'.Replace('\', '/').TrimEnd('/')
if ($actualDirectory -ne $expectedDirectory -or $metadata.Config.Labels.'com.docker.compose.service' -ne 'postgres' -or
    $metadata.State.Health.Status -ne 'healthy') {
    throw 'Refusing a container outside this repository or an unhealthy PostgreSQL service.'
}

function Invoke-AdminSql([string]$Sql, [string]$Stage) {
    # Never print SQL/psql errors: password statements may appear in error context.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = $Sql | docker exec -i -e AUTH_DB_PASSWORD -e AUTH_MIGRATION_PASSWORD -e USER_DB_PASSWORD -e USER_MIGRATION_PASSWORD `
            $container psql -X -q -U hk5 -d postgres -v ON_ERROR_STOP=1 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Provisioning stopped at $Stage; see docker/README.md recovery instructions. No database is dropped." }
    } finally { $ErrorActionPreference = $previousPreference }
}

# Check BOTH services before any mutation; do not adopt an unrelated database/role.
Invoke-AdminSql @'
DO $$
DECLARE item record;
BEGIN
  IF current_setting('server_version_num')::int / 10000 <> 16 THEN
    RAISE EXCEPTION 'PostgreSQL 16 required';
  END IF;
  FOR item IN SELECT * FROM pg_roles WHERE rolname IN ('auth_app','auth_migrator','user_app','user_migrator') LOOP
    IF shobj_description(item.oid, 'pg_authid') IS DISTINCT FROM 'hk5-tv1-readiness-v1'
       OR item.rolsuper OR item.rolcreatedb OR item.rolcreaterole OR item.rolreplication OR item.rolbypassrls
       OR NOT item.rolcanlogin
       OR EXISTS (SELECT 1 FROM pg_auth_members WHERE roleid=item.oid OR member=item.oid) THEN
      RAISE EXCEPTION 'Unrecognized or overprivileged role: %', item.rolname;
    END IF;
  END LOOP;
  FOR item IN SELECT *, pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname IN ('auth_db','user_db') LOOP
    IF item.owner <> replace(item.datname, '_db', '_migrator')
       OR shobj_description(item.oid, 'pg_database') IS DISTINCT FROM 'hk5-tv1-readiness-v1' THEN
      RAISE EXCEPTION 'Unrecognized database/owner: %', item.datname;
    END IF;
  END LOOP;
END $$;
'@ 'global preflight'

foreach ($service in @('auth', 'user')) {
    $prefix = $service.ToUpperInvariant()
    # Names come only from the fixed service list; secrets are read by psql from env.
    Invoke-AdminSql @"
\set QUIET on
SET log_statement = 'none';
SET log_min_duration_statement = -1;
SET log_min_error_statement = 'panic';
SET password_encryption = 'scram-sha-256';
\getenv app_password ${prefix}_DB_PASSWORD
\getenv migration_password ${prefix}_MIGRATION_PASSWORD
BEGIN;
SELECT format('CREATE ROLE ${service}_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='${service}_app') \gexec
SELECT format('CREATE ROLE ${service}_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', :'migration_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='${service}_migrator') \gexec
COMMENT ON ROLE ${service}_app IS 'hk5-tv1-readiness-v1';
COMMENT ON ROLE ${service}_migrator IS 'hk5-tv1-readiness-v1';
COMMIT;
SELECT 'CREATE DATABASE ${service}_db OWNER ${service}_migrator TEMPLATE template0'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='${service}_db') \gexec
COMMENT ON DATABASE ${service}_db IS 'hk5-tv1-readiness-v1';
\connect ${service}_db
BEGIN;
REVOKE ALL ON DATABASE ${service}_db FROM PUBLIC;
GRANT CONNECT ON DATABASE ${service}_db TO ${service}_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO ${service}_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ${service}_migrator REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
COMMIT;
"@ "$service roles/database/privileges"
}
Write-Output 'PASS: auth_db/user_db provisioned; existing passwords and business schemas were not changed.'
