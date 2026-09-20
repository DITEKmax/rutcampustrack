$ErrorActionPreference = 'Stop'
$target = (Get-Location).Path
$project = [IO.Path]::GetFullPath((Join-Path $target '..\..\..\..'))
$sourceBase = Join-Path $project '.agent\worktrees\student-role-02'
$manifestBase = Join-Path $project '.agent\student-role-02'
$shellRoot = Join-Path $sourceBase 'shared-shell'
$homeworkRoot = Join-Path $sourceBase 'homework-api'
$shellManifest = Get-Content -Raw (Join-Path $manifestBase 'shared-shell-final-manifest.json') | ConvertFrom-Json
$homeworkManifest = Get-Content -Raw (Join-Path $manifestBase 'pause-2026-09-07-1232\homework-api\manifest.json') | ConvertFrom-Json
$accepted = @()
foreach ($entry in $shellManifest.files) {
    $accepted += [pscustomobject]@{ path = $entry.path; sha256 = $entry.sha256; source = $shellRoot }
}
foreach ($entry in $homeworkManifest.files) {
    if ($entry.path -ne 'docs/openapi/academic.json') {
        $accepted += [pscustomobject]@{ path = $entry.path; sha256 = $entry.sha256; source = $homeworkRoot }
    }
}
if ($accepted.Count -ne 42) { throw "Expected 42 accepted files, found $($accepted.Count)" }
$rows = foreach ($entry in $accepted) {
    $sourcePath = Join-Path $entry.source $entry.path
    $targetPath = Join-Path $target $entry.path
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw "Missing source: $($entry.path)" }
    if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) { throw "Missing destination: $($entry.path)" }
    $sourceSha = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToUpperInvariant()
    $targetSha = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash.ToUpperInvariant()
    $canonicalSha = $entry.sha256.ToUpperInvariant()
    if ($sourceSha -ne $canonicalSha) { throw "Source SHA mismatch: $($entry.path)" }
    if ($targetSha -ne $canonicalSha) { throw "Destination SHA mismatch: $($entry.path)" }
    [pscustomobject]@{ path = $entry.path; sourceSha256 = $sourceSha; destinationSha256 = $targetSha }
}
[pscustomobject]@{
    acceptedCount = $accepted.Count
    sourceShaPass = $true
    destinationShaPass = $true
    excluded = @('docs/openapi/academic.json', 'frontends/mobile-core/package.json')
    paths = $rows
} | ConvertTo-Json -Depth 5
