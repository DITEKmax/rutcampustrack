#requires -Version 7.4
[CmdletBinding()]
param(
    [string]$WorktreeRoot = '',
    [string]$EvidencePath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
if (-not [string]::IsNullOrWhiteSpace($WorktreeRoot)) {
    $taskRoot = [IO.Path]::GetFullPath($WorktreeRoot)
}
$runtimeRoot = Join-Path $taskRoot '.agent\student-role-orchestrator\requests-runtime'
$runnerPath = Join-Path $runtimeRoot 'runner.ps1'
$oldProbePath = Join-Path $runtimeRoot 'probe.mjs'
$retrospectiveProbePath = Join-Path $runtimeRoot 'retrospective-probe.mjs'
$baselinePath = Join-Path $runtimeRoot 'h-retrospective-source\baseline-inventory.json'
$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json -Depth 12
$checks = [System.Collections.Generic.List[object]]::new()
$failed = [System.Collections.Generic.List[string]]::new()

function Add-Check {
    param([string]$Name, [bool]$Passed, [string]$Evidence = '', [int]$ExitCode = 0)
    $status = if ($Passed) { 'PASS' } else { 'FAIL' }
    $null = $checks.Add([ordered]@{ name = $Name; status = $status; exitCode = $ExitCode; evidence = $Evidence })
    if (-not $Passed) { $null = $failed.Add($Name) }
}

function Get-Hash([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-PowerShellFile([string]$Path) {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors) | Out-Null
    return [ordered]@{ passed = ($errors.Count -eq 0); errorCount = $errors.Count }
}

function Invoke-NodeCheck([string]$Name, [string[]]$Arguments) {
    $output = @(& node @Arguments 2>&1) -join [Environment]::NewLine
    $exitCode = [int]$LASTEXITCODE
    $safe = $output.Trim()
    if ($safe.Length -gt 500) { $safe = $safe.Substring(0, 500) }
    Add-Check -Name $Name -Passed ($exitCode -eq 0) -ExitCode $exitCode -Evidence $safe
    return [ordered]@{ exitCode = $exitCode; output = $safe }
}

try {
    Add-Check -Name 'runner-present' -Passed (Test-Path -LiteralPath $runnerPath -PathType Leaf) -Evidence $runnerPath
    Add-Check -Name 'old-probe-present' -Passed (Test-Path -LiteralPath $oldProbePath -PathType Leaf) -Evidence $oldProbePath
    Add-Check -Name 'retrospective-probe-present' -Passed (Test-Path -LiteralPath $retrospectiveProbePath -PathType Leaf) -Evidence $retrospectiveProbePath

    $runner = Get-Content -LiteralPath $runnerPath -Raw
    $oldProbe = Get-Content -LiteralPath $oldProbePath -Raw
    $retrospectiveProbe = Get-Content -LiteralPath $retrospectiveProbePath -Raw

    $runnerSyntax = Test-PowerShellFile $runnerPath
    Add-Check -Name 'runner-powershell-parser' -Passed $runnerSyntax.passed -Evidence "errors=$($runnerSyntax.errorCount)"
    $sourceCheckSyntax = Test-PowerShellFile $PSCommandPath
    Add-Check -Name 'source-check-powershell-parser' -Passed $sourceCheckSyntax.passed -Evidence "errors=$($sourceCheckSyntax.errorCount)"

    $null = Invoke-NodeCheck -Name 'old-probe-node-check' -Arguments @('--check', $oldProbePath)
    $null = Invoke-NodeCheck -Name 'retrospective-probe-node-check' -Arguments @('--check', $retrospectiveProbePath)
    $selfTest = Invoke-NodeCheck -Name 'retrospective-probe-self-test' -Arguments @($retrospectiveProbePath, '--self-test')
    $selfTestPass = $false
    if ($selfTest.exitCode -eq 0) {
        try {
            $selfJson = $selfTest.output | ConvertFrom-Json -Depth 8
            $selfTestPass = $selfJson.status -ceq 'PASS' -and $selfJson.mode -ceq 'source-self-test'
        } catch { $selfTestPass = $false }
    }
    Add-Check -Name 'retrospective-probe-self-test-shape' -Passed $selfTestPass -Evidence 'status=PASS, mode=source-self-test required'

    foreach ($marker in @('RetrospectiveOnly', 'retrospectiveProbePath', 'Invoke-RetrospectiveProbe', 'if ($RetrospectiveOnly)', "academicSqlSeed = 'SKIPPED'", "scheduleSqlSeed = 'SKIPPED'")) {
        Add-Check -Name "runner-marker:$marker" -Passed $runner.Contains($marker) -Evidence $marker
    }
    foreach ($marker in @('Invoke-RequestsProbe', '--i1-only', '--i2-only', 'I1 exact files', 'I2 edge rejection')) {
        Add-Check -Name "default-requests-preserved:$marker" -Passed $runner.Contains($marker) -Evidence $marker
    }
    foreach ($marker in @('rct.retrospective-probe.v1', 'api/academic/semesters', 'api/academic/groups', 'api/academic/users', 'api/academic/subjects', 'api/schedule/items', 'assignmentId', 'Idempotency-Key', 'api/attendance/reports/lesson', 'AUTO_SCHEDULER', 'blockedApi', 'lesson_cancellation_markers')) {
        Add-Check -Name "retrospective-marker:$marker" -Passed $retrospectiveProbe.Contains($marker) -Evidence $marker
    }
    Add-Check -Name 'retrospective-probe-no-datastore-bypass' -Passed ($retrospectiveProbe -notmatch '(?i)Invoke-Psql|mongosh|docker|SELECT\s+') -Evidence 'probe has no SQL, Docker or direct datastore access'
    Add-Check -Name 'retrospective-probe-no-fetch' -Passed ($retrospectiveProbe -notmatch '\bfetch\s*\(') -Evidence 'probe uses Node https.request'

    foreach ($entry in $baseline.preservedFiles.PSObject.Properties) {
        $relative = [string]$entry.Name
        $path = if ($relative -eq 'runner.ps1' -or $relative -eq 'probe.mjs') { Join-Path $runtimeRoot $relative } else { Join-Path $runtimeRoot $relative }
        if ($relative -eq 'runner.ps1') { continue }
        $expected = ([string]$entry.Value.sha256).ToLowerInvariant()
        $actual = if (Test-Path -LiteralPath $path -PathType Leaf) { Get-Hash $path } else { '' }
        Add-Check -Name "preserved:$relative" -Passed ($actual -eq $expected) -Evidence "expected=$expected actual=$actual"
    }
    foreach ($entry in $baseline.preexistingDirtyHashes.PSObject.Properties) {
        $path = Join-Path $runtimeRoot ([string]$entry.Name)
        $expected = ([string]$entry.Value).ToLowerInvariant()
        $actual = if (Test-Path -LiteralPath $path -PathType Leaf) { Get-Hash $path } else { '' }
        Add-Check -Name "preexisting-dirty-preserved:$($entry.Name)" -Passed ($actual -eq $expected) -Evidence "expected=$expected actual=$actual"
    }
    $runnerBaselineRecord = $baseline.preservedFiles.PSObject.Properties['runner.ps1'].Value
    $runnerExpectedOldHash = ([string]$runnerBaselineRecord.sha256).ToLowerInvariant()
    Add-Check -Name 'runner-is-scoped-change' -Passed ((Get-Hash $runnerPath) -ne $runnerExpectedOldHash) -Evidence 'runner hash differs from pre-edit baseline as required by the contract'

    $report = [ordered]@{
        schema = 'rct.retrospective-source-check.v1'
        status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
        scope = 'source-freeze/static-checks for opt-in RetrospectiveOnly Requests runtime'
        taskRoot = $taskRoot
        revision = '71bcc27572ed72c18e49f63c512ae5e4e5790b8d'
        checks = @($checks)
        runtime = [ordered]@{ status = 'NOT_RUN'; reason = 'root runtime lease required' }
        noActiveProcess = [ordered]@{ status = 'NOT_RUN'; reason = 'leaf created no runtime resources; root owns runtime inspection' }
        limitations = @('No Docker, services, browser edge or product API was started by this source-only check.', 'API-managed headman grant synchronization remains a runtime/source integration gate; the probe reports blockedApi and never bypasses it.')
    }
    $json = $report | ConvertTo-Json -Depth 12
    if (-not [string]::IsNullOrWhiteSpace($EvidencePath)) {
        $parent = Split-Path -Parent $EvidencePath
        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidencePath), $json, [Text.UTF8Encoding]::new($false))
    }
    Write-Output $json
    if ($failed.Count -gt 0) { exit 1 }
    exit 0
} catch {
    $failure = [ordered]@{ schema = 'rct.retrospective-source-check.v1'; status = 'FAIL'; error = $_.Exception.Message; checks = @($checks); runtime = 'NOT_RUN'; noActiveProcess = 'NOT_RUN' }
    $json = $failure | ConvertTo-Json -Depth 12
    if (-not [string]::IsNullOrWhiteSpace($EvidencePath)) {
        $parent = Split-Path -Parent $EvidencePath
        if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidencePath), $json, [Text.UTF8Encoding]::new($false))
    }
    Write-Output $json
    exit 1
}
