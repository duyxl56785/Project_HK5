[CmdletBinding()]
param(
    [string]$ProjectName = ("hk5-uat-tv5-" + (Get-Date -Format 'yyyyMMddHHmmss')),
    [ValidateRange(1025, 65535)][int]$GatewayPort = 18081,
    [ValidateRange(1025, 65535)][int]$FrontendPort = 5174,
    [switch]$Build,
    [switch]$Seed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-UatProject([string]$Name) {
    if ($Name -notmatch '^hk5-uat-[a-z0-9-]{3,40}$') {
        throw 'ProjectName must start with hk5-uat- and contain only lowercase letters, digits, and hyphens.'
    }
}

function Assert-LoopbackPortFree([int]$Port) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Loopback port $Port is already listening; choose another UAT port."
    }
}

function Test-ApplicationReady([string]$Gateway, [int]$Frontend) {
    $paths = @('/api/categories', '/api/stores', '/api/catalog/products', '/api/cart', '/api/orders', '/api/notifications')
    foreach ($path in $paths) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$Gateway$path" -Headers @{ Origin = "http://127.0.0.1:$Frontend" } -TimeoutSec 5
            if ($response.StatusCode -ge 500) { return $false }
        } catch {
            $response = if ($_.Exception.PSObject.Properties['Response']) { $_.Exception.Response } else { $null }
            if ($response -and [int]$response.StatusCode -lt 500) { continue }
            return $false
        }
    }
    return $true
}

Assert-UatProject $ProjectName
Assert-LoopbackPortFree $GatewayPort
Assert-LoopbackPortFree $FrontendPort

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$baseCompose = Join-Path $root 'docker\compose.demo.yml'
$overlayCompose = Join-Path $PSScriptRoot 'compose.isolated.yml'
$envFile = Join-Path $root '.env.demo'

Push-Location $root
try {
    # This generator is deliberately called without --approved-* flags. It also turns either stale gate back off.
    & node 'scripts/demo/gen-env.mjs'
    if ($LASTEXITCODE -ne 0) { throw 'Local demo environment generation failed.' }

    $content = [IO.File]::ReadAllText($envFile)
    $origins = "ALLOWED_ORIGINS=http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort"
    if ($content -match '(?m)^ALLOWED_ORIGINS=.*$') {
        $content = [regex]::Replace($content, '(?m)^ALLOWED_ORIGINS=.*$', $origins)
    } else {
        $content += "`n$origins`n"
    }
    [IO.File]::WriteAllText($envFile, $content, [Text.UTF8Encoding]::new($false))

    foreach ($gate in @('INVENTORY_ORDER_PAIR_ENABLED=false', 'HK5_FINANCE_POLICY_APPROVED=false')) {
        if ($content -notmatch [regex]::Escape($gate)) { throw "Required fail-closed gate is not off: $gate" }
    }

    $env:HK5_UAT_PROJECT = $ProjectName
    $env:HK5_UAT_GATEWAY_PORT = "$GatewayPort"
    $env:HK5_UAT_FRONTEND_PORT = "$FrontendPort"
    $compose = @('--env-file', '.env.demo', '--project-name', $ProjectName, '-f', $baseCompose, '-f', $overlayCompose)

    & docker compose @compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'The isolated Compose configuration is invalid.' }

    if ($Build) {
        Push-Location (Join-Path $root 'backend')
        try {
            & '.\gradlew.bat' bootJar -q
        } finally {
            Pop-Location
        }
        if ($LASTEXITCODE -ne 0) { throw 'Backend bootJar build failed.' }
    }

    & docker compose @compose up -d --wait --wait-timeout 240
    if ($LASTEXITCODE -ne 0) { throw 'The isolated UAT stack did not become ready.' }

    if ($Seed) {
        $oldGateway = $env:GATEWAY_URL
        try {
            $env:GATEWAY_URL = "http://127.0.0.1:$GatewayPort"
            $deadline = (Get-Date).AddMinutes(4)
            while (-not (Test-ApplicationReady $env:GATEWAY_URL $FrontendPort)) {
                if ((Get-Date) -ge $deadline) { throw 'Gateway routes did not become ready within four minutes.' }
                Start-Sleep -Seconds 3
            }
            & node 'scripts/demo/seed.mjs'
            if ($LASTEXITCODE -ne 0) { throw 'Two-Store fake-data seed failed.' }
        } finally {
            $env:GATEWAY_URL = $oldGateway
        }
    }

    Write-Output "Isolated UAT rehearsal stack is ready: project=$ProjectName gateway=http://127.0.0.1:$GatewayPort frontend=http://127.0.0.1:$FrontendPort"
    Write-Output 'Finance and Inventory pair gates are OFF. This is a local rehearsal environment, not final UAT.'
} finally {
    Pop-Location
}
