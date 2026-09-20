#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runnerPath = Join-Path $root 'runner.ps1'
$childPath = Join-Path $PSScriptRoot 'synthetic-stream-child.mjs'
$evidencePath = Join-Path $PSScriptRoot 'h68-process-drain-prefixed-evidence.json'

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

$script:currentPhase = 'h68-process-drain-prefixed'
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

function Get-ResultSummary {
    param(
        [Parameter(Mandatory = $true)][string]$Mode,
        [int]$TimeoutSeconds = 1,
        [switch]$AllowFailure
    )
    $result = Invoke-ExternalSafe -FilePath 'node' -ArgumentList @($childPath, $Mode, '1048576') -Purpose "H68 prefixed $Mode stream repro" -TimeoutSeconds $TimeoutSeconds -AllowFailure:$AllowFailure
    return [ordered]@{
        mode = $Mode
        exitCode = $result.ExitCode
        stdoutLength = ([Text.Encoding]::UTF8.GetByteCount([string]$result.Stdout))
        stderrLength = ([Text.Encoding]::UTF8.GetByteCount([string]$result.Stderr))
        outputLength = ([Text.Encoding]::UTF8.GetByteCount([string]$result.Output))
        stdoutSha256 = (Get-TextHash ([string]$result.Stdout))
        stderrSha256 = (Get-TextHash ([string]$result.Stderr))
        outputPrefix = ([string]$result.Output).Substring(0, [Math]::Min(32, ([string]$result.Output).Length))
        typedProperties = @($result.PSObject.Properties.Name)
    }
}

$large = Get-ResultSummary -Mode 'both' -AllowFailure
$largeExpectedBytes = 1048576
$blockedPipeTimeout = $large.exitCode -eq 124
$stdoutMissing = $large.stdoutLength -ne $largeExpectedBytes
$stderrMissing = $large.stderrLength -ne $largeExpectedBytes
$evidence = [ordered]@{
    schema = 'rct.student-requests-h68-process-drain-prefixed.v1'
    runnerPath = $runnerPath
    runnerSha256 = Get-Hash $runnerPath
    childPath = $childPath
    childSha256 = Get-Hash $childPath
    command = 'AST-extracted production Invoke-ExternalSafe -> node synthetic-stream-child.mjs both 1048576; TimeoutSeconds=1; AllowFailure'
    controlledRuntime = 'Synthetic Node child only; no network, ports, Docker, Gradle, credentials or keys.'
    repro = [ordered]@{
        largeSimultaneous = $large
        expected = [ordered]@{ exitCode = 0; stdoutLength = $largeExpectedBytes; stderrLength = $largeExpectedBytes }
        reproduced = [ordered]@{
            blockedPipeTimeout = $blockedPipeTimeout
            stdoutMissing = $stdoutMissing
            stderrMissing = $stderrMissing
        }
    }
    actualDefect = 'Timed production branch calls WaitForExit before draining redirected stdout/stderr. A child writing 1 MiB to both pipes blocks and is reported as timeout 124 before complete output is observed.'
    limitations = @('This prefixed record captures only the runner pre-fix reproduction; probe secret pre-fix evidence is recorded by the companion actual-entrypoint check.', 'No real credentials, keys, network, ports, Docker or Gradle were used.')
}
Assert-Local (-not (Test-Path -LiteralPath $evidencePath)) 'refusing to overwrite existing pre-fix evidence; use a new explicitly named evidence path'
[IO.File]::WriteAllText($evidencePath, ($evidence | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
Assert-Local ($blockedPipeTimeout -and $stdoutMissing -and $stderrMissing) 'expected pre-fix pipe deadlock was not reproduced'
Write-Output "H68 process drain prefixed: PASS (pre-fix timeout 124 reproduced; evidence $evidencePath)"
