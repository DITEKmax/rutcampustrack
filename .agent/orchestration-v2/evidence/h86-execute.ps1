param([Parameter(Mandatory=$true)][string]$ExpectedFreezeHash)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$treePath = Join-Path $rootPath '.agent/worktrees/v2-mass-cancel-removal'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$freezePath = Join-Path $evidencePath 'h86-source-freeze.json'
if ((Get-FileHash -LiteralPath $freezePath).Hash -ne $ExpectedFreezeHash) { throw 'Freeze manifest mismatch' }
$freeze = Get-Content -LiteralPath $freezePath -Raw | ConvertFrom-Json
function Get-SourceMismatches {
    foreach ($record in $freeze.files) {
        $path = Join-Path $treePath $record.path
        if ($record.deleted) { if (Test-Path -LiteralPath $path) { $record.path }; continue }
        if (-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash -ne $record.sha256) { $record.path }
    }
}
if (@(Get-SourceMismatches).Count) { throw 'Frozen source mismatch' }
if ((& git -C $treePath rev-parse HEAD) -ne $freeze.baseRevision) { throw 'Baseline changed' }
$contextPath = Join-Path $evidencePath 'h86-context.json'
if (Test-Path -LiteralPath $contextPath) { throw 'H86 already recorded; refuse overwrite' }
$gradleArgs = @(':services:schedule-service:schedule-app:integrationTest','--tests','ru.rutcampustrack.schedule.integration.LessonApiIT','--no-daemon','--no-parallel','--max-workers=1','--no-problems-report')
$startTime = (Get-Date).ToUniversalTime()
$context = [ordered]@{stage='H86';start=$startTime.ToString('o');head=$freeze.baseRevision;freezeManifestSha256=$ExpectedFreezeHash;command=@('.\gradlew.bat')+$gradleArgs;testcontainersReuse='false';exitCode=$null}
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
@(docker ps -a --format '{{.ID}}') | ConvertTo-Json -AsArray | Set-Content (Join-Path $evidencePath 'h86-containers-before.json')
Push-Location $treePath
try {
    & .\gradlew.bat @gradleArgs 1> (Join-Path $evidencePath 'h86-stdout.log') 2> (Join-Path $evidencePath 'h86-stderr.log')
    $checkExit = $LASTEXITCODE
    $context.exitCode = $checkExit
} finally {
    Pop-Location
    $context.end = (Get-Date).ToUniversalTime().ToString('o')
    $context.postHashMismatches = @(Get-SourceMismatches)
    $context | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $contextPath
}
$xmlPath = Join-Path $treePath 'services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.integration.LessonApiIT.xml'
$fresh = (Test-Path -LiteralPath $xmlPath) -and (Get-Item -LiteralPath $xmlPath).LastWriteTimeUtc -ge $startTime
if ($fresh) { Copy-Item -LiteralPath $xmlPath -Destination (Join-Path $evidencePath 'h86-LessonApiIT.xml') }
[ordered]@{freshXml=$fresh;source=$xmlPath} | ConvertTo-Json | Set-Content (Join-Path $evidencePath 'h86-xml-presence.json')
@(docker ps -a --format '{{.ID}}') | ConvertTo-Json -AsArray | Set-Content (Join-Path $evidencePath 'h86-containers-after.json')
$context | ConvertTo-Json -Depth 6
exit $checkExit
