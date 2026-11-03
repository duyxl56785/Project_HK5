[CmdletBinding()]
param(
    [string]$ProjectName = ("hk5-uat-tv5-" + (Get-Date -Format 'yyyyMMddHHmmss')),
    [ValidateRange(1025, 65535)][int]$GatewayPort = 18081,
    [ValidateRange(1025, 65535)][int]$FrontendPort = 5174,
    [switch]$Build,
    [switch]$Seed,
    [switch]$ConfigOnly
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

function Get-SharedDemoFingerprint([string]$Root) {
    $targets = @((Join-Path $Root '.env.demo'), (Join-Path $Root 'docker\secrets'))
    $items = foreach ($target in $targets) {
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            "file|$target|$((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash)"
        } elseif (Test-Path -LiteralPath $target -PathType Container) {
            Get-ChildItem -LiteralPath $target -File -Recurse -Force | Sort-Object FullName | ForEach-Object {
                "file|$($_.FullName)|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)"
            }
        } else { "missing|$target" }
    }
    return [string]::Join("`n", @($items))
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
$sharedBefore = Get-SharedDemoFingerprint $root
$runtimeRoot = Join-Path ([IO.Path]::GetTempPath()) (Join-Path 'Project_HK5_UAT' $ProjectName)
if (Test-Path -LiteralPath $runtimeRoot) { throw "UAT runtime directory already exists: $runtimeRoot" }
$envFile = Join-Path $runtimeRoot '.env'
$secretsDir = Join-Path $runtimeRoot 'secrets'
$metadataFile = Join-Path $runtimeRoot 'metadata.json'

Push-Location $root
try {
    New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    $env:HK5_UAT_ENV_FILE = $envFile
    $env:HK5_UAT_SECRETS_DIR = $secretsDir.Replace('\', '/')
    $env:HK5_UAT_ALLOWED_ORIGINS = "http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort"
    & node (Join-Path $PSScriptRoot 'Generate-IsolatedUatEnv.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated UAT environment generation failed.' }

    $content = [IO.File]::ReadAllText($envFile)
    foreach ($gate in @('INVENTORY_ORDER_PAIR_ENABLED=false', 'HK5_FINANCE_POLICY_APPROVED=false')) {
        if ($content -notmatch [regex]::Escape($gate)) { throw "Required fail-closed gate is not off: $gate" }
    }

    $env:HK5_UAT_PROJECT = $ProjectName
    $env:HK5_UAT_GATEWAY_PORT = "$GatewayPort"
    $env:HK5_UAT_FRONTEND_PORT = "$FrontendPort"
    $metadataJson = [ordered]@{
        projectName = $ProjectName
        gatewayPort = $GatewayPort
        frontendPort = $FrontendPort
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($metadataFile, $metadataJson, (New-Object System.Text.UTF8Encoding($false)))
    $compose = @('--env-file', $envFile, '--project-name', $ProjectName, '-f', $baseCompose, '-f', $overlayCompose)

    & docker compose @compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'The isolated Compose configuration is invalid.' }
    if ((Get-SharedDemoFingerprint $root) -ne $sharedBefore) { throw 'Refusing to continue: shared .env.demo or docker/secrets changed.' }

    if ($ConfigOnly) {
        Write-Output 'PASS isolated config uses a per-project env/secrets directory and leaves shared demo artifacts unchanged.'
        return
    }

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
            & node (Join-Path $PSScriptRoot 'Seed-IsolatedUat.mjs')
            if ($LASTEXITCODE -ne 0) { throw 'Two-Store fake-data seed failed.' }
        } finally {
            $env:GATEWAY_URL = $oldGateway
        }
    }

    Write-Output "Isolated UAT rehearsal stack is ready: project=$ProjectName gateway=http://127.0.0.1:$GatewayPort frontend=http://127.0.0.1:$FrontendPort"
    Write-Output 'Finance and Inventory pair gates are OFF. This is a local rehearsal environment, not final UAT.'
} finally {
    $sharedAfter = Get-SharedDemoFingerprint $root
    if ($sharedAfter -ne $sharedBefore) { throw 'Shared demo artifacts changed; UAT isolation failed closed.' }
    if ($ConfigOnly -and (Test-Path -LiteralPath $runtimeRoot)) { Remove-Item -LiteralPath $runtimeRoot -Recurse -Force }
    Remove-Item Env:HK5_UAT_ENV_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:HK5_UAT_SECRETS_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:HK5_UAT_ALLOWED_ORIGINS -ErrorAction SilentlyContinue
    Pop-Location
}
