param(
    [Parameter(Mandatory=$true)][string]$BuildLogPath,
    [Parameter(Mandatory=$true)][int]$BuildExitCode,
    [string]$OutputDirectory='reports/evidence/w1-followup'
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
$outputPath=Join-Path $repoRoot $OutputDirectory
$buildLog=(Resolve-Path -LiteralPath $BuildLogPath).Path
if($BuildExitCode -ne 0){throw 'Build failed; preserve its log and report FAIL before collecting PASS evidence'}
if(!(Select-String -LiteralPath $buildLog -Pattern '^BUILD SUCCESSFUL' -Quiet)){throw 'Build success marker missing'}
New-Item -ItemType Directory -Force $outputPath | Out-Null
$resultDirectories=@('backend/product-service/build/test-results/test','backend/order-service/build/test-results/test')
$xmlFiles=@(foreach($directory in $resultDirectories){Get-ChildItem -LiteralPath (Join-Path $repoRoot $directory) -Filter 'TEST-*.xml'})
$expected=@('com.projecthk5.productservice.review.ReviewRulesTest','com.projecthk5.productservice.review.ReviewRulesBoundaryTest','com.projecthk5.orderservice.finance.FinanceRulesTest','com.projecthk5.orderservice.finance.FinanceRulesBoundaryTest')
$cases=@()
$suites=foreach($file in $xmlFiles){
    [xml]$xml=Get-Content -LiteralPath $file.FullName
    $suite=$xml.testsuite
    if($suite.name -notin $expected){throw "Unexpected suite: $($suite.name)"}
    foreach($case in $suite.testcase){
        $state=if($case.failure -or $case.error){'FAIL'}elseif($case.skipped){'NOT_RUN'}else{'PASS'}
        $cases+=[pscustomobject]@{suite=$suite.name;test=$case.name;seconds=$case.time;status=$state}
    }
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $outputPath $file.Name)
    [pscustomobject]@{suite=$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped;xml_sha256=(Get-FileHash $file.FullName -Algorithm SHA256).Hash}
}
if($suites.Count -ne $expected.Count){throw 'Missing expected test suite'}
if(@($cases|Where-Object status -ne 'PASS').Count -gt 0){throw 'Some cases are not PASS; do not publish successful summary'}
$suites|Export-Csv -LiteralPath (Join-Path $outputPath 'suites.csv') -NoTypeInformation
$cases|Export-Csv -LiteralPath (Join-Path $outputPath 'test-cases.csv') -NoTypeInformation
Copy-Item -LiteralPath $buildLog -Destination (Join-Path $outputPath 'build.log')
$sourceFiles=@('backend/product-service/src/main/java/com/projecthk5/productservice/review/ReviewRules.java','backend/product-service/src/test/java/com/projecthk5/productservice/review/ReviewRulesTest.java','backend/product-service/src/test/java/com/projecthk5/productservice/review/ReviewRulesBoundaryTest.java','backend/order-service/src/main/java/com/projecthk5/orderservice/finance/FinanceRules.java','backend/order-service/src/test/java/com/projecthk5/orderservice/finance/FinanceRulesTest.java','backend/order-service/src/test/java/com/projecthk5/orderservice/finance/FinanceRulesBoundaryTest.java')
$sources=foreach($source in $sourceFiles){[pscustomobject]@{path=$source;sha256=(Get-FileHash (Join-Path $repoRoot $source) -Algorithm SHA256).Hash}}
$manifest=[ordered]@{captured_at_utc=[DateTime]::UtcNow.ToString('o');build_command='gradle-8.14.3 -p backend :product-service:build :order-service:build --rerun-tasks --no-daemon';exit_code=$BuildExitCode;scope='TV5 local rules only; not HTTP/Kafka/provider-consumer/E2E';tests=$cases.Count;pass=$cases.Count;fail=0;not_run=0;build_log_sha256=(Get-FileHash $buildLog -Algorithm SHA256).Hash;source_files=$sources}
$manifest|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $outputPath 'run.json') -Encoding utf8
$suites|Format-Table -AutoSize
Write-Output "Collected $($cases.Count) current JUnit invocations"
