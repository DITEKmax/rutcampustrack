param([Parameter(Mandatory=$true)][string]$ExpectedFreezeHash)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$treePath = Join-Path $rootPath '.agent/worktrees/v2-l5b-service-identity'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$freezePath = Join-Path $evidencePath 'h90-source-freeze.json'
if ((Get-FileHash -LiteralPath $freezePath).Hash -ne $ExpectedFreezeHash) { throw 'Freeze manifest mismatch' }
$freeze = Get-Content -LiteralPath $freezePath -Raw | ConvertFrom-Json
foreach ($entry in $freeze.files) {
    if ((Get-FileHash -LiteralPath (Join-Path $treePath $entry.path)).Hash -ne $entry.sha256) { throw "Source mismatch: $($entry.path)" }
}
if ((& git -C $treePath rev-parse HEAD) -ne '13e5fd1985b798bbb61bcc85969e6a74b1fc6837') { throw 'Unexpected implementation base' }
if (Test-Path -LiteralPath (Join-Path $evidencePath 'h90-context.json')) { throw 'H90 already recorded' }
$gradleArguments = @(
 ':services:shared:shared-security:test', '--tests', '*ServiceIdentity*Test', '--tests', '*ServiceTokenCallCredentialsTest',
 ':services:academic-service:academic-app:test', '--tests', '*AssignmentCloseServiceIdentityInterceptorTest', '--tests', '*StudentHomeworkGrpcIdentityInterceptorTest', '--tests', '*AssignmentClosureContractTest', '--tests', '*SubjectAssignmentAuthorityContractTest',
 ':services:schedule-service:schedule-app:test', '--tests', '*AssignmentCloseServiceIdentityInterceptorTest',
 '--no-daemon', '--no-parallel', '--max-workers=1', '--no-problems-report'
)
$startedAt = [DateTime]::UtcNow
$context = [ordered]@{start=$startedAt.ToString('o');baseRevision=$freeze.baseRevision;freezeHash=$ExpectedFreezeHash;worktree=$treePath;command=@('.\gradlew.bat')+$gradleArguments;exitCode=$null;javaVersion=(& java -version 2>&1 | Out-String).Trim();powershell=$PSVersionTable.PSVersion.ToString()}
$checkExit = 1
try {
    Push-Location $treePath
    try {
        & .\gradlew.bat @gradleArguments 1> (Join-Path $evidencePath 'h90-stdout.log') 2> (Join-Path $evidencePath 'h90-stderr.log')
        $checkExit = $LASTEXITCODE
        $context.exitCode = $checkExit
    } finally { Pop-Location }
} finally {
    $context.end = [DateTime]::UtcNow.ToString('o')
    $mismatches = @($freeze.files | Where-Object { (Get-FileHash -LiteralPath (Join-Path $treePath $_.path)).Hash -ne $_.sha256 })
    $context.sourceMismatchCount = $mismatches.Count
    $xmlRoot = Join-Path $evidencePath 'h90-xml'
    if (Test-Path -LiteralPath $xmlRoot) { throw 'H90 XML destination exists' }
    New-Item -ItemType Directory -Path $xmlRoot | Out-Null
    $rows = @()
    foreach ($module in @('services/shared/shared-security','services/academic-service/academic-app','services/schedule-service/schedule-app')) {
        $resultPath = Join-Path $treePath "$module/build/test-results/test"
        if (-not (Test-Path -LiteralPath $resultPath)) { continue }
        foreach ($file in @(Get-ChildItem -LiteralPath $resultPath -Filter 'TEST-*.xml' -File | Where-Object { $_.LastWriteTimeUtc -ge $startedAt })) {
            [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
            $suite = $document.testsuite
            $rows += [pscustomobject]@{module=$module;name=[string]$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped;modifiedUtc=$file.LastWriteTimeUtc.ToString('o')}
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $xmlRoot (($module.Replace('/','_'))+'-'+$file.Name))
        }
    }
    $context.freshTestClasses = $rows.Count
    $rows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidencePath 'h90-tests.json')
    $context | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidencePath 'h90-context.json')
}
$context | ConvertTo-Json -Depth 5
if ($context.sourceMismatchCount -ne 0 -or $context.freshTestClasses -eq 0) { exit 1 }
exit $checkExit
