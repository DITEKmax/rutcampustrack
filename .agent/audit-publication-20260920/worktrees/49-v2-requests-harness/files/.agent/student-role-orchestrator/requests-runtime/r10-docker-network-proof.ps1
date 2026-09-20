#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9.]+/24$')][string]$Subnet,
    [string]$EvidencePath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$resolvedEvidencePath = if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
    Join-Path $PSScriptRoot 'r11-docker-network-proof.json'
} else {
    [IO.Path]::GetFullPath($EvidencePath)
}

# The runner's -NetworkProof branch invokes the same production
# New-RequestsOwnedNetwork and Start-OwnedContainer functions used by the
# full harness. This wrapper adds no Docker command or helper implementation.
$runnerArgs = @(
    '-NoProfile', '-NonInteractive', '-File', $runnerPath,
    '-UnionRepo', $PSScriptRoot,
    '-NetworkProof',
    '-NetworkProofSubnet', $Subnet,
    '-NetworkProofEvidencePath', $resolvedEvidencePath
)
& pwsh @runnerArgs
exit $LASTEXITCODE
