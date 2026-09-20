$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repo 'docs\sources\manifest.yaml'
$manifest = [System.IO.File]::ReadAllText($manifestPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$date = '2026-09-06'

$generated = @(
    'docs/product/job-stories.yaml',
    'docs/implementation/backend-delta.yaml',
    'docs/implementation/legacy-retirement.yaml',
    'docs/implementation/readiness.md',
    'docs/implementation/backlog.md',
    'docs/implementation/checks-catalog.md',
    'docs/implementation/release-surface-matrix.md',
    'docs/implementation/source-conflicts.md',
    'docs/implementation/story-backend-map.yaml',
    'docs/implementation/parallel-development.md'
)
$parallelSourceRoot = 'C:\Users\maksd\ruttrack\rutcampustrack-kit'
$parallelSourcePath = '_work/prompts/agent-project-start/parallel-development-playbook.md'
$parallelSource = Join-Path $parallelSourceRoot $parallelSourcePath
if (-not (Test-Path -LiteralPath $parallelSource -PathType Leaf)) { throw "Canonical parallel playbook is missing: $parallelSource" }
$parallelSourceHash = (Get-FileHash -LiteralPath $parallelSource -Algorithm SHA256).Hash.ToLowerInvariant()

foreach ($relative in $generated) {
    $path = Join-Path $repo $relative
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $existing = @($manifest.entries | Where-Object { $_.target -eq $relative })
    if ($existing.Count -eq 0) {
        $entry = [pscustomobject][ordered]@{
            entry_id = if ($relative -eq 'docs/implementation/parallel-development.md') { 'preparation:parallel-development-canonical-copy' } else { "preparation:$relative" }
            source = if ($relative -eq 'docs/implementation/parallel-development.md') { 'kit:_work/prompts/agent-project-start/parallel-development-playbook.md#canonical-copy' } else { "derived:preparation/$relative" }
            source_identity = if ($relative -eq 'docs/implementation/parallel-development.md') { 'kit:_work/prompts/agent-project-start/parallel-development-playbook.md' } else { $null }
            source_root = if ($relative -eq 'docs/implementation/parallel-development.md') { $parallelSourceRoot } else { $repo }
            source_path = if ($relative -eq 'docs/implementation/parallel-development.md') { $parallelSourcePath } else { $relative }
            target = $relative
            canonical_path = $relative
            inventory_status = 'post-transfer-generated'
            status = if ($relative -eq 'docs/implementation/parallel-development.md') { 'copy-canonical-post-transfer' } else { 'generated' }
            decision_owner = $null
            decision_date = $null
            supersedes = @()
            superseded_by = @()
            category = 'preparation-artifact'
            reason = 'Generated/adapted by the 2026-09-06 preparation; not an incoming transfer source record.'
            retained_address = $null
            source_sha256 = if ($relative -eq 'docs/implementation/parallel-development.md') { $parallelSourceHash } else { $null }
            verified_source_sha256 = if ($relative -eq 'docs/implementation/parallel-development.md') { $parallelSourceHash } else { $null }
            result_sha256 = $hash
            transfer_kind = if ($relative -eq 'docs/implementation/parallel-development.md') { 'exact-copy' } else { 'generated' }
            change_nature = 'post-transfer-preparation'
            verification_state = if ($relative -eq 'docs/implementation/parallel-development.md') { 'verified-exact-target' } else { 'generated-pending-preparation-checks' }
            verification_reason = $null
            transfer_owner = '/root/preparation_writer'
            transfer_date = $date
        }
        $manifest.entries += $entry
    } else {
        $existing[0].result_sha256 = $hash
        if ($relative -eq 'docs/implementation/parallel-development.md') {
            $existing[0] | Add-Member -NotePropertyName entry_id -NotePropertyValue 'preparation:parallel-development-canonical-copy' -Force
            $existing[0].source = 'kit:_work/prompts/agent-project-start/parallel-development-playbook.md#canonical-copy'
            $existing[0] | Add-Member -NotePropertyName source_identity -NotePropertyValue 'kit:_work/prompts/agent-project-start/parallel-development-playbook.md' -Force
            $existing[0].source_root = $parallelSourceRoot
            $existing[0].source_path = $parallelSourcePath
            $existing[0].source_sha256 = $parallelSourceHash
            $existing[0].verified_source_sha256 = $parallelSourceHash
            $existing[0].status = 'copy-canonical-post-transfer'
            $existing[0].inventory_status = 'post-transfer-canonical-copy'
            $existing[0].transfer_kind = 'exact-copy'
            $existing[0].reason = 'Exact canonical copy made during preparation; archive target remains a distinct historical entry.'
            $existing[0].verification_state = 'verified-exact-target'
        } else { $existing[0].verification_state = 'generated-pending-preparation-checks' }
        $existing[0].transfer_owner = '/root/preparation_writer'
        $existing[0].transfer_date = $date
    }
}

$index = @($manifest.entries | Where-Object { $_.target -eq 'docs/INDEX.md' }) | Select-Object -First 1
if ($null -ne $index) {
    $index.result_sha256 = (Get-FileHash -LiteralPath (Join-Path $repo 'docs\INDEX.md') -Algorithm SHA256).Hash.ToLowerInvariant()
    $index.change_nature = 'adapted-navigation-plus-preparation-links'
    $index.verification_state = 'adapted-pending-preparation-checks'
    $index.transfer_owner = '/root/preparation_writer'
    $index.transfer_date = $date
}

$manifest | Add-Member -NotePropertyName post_transfer_adaptations -NotePropertyValue @([pscustomobject][ordered]@{
    date = $date
    owner = '/root/preparation_writer'
    source_decision_owner = $null
    source_decision_date = $null
    note = 'Preparation artifacts and INDEX navigation were added without rerunning bulk transfer or changing external source bytes.'
}) -Force
$manifest | Add-Member -NotePropertyName governance_changes -NotePropertyValue @([pscustomobject][ordered]@{
    date = $date
    owner = '/root/preparation_writer'
    paths = @('AGENTS.md','docs/agent-workflow.md')
    reason = 'Owner-authorized dated rule: after contract freeze FE and BE developers of one story may work in separate worktrees with single ownership of shared artifacts.'
    prior_hashes = [ordered]@{ 'AGENTS.md'='82e6ce5ca3eabd088afbd7e7fc89544ec1b5e78f5a63876dee0463dd97acd759'; 'docs/agent-workflow.md'='daa588fc331cc5b4042b2f2d6c8e4d1a78e90bda79cdab60006dbb8dadfcb3d4' }
}) -Force
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 14), [System.Text.UTF8Encoding]::new($false))
Write-Output "entries=$($manifest.entries.Count); generated=$($generated.Count)"
