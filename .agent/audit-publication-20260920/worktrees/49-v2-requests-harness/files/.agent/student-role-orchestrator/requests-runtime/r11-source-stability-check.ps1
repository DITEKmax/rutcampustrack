#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Synthetic-only proof for the source pin boundary. It exercises the actual
# Assert-UnionSourceStability function with temporary edge files and a clean
# stubbed git transport; no Docker, build, server or port is used.
$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$runnerAst = $null
$tokens = $null
$errors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "runner.ps1 parser errors: $($errors.Count)" }

function Get-FunctionDefinitionText {
    param([Parameter(Mandatory = $true)][string]$Name)
    $definition = @($runnerAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name }, $true)) | Select-Object -First 1
    if ($null -eq $definition) { throw "runner function missing: $Name" }
    return $definition.Extent.Text
}

foreach ($name in @('Assert-That', 'Get-UnionRevision', 'Get-UnionTrackedStatus', 'Get-UnionTrackedBlobHash', 'Assert-UnionSourceStability')) {
    Invoke-Expression (Get-FunctionDefinitionText -Name $name)
}

$script:revision = ('a' * 40)
$script:blob = ('b' * 40)
$script:gitStatus = ''
$ExpectedUnionRevision = $script:revision
$script:unionRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) "rct-r11-source-$([Guid]::NewGuid().ToString('N'))"))
$script:validatedEdgeSources = $null
$script:validatedSourceAnchor = $null

function Invoke-ExternalSafe {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$Purpose = '',
        [int]$TimeoutSeconds = 0,
        [switch]$AllowFailure
    )
    if ($ArgumentList -contains 'status') { return [pscustomobject]@{ ExitCode = 0; Output = $script:gitStatus } }
    if ($ArgumentList -contains 'HEAD') { return [pscustomobject]@{ ExitCode = 0; Output = $script:revision } }
    if (@($ArgumentList | Where-Object { [string]$_ -match '^[0-9a-f]{40}:' }).Count -eq 1) { return [pscustomobject]@{ ExitCode = 0; Output = $script:blob } }
    throw "unexpected synthetic external command: $($ArgumentList -join ' ')"
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

try {
    New-Item -ItemType Directory -Path $script:unionRoot -Force | Out-Null
    $nginxPath = Join-Path $script:unionRoot 'nginx.conf'
    $defaultPath = Join-Path $script:unionRoot 'default.conf'
    [IO.File]::WriteAllText($nginxPath, 'validated nginx')
    [IO.File]::WriteAllText($defaultPath, 'validated default')
    $script:validatedEdgeSources = [pscustomobject]@{ nginxPath = $nginxPath; defaultPath = $defaultPath }
    $script:validatedSourceAnchor = [ordered]@{
        revision = $script:revision
        trackedStatus = ''
        nginxSha256 = Get-Sha256Hex -Path $nginxPath
        defaultSha256 = Get-Sha256Hex -Path $defaultPath
        nginxBlob = $script:blob
        defaultBlob = $script:blob
    }
    Assert-UnionSourceStability -Context 'synthetic clean source' | Out-Null

    $script:revision = ('c' * 40)
    $revisionRejected = $false
    try { Assert-UnionSourceStability -Context 'synthetic changed revision' | Out-Null } catch { $revisionRejected = $true }
    if (-not $revisionRejected) { throw 'changed UnionRepo revision was accepted despite the expected revision pin' }
    $script:revision = ('a' * 40)

    $script:blob = ('d' * 40)
    $blobRejected = $false
    try { Assert-UnionSourceStability -Context 'synthetic changed tracked blob' | Out-Null } catch { $blobRejected = $true }
    if (-not $blobRejected) { throw 'changed tracked edge source blob was accepted despite the revision blob pin' }
    $script:blob = ('b' * 40)

    [IO.File]::WriteAllText($nginxPath, 'tampered nginx')
    $swapRejected = $false
    try { Assert-UnionSourceStability -Context 'synthetic between-phase swap' | Out-Null } catch { $swapRejected = $true }
    if (-not $swapRejected) { throw 'between-phase edge source swap was accepted despite the immutable source pin' }

    [IO.File]::WriteAllText($nginxPath, 'validated nginx')
    $script:gitStatus = " M nginx.conf"
    $dirtyRejected = $false
    try { Assert-UnionSourceStability -Context 'synthetic dirty source' | Out-Null } catch { $dirtyRejected = $true }
    if (-not $dirtyRejected) { throw 'dirty UnionRepo source was accepted by the runtime preflight guard' }

    $runtimeConfig = @($runnerAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'New-RuntimeConfig' }, $true)) | Select-Object -First 1
    if ($null -eq $runtimeConfig -or $runtimeConfig.Extent.Text -match 'Resolve-EdgeSources') { throw 'New-RuntimeConfig still re-resolves edge source paths after source validation' }
    Write-Output 'R11 source stability check: PASS (exact revision/clean state, tracked blob and edge bytes plus between-phase swap are fail-closed; runtime config uses cached validated source text)'
} finally {
    if (Test-Path -LiteralPath $script:unionRoot) { Remove-Item -LiteralPath $script:unionRoot -Recurse -Force }
}
