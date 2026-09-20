param([Parameter(Mandatory=$true)][string]$ExpectedFreezeHash)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$treePath = Join-Path $rootPath '.agent/worktrees/v2-l5b-recurring-writer'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$freezePath = Join-Path $evidencePath 'h101-source-freeze.json'
$planPath = Join-Path $evidencePath 'h101-plan.json'
$contextPath = Join-Path $evidencePath 'h101-context.json'
if (Test-Path -LiteralPath $contextPath) { throw 'H101 already recorded; do not overwrite' }
if ((Get-FileHash -LiteralPath $freezePath).Hash -ne $ExpectedFreezeHash) { throw 'Freeze manifest mismatch' }
$freeze = Get-Content -LiteralPath $freezePath -Raw | ConvertFrom-Json
$plan = Get-Content -LiteralPath $planPath -Raw | ConvertFrom-Json
function Get-SourceMismatches {
    foreach ($record in $freeze.files) {
        $targetPath = Join-Path $treePath $record.path
        if ($record.deleted) { if (Test-Path -LiteralPath $targetPath) { $record.path }; continue }
        if (-not (Test-Path -LiteralPath $targetPath) -or (Get-FileHash -LiteralPath $targetPath).Hash -ne $record.sha256) { $record.path }
    }
}
if (@(Get-SourceMismatches).Count) { throw 'Source differs from freeze' }
if ((& git -C $treePath rev-parse HEAD) -ne $freeze.baseRevision) { throw 'Base revision differs' }
$gradleArguments = @($plan.arguments)
$startedAt = [DateTime]::UtcNow
$context = [ordered]@{stage='H101';start=$startedAt.ToString('o');baseRevision=$freeze.baseRevision;freezeHash=$ExpectedFreezeHash;planHash=(Get-FileHash -LiteralPath $planPath).Hash;worktree=$treePath;command=@('.\gradlew.bat')+$gradleArguments;exitCode=$null;javaVersion=(& java -version 2>&1 | Out-String).Trim();powershell=$PSVersionTable.PSVersion.ToString();testcontainersReuse='false'}
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
@(docker ps -a --format '{{.ID}}') | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $evidencePath 'h101-containers-before.json')
$checkExit = 1
try {
    Push-Location $treePath
    try {
        & .\gradlew.bat @gradleArguments 1> (Join-Path $evidencePath 'h101-stdout.log') 2> (Join-Path $evidencePath 'h101-stderr.log')
        $checkExit = $LASTEXITCODE
        $context.exitCode = $checkExit
    } finally { Pop-Location }
} finally {
    $context.end = [DateTime]::UtcNow.ToString('o')
    $context.sourceMismatches = @(Get-SourceMismatches)
    @(docker ps -a --format '{{.ID}}') | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $evidencePath 'h101-containers-after.json')
    $xmlRoot = Join-Path $evidencePath 'h101-xml'
    if (Test-Path -LiteralPath $xmlRoot) { throw 'H101 XML destination already exists' }
    New-Item -ItemType Directory -Path $xmlRoot | Out-Null
    $rows = @()
    foreach ($resultDirectory in $plan.resultDirectories) {
        $resultPath = Join-Path $treePath $resultDirectory
        if (-not (Test-Path -LiteralPath $resultPath)) { continue }
        foreach ($file in @(Get-ChildItem -LiteralPath $resultPath -Filter 'TEST-*.xml' -File | Where-Object { $_.LastWriteTimeUtc -ge $startedAt })) {
            [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
            $suite = $document.testsuite
            $rows += [pscustomobject]@{directory=$resultDirectory;name=[string]$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped;modifiedUtc=$file.LastWriteTimeUtc.ToString('o')}
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $xmlRoot (($resultDirectory.Replace('/','_'))+'-'+$file.Name))
        }
    }
    $context.freshTestClasses = $rows.Count
    ConvertTo-Json -InputObject @($rows) -Depth 4 | Set-Content -LiteralPath (Join-Path $evidencePath 'h101-tests.json')
    $context | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $contextPath
}
$context | ConvertTo-Json -Depth 6
if ($context.sourceMismatches.Count -ne 0 -or $context.freshTestClasses -eq 0) { exit 1 }
exit $checkExit



