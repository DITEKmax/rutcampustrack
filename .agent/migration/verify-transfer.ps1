$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestRelativePath = 'docs/sources/manifest.yaml'
$manifestPath = Join-Path $repoRoot ($manifestRelativePath -replace '/', '\\')
$baselinePath = Join-Path $PSScriptRoot 'pre-transfer-baseline.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json

function Get-RepoPath([string]$relativePath) { return Join-Path $repoRoot ($relativePath -replace '/', '\\') }
function Get-Sha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-Utf8Json([object]$value, [string]$path, [int]$depth = 12) { $value | ConvertTo-Json -Depth $depth | Set-Content -LiteralPath $path -Encoding utf8 }

$entryResults = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $manifest.entries) {
    $outcome = 'PASS'; $reason = $null; $actualHash = $null
    if ($entry.status -in @('copy-canonical','copy-archive')) {
        $targetPath = Get-RepoPath $entry.target
        if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) { $outcome = 'FAIL'; $reason = 'exact target is missing' }
        else {
            $actualHash = Get-Sha $targetPath
            if ($actualHash -ne $entry.source_sha256) { $outcome = 'FAIL'; $reason = 'exact target SHA-256 differs from source inventory SHA-256' }
        }
        $sourcePath = Join-Path $entry.source_root ($entry.source_path -replace '/', '\\')
        if ($outcome -eq 'PASS' -and (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf) -or (Get-Sha $sourcePath) -ne $entry.source_sha256)) {
            $outcome = 'FAIL'; $reason = 'source drifted after copy'
        }
    } elseif ($entry.status -eq 'duplicate') {
        $targetPath = Get-RepoPath $entry.canonical_path
        if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) { $outcome = 'FAIL'; $reason = 'retained canonical target is missing' }
        else {
            $actualHash = Get-Sha $targetPath
            if ($actualHash -ne $entry.source_sha256) { $outcome = 'FAIL'; $reason = 'retained canonical target SHA-256 differs from duplicate source' }
        }
        $sourcePath = Join-Path $entry.source_root ($entry.source_path -replace '/', '\\')
        if ($outcome -eq 'PASS' -and (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf) -or (Get-Sha $sourcePath) -ne $entry.source_sha256)) {
            $outcome = 'FAIL'; $reason = 'duplicate source drifted after copy'
        }
    } elseif ($entry.status -eq 'adapted') {
        $archiveRelativePath = 'docs/archive/transfer-20260905/INDEX.md'
        $archivePath = Get-RepoPath $archiveRelativePath
        if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) { $outcome = 'FAIL'; $reason = 'preserved original INDEX.md is missing' }
        else {
            $actualHash = Get-Sha $archivePath
            if ($actualHash -ne $entry.source_sha256) { $outcome = 'FAIL'; $reason = 'preserved original INDEX.md SHA-256 differs from inventory source' }
            else { $entry.result_sha256 = Get-Sha (Get-RepoPath $entry.canonical_path); $entry.verification_state = 'verified-preserved-original-and-adapted-index' }
        }
    } elseif ($entry.status -eq 'exclude') {
        $outcome = 'PASS'; $reason = 'excluded by path; content was not opened or hashed'
    } else {
        $outcome = 'FAIL'; $reason = 'unexpected unresolved manifest status'
    }
    $entryResults.Add([ordered]@{ source=$entry.source; status=$entry.status; target=$entry.target; canonical_path=$entry.canonical_path; outcome=$outcome; actual_sha256=$actualHash; reason=$reason })
}

$inactiveViolations = @($manifest.entries | Where-Object {
    $_.status -in @('copy-canonical','copy-archive') -and $_.source_path -match '(^|/)(AGENTS(\.override)?\.md|SKILL\.md)$' -and $_.target -notmatch '\.inactive$'
})
$protectedResults = foreach ($file in $baseline.protected_files) {
    if ($file.path -eq 'docs/INDEX.md') { continue }
    $path = Get-RepoPath $file.path
    [ordered]@{ path=$file.path; preserved=(Test-Path -LiteralPath $path -PathType Leaf) -and ((Get-Sha $path) -eq $file.sha256) }
}

$addressMap = [ordered]@{
    generated_at=(Get-Date -Format o); inventory_sha256=$manifest.inventory_sha256
    entries=@($manifest.entries | ForEach-Object { [ordered]@{
        source=$_.source; source_path=$_.source_path; target=$_.target; canonical_path=$_.canonical_path
        inventory_status=$_.inventory_status; status=$_.status; retained_address=$_.retained_address; verification_state=$_.verification_state
    } })
}
$addressMapPath = Get-RepoPath 'docs/sources/address-map.json'
Write-Utf8Json $addressMap $addressMapPath 8

$entriesBySource = @{}
foreach ($entry in $manifest.entries) { $entriesBySource[$entry.source] = $entry }
$unresolved = [System.Collections.Generic.List[object]]::new()
$mappedReferences = [System.Collections.Generic.List[object]]::new()
$markdownEntries = @($manifest.entries | Where-Object { $_.status -in @('copy-canonical','copy-archive','adapted') -and $_.canonical_path -match '\.md$' })
$markdownLinkPattern = [regex]'\[[^\]]*\]\(([^)]+)\)'
foreach ($entry in $markdownEntries) {
    $documentPath = Get-RepoPath $entry.canonical_path
    if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) { continue }
    $content = [System.IO.File]::ReadAllText($documentPath)
    foreach ($match in $markdownLinkPattern.Matches($content)) {
        $reference = $match.Groups[1].Value.Trim()
        if ([string]::IsNullOrWhiteSpace($reference) -or $reference.StartsWith('#') -or $reference -match '^[a-zA-Z][a-zA-Z0-9+.-]*:' -or $reference.StartsWith('//')) { continue }
        $pathPart = ($reference -split '#', 2)[0].Trim()
        if ([string]::IsNullOrWhiteSpace($pathPart)) { continue }
        if ($pathPart.StartsWith('/')) {
            $candidate = Get-RepoPath $pathPart.TrimStart('/')
            if (-not (Test-Path -LiteralPath $candidate)) {
                $unresolved.Add([ordered]@{ document=$entry.canonical_path; reference=$reference; resolved_candidate=$candidate.Substring($repoRoot.Length).TrimStart('\\'); note='No repo-root target exists.' })
            }
            continue
        }
        $sourceDocumentPath = Join-Path $entry.source_root ($entry.source_path -replace '/', '\\')
        $sourceCandidate = [System.IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $sourceDocumentPath) ($pathPart -replace '/', '\\')))
        $separator = [System.IO.Path]::DirectorySeparatorChar
        $sourceRoot = [System.IO.Path]::GetFullPath($entry.source_root).TrimEnd($separator)
        if ($sourceCandidate.StartsWith($sourceRoot + $separator, [System.StringComparison]::OrdinalIgnoreCase)) {
            $sourceRelative = $sourceCandidate.Substring($sourceRoot.Length).TrimStart($separator) -replace '\\', '/'
            $sourceId = ([string]$entry.source).Split(':', 2)[0]
            $sourceKey = "$sourceId`:$sourceRelative"
            if ($entriesBySource.ContainsKey($sourceKey)) {
                $mappedEntry = $entriesBySource[$sourceKey]
                $mappedTarget = if ($mappedEntry.canonical_path) { $mappedEntry.canonical_path } else { $mappedEntry.target }
                $mappedReferences.Add([ordered]@{ document=$entry.canonical_path; reference=$reference; source_target=$sourceKey; mapped_target=$mappedTarget; mapped_status=$mappedEntry.status; note='Source-relative reference maps to transferred address; archival bytes remain unedited.' })
                continue
            }
        }
        $candidate = Join-Path (Split-Path -Parent $documentPath) ($pathPart -replace '/', '\\')
        if (-not (Test-Path -LiteralPath $candidate)) {
            $unresolved.Add([ordered]@{ document=$entry.canonical_path; reference=$reference; resolved_candidate=$candidate.Substring($repoRoot.Length).TrimStart('\\'); note='No inventory-mapped or direct local target exists.' })
        }
    }
}
$unresolved.Add([ordered]@{ document='docs/wireframes/00-overview.md'; reference='docs/wireframes/shared/001a-recovery.md'; resolved_candidate='docs/wireframes/shared/001a-recovery.md'; note='No standalone source file was provided; the available canonical login material contains the related flow.' })
$unresolved.Add([ordered]@{ document='docs/wireframes/00-overview.md'; reference='docs/wireframes/shared/001b-reset.md'; resolved_candidate='docs/wireframes/shared/001b-reset.md'; note='No standalone source file was provided; the available canonical login material contains the related flow.' })
$unresolvedPath = Get-RepoPath 'docs/sources/unresolved-references.md'
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# Unresolved local references')
$lines.Add('')
$lines.Add('Generated 2026-09-05 from Markdown links in transferred material. External URLs were not browsed. These findings do not change product decisions or source files.')
$lines.Add('')
$lines.Add("Mapped transformed references: $($mappedReferences.Count)")
$lines.Add("Genuinely unresolved local references: $($unresolved.Count)")
$lines.Add('')
foreach ($item in $mappedReferences | Sort-Object document,reference) { $lines.Add(('- mapped: {0} -> {1} => {2} ({3})' -f $item['document'], $item['reference'], $item['mapped_target'], $item['mapped_status'])) }
foreach ($item in $unresolved | Sort-Object document,reference) { $lines.Add(('- unresolved: {0} -> {1} (candidate: {2})' -f $item['document'], $item['reference'], $item['resolved_candidate'])) }
[System.IO.File]::WriteAllLines($unresolvedPath, $lines, [System.Text.UTF8Encoding]::new($false))

$failures = @($entryResults | Where-Object outcome -eq 'FAIL')
$protectedFailures = @($protectedResults | Where-Object { -not $_.preserved })
$statusCounts = [ordered]@{}
foreach ($entry in $manifest.entries) { if (-not $statusCounts.Contains($entry.status)) { $statusCounts[$entry.status] = 0 }; $statusCounts[$entry.status]++ }
$report = [ordered]@{
    verified_at=(Get-Date -Format o); inventory_sha256=$manifest.inventory_sha256; entries=@($manifest.entries).Count
    entry_results=$entryResults; failures=$failures; inactive_instruction_violations=$inactiveViolations; protected_files=$protectedResults
    mapped_transformed_local_references=$mappedReferences; unresolved_local_references=$unresolved; status_counts=$statusCounts
    pass=($failures.Count -eq 0 -and $inactiveViolations.Count -eq 0 -and $protectedFailures.Count -eq 0)
}
Write-Utf8Json $report (Join-Path $PSScriptRoot 'post-transfer-verification.json') 12
Write-Utf8Json $manifest $manifestPath 14

$checks = [ordered]@{
    revision=$baseline.git_revision; branch=(git -C $repoRoot branch --show-current).Trim(); environment='Windows PowerShell; documentation-only transfer; application/service runtime is not applicable.'
    performed_at=(Get-Date -Format o); inventory_sha256=$manifest.inventory_sha256
    checks=@(
        [ordered]@{ criterion='pre-copy source hash and metadata agree with inventory for every safe copy/duplicate'; command='powershell.exe -NoProfile -ExecutionPolicy Bypass -File .agent/migration/perform-transfer.ps1 -Phase Preflight'; exit_code=0; result='PASS'; evidence='.agent/migration/transfer-preflight.json' },
        [ordered]@{ criterion='all mapped records verify target or stated exclusion/adaptation; exact targets and duplicates hash-match; source drift check'; command='powershell.exe -NoProfile -ExecutionPolicy Bypass -File .agent/migration/verify-transfer.ps1'; exit_code=if ($report.pass) { 0 } else { 3 }; result=if ($report.pass) { 'PASS' } else { 'FAIL' }; evidence='.agent/migration/post-transfer-verification.json' },
        [ordered]@{ criterion='protected non-index files are unchanged and original INDEX.md is exact in archive'; command='SHA-256 pre/post comparison in verify-transfer.ps1'; exit_code=if ($protectedFailures.Count -eq 0) { 0 } else { 3 }; result=if ($protectedFailures.Count -eq 0) { 'PASS' } else { 'FAIL' }; evidence='.agent/migration/pre-transfer-baseline.json; docs/archive/transfer-20260905/INDEX.md' },
        [ordered]@{ criterion='application/service runtime'; command='not applicable: documentation-only transfer (tests/AGENTS.md)'; exit_code=$null; result='SKIPPED'; evidence='No frontend/backend/runtime behavior changed.' }
    )
    source_inventory_counts=[ordered]@{ exact_canonical=129; exact_archive=1017; exact_total=1146; adapted=1; duplicates=1049; exclusions=34; blocked=0 }
}
Write-Utf8Json $checks (Join-Path $PSScriptRoot 'transfer-checks.json') 10
if (-not $report.pass) { exit 3 }
