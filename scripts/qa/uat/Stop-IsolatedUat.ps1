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
$runtimeRoot = Join-Path ([IO.Path]::GetTempPath()) (Join-Path 'Project_HK5_UAT' $ProjectName)
$envFile = Join-Path $runtimeRoot '.env'
$secretsDir = Join-Path $runtimeRoot 'secrets'
if (-not (Test-Path -LiteralPath $envFile) -or -not (Test-Path -LiteralPath $secretsDir)) {
    throw 'The isolated UAT runtime env/secrets are absent; inspect named resources manually instead of guessing.'
}

foreach ($volume in @("$ProjectName-pgdemo", "$ProjectName-productimages")) {
    $owner = (& docker volume inspect $volume --format '{{ index .Labels "com.docker.compose.project" }}' 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $owner -ne $ProjectName) {
        throw "Refusing cleanup: $volume is absent or is not labeled for project $ProjectName."
    }
}

$env:HK5_UAT_PROJECT = $ProjectName
$env:HK5_UAT_GATEWAY_PORT = '18081'
$env:HK5_UAT_FRONTEND_PORT = '5174'
$env:HK5_UAT_SECRETS_DIR = $secretsDir.Replace('\', '/')
Push-Location $root
$cleaned = $false
try {
    # Compose removes only this named project's containers/network and its two declared UAT volumes.
    & docker compose --env-file $envFile --project-name $ProjectName -f $baseCompose -f $overlayCompose down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Isolated UAT cleanup failed; inspect only the named project before retrying.' }
    $cleaned = $true
} finally {
    Remove-Item Env:HK5_UAT_SECRETS_DIR -ErrorAction SilentlyContinue
    Pop-Location
}
if ($cleaned -and (Test-Path -LiteralPath $runtimeRoot)) {
    Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
}
