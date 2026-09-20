[CmdletBinding()]
param(
    [ValidateSet('All', 'Global', 'Project')]
    [string]$Scope = 'All',
    [switch]$Apply,
    [string]$GlobalRoot = 'C:\Users\maksd\.codex',
    [string]$ProjectRoot = '',
    [string]$StageRoot = '',
    [string]$BackupRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $Apply) {
    Write-Error 'Staged only. Re-run with -Apply after root review and explicit protected-file approval.'
    exit 1
}

function Full-Path([string]$Path) {
    return [IO.Path]::GetFullPath($Path)
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Full-Path (Join-Path $PSScriptRoot '..\..')
}
if ([string]::IsNullOrWhiteSpace($StageRoot)) {
    $StageRoot = Join-Path $PSScriptRoot 'staged'
}
$GlobalRoot = Full-Path $GlobalRoot
$ProjectRoot = Full-Path $ProjectRoot
$StageRoot = Full-Path $StageRoot
if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
    $BackupRoot = Join-Path $GlobalRoot ('backups\routing-20260906-v2-' + [guid]::NewGuid().ToString('N'))
}
$BackupRoot = Full-Path $BackupRoot

function Assert-SafePath([string]$Path, [string]$Root) {
    $full = Full-Path $Path
    $rootFull = (Full-Path $Root).TrimEnd('\')
    if (-not ($full.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase) -or
            $full.StartsWith($rootFull + '\', [StringComparison]::OrdinalIgnoreCase))) {
        throw "Path is outside allowed root: $full"
    }
    $cursor = $full
    while ($true) {
        $item = Get-Item -Force -LiteralPath $cursor -ErrorAction SilentlyContinue
        if ($null -ne $item -and (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Refusing symlink/reparse path: $cursor"
        }
        if ($cursor.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase)) { break }
        $parent = Split-Path -Parent $cursor
        if ([string]::IsNullOrWhiteSpace($parent) -or $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = $parent
    }
}

function Sha256([string]$Path) {
    $item = Get-Item -Force -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $item -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) -or $item.PSIsContainer) {
        return $null
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Assert-Sha256([string]$Path, [string]$Expected, [string]$Label) {
    if ((Sha256 $Path) -ne $Expected) {
        throw "Refusing changed or missing ${Label}: $Path"
    }
}

function Read-Utf8([byte[]]$Bytes, [string]$Label) {
    try {
        return [Text.UTF8Encoding]::new($false, $true).GetString($Bytes)
    } catch {
        throw "Invalid UTF-8 payload: $Label"
    }
}

function Set-TomlKey([string]$Text, [string]$Section, [string]$Key, [string]$Value) {
    $newline = if ($Text.Contains("`r`n")) { "`r`n" } elseif ($Text.Contains("`n")) { "`n" } else { "`r`n" }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in [regex]::Split($Text, '(?<=\n)')) { [void]$lines.Add($line) }
    $start = if ($Section -eq '') { 0 } else { -1 }
    $end = $lines.Count
    $current = ''
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $trim = $lines[$i].Trim()
        if ($trim -match '^\[([^]]+)\]') {
            if ($current -eq $Section -and $end -eq $lines.Count) { $end = $i }
            $current = $Matches[1]
            if ($current -eq $Section -and $start -lt 0) { $start = $i + 1 }
        }
    }
    if ($start -lt 0) { throw "Missing TOML section [$Section]" }
    $found = @()
    for ($i = $start; $i -lt $end; $i++) {
        if ($lines[$i] -match "^\s*$([regex]::Escape($Key))\s*=") { $found += $i }
    }
    if ($found.Count -gt 1) { throw "Duplicate TOML key $Section.$Key" }
    if ($found.Count -eq 1) {
        $old = $lines[$found[0]]
        $eol = if ($old -match '(\r?\n)$') { $Matches[1] } else { '' }
        $lines[$found[0]] = "$Key = $Value$eol"
    } else {
        $lines.Insert($end, "$Key = $Value$newline")
    }
    return -join $lines
}

function Strip-RoutingKeys([string]$Text) {
    $lines = [Collections.Generic.List[string]]::new()
    $current = ''
    foreach ($line in [regex]::Split($Text, '(?<=\n)')) {
        $trim = $line.Trim()
        if ($trim -match '^\[([^]]+)\]') { $current = $Matches[1] }
        $routing = (($current -eq '' -and $trim -match '^model\s*=') -or
            ($current -eq '' -and $trim -match '^model_reasoning_effort\s*=') -or
            ($current -eq 'agents' -and $trim -match '^(enabled|max_concurrent_threads_per_session|default_subagent_model|default_subagent_reasoning_effort)\s*='))
        if (-not $routing) { [void]$lines.Add($line) }
    }
    return -join $lines
}

$stage = @{
    config = Join-Path $StageRoot 'config-routing.toml'
    agents = @{
        explorer = Join-Path $StageRoot 'global\agents\explorer.toml'
        developer = Join-Path $StageRoot 'global\agents\developer.toml'
        reviewer = Join-Path $StageRoot 'global\agents\reviewer.toml'
    }
}
$stageHashes = @{
    config = 'B0A4194064FD48147A8EA50C895181446105D4C0152637552E1793A34C670735'
    explorer = 'BF5B77B494CFFB03E167C906B7254C1A0F2A002D27B290316096974E31614053'
    developer = 'B694FAAEB5704D09E8CA45544A94E931A4C2C6B9F9CA95C63576502CB6F0BD9A'
    reviewer = '67D64FE394017E9D9A0A9A711193AACA41A21A7FDB5C5FB99B37778A09183DCA'
    globalAgents = '0F7CAF7DC94A9A9151AE8B3ECBB0E3D71393007BF8449684555AB1C0F0C4931E'
}

$definitions = @()
if ($Scope -in @('All', 'Global')) {
    $definitions += @(
        @{ Id = 'global-agents'; Target = Join-Path $GlobalRoot 'AGENTS.md'; Stage = Join-Path $StageRoot 'global\AGENTS.md'; Root = $GlobalRoot; Expected = 'F1099BC84FDB03831C85A5BA30A0C3B80B4ACF585CF2DD4FED9684F7B7E1FD17'; Relative = 'global\AGENTS.md'; Kind = 'file' },
        @{ Id = 'global-config'; Target = Join-Path $GlobalRoot 'config.toml'; Stage = $stage.config; Root = $GlobalRoot; Expected = 'DA7A5FBA1C17E1F28458D51492F93EEF1396B6BFC69BE1FD52FF82B23EC3FB9F'; Relative = 'global\config.toml'; Kind = 'config' },
        @{ Id = 'global-explorer'; Target = Join-Path $GlobalRoot 'agents\explorer.toml'; Stage = $stage.agents.explorer; Root = $GlobalRoot; Expected = 'DEEBA6EB84B9AAC37A54E254DAB62DAE1F43C737BE2499F14CE9770EC3830173'; Relative = 'global\agents\explorer.toml'; Kind = 'file' },
        @{ Id = 'global-developer'; Target = Join-Path $GlobalRoot 'agents\developer.toml'; Stage = $stage.agents.developer; Root = $GlobalRoot; Expected = '12D59489A557B49852174ACEA12071C4CBED49943547193F077226B08393125C'; Relative = 'global\agents\developer.toml'; Kind = 'file' },
        @{ Id = 'global-reviewer'; Target = Join-Path $GlobalRoot 'agents\reviewer.toml'; Stage = $stage.agents.reviewer; Root = $GlobalRoot; Expected = '6CAD88ABC625E96626283AC34F54600F7066FBFAB8556CA415E9D194B3CAB249'; Relative = 'global\agents\reviewer.toml'; Kind = 'file' }
    )
}
if ($Scope -in @('All', 'Project')) {
    $definitions += @(
        @{ Id = 'project-config'; Target = Join-Path $ProjectRoot '.codex\config.toml'; Stage = $stage.config; Root = $ProjectRoot; Expected = '5847FE4EFE0A72551E920CC562A664D211F3EA18E0DE0169183D37EC9F81A1FA'; Relative = 'project\config.toml'; Kind = 'config' },
        @{ Id = 'project-explorer'; Target = Join-Path $ProjectRoot '.codex\agents\explorer.toml'; Stage = $stage.agents.explorer; Root = $ProjectRoot; Expected = '9727DF7BF9C114956E6EF4B1180175D2657584919A00D339379FA8E8AFDAAB40'; Relative = 'project\agents\explorer.toml'; Kind = 'file' },
        @{ Id = 'project-developer'; Target = Join-Path $ProjectRoot '.codex\agents\developer.toml'; Stage = $stage.agents.developer; Root = $ProjectRoot; Expected = 'E1EE51A54BC4A839465A97C977BA5962149B5DFF79684A7F17E8888ACEC5F170'; Relative = 'project\agents\developer.toml'; Kind = 'file' },
        @{ Id = 'project-reviewer'; Target = Join-Path $ProjectRoot '.codex\agents\reviewer.toml'; Stage = $stage.agents.reviewer; Root = $ProjectRoot; Expected = '17DF508550AEE4F5802A9AC6D5E88E388B56061F6F019CCC60DF7752A6528E5E'; Relative = 'project\agents\reviewer.toml'; Kind = 'file' }
    )
}

Assert-SafePath $StageRoot $StageRoot
foreach ($path in @($stage.config, $stage.agents.explorer, $stage.agents.developer, $stage.agents.reviewer, (Join-Path $StageRoot 'global\AGENTS.md'))) {
    Assert-SafePath $path $StageRoot
}
foreach ($entry in $stageHashes.GetEnumerator()) {
    $path = if ($entry.Key -eq 'config') { $stage.config } elseif ($entry.Key -eq 'globalAgents') { Join-Path $StageRoot 'global\AGENTS.md' } else { $stage.agents[$entry.Key] }
    Assert-Sha256 $path $entry.Value "staged $($entry.Key)"
}

$stageTexts = @{
    config = Read-Utf8 ([IO.File]::ReadAllBytes($stage.config)) 'config-routing.toml'
    globalAgents = Read-Utf8 ([IO.File]::ReadAllBytes((Join-Path $StageRoot 'global\AGENTS.md'))) 'global/AGENTS.md'
}
if ($stageTexts.config -notmatch '(?m)^\s*model\s*=\s*"gpt-6-astra"\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*model_reasoning_effort\s*=\s*"medium"\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*\[agents\]\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*enabled\s*=\s*true\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*max_concurrent_threads_per_session\s*=\s*3\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*default_subagent_model\s*=\s*"gpt-5.6-luna"\s*$' -or
    $stageTexts.config -notmatch '(?m)^\s*default_subagent_reasoning_effort\s*=\s*"max"\s*$') {
    throw 'Staged config does not contain the six reviewed routing values'
}
if ([string]::IsNullOrWhiteSpace($stageTexts.globalAgents)) { throw 'Staged global AGENTS.md is empty' }
foreach ($role in @('explorer', 'developer', 'reviewer')) {
    $rolePath = $stage.agents[$role]
    $roleText = Read-Utf8 ([IO.File]::ReadAllBytes($rolePath)) $rolePath
    $rolePattern = '(?m)^\s*name\s*=\s*"' + $role + '"\s*$'
    if ($roleText -notmatch $rolePattern -or
        $roleText -notmatch '(?m)^\s*description\s*=\s*"' -or
        $roleText -notmatch '(?m)^\s*developer_instructions\s*=\s*"""') {
        throw "Invalid staged role TOML: $role"
    }
}

$prepared = @()
foreach ($definition in $definitions) {
    Assert-SafePath $definition.Target $definition.Root
    Assert-Sha256 $definition.Target $definition.Expected $definition.Id
    $original = [IO.File]::ReadAllBytes($definition.Target)
    $payload = if ($definition.Kind -eq 'file') {
        [IO.File]::ReadAllBytes($definition.Stage)
    } else {
        $beforeText = Read-Utf8 $original $definition.Target
        $afterText = $beforeText
        $afterText = Set-TomlKey $afterText '' 'model' '"gpt-6-astra"'
        $afterText = Set-TomlKey $afterText '' 'model_reasoning_effort' '"medium"'
        $afterText = Set-TomlKey $afterText 'agents' 'enabled' 'true'
        $afterText = Set-TomlKey $afterText 'agents' 'max_concurrent_threads_per_session' '3'
        $afterText = Set-TomlKey $afterText 'agents' 'default_subagent_model' '"gpt-5.6-luna"'
        $afterText = Set-TomlKey $afterText 'agents' 'default_subagent_reasoning_effort' '"max"'
        if ((Strip-RoutingKeys $beforeText) -cne (Strip-RoutingKeys $afterText)) {
            throw "Config semantic scope changed outside six routing keys: $($definition.Id)"
        }
        [Text.UTF8Encoding]::new($false).GetBytes($afterText)
    }
    $prepared += [pscustomobject]@{ Definition = $definition; Original = $original; Payload = $payload }
}

Assert-SafePath $BackupRoot $GlobalRoot
if (Test-Path -LiteralPath $BackupRoot) { throw "Refusing pre-existing backup path: $BackupRoot" }
New-Item -ItemType Directory -Path $BackupRoot | Out-Null
foreach ($item in $prepared) {
    $destination = Join-Path $BackupRoot $item.Definition.Relative
    Assert-SafePath $destination $BackupRoot
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    [IO.File]::WriteAllBytes($destination, $item.Original)
}

function Atomic-Replace([string]$Path, [byte[]]$Bytes, [string]$Root) {
    Assert-SafePath $Path $Root
    $temporary = Join-Path (Split-Path -Parent $Path) ('.routing-v2-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllBytes($temporary, $Bytes)
        # Same-directory Move with overwrite is the Windows atomic replace used
        # after the target/precondition and backup checks above.
        [IO.File]::Move($temporary, $Path, $true)
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -Force -LiteralPath $temporary }
    }
}

function Bytes-Equal([byte[]]$Left, [byte[]]$Right) {
    if ($Left.Length -ne $Right.Length) { return $false }
    for ($i = 0; $i -lt $Left.Length; $i++) {
        if ($Left[$i] -ne $Right[$i]) { return $false }
    }
    return $true
}

$changed = @()
try {
    foreach ($item in $prepared) {
        Assert-Sha256 $item.Definition.Target $item.Definition.Expected $item.Definition.Id
        Atomic-Replace $item.Definition.Target $item.Payload $item.Definition.Root
        $changed += $item
    }
    foreach ($item in $prepared) {
        $actual = [IO.File]::ReadAllBytes($item.Definition.Target)
        if (-not (Bytes-Equal $actual $item.Payload)) {
            throw "Readback mismatch: $($item.Definition.Id)"
        }
        if ($item.Definition.Kind -eq 'config') {
            $rendered = Read-Utf8 $actual $item.Definition.Target
            if ($rendered -notmatch '(?m)^\s*default_subagent_reasoning_effort\s*=\s*"max"\s*$') {
                throw "Readback routing value mismatch: $($item.Definition.Id)"
            }
        }
    }
} catch {
    for ($i = $changed.Count - 1; $i -ge 0; $i--) {
        $item = $changed[$i]
        Atomic-Replace $item.Definition.Target $item.Original $item.Definition.Root
    }
    throw
}

Write-Output ("Protected routing apply PASS for {0} targets; backup={1}" -f $prepared.Count, $BackupRoot)
