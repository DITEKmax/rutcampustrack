# Pure scoped check: exercise the actual cleanup functions with an injected rm failure.
# It never invokes Docker and does not create files, containers, networks, or keys.
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$runnerText = Get-Content -LiteralPath $runnerPath -Raw
$start = $runnerText.IndexOf('function Assert-That', [StringComparison]::Ordinal)
$end = $runnerText.IndexOf('function Invoke-FullRuntime', [StringComparison]::Ordinal)
if ($start -lt 0 -or $end -le $start) { throw 'cleanup function boundaries are missing from runner.ps1' }
. ([scriptblock]::Create($runnerText.Substring($start, $end - $start)))

$script:runId = 'failure-injection'
$script:ownedContainers = [System.Collections.Generic.List[string]]::new()
$script:ownedContainers.Add('container-1')
$script:ownedNetwork = $null
$script:keysDir = $null
$script:artifactSnapshotRoot = $null
$script:report = [ordered]@{
    cleanup = [ordered]@{
        status = 'NOT_RUN'
        ownedContainers = @()
        removedContainers = @()
        verifiedAbsentContainers = @()
        ownedNetwork = $null
        networkRemoved = $false
        networkVerifiedAbsent = $false
        keysRemoved = $false
        artifactRoot = $null
        artifactRemoved = $false
        artifactVerifiedAbsent = $false
        errors = @()
    }
}

function Invoke-DockerSafe {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArgs,
        [string]$Purpose = 'Docker command',
        [switch]$AllowFailure
    )
    if ($DockerArgs[0] -eq 'container' -and $DockerArgs[1] -eq 'inspect') {
        return [pscustomobject]@{ ExitCode = 0; Output = 'student-requests-gate|failure-injection' }
    }
    if ($DockerArgs[0] -eq 'rm' -and $DockerArgs[1] -eq '-f') {
        return [pscustomobject]@{ ExitCode = 23; Output = 'injected cleanup failure' }
    }
    throw "unexpected Docker command in pure cleanup test: $($DockerArgs -join ' ')"
}

$caught = $false
$errorText = $null
try { Remove-OwnedResources } catch { $caught = $true; $errorText = $_.Exception.Message }
if (-not $caught) { throw 'cleanup failure injection was swallowed' }
if ($script:report.cleanup.status -ne 'FAIL') { throw 'cleanup failure injection did not set cleanup status FAIL' }
if (@($script:report.cleanup.errors | Where-Object { $_ -match 'container container-1' }).Count -ne 1) {
    throw 'cleanup failure injection did not record the failing owned container'
}

[ordered]@{
    status = 'PASS'
    cleanupStatus = $script:report.cleanup.status
    caught = $caught
    error = $errorText
    recordedErrors = @($script:report.cleanup.errors)
} | ConvertTo-Json -Depth 6 -Compress
