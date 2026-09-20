param([Parameter(Mandatory=$true)][string]$ExpectedRunnerHash,[Parameter(Mandatory=$true)][string]$ExpectedProbeHash)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$harnessPath = Join-Path $rootPath '.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime'
$runnerPath = Join-Path $harnessPath 'runner.ps1'
$probePath = Join-Path $harnessPath 'probe.mjs'
$unionPath = Join-Path $rootPath '.agent/worktrees/v2-runtime-build-r2'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$manifestPath = Join-Path $rootPath '.agent/orchestration-v2/build-i6/h57-build/requests-build-manifest.v1.json'
$manifestHash = '61E73ED658D43CD878AFE6DA6AB703D2C73844E8114329FD991566BF9C009F4F'
$unionRevision = '426a15b6b42e816deaa3ca5c50437e0964aaf85e'
if ((Get-FileHash $runnerPath).Hash -ne $ExpectedRunnerHash) { throw 'Runner freeze mismatch' }
if ((Get-FileHash $probePath).Hash -ne $ExpectedProbeHash) { throw 'Probe freeze mismatch' }
if ((Get-FileHash $manifestPath).Hash -ne $manifestHash) { throw 'Manifest mismatch' }
if ((& git -C $unionPath rev-parse HEAD) -ne $unionRevision) { throw 'Union revision mismatch' }
if ((& git -C $unionPath status --porcelain) -join '') { throw 'Union worktree dirty' }
if (Test-Path (Join-Path $evidencePath 'h84-context.json')) { throw 'H84 already recorded; refuse overwrite' }
$beforeRunNames = @(Get-ChildItem -LiteralPath (Join-Path $harnessPath 'runs') -Directory | Select-Object -ExpandProperty Name)
$context = [ordered]@{start=(Get-Date).ToUniversalTime().ToString('o'); identity=(& whoami); runnerHash=$ExpectedRunnerHash; probeHash=$ExpectedProbeHash; manifestHash=$manifestHash; unionRevision=$unionRevision; command=@('pwsh','-NoProfile','-File',$runnerPath,'-UnionRepo',$unionPath,'-ExpectedUnionRevision',$unionRevision,'-BuildManifestPath',$manifestPath,'-BuildManifestSha256',$manifestHash); exitCode=$null}
try {
    & pwsh -NoProfile -File $runnerPath -UnionRepo $unionPath -ExpectedUnionRevision $unionRevision -BuildManifestPath $manifestPath -BuildManifestSha256 $manifestHash 1> (Join-Path $evidencePath 'h84-stdout.log') 2> (Join-Path $evidencePath 'h84-stderr.log')
    $checkExit = $LASTEXITCODE
    $context.exitCode = $checkExit
} finally {
    $context.end = (Get-Date).ToUniversalTime().ToString('o')
    $newRuns = @(Get-ChildItem -LiteralPath (Join-Path $harnessPath 'runs') -Directory | Where-Object { $_.Name -notin $beforeRunNames })
    $context.newRunNames = @($newRuns.Name)
    if ($newRuns.Count -eq 1) {
        $reportPath = Join-Path $newRuns[0].FullName 'report.json'
        if (Test-Path $reportPath) { Copy-Item -LiteralPath $reportPath -Destination (Join-Path $evidencePath 'h84-report.json') }
    }
    $context | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidencePath 'h84-context.json')
}
$context | ConvertTo-Json -Depth 5
exit $checkExit




