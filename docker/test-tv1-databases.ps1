$ErrorActionPreference = 'Stop'
$container = docker compose -f (Join-Path $PSScriptRoot 'compose.dev.yml') ps -q postgres
if ($LASTEXITCODE -ne 0 -or !$container) { throw 'PostgreSQL service is not running.' }
$metadata = (docker inspect $container | ConvertFrom-Json)[0]
# Non-loopback forces the image's SCRAM host rule rather than local trust.
$address = @($metadata.NetworkSettings.Networks.PSObject.Properties.Value.IPAddress | Where-Object { $_ })[0]
if (!$address) { throw 'No container network address for password authentication checks.' }

function Invoke-Login([string]$Role, [string]$Database, [string]$Password, [string]$Sql, [string]$ExpectedError = '') {
    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD')
    $env:PGPASSWORD = $Password
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = $Sql | docker exec -i -e PGPASSWORD $container psql -X -q -A -t -h $address -U $Role -d $Database `
            -v ON_ERROR_STOP=1 -v VERBOSITY=verbose 2>&1
        $code = $LASTEXITCODE
        $output = $result -join "`n"
        if ($ExpectedError) {
            if ($code -eq 0 -or $output -notmatch [regex]::Escape($ExpectedError)) {
                throw "FAIL: expected error $ExpectedError for $Role/$Database."
            }
        } elseif ($code -ne 0) { throw "FAIL: $Role/$Database login or SQL verification." }
    } finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
        $ErrorActionPreference = $previousPreference
    }
}

foreach ($service in @('auth', 'user')) {
    $prefix = $service.ToUpperInvariant()
    $other = if ($service -eq 'auth') { 'user' } else { 'auth' }
    foreach ($kind in @('app', 'migrator')) {
        $secretName = if ($kind -eq 'app') { "${prefix}_DB_PASSWORD" } else { "${prefix}_MIGRATION_PASSWORD" }
        $password = [Environment]::GetEnvironmentVariable($secretName)
        if ([string]::IsNullOrWhiteSpace($password)) { throw "$secretName is required." }
        Invoke-Login "${service}_$kind" "${service}_db" $password @"
DO `$$
BEGIN
  IF EXISTS (SELECT FROM pg_roles WHERE rolname=current_user AND
      (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
     OR EXISTS (SELECT FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user))
     OR has_database_privilege(current_user, '${other}_db', 'CONNECT') THEN
    RAISE EXCEPTION 'Role isolation failed';
  END IF;
END `$$;
SELECT current_user, current_database();
"@
        Invoke-Login "${service}_$kind" "${service}_db" 'incorrect-password-for-negative-check' 'SELECT 1;' 'password authentication failed'
        Invoke-Login "${service}_$kind" "${other}_db" $password 'SELECT 1;' 'pg_hba.conf rejects connection'
        Invoke-Login "${service}_$kind" 'hk5_dev' $password 'SELECT 1;' 'pg_hba.conf rejects connection'
        Invoke-Login "${service}_$kind" 'postgres' $password 'SELECT 1;' 'pg_hba.conf rejects connection'
        if ($kind -eq 'app') {
            Invoke-Login "${service}_$kind" "${service}_db" $password 'BEGIN; CREATE TABLE public.__tv1_readiness_probe(id int); ROLLBACK;' '42501'
            Invoke-Login "${service}_$kind" "${service}_db" $password 'BEGIN; CREATE SCHEMA __tv1_readiness_probe; ROLLBACK;' '42501'
            Invoke-Login "${service}_$kind" "${service}_db" $password 'BEGIN; CREATE TEMP TABLE __tv1_readiness_probe(id int); ROLLBACK;' '42501'
            Invoke-Login "${service}_$kind" "${service}_db" $password "SET ROLE ${service}_migrator;" '42501'
        } else {
            Invoke-Login "${service}_$kind" "${service}_db" $password 'BEGIN; CREATE TABLE public.__tv1_readiness_probe(id int); INSERT INTO public.__tv1_readiness_probe VALUES (1); ROLLBACK;'
        }
        Write-Output "PASS: ${service}_$kind own DB login, wrong password, cross-DB denial and DDL privileges."
    }
}
