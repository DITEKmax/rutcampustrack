#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runnerPath = Join-Path $root 'runner.ps1'
$childPath = Join-Path $PSScriptRoot 'synthetic-stream-child.mjs'
$markerPath = Join-Path $PSScriptRoot 'timeout-child.pid'
$descendantMarkerPath = Join-Path $PSScriptRoot 'timeout-descendant.pid'
$evidencePath = Join-Path $PSScriptRoot 'h68-process-drain-correction-evidence.json'

function Assert-Local {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-Hash {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextHash {
    param([AllowEmptyString()][AllowNull()][string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes([string]$Text)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

$runnerTokens = $null
$runnerParseErrors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$runnerTokens, [ref]$runnerParseErrors)
Assert-Local ($runnerParseErrors.Count -eq 0) "runner parser errors: $($runnerParseErrors.Count)"

$script:currentPhase = 'h68-process-drain-correction'
$script:runtimeSecrets = @()
$script:report = [ordered]@{ commands = [System.Collections.Generic.List[object]]::new() }

foreach ($name in @('Protect-ReportText', 'Add-CommandEvidence', 'Get-Sha256Text', 'Invoke-ExternalSafe')) {
    $definition = @($runnerAst.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
            }, $true)) | Select-Object -First 1
    Assert-Local ($null -ne $definition) "runner function missing: $name"
    Invoke-Expression $definition.Extent.Text
}
$invokeDefinition = @($runnerAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Invoke-ExternalSafe'
        }, $true)) | Select-Object -First 1
$invokeText = $invokeDefinition.Extent.Text
$boundedDrainSource = $invokeText -match 'ReadToEndAsync\(\)' -and $invokeText -notmatch 'ReadToEnd\(\)' -and
    $invokeText -notmatch 'WaitForExit\(\)' -and $invokeText -match 'WaitForExit\([^)]*\)' -and $invokeText -match '\.Dispose\(\)'

function Invoke-StreamCase {
    param(
        [Parameter(Mandatory = $true)][string]$Mode,
        [int]$TimeoutSeconds = 5,
        [string[]]$ExtraArguments = @()
    )
    $args = @($childPath, $Mode, '1048576') + $ExtraArguments
    $result = Invoke-ExternalSafe -FilePath 'node' -ArgumentList $args -Purpose "H68 corrected $Mode stream case" -TimeoutSeconds $TimeoutSeconds -AllowFailure
    return [ordered]@{
        mode = $Mode
        exitCode = $result.ExitCode
        stdoutLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Stdout)
        stderrLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Stderr)
        outputLength = [Text.Encoding]::UTF8.GetByteCount([string]$result.Output)
        stdoutSha256 = Get-TextHash ([string]$result.Stdout)
        stderrSha256 = Get-TextHash ([string]$result.Stderr)
        stdoutPrefix = ([string]$result.Stdout).Substring(0, [Math]::Min(8, ([string]$result.Stdout).Length))
        stderrPrefix = ([string]$result.Stderr).Substring(0, [Math]::Min(8, ([string]$result.Stderr).Length))
        typedProperties = @($result.PSObject.Properties.Name)
    }
}

$size = 1048576
$stdoutExpected = Get-TextHash ('O' * $size)
$stderrExpected = Get-TextHash ('E' * $size)
$stdoutOnly = Invoke-StreamCase -Mode 'stdout'
$stderrOnly = Invoke-StreamCase -Mode 'stderr'
$both = Invoke-StreamCase -Mode 'both'
$nonzero = Invoke-StreamCase -Mode 'nonzero'

if (Test-Path -LiteralPath $markerPath) { Remove-Item -LiteralPath $markerPath -Force }
if (Test-Path -LiteralPath $descendantMarkerPath) { Remove-Item -LiteralPath $descendantMarkerPath -Force }
$timeout = Invoke-StreamCase -Mode 'timeout' -TimeoutSeconds 1 -ExtraArguments @($markerPath)
$timeoutPid = $null
if (Test-Path -LiteralPath $markerPath) {
    $timeoutPid = [int](Get-Content -LiteralPath $markerPath -Raw).Trim()
    Start-Sleep -Milliseconds 250
}
$ownedProcessAbsent = $null -ne $timeoutPid -and $null -eq (Get-Process -Id $timeoutPid -ErrorAction SilentlyContinue)
if (Test-Path -LiteralPath $markerPath) { Remove-Item -LiteralPath $markerPath -Force }
$timeoutTree = Invoke-StreamCase -Mode 'timeout-tree' -TimeoutSeconds 1 -ExtraArguments @($markerPath, $descendantMarkerPath)
$timeoutTreePid = $null
$timeoutDescendantPid = $null
if (Test-Path -LiteralPath $markerPath) { $timeoutTreePid = [int](Get-Content -LiteralPath $markerPath -Raw).Trim() }
if (Test-Path -LiteralPath $descendantMarkerPath) { $timeoutDescendantPid = [int](Get-Content -LiteralPath $descendantMarkerPath -Raw).Trim() }
Start-Sleep -Milliseconds 250
$ownedTreeAbsent = $null -ne $timeoutTreePid -and $null -ne $timeoutDescendantPid -and
    $null -eq (Get-Process -Id $timeoutTreePid -ErrorAction SilentlyContinue) -and
    $null -eq (Get-Process -Id $timeoutDescendantPid -ErrorAction SilentlyContinue)
if (Test-Path -LiteralPath $markerPath) { Remove-Item -LiteralPath $markerPath -Force }
if (Test-Path -LiteralPath $descendantMarkerPath) { Remove-Item -LiteralPath $descendantMarkerPath -Force }

$largeCases = @($stdoutOnly, $stderrOnly, $both)
$largePass = $true
foreach ($case in $largeCases) {
    $largePass = $largePass -and $case.exitCode -eq 0
}
$largePass = $largePass -and
    $stdoutOnly.stdoutLength -eq $size -and $stdoutOnly.stderrLength -eq 0 -and $stdoutOnly.stdoutSha256 -eq $stdoutExpected -and $stdoutOnly.stdoutPrefix -eq 'OOOOOOOO' -and
    $stderrOnly.stdoutLength -eq 0 -and $stderrOnly.stderrLength -eq $size -and $stderrOnly.stderrSha256 -eq $stderrExpected -and $stderrOnly.stderrPrefix -eq 'EEEEEEEE' -and
    $both.stdoutLength -eq $size -and $both.stderrLength -eq $size -and $both.stdoutSha256 -eq $stdoutExpected -and $both.stderrSha256 -eq $stderrExpected -and $both.stdoutPrefix -eq 'OOOOOOOO' -and $both.stderrPrefix -eq 'EEEEEEEE'
$nonzeroPass = $nonzero.exitCode -eq 7 -and $nonzero.stdoutLength -eq $size -and $nonzero.stderrLength -eq $size -and $nonzero.stdoutSha256 -eq $stdoutExpected -and $nonzero.stderrSha256 -eq $stderrExpected
$timeoutPass = $timeout.exitCode -eq 124 -and $ownedProcessAbsent -and $timeoutTree.exitCode -eq 124 -and $ownedTreeAbsent

$evidence = [ordered]@{
    schema = 'rct.student-requests-h68-process-drain-correction.v1'
    runnerPath = $runnerPath
    runnerSha256 = Get-Hash $runnerPath
    childPath = $childPath
    childSha256 = Get-Hash $childPath
    command = 'AST-extracted production Invoke-ExternalSafe -> node synthetic-stream-child.mjs; stdout/stderr/both/nonzero 1 MiB, timeout child 1 s'
    controlledRuntime = 'Synthetic Node children only; no network, ports, Docker, Gradle, credentials or keys.'
    largeStreamCases = [ordered]@{ stdoutOnly = $stdoutOnly; stderrOnly = $stderrOnly; simultaneous = $both; expectedBytes = $size; expectedStdoutSha256 = $stdoutExpected; expectedStderrSha256 = $stderrExpected }
    nonzero = $nonzero
    timeout = [ordered]@{ result = $timeout; markerPidObserved = $timeoutPid; ownedProcessAbsent = $ownedProcessAbsent; treeResult = $timeoutTree; treeMarkerPidObserved = $timeoutTreePid; descendantMarkerPidObserved = $timeoutDescendantPid; ownedProcessTreeAbsent = $ownedTreeAbsent }
    checks = [ordered]@{ largeStreamsExact = $largePass; nonzeroExitPreserved = $nonzeroPass; timeout124 = $timeoutPass; noOwnedChildAfterTimeout = $ownedProcessAbsent; noOwnedProcessTreeAfterTimeout = $ownedTreeAbsent; boundedAsyncDrainAndDispose = $boundedDrainSource; typedFields = @('ExitCode', 'Output', 'Stdout', 'Stderr') }
    limitations = @('Synthetic subprocesses prove the production helper through AST extraction; no Docker/Gradle/network/runtime services were used.', 'The tree case keeps the owned parent alive until the bounded timeout and verifies its known descendant PID is gone after Kill(true); this does not claim arbitrary daemon discovery or cleanup when a parent has already exited while retaining pipe handles.')
}
[IO.File]::WriteAllText($evidencePath, ($evidence | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
Assert-Local $largePass 'large stdout/stderr drain did not preserve exact content and exit 0'
Assert-Local $nonzeroPass 'nonzero exit or output was not preserved'
Assert-Local $timeoutPass 'timeout 124 or owned-child cleanup failed'
Assert-Local $boundedDrainSource 'timed helper does not show bounded asynchronous drains and disposal'
Write-Output "H68 process drain correction: PASS (large concurrent streams, nonzero 7 and timeout 124/tree cleanup; evidence $evidencePath)"
