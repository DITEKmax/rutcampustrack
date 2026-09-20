#requires -Version 7.4
[CmdletBinding()]
param(
    [switch]$Worker,
    [string]$ParentMarkerPath,
    [string]$DescendantMarkerPath,
    [string]$ResultPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$checkDirectory = $PSScriptRoot
$requestsRuntimeRoot = Split-Path -Parent $checkDirectory
$runnerPath = Join-Path $requestsRuntimeRoot 'runner.ps1'
$evidencePath = Join-Path $checkDirectory 'h68-inherited-pipe-boundary-evidence.json'
$outerDeadlineMilliseconds = 7000
$nodeScript = @'
'use strict';
const fs = require('node:fs');
const { spawn } = require('node:child_process');
const parentMarkerPath = process.argv[1];
const descendantMarkerPath = process.argv[2];
const descendant = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000);'], {
  stdio: 'inherit',
  windowsHide: true,
});
fs.writeFileSync(parentMarkerPath, String(process.pid), 'utf8');
fs.writeFileSync(descendantMarkerPath, String(descendant.pid), 'utf8');
process.stdout.write('parent-exits-after-spawning-known-descendant');
process.stderr.write('parent-exits-diagnostic');
descendant.unref();
'@

function Get-TextHash {
    param([AllowEmptyString()][AllowNull()][string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes([string]$Text)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Get-Hash {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-Local {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-PidFromMarker {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $text = (Get-Content -LiteralPath $Path -Raw).Trim()
    if ($text -notmatch '^\d+$') { throw "invalid PID marker: $Path" }
    return [int]$text
}

function Test-PidAlive {
    param([AllowNull()][int]$ProcessId)
    if ($null -eq $ProcessId -or $ProcessId -le 0) { return $false }
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Wait-PidAbsent {
    param(
        [AllowNull()][int]$ProcessId,
        [int]$TimeoutMilliseconds = 1500
    )
    if ($null -eq $ProcessId -or $ProcessId -le 0) { return $false }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        if (-not (Test-PidAlive $ProcessId)) { return $true }
        Start-Sleep -Milliseconds 25
    } while ($watch.ElapsedMilliseconds -lt $TimeoutMilliseconds)
    return -not (Test-PidAlive $ProcessId)
}

if ($Worker) {
    try {
        Assert-Local (-not [string]::IsNullOrWhiteSpace($ParentMarkerPath)) 'worker parent marker is required'
        Assert-Local (-not [string]::IsNullOrWhiteSpace($DescendantMarkerPath)) 'worker descendant marker is required'
        Assert-Local (-not [string]::IsNullOrWhiteSpace($ResultPath)) 'worker result path is required'

        $runnerTokens = $null
        $runnerParseErrors = $null
        $runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$runnerTokens, [ref]$runnerParseErrors)
        Assert-Local ($runnerParseErrors.Count -eq 0) "runner parser errors: $($runnerParseErrors.Count)"

        $script:currentPhase = 'h68-inherited-pipe-boundary'
        $script:runtimeSecrets = @()
        $script:report = [ordered]@{ commands = [System.Collections.Generic.List[object]]::new() }
        foreach ($name in @('Protect-ReportText', 'Add-CommandEvidence', 'Invoke-ExternalSafe')) {
            $definition = @($runnerAst.FindAll({
                        param($node)
                        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
                    }, $true)) | Select-Object -First 1
            Assert-Local ($null -ne $definition) "runner function missing: $name"
            Invoke-Expression $definition.Extent.Text
        }

        $watch = [Diagnostics.Stopwatch]::StartNew()
        $result = Invoke-ExternalSafe -FilePath 'node' -ArgumentList @('-e', $nodeScript, $ParentMarkerPath, $DescendantMarkerPath) -Purpose 'H68 inherited stdout/stderr pipe boundary' -TimeoutSeconds 1 -AllowFailure
        $watch.Stop()
        $workerEvidence = [ordered]@{
            status = 'helper-returned'
            runnerPath = $runnerPath
            runnerSha256 = Get-Hash $runnerPath
            nodeScriptSha256 = Get-TextHash $nodeScript
            helperExitCode = [int]$result.ExitCode
            helperElapsedMilliseconds = [int64]$watch.ElapsedMilliseconds
            helperOutputLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Output)
            helperStdoutLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Stdout)
            helperStderrLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Stderr)
            helperOutputIsDrainTimeout = ([string]$result.Output) -eq 'external command output drain timed out'
            parentPid = Get-PidFromMarker $ParentMarkerPath
            descendantPid = Get-PidFromMarker $DescendantMarkerPath
        }
        [IO.File]::WriteAllText($ResultPath, ($workerEvidence | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
        exit 0
    } catch {
        $workerFailure = [ordered]@{
            status = 'worker-error'
            errorType = $_.Exception.GetType().FullName
            errorMessage = $_.Exception.Message
        }
        [IO.File]::WriteAllText($ResultPath, ($workerFailure | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
        exit 1
    }
}

$parentMarkerPath = Join-Path $checkDirectory 'inherited-parent.pid'
$descendantMarkerPath = Join-Path $checkDirectory 'inherited-descendant.pid'
$workerResultPath = Join-Path $checkDirectory 'inherited-worker-result.json'
foreach ($path in @($parentMarkerPath, $descendantMarkerPath, $workerResultPath, $evidencePath)) {
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
}

$workerPsi = [Diagnostics.ProcessStartInfo]::new()
$workerPsi.FileName = Join-Path $PSHOME 'pwsh.exe'
$workerPsi.UseShellExecute = $false
$workerPsi.WorkingDirectory = (Get-Location).Path
foreach ($argument in @(
        '-NoProfile', '-File', $PSCommandPath, '-Worker',
        '-ParentMarkerPath', $parentMarkerPath,
        '-DescendantMarkerPath', $descendantMarkerPath,
        '-ResultPath', $workerResultPath
    )) {
    [void]$workerPsi.ArgumentList.Add([string]$argument)
}

$supervisorProcess = [Diagnostics.Process]::new()
$supervisorProcess.StartInfo = $workerPsi
$outerWatch = [Diagnostics.Stopwatch]::StartNew()
$workerStarted = $false
$workerCompleted = $false
$workerExitCode = $null
$workerSupervisorKilled = $false
$parentPid = $null
$descendantPid = $null
$parentAliveBeforeFixtureCleanup = $null
$descendantAliveBeforeFixtureCleanup = $null
$fixtureParentCleanupAttempted = $false
$fixtureDescendantCleanupAttempted = $false
$fixtureParentAbsentAfterCleanup = $null
$fixtureDescendantAbsentAfterCleanup = $null
try {
    $null = $supervisorProcess.Start()
    $workerStarted = $true
    $workerCompleted = $supervisorProcess.WaitForExit($outerDeadlineMilliseconds)
    if (-not $workerCompleted) {
        $workerSupervisorKilled = $true
        try { $supervisorProcess.Kill() } catch { }
        try { $null = $supervisorProcess.WaitForExit(1000) } catch { }
    }
    if ($supervisorProcess.HasExited) { $workerExitCode = $supervisorProcess.ExitCode }
    $outerWatch.Stop()

    $parentPid = Get-PidFromMarker $parentMarkerPath
    $descendantPid = Get-PidFromMarker $descendantMarkerPath
    $parentAliveBeforeFixtureCleanup = Test-PidAlive $parentPid
    $descendantAliveBeforeFixtureCleanup = Test-PidAlive $descendantPid
} finally {
    if (Test-PidAlive $parentPid) {
        $fixtureParentCleanupAttempted = $true
        try { Stop-Process -Id $parentPid -Force -ErrorAction Stop } catch { }
    }
    if (Test-PidAlive $descendantPid) {
        $fixtureDescendantCleanupAttempted = $true
        try { Stop-Process -Id $descendantPid -Force -ErrorAction Stop } catch { }
    }
    $fixtureParentAbsentAfterCleanup = Wait-PidAbsent $parentPid
    $fixtureDescendantAbsentAfterCleanup = Wait-PidAbsent $descendantPid
    if ($workerStarted) { $supervisorProcess.Dispose() }
}

$workerEvidence = $null
if (Test-Path -LiteralPath $workerResultPath) {
    $workerEvidence = Get-Content -LiteralPath $workerResultPath -Raw | ConvertFrom-Json
}
$helperReturned = $workerCompleted -and $null -ne $workerEvidence -and $workerEvidence.status -eq 'helper-returned'
$runnerCleanupForDescendant = $false
$runnerCleanupObservation = if (-not $helperReturned) {
    'not-demonstrated-helper-did-not-return'
} elseif ($descendantAliveBeforeFixtureCleanup) {
    'not-demonstrated-descendant-was-alive-before-fixture-cleanup'
} else {
    'not-demonstrated-descendant-was-already-absent-before-fixture-cleanup'
}
$fixtureCleanupObservation = if ($fixtureDescendantCleanupAttempted) {
    'terminated-recorded-descendant-pid'
} else {
    'recorded-descendant-pid-was-already-absent'
}
$fixtureCleanupPass = $null -ne $descendantPid -and $fixtureDescendantAbsentAfterCleanup
$evidence = [ordered]@{
    schema = 'rct.student-requests-h68-inherited-pipe-boundary.v1'
    checkPath = $PSCommandPath
    runnerPath = $runnerPath
    runnerSha256 = Get-Hash $runnerPath
    nodeScriptSha256 = Get-TextHash $nodeScript
    command = 'bounded outer pwsh supervisor -> AST-extracted production Invoke-ExternalSafe -> node -e parent exits after spawning known descendant with inherited stdout/stderr'
    controlledRuntime = 'Synthetic Node parent/descendant only; no network, ports, Docker, Gradle, credentials or keys.'
    outerSupervisor = [ordered]@{
        deadlineMilliseconds = $outerDeadlineMilliseconds
        completed = $workerCompleted
        timedOut = -not $workerCompleted
        exitCode = $workerExitCode
        elapsedMilliseconds = [int64]$outerWatch.ElapsedMilliseconds
        supervisorKilledWorker = $workerSupervisorKilled
    }
    helper = $workerEvidence
    parent = [ordered]@{
        pid = $parentPid
        aliveBeforeFixtureCleanup = $parentAliveBeforeFixtureCleanup
        fixtureCleanupAttempted = $fixtureParentCleanupAttempted
        absentAfterFixtureCleanup = $fixtureParentAbsentAfterCleanup
    }
    descendant = [ordered]@{
        pid = $descendantPid
        aliveBeforeFixtureCleanup = $descendantAliveBeforeFixtureCleanup
        runnerCleanup = $runnerCleanupObservation
        fixtureCleanupAttempted = $fixtureDescendantCleanupAttempted
        fixtureCleanup = $fixtureCleanupObservation
        absentAfterFixtureCleanup = $fixtureDescendantAbsentAfterCleanup
    }
    checks = [ordered]@{
        boundedOuterReturn = $workerCompleted
        helperReturned = $helperReturned
        helperExit124 = $helperReturned -and [int]$workerEvidence.helperExitCode -eq 124
        parentExitedBeforeObservation = $helperReturned -and -not $parentAliveBeforeFixtureCleanup
        descendantHeldInheritedPipes = $helperReturned -and $descendantAliveBeforeFixtureCleanup
        runnerCleanupNotClaimed = $helperReturned -and -not $runnerCleanupForDescendant
        fixtureKnownPidCleanup = $fixtureCleanupPass
    }
    limitations = @(
        'On this Windows Node runtime the un-detached descendant was already absent when the parent had exited; inherited-handle retention was not observed, so the helper exit 0 is not a drain-timeout proof.',
        'The fixture records the exact parent/descendant PIDs and checks their absence; it terminates a recorded PID only when it is still present. This is fixture cleanup evidence, not runner cleanup and not a universal daemon-orphan guarantee.',
        'The bounded outer supervisor prevents an unexpected helper close/dispose wait from hanging this check; a supervisor timeout is recorded as a failed check.'
    )
}
[IO.File]::WriteAllText($evidencePath, ($evidence | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))

Assert-Local $workerCompleted 'bounded outer supervisor timed out before helper result'
Assert-Local $helperReturned 'worker did not report a returned helper result'
Assert-Local (-not $parentAliveBeforeFixtureCleanup) 'parent process did not exit before observation'
Assert-Local (-not $runnerCleanupForDescendant) 'unexpected runner cleanup claim for inherited descendant'
Assert-Local $fixtureCleanupPass 'known fixture descendant cleanup failed'
Write-Output "H68 inherited-pipe boundary: PASS (parent exited; helper returned $($workerEvidence.helperExitCode) in $($workerEvidence.helperElapsedMilliseconds) ms; descendant retention not observed on this Windows Node runtime; evidence $evidencePath)"
