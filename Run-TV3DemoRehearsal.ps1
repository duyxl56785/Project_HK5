[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedSha,
  [ValidatePattern('^hk5-tv3-rehearsal$')][string]$ApprovedIsolatedStack,
  [switch]$InstallDeps, [switch]$UsePostgresReadOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
if ((Get-Location).Path -ne $root) { throw "Chạy từ root chứa runner: $root" }
function Read-Command($exe, [string[]]$commandArgs) {
  $result = @(& $exe @commandArgs 2>$null)
  if ($LASTEXITCODE -ne 0) { throw "$exe $($commandArgs[0]) thất bại (exit $LASTEXITCODE)" }
  return $result
}
function Check-Git {
  $actual = Read-Command git @('rev-parse', 'HEAD')
  $gitRoot = Read-Command git @('rev-parse', '--show-toplevel')
  if ($actual -cne $ExpectedSha -or [IO.Path]::GetFullPath([string]$gitRoot) -ne $root) { throw 'Sai Git HEAD hoặc Git root' }
  $remote = Read-Command git @('config','--get','remote.origin.url')
  if ($remote -notmatch '^(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)duyxl56785/Project_HK5(?:\.git)?$') {
    throw 'Origin không phải Project_HK5 chính thức'
  }
  if (@(Read-Command git @('status','--porcelain','--untracked-files=no')).Count) { throw 'Có thay đổi tracked; dừng rehearsal' }
}
Check-Git
if (!$ApprovedIsolatedStack) { throw 'Thiếu xác nhận tên Compose project cô lập đã được TV2 duyệt' }
$origin = $null
if (![uri]::TryCreate($env:E2E_BASE_URL, [UriKind]::Absolute, [ref]$origin) -or
    $origin.Scheme -ne 'http' -or $origin.Host -notin 'localhost','127.0.0.1' -or
    $origin.UserInfo -or $origin.AbsolutePath -ne '/' -or $origin.Query -or $origin.Fragment) {
  throw 'E2E_BASE_URL phải là HTTP loopback origin, không có path, query hoặc credentials'
}
if (!(Test-Path -LiteralPath (Join-Path $root '.env.demo') -PathType Leaf)) {
  throw 'Thiếu .env.demo tại root đã được TV2 duyệt'
}
if ($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^(npipe|unix):') { throw 'Docker Engine không phải endpoint local' }
$context = Read-Command docker @('context','show')
$endpoint = Read-Command docker @('context','inspect',[string]$context,'--format','{{.Endpoints.docker.Host}}')
if ($endpoint -notmatch '^(npipe|unix):') { throw 'Docker context không phải endpoint local' }

$images = [ordered]@{}
$containers = [ordered]@{}
$project = $ApprovedIsolatedStack
foreach ($service in 'frontend','api-gateway','postgres','inventory-service','order-service','cart-service','product-service','seller-service','auth-service','user-service') {
  $name = "$project-$service-1"
  $format = '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{index .Config.Labels "com.docker.compose.project.working_dir"}}|{{.State.Running}}|{{.Id}}|{{.Image}}'
  $label,$role,$working,$running,$id,$image = (Read-Command docker @('inspect','--format',$format,$name)) -split '\|',6
  if (!$working -or $label -cne $project -or $role -cne $service -or
      [IO.Path]::GetFullPath($working) -ne (Join-Path $root 'docker') -or $running -cne 'true' -or
      $id -cnotmatch '^[a-f0-9]{64}$' -or $image -cnotmatch '^sha256:[a-f0-9]{64}$') {
    throw "Stack không khớp project, worktree hoặc service chưa chạy: $service"
  }
  $containers[$service] = $id
  $images[$service] = $image
}
$binding = @(Read-Command docker @('port',"$project-frontend-1",'80/tcp'))
if ($binding.Count -ne 1 -or $binding[0] -cne "127.0.0.1:$($origin.Port)") {
  throw 'Frontend loopback port không khớp container đã xác minh'
}
$mounts = (Read-Command docker @('inspect','--format','{{json .Mounts}}',"$project-postgres-1")) | ConvertFrom-Json
$dataMount = @($mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
if ($dataMount.Count -ne 1 -or $dataMount[0].Type -ne 'volume' -or !$dataMount[0].Name) { throw 'Thiếu volume PostgreSQL riêng' }
$volumeName = $dataMount[0].Name
$volumeOwner = Read-Command docker @('volume','inspect','--format','{{index .Labels "com.docker.compose.project"}}',$volumeName)
$volumeUsers = @(Read-Command docker @('ps','-a','--filter',"volume=$volumeName",'--format','{{.Names}}'))
if ($volumeOwner -cne $project -or $volumeUsers.Count -ne 1 -or $volumeUsers[0] -cne "$project-postgres-1") {
  throw 'PostgreSQL volume có thể dùng chung hoặc khác Compose project'
}
foreach ($rule in @(@{ service='inventory-service'; key='INVENTORY_ORDER_PAIR_ENABLED' },
                    @{ service='order-service'; key='HK5_FINANCE_POLICY_APPROVED' })) {
  $key = $rule.key
  $format = '{{range .Config.Env}}{{if or (eq . "' + $key + '=true") (eq . "' + $key + '=false")}}{{println .}}{{end}}{{end}}'
  $value = @(Read-Command docker @('inspect','--format',$format,"$project-$($rule.service)-1") | Where-Object {$_})
  if ($value.Count -ne 1 -or $value[0] -cne "$key=true") { throw "Feature gate chưa bật sẵn: $key" }
}

$start = [DateTimeOffset]::Now
$evidence = Join-Path ([IO.Path]::GetTempPath()) ("tv3-demo-rehearsal-" + $start.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N'))
if ([IO.Path]::GetFullPath($evidence).StartsWith($root + '\',[StringComparison]::OrdinalIgnoreCase)) {
  throw 'Evidence root phải nằm ngoài repository'
}
$null = New-Item -ItemType Directory -Path $evidence
Write-Host "Evidence directory $evidence"
$environment = @{
  TV3_REHEARSAL_SHA=$ExpectedSha
  TV3_REHEARSAL_PROJECT=$project
  TV3_REHEARSAL_PG_READ_ONLY=$(if($UsePostgresReadOnly){'1'}else{'0'})
  TV3_REHEARSAL_EVIDENCE=$evidence
  PLAYWRIGHT_JSON_OUTPUT_FILE=(Join-Path $evidence 'playwright-results.json')
  DEBUG=$null
  NODE_DEBUG=$null
  PWDEBUG=$null
}
$prior = @{}
foreach ($key in $environment.Keys) {
  $prior[$key] = [Environment]::GetEnvironmentVariable($key,'Process')
  [Environment]::SetEnvironmentVariable($key,$environment[$key],'Process')
}

$code = 1
$phase = 'dependencies'
$playwrightExit = $null
Push-Location -LiteralPath (Join-Path $root 'frontend')
try {
  if ($InstallDeps) {
    & npm.cmd ci --no-audit --no-fund 2>&1 | Out-File -LiteralPath (Join-Path $evidence 'npm-install.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'npm ci thất bại' }
  }
  $pw = Join-Path (Get-Location).Path 'node_modules\.bin\playwright.cmd'
  if (!(Test-Path -LiteralPath $pw)) { throw 'Playwright chưa cài trong frontend' }
  if ($InstallDeps) {
    & $pw install chromium 2>&1 | Out-File -LiteralPath (Join-Path $evidence 'chromium-install.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Cài Chromium thất bại' }
  }
  $phase = 'check-stack-before-test'
  Check-Git
  foreach ($service in $containers.Keys) {
    $state = Read-Command docker @('inspect','--format','{{.Id}}|{{.State.Running}}',"$project-$service-1")
    if ($state -cne ($containers[$service]+'|true')) { throw "Container đã đổi trước test: $service" }
  }
  $phase = 'playwright'
  & $pw test 'e2e/tv3-demo-rehearsal.spec.ts' --workers 1 --retries 0 --trace off --reporter list,json --output (Join-Path $evidence 'test-results') 2>&1 |
    Out-File -LiteralPath (Join-Path $evidence 'playwright.log') -Encoding utf8
  $playwrightExit = $LASTEXITCODE
  if ($playwrightExit -ne 0) { $code=$playwrightExit; throw 'Playwright thất bại, xem log private' }
  if (!(Test-Path -LiteralPath (Join-Path $evidence 'receipt.json'))) { throw 'Thiếu receipt, lượt chạy có thể đã skip' }
  $receipt = Get-Content -LiteralPath (Join-Path $evidence 'receipt.json') -Raw | ConvertFrom-Json
  $wanted = if($UsePostgresReadOnly){'PASS'}else{'PARTIAL'}
  if($receipt.sourceSha -cne $ExpectedSha -or $receipt.project -cne $project -or $receipt.status -cne $wanted){
    throw 'Receipt không khớp SHA, project hoặc phạm vi đã chạy'
  }
  $code = 0
  $phase = 'complete'
} catch {
  Write-Host "Rehearsal dừng tại $phase. Chi tiết kiểm thử nằm trong evidence private."
} finally {
  Pop-Location
  foreach ($key in $prior.Keys) { [Environment]::SetEnvironmentVariable($key,$prior[$key],'Process') }
  $runReceipt = [ordered]@{
    kind='TV3_CHECKOUT_REHEARSAL_RUN'
    sourceSha=$ExpectedSha
    stackBuildSha='NOT_ATTESTED'
    project=$project
    imageIds=$images
    cwd=(Join-Path $root 'frontend')
    startedAt=$start.ToString('o')
    finishedAt=[DateTimeOffset]::Now.ToString('o')
    phase=$phase
    proofScope=$(if($UsePostgresReadOnly){'POSTGRES_READ_ONLY'}else{'CATALOG_API_ONLY'})
    status=$(if($code -ne 0){'FAIL'}elseif($UsePostgresReadOnly){'PASS'}else{'PARTIAL'})
    exitCode=$code
    playwrightExitCode=$playwrightExit
  }
  # Windows PowerShell 5.1 (.NET Framework) does not support Set-Content -Encoding utf8NoBOM.
  # Write the same UTF-8 no-BOM JSON with a .NET Framework-compatible API.
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [IO.File]::WriteAllText(
    (Join-Path $evidence 'run.json'),
    (($runReceipt | ConvertTo-Json -Depth 5) + [Environment]::NewLine),
    $utf8NoBom
  )
  # Path.GetRelativePath is unavailable in Windows PowerShell 5.1 / .NET Framework.
  # Build paths relative to the fixed evidence root, with an explicit prefix guard.
  $evidencePrefix = [IO.Path]::GetFullPath($evidence).TrimEnd(
    [IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar
  ) + [IO.Path]::DirectorySeparatorChar
  Get-ChildItem -LiteralPath $evidence -Recurse -File | Sort-Object FullName | ForEach-Object {
    $fullFile = [IO.Path]::GetFullPath($_.FullName)
    if (!$fullFile.StartsWith($evidencePrefix, [StringComparison]::OrdinalIgnoreCase)) {
      throw 'Evidence path outside approved root'
    }
    $relative = $fullFile.Substring($evidencePrefix.Length).Replace([char]92,[char]47)
    (Get-FileHash -LiteralPath $fullFile -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $relative
  } | Set-Content -LiteralPath (Join-Path $evidence 'SHA256SUMS.txt') -Encoding ascii
}
exit $code
