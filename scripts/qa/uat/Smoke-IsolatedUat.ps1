[CmdletBinding()]
param(
    [string]$ProjectName = ("hk5-uat-tv5-" + (Get-Date -Format 'yyyyMMddHHmmss')),
    [ValidateRange(1025, 65535)][int]$GatewayPort = 18081,
    [ValidateRange(1025, 65535)][int]$FrontendPort = 5174,
    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$start = Join-Path $PSScriptRoot 'Start-IsolatedUat.ps1'
$overlayCompose = Join-Path $PSScriptRoot 'compose.isolated.yml'

& $start -ProjectName $ProjectName -GatewayPort $GatewayPort -FrontendPort $FrontendPort -Build:$Build -Seed
if ($LASTEXITCODE -ne 0) { throw 'The isolated UAT smoke start or seed failed.' }

$env:HK5_UAT_PROJECT = $ProjectName
$env:HK5_UAT_GATEWAY_PORT = "$GatewayPort"
$env:HK5_UAT_FRONTEND_PORT = "$FrontendPort"
Push-Location $root
try {
    $compose = @('--env-file', '.env.demo', '--project-name', $ProjectName, '-f', 'docker/compose.demo.yml', '-f', $overlayCompose)
    $postgres = (& docker compose @compose ps -q postgres).Trim()
    if ([string]::IsNullOrWhiteSpace($postgres)) { throw 'No PostgreSQL container was found in the isolated project.' }

    $expected = @('auth_db', 'user_db', 'seller_db', 'product_db', 'inventory_db', 'cart_db', 'order_db', 'notification_db')
    $actual = @(& docker exec $postgres psql -X -U hk5 -d postgres -At -c "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")
    foreach ($database in $expected) {
        if ($actual -notcontains $database) { throw "Isolated database bootstrap is missing $database." }
        $history = (& docker exec $postgres psql -X -U hk5 -d $database -At -c "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL") | Select-Object -Last 1
        if ($history -ne 't') { throw "Flyway history is absent in $database after service startup." }
    }

    $gateway = "http://127.0.0.1:$GatewayPort"
    foreach ($path in @('/api/categories', '/api/catalog/products')) {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$gateway$path" -Headers @{ Origin = "http://127.0.0.1:$FrontendPort" } -TimeoutSec 20
        if ($response.StatusCode -ne 200) { throw "Seeded local endpoint $path returned $($response.StatusCode)." }
    }
    Write-Output "PASS isolated infrastructure, database bootstrap/Flyway history, and fake two-Store seed for project $ProjectName."
    Write-Output 'NOT_UAT_FINAL: Finance and Inventory pair gates remain OFF; no checkout/COD/settlement acceptance was exercised.'
} finally {
    Pop-Location
}
