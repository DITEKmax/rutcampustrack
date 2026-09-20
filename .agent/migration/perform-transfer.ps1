param(
    [ValidateSet('Preflight', 'Transfer')]
    [string]$Phase = 'Preflight'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$inventoryPath = Join-Path $PSScriptRoot 'inventory.json'
$inventoryBytesHash = (Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToLowerInvariant()
$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json
$date = '2026-09-05'
$sourceRoots = @{}
foreach ($summary in $inventory.source_summaries) { $sourceRoots[$summary.source_id] = $summary.root }

function Get-SourcePath([object]$item) {
    return Join-Path $sourceRoots[$item.source_id] ($item.relative_path -replace '/', '\\')
}
function Get-RepoPath([string]$relativePath) {
    return Join-Path $repoRoot ($relativePath -replace '/', '\\')
}
function Get-Sha([string]$path) {
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Write-Utf8Json([object]$value, [string]$path, [int]$depth = 12) {
    $value | ConvertTo-Json -Depth $depth | Set-Content -LiteralPath $path -Encoding utf8
}

$protectedRelativePaths = @(
    'AGENTS.md',
    'frontends/AGENTS.md',
    'services/AGENTS.md',
    'tests/AGENTS.md',
    'docs/agent-workflow.md',
    'DATABASES_OVERVIEW.md',
    'docs/INDEX.md'
)
$protected = foreach ($relativePath in $protectedRelativePaths) {
    $path = Get-RepoPath $relativePath
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        [ordered]@{ path=$relativePath; sha256=(Get-Sha $path); size_bytes=(Get-Item -LiteralPath $path).Length }
    }
}
$gitRevision = (git -C $repoRoot rev-parse HEAD).Trim()
$baseline = [ordered]@{
    recorded_at=(Get-Date -Format o)
    git_revision=$gitRevision
    branch=(git -C $repoRoot branch --show-current).Trim()
    inventory_sha256=$inventoryBytesHash
    protected_files=$protected
    note='Hashes exclude secret/config files. Branch is intentionally unchanged because this shared checkout has pre-existing user work.'
}
Write-Utf8Json $baseline (Join-Path $PSScriptRoot 'pre-transfer-baseline.json') 8

$hashableItems = @($inventory.items | Where-Object { $null -ne $_.sha256 -and $_.status -in @('copy-canonical','copy-archive','duplicate') })
$sourceResults = [System.Collections.Generic.List[object]]::new()
foreach ($item in $hashableItems) {
    $sourcePath = Get-SourcePath $item
    $state = 'match'; $actualHash = $null; $actualSize = $null; $reason = $null
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        $state = 'missing'; $reason = 'source file no longer exists'
    } else {
        $actualSize = (Get-Item -LiteralPath $sourcePath).Length
        $actualHash = Get-Sha $sourcePath
        if ($actualHash -ne $item.sha256 -or $actualSize -ne [int64]$item.size_bytes) {
            $state = 'drifted'; $reason = 'source SHA-256 or size differs from inventory'
        }
    }
    $sourceResults.Add([ordered]@{
        source_id=$item.source_id; source_path=$item.relative_path; inventory_status=$item.status
        expected_sha256=$item.sha256; actual_sha256=$actualHash; expected_size_bytes=$item.size_bytes
        actual_size_bytes=$actualSize; state=$state; reason=$reason
    })
}
$copyItems = @($inventory.items | Where-Object { $_.status -in @('copy-canonical','copy-archive') })
$targetCollisions = [System.Collections.Generic.List[object]]::new()
foreach ($item in $copyItems) {
    $targetPath = Get-RepoPath $item.destination
    if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
        $targetCollisions.Add([ordered]@{ source_id=$item.source_id; source_path=$item.relative_path; target=$item.destination; target_sha256=(Get-Sha $targetPath); expected_sha256=$item.sha256 })
    }
}
$unsafeInstructionTargets = @($copyItems | Where-Object {
    $_.relative_path -match '(^|/)(AGENTS(\.override)?\.md|SKILL\.md)$' -and $_.destination -notmatch '\.inactive$'
} | ForEach-Object { [ordered]@{ source_id=$_.source_id; source_path=$_.relative_path; target=$_.destination } })
$preflight = [ordered]@{
    checked_at=(Get-Date -Format o); inventory_sha256=$inventoryBytesHash; safe_hashable_items=$hashableItems.Count
    source_matches=@($sourceResults | Where-Object state -eq 'match').Count
    source_drift_or_missing=@($sourceResults | Where-Object state -ne 'match').Count
    source_results=$sourceResults
    target_collisions=$targetCollisions
    unsafe_instruction_targets=$unsafeInstructionTargets
    pass=(@($sourceResults | Where-Object state -ne 'match').Count -eq 0 -and $targetCollisions.Count -eq 0 -and $unsafeInstructionTargets.Count -eq 0)
}
Write-Utf8Json $preflight (Join-Path $PSScriptRoot 'transfer-preflight.json') 10

if ($Phase -eq 'Preflight') {
    if (-not $preflight.pass) { exit 2 }
    exit 0
}

$sourceState = @{}
foreach ($entry in $sourceResults) { $sourceState["$($entry.source_id):$($entry.source_path)"] = $entry }
$blockedBySource = @{}
foreach ($entry in $sourceResults | Where-Object state -ne 'match') { $blockedBySource["$($entry.source_id):$($entry.source_path)"] = $entry.reason }
$targetCollisionKeys = @{}
foreach ($entry in $targetCollisions) { $targetCollisionKeys["$($entry.source_id):$($entry.source_path)"] = $entry }

$copyResults = [System.Collections.Generic.List[object]]::new()
foreach ($item in $copyItems) {
    $key = "$($item.source_id):$($item.relative_path)"
    $targetPath = Get-RepoPath $item.destination
    $copyState = 'exact-copy'; $reason = $null; $resultHash = $null
    if ($blockedBySource.ContainsKey($key)) {
        $copyState = 'blocked-source-drift'; $reason = $blockedBySource[$key]
    } elseif ($targetCollisionKeys.ContainsKey($key)) {
        $collision = $targetCollisionKeys[$key]
        if ($collision.target_sha256 -eq $item.sha256) {
            $copyState = 'already-present-exact'; $resultHash = $collision.target_sha256
        } else {
            $copyState = 'blocked-target-collision'; $reason = 'target exists with different SHA-256; preserved and not overwritten'
        }
    } else {
        $targetDirectory = Split-Path -Parent $targetPath
        New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
        Copy-Item -LiteralPath (Get-SourcePath $item) -Destination $targetPath -Force:$false
        $resultHash = Get-Sha $targetPath
        if ($resultHash -ne $item.sha256) { throw "Copied hash mismatch: $key" }
    }
    $copyResults.Add([ordered]@{ key=$key; source_id=$item.source_id; source_path=$item.relative_path; target=$item.destination; state=$copyState; result_sha256=$resultHash; reason=$reason })
}

$indexRelativePath = 'docs/INDEX.md'
$indexPath = Get-RepoPath $indexRelativePath
$indexArchiveRelativePath = 'docs/archive/transfer-20260905/INDEX.md'
$indexArchivePath = Get-RepoPath $indexArchiveRelativePath
$indexOriginalHash = Get-Sha $indexPath
if (Test-Path -LiteralPath $indexArchivePath) { throw "Index preservation target already exists: $indexArchiveRelativePath" }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $indexArchivePath) | Out-Null
Copy-Item -LiteralPath $indexPath -Destination $indexArchivePath -Force:$false
if ((Get-Sha $indexArchivePath) -ne $indexOriginalHash) { throw 'Preserved INDEX.md hash mismatch' }
$indexOriginal = [System.IO.File]::ReadAllText($indexPath)
$indexAddition = @"
<!--
Materials transfer addition: 2026-09-05.
This dated addition supersedes only the navigation statement in the previous INDEX.md dated 2026-04-27.
The exact prior version is preserved at $indexArchiveRelativePath.
Source provenance registry: docs/sources/manifest.yaml.
-->

## Materials transfer registry — 2026-09-05

`docs/sources/manifest.yaml` lists every material and its status. Archive records are evidence, not active instructions or adopted product decisions. Open owner decisions remain blocked in the registry and transfer report.

---

"@
$mojibakeDash = [string]([char]0x0432) + [char]0x0402 + [char]0x201D
$indexAddition = $indexAddition.Replace($mojibakeDash, '--')
[System.IO.File]::WriteAllText($indexPath, $indexAddition + $indexOriginal, [System.Text.UTF8Encoding]::new($false))
$indexFinalHash = Get-Sha $indexPath

$bySourcePath = @{}
foreach ($item in $inventory.items) { $bySourcePath["$($item.source_id):$($item.relative_path)"] = $item }
$copyByKey = @{}
foreach ($result in $copyResults) { $copyByKey[$result.key] = $result }
function Resolve-DuplicateTarget([object]$item) {
    if ($item.source_id -eq 'repo-history') { return $item.relative_path }
    if ($null -eq $item.retained_address) { return $null }
    $reference = [string]$item.retained_address
    $colon = $reference.IndexOf(':')
    if ($colon -lt 1) { return $null }
    $referenceKey = $reference.Substring(0, $colon) + ':' + $reference.Substring($colon + 1)
    if ($bySourcePath.ContainsKey($referenceKey)) { return $bySourcePath[$referenceKey].destination }
    return $null
}

$manifestItems = [System.Collections.Generic.List[object]]::new()
foreach ($item in $inventory.items) {
    $key = "$($item.source_id):$($item.relative_path)"
    $actualStatus = $item.status; $canonicalPath = $item.destination; $resultHash = $null; $verificationState = $null; $verificationReason = $null
    if ($item.status -in @('copy-canonical','copy-archive')) {
        $copyResult = $copyByKey[$key]
        if ($copyResult.state -in @('exact-copy','already-present-exact')) {
            $resultHash = $copyResult.result_sha256; $verificationState = 'verified-exact-target'
        } else {
            $actualStatus = 'blocked'; $verificationState = $copyResult.state; $verificationReason = $copyResult.reason
        }
    } elseif ($item.status -eq 'duplicate') {
        $canonicalPath = Resolve-DuplicateTarget $item
        if ($item.source_id -eq 'repo-history' -and $item.relative_path -eq $indexRelativePath) {
            $actualStatus = 'adapted'; $canonicalPath = $indexRelativePath; $resultHash = $indexFinalHash
            $verificationState = 'original-preserved-and-index-adapted'; $verificationReason = "Original exact source preserved at $indexArchiveRelativePath ($indexOriginalHash)."
        } elseif ($null -eq $canonicalPath) {
            $actualStatus = 'blocked'; $verificationState = 'unresolved-retained-address'; $verificationReason = 'No inventory target resolves retained_address.'
        } else {
            $duplicateTarget = Get-RepoPath $canonicalPath
            if (Test-Path -LiteralPath $duplicateTarget -PathType Leaf) {
                $resultHash = Get-Sha $duplicateTarget
                if ($resultHash -eq $item.sha256) { $verificationState = 'verified-retained-target' }
                else { $actualStatus = 'blocked'; $verificationState = 'retained-target-hash-mismatch'; $verificationReason = 'Resolved retained target hash differs from the duplicate source.' }
            } else {
                $actualStatus = 'blocked'; $verificationState = 'retained-target-missing'; $verificationReason = 'Resolved retained target does not exist.'
            }
        }
    } elseif ($item.status -eq 'exclude') {
        $canonicalPath = $null; $verificationState = 'excluded-by-path-without-content-hash'; $verificationReason = $item.reason
    }
    $decisionOwner = $null
    $decisionDate = $null
    $supersedes = @()
    $supersededBy = @()
    if ($key -eq 'kit:journal/DECISIONS.md') {
        $decisionOwner = 'project owner'
        $decisionDate = '2026-09-05'
        $supersedes = @(
            'design:knowledge/PROJECT_CONTEXT.md (desktop-first implementation order only)',
            'design:02-backend/transport-decision.md (per-surface BFF recommendation)',
            'design:04-figma/figma-spec-shadcn-overlay.md (Tailwind/shadcn overlay)'
        )
    }
    if ($key -in @('design:knowledge/PROJECT_CONTEXT.md','design:02-backend/transport-decision.md','design:04-figma/figma-spec-shadcn-overlay.md')) { $supersededBy = @('kit:journal/DECISIONS.md') }
    $sourceVerification = $sourceState[$key]
    $manifestItems.Add([ordered]@{
        source=$key; source_root=$sourceRoots[$item.source_id]; source_path=$item.relative_path
        target=$item.destination; canonical_path=$canonicalPath; inventory_status=$item.status; status=$actualStatus
        decision_owner=$decisionOwner; decision_date=$decisionDate; transfer_owner='root'; transfer_date=(Get-Date -Format 'yyyy-MM-dd')
        supersedes=$supersedes; superseded_by=$supersededBy; category=$item.category; reason=$item.reason
        retained_address=$item.retained_address; source_sha256=$item.sha256; verified_source_sha256=if ($null -ne $sourceVerification) { $sourceVerification.actual_sha256 } else { $null }
        result_sha256=$resultHash; transfer_kind=if ($actualStatus -in @('copy-canonical','copy-archive')) { 'exact-copy' } elseif ($actualStatus -eq 'adapted') { 'adapted-index' } else { $null }
        change_nature=if ($actualStatus -eq 'adapted') { 'Prepend dated navigation addition; exact original preserved in archive.' } else { $null }
        verification_state=$verificationState; verification_reason=$verificationReason
    })
}
$manifest = [ordered]@{
    schema_version='1.0'; generated_at=(Get-Date -Format o); inventory_sha256=$inventoryBytesHash
    transfer_policy=[ordered]@{
        source_materials_are_evidence='Archive/research copies do not activate instructions, change Figma, or adopt product/API decisions.'
        architecture_resolution='PWA/TMA before web; Vue/PCSS without Tailwind/shadcn; one BFF with REST at edge and gRPC down to services.'
        unresolved_owner_decisions=@('first PWA/TMA release scope','cache owner','role visibility and gradient tokens','radius baseline','Admin 668/659 discrepancy')
        part_26_scope='Mobile Teacher Profile role entry and three named mobile role-gradient carriers only; no desktop/forms/auth adoption.'
    }
    entries=$manifestItems
}
$manifestPath = Get-RepoPath 'docs/sources/manifest.yaml'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $manifestPath) | Out-Null
Write-Utf8Json $manifest $manifestPath 14

$statusCounts = @{}
foreach ($entry in $manifestItems) { if (-not $statusCounts.ContainsKey($entry.status)) { $statusCounts[$entry.status] = 0 }; $statusCounts[$entry.status]++ }
$postProtected = foreach ($relativePath in $protectedRelativePaths) {
    $path = Get-RepoPath $relativePath
    if (Test-Path -LiteralPath $path -PathType Leaf) { [ordered]@{ path=$relativePath; sha256=(Get-Sha $path); size_bytes=(Get-Item -LiteralPath $path).Length } }
}
$preservedProtected = @($protected | Where-Object path -ne $indexRelativePath | ForEach-Object {
    $after = $postProtected | Where-Object path -eq $_.path
    [ordered]@{ path=$_.path; preserved=($null -ne $after -and $after.sha256 -eq $_.sha256) }
})
$blockedCount = if ($statusCounts.ContainsKey('blocked')) { [int]$statusCounts['blocked'] } else { 0 }
$transferChecks = [ordered]@{
    revision=$gitRevision; environment='Windows PowerShell; documentation-only transfer; application/service runtime is not applicable.'
    performed_at=(Get-Date -Format o); inventory_sha256=$inventoryBytesHash
    checks=@(
        [ordered]@{ criterion='pre-copy source hash and metadata agree with inventory for every safe copy/duplicate'; command='.agent/migration/perform-transfer.ps1 -Phase Preflight'; exit_code=0; result=if ($preflight.pass) { 'PASS' } else { 'FAIL' }; evidence='transfer-preflight.json' },
        [ordered]@{ criterion='exact copies hash-identical at canonical/archive targets'; command='second pass in perform-transfer.ps1'; exit_code=0; result=if ($blockedCount -eq 0) { 'PASS' } else { 'FAIL' }; evidence='docs/sources/manifest.yaml' },
        [ordered]@{ criterion='protected non-index files are unchanged'; command='SHA-256 pre/post comparison'; exit_code=0; result=if (@($preservedProtected | Where-Object { -not $_.preserved }).Count -eq 0) { 'PASS' } else { 'FAIL' }; evidence='pre-transfer-baseline.json' },
        [ordered]@{ criterion='application/service runtime'; command='not applicable: documentation-only transfer (tests/AGENTS.md)'; exit_code=$null; result='SKIPPED'; evidence='No frontend/backend/runtime behavior changed.' }
    )
    status_counts=$statusCounts; protected_preservation=$preservedProtected
}
Write-Utf8Json $transferChecks (Join-Path $PSScriptRoot 'transfer-checks.json') 10

if ($blockedCount -gt 0) { exit 3 }
exit 0
