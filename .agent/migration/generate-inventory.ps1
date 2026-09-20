param(
    [string]$OutputPath = (Join-Path $PSScriptRoot 'inventory.json')
)

$ErrorActionPreference = 'Stop'
$scanStartedAt = Get-Date -Format 'o'

# This script inventories only metadata and safe, candidate source materials.  It never copies files.
$sourceDefinitions = @(
    [pscustomobject]@{ id = 'kit'; root = 'C:\Users\maksd\ruttrack\rutcampustrack-kit'; label = 'rutcampustrack-kit' },
    [pscustomobject]@{ id = 'design'; root = 'C:\Users\maksd\OneDrive\Desktop\rutcampustrack-design'; label = 'rutcampustrack-design' },
    [pscustomobject]@{ id = 'research'; root = 'C:\Users\maksd\Documents\Codex\2026-08-30\vue-vue-pcss-eslint-c-users\outputs\repository-research-contour\research\reports'; label = 'repository-research-contour/research/reports' },
    [pscustomobject]@{ id = 'repo-history'; root = 'C:\Users\maksd\IntelliJIDEA\rutcampustrack'; label = 'current-repository-history'; include_root_names = @('docs', '.planning', 'CLAUDE.md') }
)

$excludedDirectoryNames = @('.git', 'node_modules', 'build', 'dist', 'cache', '.cache', '.pytest_cache', '.idea', '.vscode', 'tmp')
$secretFileNames = @('.env', '.env.local', '.env.production', '.npmrc', '.pypirc', 'credentials.json', 'service-account.json', 'secrets.yaml', 'secrets.yml', 'id_rsa', 'id_ed25519')
$secretExtensions = @('.pem', '.key', '.p12', '.pfx', '.kdbx')
$instructionNames = @('AGENTS.md', 'CLAUDE.md', 'SETUP.md', 'SKILL.md')
$configFileNames = @('.mcp.json', '.npmrc', '.pypirc')
$codeExtensions = @('.ps1', '.py', '.js', '.ts', '.tsx', '.jsx', '.java', '.kt', '.kts', '.sh', '.cmd', '.bat', '.cs', '.go', '.rb', '.php')
$configExtensions = @('.toml', '.yaml', '.yml', '.ini', '.properties', '.env')

function Test-SecretPath([System.IO.FileInfo]$File) {
    $name = $File.Name.ToLowerInvariant()
    return $secretFileNames -contains $name -or $secretExtensions -contains $File.Extension.ToLowerInvariant() -or $name -match '(^|[._-])(secret|token|credential|password)([._-]|$)'
}

function Get-Target([string]$SourceId, [string]$RelativePath) {
    $normal = $RelativePath -replace '\\', '/'
    $target = $null
    switch ($SourceId) {
        'kit' {
            if ($normal -match '^[^/]+\.(png|jpg|jpeg|webp|gif|svg)$') { $target = 'docs/design/evidence/' + $normal }
            elseif ($normal.StartsWith('design/')) { $target = 'docs/design/' + $normal.Substring(7) }
            elseif ($normal.StartsWith('specs/')) { $target = 'docs/product/specs/' + $normal.Substring(6) }
            else { $target = 'docs/archive/rutcampustrack-kit/' + $normal }
        }
        'design' {
            if ($normal.StartsWith('01-wireframes/')) { $target = 'docs/wireframes/' + $normal.Substring(14) }
            elseif ($normal.StartsWith('04-figma/')) { $target = 'docs/design/figma-source/' + $normal.Substring(9) }
            elseif ($normal.StartsWith('02-backend/')) { $target = 'docs/architecture/reference-rutcampustrack-design/' + $normal.Substring(11) }
            elseif ($normal.StartsWith('03-brand/')) { $target = 'docs/design/brand/' + $normal.Substring(9) }
            elseif ($normal.StartsWith('05-adaptive/')) { $target = 'docs/design/adaptive/' + $normal.Substring(12) }
            elseif ($normal -eq 'knowledge/TRANSPORT.md') { $target = 'docs/architecture/reference-rutcampustrack-design/TRANSPORT.md' }
            elseif ($normal -match '^knowledge/(PROJECT_CONTEXT|A11Y_REQUIREMENTS|COMPONENT_REGISTRY|BRAND_DIRECTION)\.md$') { $target = 'docs/product/reference-rutcampustrack-design/' + $normal.Substring(10) }
            else { $target = 'docs/research/reference-rutcampustrack-design/' + $normal }
        }
        'research' { $target = 'docs/research/reference-repo/repository-research-contour/' + $normal }
        'repo-history' { $target = $normal }
    }
    if ($SourceId -ne 'repo-history' -and ($normal -match '(^|/)(AGENTS|CLAUDE|SETUP|SKILL)\.md$' -or $normal -match '(^|/)(prompt|prompts)(/|$)')) { return $target + '.inactive' }
    return $target
}

function Get-Classification([string]$SourceId, [string]$RelativePath, [System.IO.FileInfo]$File) {
    $normal = $RelativePath -replace '\\', '/'
    if (Test-SecretPath $File) { return [pscustomobject]@{ status='exclude'; category='secret-by-path'; reason='Potential secret excluded by filename or extension; content was not read.' } }
    if ($configFileNames -contains $File.Name -or $normal -match '(^|/)(\.codex|\.claude|\.agents)/.*\.(toml|json)$') { return [pscustomobject]@{ status='exclude'; category='config-by-path'; reason='Configuration is excluded by path before hashing or content access.' } }
    if ($codeExtensions -contains $File.Extension.ToLowerInvariant()) { return [pscustomobject]@{ status='exclude'; category='code-by-path'; reason='Product or utility code is outside this materials-only transfer batch.' } }
    if ($configExtensions -contains $File.Extension.ToLowerInvariant()) { return [pscustomobject]@{ status='exclude'; category='config-by-path'; reason='Configuration is outside this materials-only transfer batch.' } }
    if ($SourceId -eq 'repo-history') {
        if ($normal -eq 'docs/agent-workflow.md' -or $normal -match '^docs/sources/') { return [pscustomobject]@{ status='exclude'; category='current-root-owned-workflow'; reason='Current root-owned workflow or manifest location remains in place and is not migrated.'; retained_address=$normal } }
        return [pscustomobject]@{ status='duplicate'; category='current-repository-history'; reason='Retain in place as technical history at the existing address; do not treat it as an incoming canonical copy.'; retained_address="repo-history:$normal" }
    }
    if ($instructionNames -contains $File.Name) { return [pscustomobject]@{ status='copy-archive'; category='inactive-instruction'; reason='Historical instruction; archive as .inactive and do not replace current agent instructions.' } }
    if ($normal -match '(^|/)(prompt|prompts)(/|$)') { return [pscustomobject]@{ status='copy-archive'; category='inactive-prompt'; reason='Historical prompt is archived as .inactive and must not be executed as an instruction.' } }
    if ($SourceId -eq 'design' -and $normal -eq '04-figma/figma-spec-shadcn-overlay.md') { return [pscustomobject]@{ status='copy-archive'; category='superseded-design-reference'; reason='Preserve as superseded evidence: owner decision prohibits Tailwind and shadcn for implementation.' } }
    if ($SourceId -eq 'design' -and $normal.StartsWith('04-figma/')) { return [pscustomobject]@{ status='copy-canonical'; category='design-figma-reference'; reason='Local accepted Figma-derived report or asset; no live Figma file is read or used.' } }
    if ($SourceId -eq 'kit' -and $normal.StartsWith('design/')) { return [pscustomobject]@{ status='copy-canonical'; category='design-material'; reason='Candidate canonical design material; semantic priority is recorded in transfer-plan.' } }
    if ($SourceId -eq 'kit' -and $normal.StartsWith('specs/')) { return [pscustomobject]@{ status='copy-canonical'; category='product-spec'; reason='Specification is transferred verbatim; owner registry and decisions determine canonical status.' } }
    if ($SourceId -eq 'design' -and ($normal.StartsWith('01-wireframes/') -or $normal.StartsWith('03-brand/') -or $normal.StartsWith('05-adaptive/'))) { return [pscustomobject]@{ status='copy-canonical'; category='design-reference'; reason='Visual or brand reference candidate; it does not change product decisions before owner registry.' } }
    if ($SourceId -eq 'design' -and $normal -match '^knowledge/(PROJECT_CONTEXT|TRANSPORT|A11Y_REQUIREMENTS|COMPONENT_REGISTRY|BRAND_DIRECTION)\.md$') { return [pscustomobject]@{ status='copy-canonical'; category='product-or-architecture-reference'; reason='Reference candidate; transfer keeps provenance and does not silently rewrite owner decisions.' } }
    if ($SourceId -eq 'design' -and $normal.StartsWith('02-backend/')) { return [pscustomobject]@{ status='copy-archive'; category='architecture-reference'; reason='External backend reference retained with provenance; it does not change current contracts.' } }
    if ($SourceId -eq 'research') { return [pscustomobject]@{ status='copy-archive'; category='research-report'; reason='Research is reference material, not a canonical task or product decision.' } }
    if ($SourceId -eq 'kit' -and ($normal.StartsWith('journal/') -or $normal.StartsWith('tasks/'))) { return [pscustomobject]@{ status='copy-archive'; category='journal-or-task'; reason='Journal or task is retained as historical material with provenance.' } }
    return [pscustomobject]@{ status='copy-archive'; category='reference-material'; reason='Retain reference material in archive with its source-relative path.' }
}

$items = [System.Collections.Generic.List[object]]::new()
$sourceSummaries = [System.Collections.Generic.List[object]]::new()

foreach ($source in $sourceDefinitions) {
    if (-not (Test-Path -LiteralPath $source.root -PathType Container)) { throw "Source root is unavailable: $($source.root)" }
    $summary = [ordered]@{ source_id=$source.id; root=$source.root; files=0; directories=0; bytes=0; excluded_directories=0; excluded_unscanned=$true }
    $stack = [System.Collections.Generic.Stack[string]]::new()
    $stack.Push($source.root)
    while ($stack.Count -gt 0) {
        $directory = $stack.Pop()
        foreach ($entry in (Get-ChildItem -LiteralPath $directory -Force -ErrorAction Stop)) {
            $relative = $entry.FullName.Substring($source.root.Length).TrimStart('\') -replace '\\', '/'
            if ($directory -eq $source.root -and $source.PSObject.Properties.Name -contains 'include_root_names' -and $source.include_root_names -notcontains $entry.Name) { continue }
            if ($entry.PSIsContainer) {
                if ($excludedDirectoryNames -contains $entry.Name) {
                    $items.Add([ordered]@{ source_id=$source.id; relative_path=$relative; kind='directory'; category='excluded-tree'; size_bytes=$null; sha256=$null; destination=$null; status='exclude'; retained_address=$null; reason="Excluded tree '$($entry.Name)' was not traversed (git/dependency/build/cache/IDE rule)." })
                    $summary.excluded_directories++
                } else {
                    $summary.directories++
                    $stack.Push($entry.FullName)
                }
                continue
            }
            $summary.files++
            $summary.bytes += $entry.Length
            $classification = Get-Classification $source.id $relative $entry
            $hash = $null
            $destination = $null
            if (($classification.status -in @('copy-canonical','copy-archive')) -or ($source.id -eq 'repo-history' -and $classification.status -eq 'duplicate')) { $hash = (Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
            if ($classification.status -in @('copy-canonical','copy-archive') -or $source.id -eq 'repo-history') { $destination = Get-Target $source.id $relative }
            $items.Add([ordered]@{ source_id=$source.id; relative_path=$relative; kind='file'; category=$classification.category; size_bytes=[int64]$entry.Length; sha256=$hash; destination=$destination; status=$classification.status; retained_address=$classification.retained_address; reason=$classification.reason })
        }
    }
    $sourceSummaries.Add([pscustomobject]$summary)
}

$copyable = @($items | Where-Object { $_.kind -eq 'file' -and $_.status -in @('copy-canonical','copy-archive') })
$byHash = @{}
foreach ($candidate in $copyable) {
    if (-not $byHash.ContainsKey($candidate.sha256)) { $byHash[$candidate.sha256] = [System.Collections.Generic.List[object]]::new() }
    $byHash[$candidate.sha256].Add($candidate)
}
foreach ($hashKey in @($byHash.Keys)) {
    $members = @($byHash[$hashKey] | Sort-Object source_id, relative_path)
    if ($members.Count -lt 2) { continue }
    $canonical = $members[0]
    for ($memberIndex = 1; $memberIndex -lt $members.Count; $memberIndex++) {
        $duplicate = $members[$memberIndex]
        $duplicate.status = 'duplicate'
        $duplicate.retained_address = "$($canonical.source_id):$($canonical.relative_path)"
        $duplicate.reason = "Побайтовый duplicate; retained address у исходного кандидата $($duplicate.retained_address)."
    }
}

$targetBuckets = @{}
foreach ($item in $items) {
    if ($item.kind -ne 'file' -or $item.status -notin @('copy-canonical','copy-archive')) { continue }
    if (-not $targetBuckets.ContainsKey($item.destination)) { $targetBuckets[$item.destination] = [System.Collections.Generic.List[object]]::new() }
    $targetBuckets[$item.destination].Add($item)
}
$targetCollisions = [System.Collections.Generic.List[object]]::new()
foreach ($target in @($targetBuckets.Keys | Sort-Object)) {
    $targetMembers = $targetBuckets[$target]
    if ($targetMembers.Count -lt 2) { continue }
    $targetSources = [System.Collections.Generic.List[string]]::new()
    foreach ($targetMember in $targetMembers) { $targetSources.Add("$($targetMember.source_id):$($targetMember.relative_path)") }
    $targetCollisions.Add([ordered]@{ destination=$target; sources=@($targetSources); resolution='blocked: retain every version under provenance path; owner must select canonical target before copy' })
}
$statusCounts = [ordered]@{}
foreach ($status in @('copy-canonical','copy-archive','duplicate','exclude','blocked')) { $statusCounts[$status] = @($items | Where-Object status -eq $status).Count }

$inventory = [ordered]@{
    schema_version='1.0'
    scan_started_at=$scanStartedAt
    scan_finished_at=(Get-Date -Format 'o')
    generated_at=(Get-Date -Format 'o')
    purpose='Read-only transfer map. No product, code, configuration, Figma, or source file has been copied.'
    source_summaries=$sourceSummaries
    totals=[ordered]@{ files=($sourceSummaries | Measure-Object files -Sum).Sum; directories=($sourceSummaries | Measure-Object directories -Sum).Sum; bytes=($sourceSummaries | Measure-Object bytes -Sum).Sum; excluded_directories=($sourceSummaries | Measure-Object excluded_directories -Sum).Sum; status_counts=$statusCounts }
    target_collisions=$targetCollisions
    items=@($items | Sort-Object source_id, relative_path)
}
$inventory | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding utf8
