[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{40}$')]
    [string]$FinalSHA,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{40}$')]
    [string]$ExpectedRootHEAD,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._/-]+$')]
    [string]$ExpectedBranch,

    [ValidatePattern('^[A-Za-z0-9._/-]+$')]
    [string]$TargetBranch = 'main',

    [string]$BackupRoot = '',

    [switch]$Apply,

    [switch]$AllowInstructionConflicts
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'PowerShell 7 or newer is required.'
}

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$sensitivePattern = '(?i)(^|/)(\.env($|\.)|.*(secret|credential|password|private[-_]?key)(/|$|[._]))|(^|/).*?\.(pem|key|p12|pfx)$'
$instructionPaths = @('AGENTS.md', 'docs/agent-workflow.md')
$backupPathUsed = $null
$backupManifestPath = $null
$quarantinePath = $null
$movedCollisions = [System.Collections.Generic.List[object]]::new()
$blocked = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$trackedDirty = [System.Collections.Generic.List[object]]::new()
$collisions = [System.Collections.Generic.List[object]]::new()
$instructionConflicts = [System.Collections.Generic.List[object]]::new()
$sensitiveSkipped = [System.Collections.Generic.List[string]]::new()
$preflight = $null

function Assert-InRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Candidate,
        [Parameter(Mandatory = $true)][string]$Base
    )
    $candidateFull = [IO.Path]::GetFullPath($Candidate)
    $baseFull = [IO.Path]::GetFullPath($Base).TrimEnd('\')
    $prefix = $baseFull + '\'
    if (-not $candidateFull.Equals($baseFull, [StringComparison]::OrdinalIgnoreCase) -and
        -not $candidateFull.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes intended root: $candidateFull"
    }
}

function Get-RepoPath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    $normalized = ($RelativePath -replace '\\', '/').Trim()
    if ([string]::IsNullOrWhiteSpace($normalized) -or
        $normalized.StartsWith('/') -or
        $normalized -match '(^|/)\.\.?(/|$)') {
        throw "Unsafe repository-relative path: $RelativePath"
    }
    $full = [IO.Path]::GetFullPath((Join-Path $repo ($normalized -replace '/', '\')))
    Assert-InRoot -Candidate $full -Base $repo
    return $full
}

function Assert-NoReparse {
    param([Parameter(Mandatory = $true)][string]$Path)
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Reparse point is not allowed for guarded file operation: $Path"
    }
}

function Invoke-GitText {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$Mutating
    )
    $gitArguments = @('-c', 'core.excludesFile=')
    if (-not $Mutating) {
        $gitArguments += '--no-optional-locks'
    }
    $gitArguments += $Arguments
    $output = @(& git @gitArguments 2>$null | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit $exitCode"
    }
    return $output
}

function Get-GitExitCode {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $gitArguments = @('-c', 'core.excludesFile=', '--no-optional-locks') + $Arguments
    $null = & git @gitArguments 2>$null
    return [int]$LASTEXITCODE
}

function Get-FileRecord {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    $full = Get-RepoPath -RelativePath $RelativePath
    if (Test-Path -LiteralPath $full -PathType Leaf) {
        Assert-NoReparse -Path $full
        $item = Get-Item -LiteralPath $full -Force
        return [ordered]@{
            path = $RelativePath
            state = 'present'
            size_bytes = [int64]$item.Length
            sha256 = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
            git_blob = (@(Invoke-GitText -Arguments @('hash-object', '--no-filters', '--', $full)))[0].Trim()
            last_write_utc = $item.LastWriteTimeUtc.ToString('o')
        }
    }
    if (Test-Path -LiteralPath $full -PathType Container) {
        return [ordered]@{
            path = $RelativePath
            state = 'directory'
            size_bytes = $null
            sha256 = $null
            git_blob = $null
            last_write_utc = $null
        }
    }
    return [ordered]@{
        path = $RelativePath
        state = 'absent'
        size_bytes = $null
        sha256 = $null
        git_blob = $null
        last_write_utc = $null
    }
}

function Get-TreeBlob {
    param(
        [Parameter(Mandatory = $true)][string]$SHA,
        [Parameter(Mandatory = $true)][string]$RelativePath
    )
    try {
        $value = (@(Invoke-GitText -Arguments @('rev-parse', ($SHA + ':' + $RelativePath))))[0].Trim()
        if ($value -match '^[0-9a-fA-F]{40}$') {
            return $value.ToLowerInvariant()
        }
    } catch {
        return $null
    }
    return $null
}

function Ensure-DirectoryChain {
    param([Parameter(Mandatory = $true)][string]$Directory)
    Assert-InRoot -Candidate $Directory -Base $repo
    $relative = $Directory.Substring($repo.Length).TrimStart('\').TrimStart('/')
    if ([string]::IsNullOrWhiteSpace($relative)) {
        return
    }
    $cursor = $repo
    foreach ($part in ($relative -split '[\\/]')) {
        if ([string]::IsNullOrWhiteSpace($part)) {
            continue
        }
        $cursor = Join-Path $cursor $part
        if (Test-Path -LiteralPath $cursor) {
            Assert-NoReparse -Path $cursor
        } else {
            New-Item -ItemType Directory -Path $cursor | Out-Null
        }
    }
}

function Write-NewJson {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Value
    )
    if (Test-Path -LiteralPath $Path) {
        throw "Refusing to overwrite existing manifest: $Path"
    }
    $text = $Value | ConvertTo-Json -Depth 20
    $encoding = [Text.UTF8Encoding]::new($false)
    $bytes = $encoding.GetBytes($text + [Environment]::NewLine)
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }
}

function Restore-QuarantinedFiles {
    foreach ($entry in $movedCollisions) {
        $source = [string]$entry.quarantine_path
        $destination = [string]$entry.original_path
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            continue
        }
        Ensure-DirectoryChain -Directory (Split-Path -Parent $destination)
        if (Test-Path -LiteralPath $destination -PathType Container) {
            throw "Cannot restore file over directory: $destination"
        }
        Copy-Item -LiteralPath $source -Destination $destination -Force
        Assert-NoReparse -Path $destination
        $restored = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($restored -ne $entry.owner_sha256) {
            throw "Restoration hash mismatch for $($entry.relative_path)"
        }
    }
}

function Assert-TrackedDirtySnapshots {
    foreach ($record in $trackedDirty) {
        $current = Get-FileRecord -RelativePath $record.path
        if ($current.state -ne $record.state) {
            throw "Tracked dirty state changed for $($record.path): expected $($record.state), got $($current.state)"
        }
        if ($record.state -eq 'present' -and $current.sha256 -ne $record.sha256) {
            throw "Tracked dirty SHA-256 changed for $($record.path)"
        }
    }
}

try {
    $actualTop = [IO.Path]::GetFullPath((@(Invoke-GitText -Arguments @('rev-parse', '--show-toplevel')))[0].Trim())
    if (-not $actualTop.Equals($repo, [StringComparison]::OrdinalIgnoreCase)) {
        [void]$blocked.Add("Git top-level differs from script root: $actualTop")
    }

    $actualBranch = (Invoke-GitText -Arguments @('branch', '--show-current') | Select-Object -First 1).Trim()
    $actualHead = (Invoke-GitText -Arguments @('rev-parse', 'HEAD') | Select-Object -First 1).Trim().ToLowerInvariant()
    $expectedHead = $ExpectedRootHEAD.ToLowerInvariant()
    $finalShaNormalized = $FinalSHA.ToLowerInvariant()
    $targetRef = 'refs/heads/' + $TargetBranch
    $targetHead = (Invoke-GitText -Arguments @('rev-parse', '--verify', $targetRef) | Select-Object -First 1).Trim().ToLowerInvariant()
    $finalType = (Invoke-GitText -Arguments @('cat-file', '-t', $finalShaNormalized) | Select-Object -First 1).Trim()
    $ancestorExit = Get-GitExitCode -Arguments @('merge-base', '--is-ancestor', $expectedHead, $finalShaNormalized)

    if ($actualBranch -ne $ExpectedBranch) {
        [void]$blocked.Add("Current branch is '$actualBranch'; expected '$ExpectedBranch'")
    }
    if ($actualHead -ne $expectedHead) {
        [void]$blocked.Add("Current HEAD is $actualHead; expected $expectedHead")
    }
    if ($finalType -ne 'commit') {
        [void]$blocked.Add("FinalSHA is not an existing commit: $finalShaNormalized")
    }
    if ($ancestorExit -ne 0) {
        [void]$blocked.Add("FinalSHA is not a descendant of expected root HEAD")
    }
    if ($targetHead -ne $expectedHead) {
        [void]$blocked.Add("Target '$TargetBranch' is $targetHead; expected base $expectedHead")
    }

    $gitDirRaw = (Invoke-GitText -Arguments @('rev-parse', '--git-dir') | Select-Object -First 1).Trim()
    $gitDir = if ([IO.Path]::IsPathRooted($gitDirRaw)) {
        [IO.Path]::GetFullPath($gitDirRaw)
    } else {
        [IO.Path]::GetFullPath((Join-Path $repo $gitDirRaw))
    }
    $operationMarkers = @('MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'BISECT_LOG')
    foreach ($marker in $operationMarkers) {
        if (Test-Path -LiteralPath (Join-Path $gitDir $marker)) {
            [void]$blocked.Add("Git operation marker exists: $marker")
        }
    }
    foreach ($directory in @('rebase-merge', 'rebase-apply')) {
        if (Test-Path -LiteralPath (Join-Path $gitDir $directory) -PathType Container) {
            [void]$blocked.Add("Git operation directory exists: $directory")
        }
    }

    $trackedSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in (Invoke-GitText -Arguments @('ls-files'))) {
        [void]$trackedSet.Add(($path -replace '\\', '/'))
    }

    $stagedPaths = @(Invoke-GitText -Arguments @('diff', '--cached', '--name-only'))
    if ($stagedPaths.Count -gt 0) {
        [void]$blocked.Add("Staged changes are present; preserving the index is outside this guarded operation")
    }
    $dirtyPaths = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in (Invoke-GitText -Arguments @('diff', '--name-only'))) {
        [void]$dirtyPaths.Add(($path -replace '\\', '/'))
    }
    foreach ($path in $dirtyPaths) {
        if ($path -match $sensitivePattern) {
            [void]$sensitiveSkipped.Add($path)
            [void]$blocked.Add("Sensitive tracked dirty path cannot be inspected: $path")
            continue
        }
        $record = Get-FileRecord -RelativePath $path
        [void]$trackedDirty.Add($record)
    }

    $changedSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $allChangedPaths = @(Invoke-GitText -Arguments @('diff', '--name-only', '--no-renames', $expectedHead, $finalShaNormalized))
    foreach ($path in $allChangedPaths) {
        [void]$changedSet.Add(($path -replace '\\', '/'))
    }
    foreach ($path in $dirtyPaths) {
        if ($changedSet.Contains($path)) {
            [void]$blocked.Add("Tracked dirty path intersects incoming change: $path")
        }
    }

    $incomingPaths = @(Invoke-GitText -Arguments @('diff', '--name-only', '--diff-filter=ACMRTUXB', '--no-renames', $expectedHead, $finalShaNormalized))
    foreach ($path in $incomingPaths) {
        $relativePath = ($path -replace '\\', '/')
        $fullPath = Get-RepoPath -RelativePath $relativePath
        if ($relativePath -match $sensitivePattern) {
            [void]$sensitiveSkipped.Add($relativePath)
            continue
        }
        if ($trackedSet.Contains($relativePath)) {
            continue
        }
        if (-not (Test-Path -LiteralPath $fullPath)) {
            continue
        }
        if (Test-Path -LiteralPath $fullPath -PathType Container) {
            [void]$blocked.Add("Untracked directory obstructs incoming file: $relativePath")
            continue
        }
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            [void]$blocked.Add("Unsupported untracked path obstructs incoming file: $relativePath")
            continue
        }
        Assert-NoReparse -Path $fullPath
        $owner = Get-FileRecord -RelativePath $relativePath
        $incomingBlob = Get-TreeBlob -SHA $finalShaNormalized -RelativePath $relativePath
        if ([string]::IsNullOrWhiteSpace($incomingBlob)) {
            [void]$blocked.Add("Incoming changed path is absent from final tree: $relativePath")
            continue
        }
        $classification = if ($owner.git_blob -eq $incomingBlob) { 'identical' } else { 'different' }
        $entry = [ordered]@{
            relative_path = $relativePath
            original_path = $fullPath
            owner_sha256 = $owner.sha256
            owner_size_bytes = $owner.size_bytes
            owner_git_blob = $owner.git_blob
            incoming_git_blob = $incomingBlob
            classification = $classification
            backup_path = $null
            quarantine_path = $null
            restored_sha256 = $null
        }
        [void]$collisions.Add($entry)
        if ($relativePath -in $instructionPaths -and $classification -eq 'different') {
            $integrationPath = Join-Path $repo ('.agent\worktrees\js-student-01\integration\' + ($relativePath -replace '/', '\'))
            $integrationTime = $null
            if (Test-Path -LiteralPath $integrationPath -PathType Leaf) {
                $integrationTime = (Get-Item -LiteralPath $integrationPath -Force).LastWriteTimeUtc.ToString('o')
            }
            $rootNewer = $false
            if ($integrationTime) {
                $rootNewer = ([datetime]$owner.last_write_utc) -gt ([datetime]$integrationTime)
            }
            [void]$instructionConflicts.Add([ordered]@{
                path = $relativePath
                owner_sha256 = $owner.sha256
                incoming_git_blob = $incomingBlob
                root_last_write_utc = $owner.last_write_utc
                integration_last_write_utc = $integrationTime
                root_newer_than_integration = $rootNewer
            })
        }
    }

    $preflightStatus = if ($blocked.Count -gt 0) { 'BLOCKED' } elseif ($instructionConflicts.Count -gt 0) { 'PASS_WITH_INSTRUCTION_REVIEW' } else { 'PASS' }
    $preflight = [ordered]@{
        schema_version = '1'
        status = $preflightStatus
        mode = if ($Apply) { 'apply' } else { 'dry-run' }
        generated_at_utc = (Get-Date).ToUniversalTime().ToString('o')
        repo = $repo
        expected_root_head = $expectedHead
        actual_root_head = $actualHead
        expected_branch = $ExpectedBranch
        actual_branch = $actualBranch
        target_branch = $TargetBranch
        target_branch_head = $targetHead
        final_sha = $finalShaNormalized
        incoming_changed_paths = $allChangedPaths.Count
        staged_paths = $stagedPaths.Count
        tracked_dirty = @($trackedDirty)
        tracked_dirty_incoming_overlap = @($dirtyPaths | Where-Object { $changedSet.Contains($_) })
        untracked_collisions = @($collisions)
        collision_counts = [ordered]@{
            total = $collisions.Count
            identical = @($collisions | Where-Object { $_.classification -eq 'identical' }).Count
            different = @($collisions | Where-Object { $_.classification -eq 'different' }).Count
        }
        instruction_conflicts = @($instructionConflicts)
        sensitive_paths_skipped = @($sensitiveSkipped | Sort-Object -Unique)
        blocked = @($blocked)
        warnings = @($warnings)
        main_branch_master_absent = (Get-GitExitCode -Arguments @('show-ref', '--verify', '--quiet', 'refs/heads/master')) -ne 0
        apply_gate = 'Use -Apply only after root records independent Sol PASS; use -AllowInstructionConflicts only after root resolves the two instruction-file conflicts.'
    }

    if ($blocked.Count -gt 0) {
        $preflight | ConvertTo-Json -Depth 20
        exit 2
    }
    if (-not $Apply) {
        $preflight | ConvertTo-Json -Depth 20
        exit 0
    }
    if ($instructionConflicts.Count -gt 0 -and -not $AllowInstructionConflicts) {
        $preflight.status = 'BLOCKED_INSTRUCTION_REVIEW'
        $preflight.blocked = @('Instruction-file collisions require explicit -AllowInstructionConflicts after root decision')
        $preflight | ConvertTo-Json -Depth 20
        exit 2
    }

    if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
        $BackupRoot = Join-Path $artifactRoot ('backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N'))
    }
    $backupPathUsed = [IO.Path]::GetFullPath($BackupRoot)
    Assert-InRoot -Candidate $backupPathUsed -Base $artifactRoot
    if ($backupPathUsed.Equals($artifactRoot, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $backupPathUsed)) {
        throw "Backup root must be a new child of the assigned artifact directory: $backupPathUsed"
    }
    New-Item -ItemType Directory -Path $backupPathUsed | Out-Null
    $copyRoot = Join-Path $backupPathUsed 'owner-copy'
    $quarantinePath = Join-Path $backupPathUsed 'quarantine'
    Ensure-DirectoryChain -Directory $copyRoot
    Ensure-DirectoryChain -Directory $quarantinePath

    foreach ($entry in $collisions) {
        $copyPath = Join-Path $copyRoot ($entry.relative_path -replace '/', '\')
        $quarantineFile = Join-Path $quarantinePath ($entry.relative_path -replace '/', '\')
        Ensure-DirectoryChain -Directory (Split-Path -Parent $copyPath)
        Ensure-DirectoryChain -Directory (Split-Path -Parent $quarantineFile)
        Copy-Item -LiteralPath $entry.original_path -Destination $copyPath -Force:$false
        Assert-NoReparse -Path $copyPath
        $copyHash = (Get-FileHash -LiteralPath $copyPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($copyHash -ne $entry.owner_sha256) {
            throw "Backup hash mismatch for $($entry.relative_path)"
        }
        $entry.backup_path = $copyPath
        $entry.quarantine_path = $quarantineFile
    }

    $backupManifestPath = Join-Path $backupPathUsed 'manifest.json'
    $manifest = [ordered]@{
        schema_version = '1'
        created_at_utc = (Get-Date).ToUniversalTime().ToString('o')
        repo = $repo
        source_branch = $ExpectedBranch
        expected_root_head = $expectedHead
        final_sha = $finalShaNormalized
        target_branch = $TargetBranch
        tracked_dirty_snapshot = @($trackedDirty)
        collisions = @($collisions | ForEach-Object {
            [ordered]@{
                relative_path = $_.relative_path
                owner_sha256 = $_.owner_sha256
                owner_size_bytes = $_.owner_size_bytes
                owner_git_blob = $_.owner_git_blob
                incoming_git_blob = $_.incoming_git_blob
                classification = $_.classification
                backup_path = $_.backup_path
                quarantine_path = $_.quarantine_path
            }
        })
        instruction_conflicts = @($instructionConflicts)
        policy = 'Each owner file is copied and SHA-256 verified before any owner path is moved. No recursive move/delete is used.'
    }
    Write-NewJson -Path $backupManifestPath -Value $manifest
    foreach ($entry in $collisions) {
        $currentOwner = Get-FileRecord -RelativePath $entry.relative_path
        if ($currentOwner.state -ne 'present' -or $currentOwner.sha256 -ne $entry.owner_sha256) {
            throw "Owner file changed after backup for $($entry.relative_path)"
        }
        [void]$movedCollisions.Add($entry)
        Move-Item -LiteralPath $entry.original_path -Destination $entry.quarantine_path -Force:$false
        Assert-NoReparse -Path $entry.quarantine_path
        $quarantineHash = (Get-FileHash -LiteralPath $entry.quarantine_path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($quarantineHash -ne $entry.owner_sha256) {
            throw "Quarantine hash mismatch for $($entry.relative_path)"
        }
    }

    $branchAfterBackup = (Invoke-GitText -Arguments @('branch', '--show-current')).Trim()
    $headAfterBackup = (Invoke-GitText -Arguments @('rev-parse', 'HEAD')).Trim().ToLowerInvariant()
    if ($branchAfterBackup -ne $ExpectedBranch -or $headAfterBackup -ne $expectedHead) {
        throw 'Branch or HEAD changed before guarded mutation.'
    }
    Invoke-GitText -Arguments @('switch', '--no-guess', $TargetBranch) -Mutating | Out-Null
    Invoke-GitText -Arguments @('merge', '--ff-only', $finalShaNormalized) -Mutating | Out-Null
    $mergedHead = (Invoke-GitText -Arguments @('rev-parse', 'HEAD')).Trim().ToLowerInvariant()
    if ($mergedHead -ne $finalShaNormalized) {
        throw "Fast-forward ended at $mergedHead instead of $finalShaNormalized"
    }
    Restore-QuarantinedFiles
    Assert-TrackedDirtySnapshots
    foreach ($entry in $movedCollisions) {
        $entry.restored_sha256 = (Get-FileHash -LiteralPath $entry.original_path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $preflight.status = 'PASS'
    $preflight.mode = 'apply'
    $preflight.backup_root = $backupPathUsed
    $preflight.backup_manifest = $backupManifestPath
    $preflight.final_main_head = $mergedHead
    $preflight.restored_collisions = @($movedCollisions)
    $preflight | ConvertTo-Json -Depth 20
    exit 0
} catch {
    $failureHead = $null
    $failureBranch = $null
    try {
        $failureHead = (Invoke-GitText -Arguments @('rev-parse', 'HEAD')).Trim()
        $failureBranch = (Invoke-GitText -Arguments @('branch', '--show-current')).Trim()
    } catch {
    }
    $failure = [ordered]@{
        schema_version = '1'
        status = 'FAIL'
        mode = if ($Apply) { 'apply' } else { 'dry-run' }
        error = $_.Exception.Message
        backup_root = $backupPathUsed
        backup_manifest = $backupManifestPath
        current_head = $failureHead
        current_branch = $failureBranch
    }
    if ($movedCollisions.Count -gt 0) {
        try {
            Restore-QuarantinedFiles
            $failure.rollback = 'PASS'
        } catch {
            $failure.rollback = 'FAIL'
            $failure.rollback_error = $_.Exception.Message
        }
    }
    $failure | ConvertTo-Json -Depth 20
    exit 3
}
