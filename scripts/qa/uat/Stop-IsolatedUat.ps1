[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ProjectName)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^hk5-uat-[a-z0-9-]{3,40}$') {
    throw 'Refusing to remove a project that is not explicitly named hk5-uat-*.'
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$baseCompose = Join-Path $root 'docker\compose.demo.yml'
$overlayCompose = Join-Path $PSScriptRoot 'compose.isolated.yml'
$envFile = Join-Path $root '.env.demo'
if (-not (Test-Path $envFile)) { throw 'The local UAT env file is absent; inspect resources manually instead of guessing.' }

$env:HK5_UAT_PROJECT = $ProjectName
$env:HK5_UAT_GATEWAY_PORT = '18081'
$env:HK5_UAT_FRONTEND_PORT = '5174'
Push-Location $root
try {
    # Compose removes only this named project's containers/network and its two declared UAT volumes.
    & docker compose --env-file '.env.demo' --project-name $ProjectName -f $baseCompose -f $overlayCompose down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Isolated UAT cleanup failed; inspect only the named project before retrying.' }
} finally {
    Pop-Location
}
