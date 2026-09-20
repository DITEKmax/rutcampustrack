$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifest = [System.IO.File]::ReadAllText((Join-Path $repo 'docs\sources\manifest.yaml'), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$bySource = @{}
$seenEntryIds = @{}
foreach ($item in $manifest.entries) {
    if ($bySource.ContainsKey($item.source)) { throw "duplicate source identity: $($item.source)" }
    $bySource[$item.source] = $item
    $entryId = if ($item.entry_id) { $item.entry_id } else { "source:$($item.source)|target:$($item.target)" }
    if ($seenEntryIds.ContainsKey($entryId)) { throw "duplicate entry identity: $entryId" }
    $seenEntryIds[$entryId] = $true
}
function Resolve-RetainedTarget($entry) {
    $seen = @{}; $current = $entry
    while ($current.status -eq 'duplicate' -and $current.retained_address) {
        if ($seen.ContainsKey($current.source)) { throw "retained-address cycle: $($current.source)" }
        $seen[$current.source] = $true
        if (-not $bySource.ContainsKey($current.retained_address)) { throw "retained-address missing: $($current.retained_address)" }
        $current = $bySource[$current.retained_address]
    }
    return $current.canonical_path
}
$checked = 0; $failures = @(); $skipped = 0; $missingTarget = 0; $missingSource = 0; $targetHashFailures = 0; $sourceDrift = 0; $resolvedDuplicates = 0
foreach ($entry in $manifest.entries) {
    if ($entry.status -eq 'exclude') { $skipped++; continue }
    $targetRelative = $entry.target
    $target = Join-Path $repo $targetRelative
    if (-not (Test-Path -LiteralPath $target -PathType Leaf) -and $entry.status -eq 'duplicate' -and $entry.retained_address) {
        $targetRelative = Resolve-RetainedTarget $entry
        $target = Join-Path $repo $targetRelative
        $resolvedDuplicates++
    }
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { $failures += "missing target: $($entry.target)"; $missingTarget++; continue }
    $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($entry.result_sha256 -and $targetHash -ne $entry.result_sha256) { $failures += "target hash: $($entry.target)"; $targetHashFailures++ }
    if ($entry.status -in @('copy-canonical','copy-canonical-post-transfer','copy-archive','duplicate')) {
        $source = Join-Path $entry.source_root $entry.source_path
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { $failures += "missing source: $($entry.source)"; $missingSource++ }
        elseif ($entry.source_sha256 -and ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.source_sha256)) { $failures += "source drift: $($entry.source)"; $sourceDrift++ }
    }
    $checked++
}
[pscustomobject]@{ result = if ($failures.Count -eq 0) {'PASS'} else {'FAIL'}; checked=$checked; excluded=$skipped; failures=$failures.Count; resolved_duplicates=$resolvedDuplicates; missing_target=$missingTarget; missing_source=$missingSource; target_hash_failures=$targetHashFailures; source_drift=$sourceDrift; details=$failures } | ConvertTo-Json -Depth 4
if ($failures.Count -gt 0) { exit 1 }
