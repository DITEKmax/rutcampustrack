$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$source = 'C:\Users\maksd\OneDrive\Desktop\rutcampustrack-design\knowledge\job-stories.md'
$sourceArchiveCopy = Join-Path $repo 'docs\research\reference-rutcampustrack-design\knowledge\job-stories.md'
$currentCopy = Join-Path $repo 'docs\product\job-stories.md'
$target = Join-Path $repo 'docs\product\job-stories.yaml'
$date = '2026-09-06'

if (-not (Test-Path -LiteralPath $source)) { throw "Canonical story source is missing: $source" }
if (-not (Test-Path -LiteralPath $sourceArchiveCopy)) { throw "Transferred source copy is missing: $sourceArchiveCopy" }
if (-not (Test-Path -LiteralPath $currentCopy)) { throw "Transferred story copy is missing: $currentCopy" }

$lines = [System.IO.File]::ReadAllText($source, [System.Text.Encoding]::UTF8) -split "`r?`n"
$mentions = [ordered]@{}

for ($index = 0; $index -lt $lines.Count; $index++) {
    $line = $lines[$index]
    foreach ($match in [regex]::Matches($line, 'JS-(?:SYSTEM|STUDENT|HEADMAN|TEACHER|ADMIN)(?:-WEB)?-\d+')) {
        $id = $match.Value
        if (-not $mentions.Contains($id)) { $mentions[$id] = @() }
        $mentions[$id] += ($index + 1)
    }
}

$currentLines = [System.IO.File]::ReadAllText($currentCopy, [System.Text.Encoding]::UTF8) -split "`r?`n"

function Get-DefinitionBlocks {
    param([string[]]$InputLines)
    $result = [ordered]@{}
    for ($index = 0; $index -lt $InputLines.Count; $index++) {
        $line = $InputLines[$index]
        if ($line -notmatch '\*\*(JS-(?:SYSTEM|STUDENT|HEADMAN|TEACHER|ADMIN)(?:-WEB)?-\d+)\*\*[^:]*:\s*(.+)$') { continue }
        $id = $matches[1]
        $primaryText = $matches[2].Trim()
        $block = @([ordered]@{ line=$index + 1; text=$primaryText; kind='definition' })
        for ($cursor = $index + 1; $cursor -lt $InputLines.Count; $cursor++) {
            $next = $InputLines[$cursor]
            if ($next -match '\*\*JS-(?:SYSTEM|STUDENT|HEADMAN|TEACHER|ADMIN)(?:-WEB)?-\d+\*\*[^:]*:') { break }
            if ($next -match '^#{1,6}\s' -or $next -match '^---\s*$') { break }
            if (-not [string]::IsNullOrWhiteSpace($next)) { $block += [ordered]@{ line=$cursor + 1; text=$next.Trim(); kind='continuation' } }
        }
        $occurrence = [ordered]@{ line=$index + 1; end_line=$block[$block.Count - 1].line; text=$primaryText; block=$block }
        if (-not $result.Contains($id)) {
            $result[$id] = [ordered]@{ line=$occurrence.line; text=$primaryText; block=$block; occurrences=@() }
        }
        # A later definition is the current rendering, while every earlier variant is
        # retained as evidence instead of being silently overwritten by the dictionary.
        $result[$id].occurrences += $occurrence
        $result[$id].line = $occurrence.line
        $result[$id].text = $primaryText
        $result[$id].block = $block
    }
    return $result
}

function Get-ContinuationKind {
    param([string]$Text)
    if ($Text -match '(?i)\[нет API\]|❓|открыт(?:ый|о)?\s+(?:вопрос|пункт)|требует\s+(?:решения|подтверждения)') { return 'open-question' }
    if ($Text -match '(?i)^>\s*(?:было|исторически|ранее)\b') { return 'historical-context' }
    if ($Text -match '(?i)требовани|обязательно|fallback|🆕|✏️') { return 'accepted-rule' }
    return 'accepted-continuation'
}

$definitions = Get-DefinitionBlocks $lines
$currentDefinitions = Get-DefinitionBlocks $currentLines

$storyBackendMapPath = Join-Path $repo 'docs\implementation\story-backend-map.yaml'
if (-not (Test-Path -LiteralPath $storyBackendMapPath)) { throw "Canonical story/backend map is missing: $storyBackendMapPath. Run generate-story-backend-map.ps1 first." }
$storyBackendMap = [System.IO.File]::ReadAllText($storyBackendMapPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$backendByStory = @{}
foreach ($link in @($storyBackendMap.links)) {
    if (-not $backendByStory.ContainsKey($link.story_id)) { $backendByStory[$link.story_id] = @() }
    if ($backendByStory[$link.story_id] -notcontains $link.request_id) { $backendByStory[$link.story_id] += $link.request_id }
}

$ids = @($mentions.Keys | Sort-Object)
$cancelledIds = @('JS-HEADMAN-10','JS-STUDENT-04')
$revisedIds = @(
    'JS-ADMIN-01','JS-ADMIN-02','JS-ADMIN-05','JS-ADMIN-09','JS-ADMIN-10','JS-ADMIN-12','JS-ADMIN-13',
    'JS-TEACHER-01','JS-TEACHER-05','JS-TEACHER-06','JS-TEACHER-07',
    'JS-HEADMAN-01','JS-HEADMAN-05','JS-HEADMAN-09','JS-HEADMAN-12','JS-HEADMAN-17','JS-HEADMAN-19','JS-HEADMAN-23',
    'JS-STUDENT-02','JS-STUDENT-03','JS-STUDENT-06','JS-STUDENT-07',
    'JS-SYSTEM-04','JS-SYSTEM-11',
    'JS-STUDENT-WEB-01','JS-STUDENT-WEB-04','JS-STUDENT-WEB-05','JS-STUDENT-WEB-07','JS-STUDENT-WEB-08','JS-STUDENT-WEB-09',
    'JS-HEADMAN-WEB-02','JS-HEADMAN-WEB-05','JS-HEADMAN-WEB-07','JS-HEADMAN-WEB-08'
)
$newIds = @($definitions.Keys | Where-Object { -not $currentDefinitions.Contains($_) })
$revisedIds = @($revisedIds | Where-Object { $currentDefinitions.Contains($_) })
$coverage = foreach ($id in $ids) {
    [ordered]@{
        stable_id = $id
        maps_to = $id
        mapping_status = 'preserved'
        source_lines = @($mentions[$id])
        note = 'Stable source ID retained; no cancellation or supersedence is inferred from a missing UI or current code.'
    }
}

function Remove-SourceMarkdown {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { return $null }
    return (($Text -replace '\*\*', '') -replace '~~', '').Trim()
}

function Get-SourceAction {
    param([AllowNull()][string]$Text)
    $clean = Remove-SourceMarkdown $Text
    if ([string]::IsNullOrWhiteSpace($clean)) { return $null }
    $match = [regex]::Match($clean, '(?is)я\s*(?:\([^)]*\)\s*)?хочу\s+(?<action>.+?)(?=\s*,?\s*чтобы\b|[.!?]|$)')
    if ($match.Success) { return $match.Groups['action'].Value.Trim(' ', ',', ';', ':') }
    $match = [regex]::Match($clean, '(?is)(?<action>[^.!?]*?\bне\s+проектировать\b)')
    if ($match.Success) { return $match.Groups['action'].Value.Trim(' ', ',', ';', ':') }
    return $clean
}

function Get-SourceNamedData {
    param([AllowNull()][string]$Text, [int]$Line)
    $clean = Remove-SourceMarkdown $Text
    if ([string]::IsNullOrWhiteSpace($clean)) { return [object[]]@() }

    # These are source phrases, deliberately not DTO fields. Exact request/response
    # shapes remain unknown until the contract revision is selected.
    $patterns = @(
        '(?is)\b(?:указав|заполнив)\s+(?<value>.+?)(?=\s*,?\s*чтобы\b|[.;]|$)',
        '(?is)\bс\s+фильтрацией\s+по\s+(?<value>.+?)(?=\s*,?\s*чтобы\b|[.;]|$)',
        '(?is)\bс\s+названием\s+и\s+датами\s*(?<value>\([^)]*\)|[^.,;]+)',
        '(?is)\bвводом\s+(?<value>.+?)(?=\s*,?\s*чтобы\b|[.;]|$)'
    )
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    [object[]]$result = @()
    foreach ($pattern in $patterns) {
        foreach ($match in [regex]::Matches($clean, $pattern)) {
            $value = $match.Groups['value'].Value.Trim(' ', ',', ';', ':')
            if ([string]::IsNullOrWhiteSpace($value) -or -not $seen.Add($value)) { continue }
            $result += [ordered]@{
                source_named_field_group = $value
                source = 'design:knowledge/job-stories.md'
                line = $Line
                evidence_status = 'derived from source text; exact DTO field names and types are contract-pending'
            }
        }
    }
    return $result
}

$stories = foreach ($id in $ids) {
    $definition = $definitions[$id]
    $currentDefinition = $currentDefinitions[$id]
    $role = if ($id -match '^JS-SYSTEM-') { 'system' } else { ($id -split '-')[1].ToLowerInvariant() }
    $surface = if ($id -match '-WEB-') { @('web') } elseif ($id -match '^JS-SYSTEM-') { @('cross-surface') } else { @('mobile-or-web: source statement needs first-story routing') }
    $raw = if ($null -ne $definition) { @($definition.block | ForEach-Object { $_.text }) -join "`n" } else { $null }
    $line = if ($null -ne $definition) { $definition.line } else { @($mentions[$id])[0] }
    $currentRaw = if ($null -ne $currentDefinition) { @($currentDefinition.block | ForEach-Object { $_.text }) -join "`n" } else { $null }
    $decision = if ($cancelledIds -contains $id) { 'cancelled' }
        elseif ($newIds -contains $id) { 'new' }
        elseif ($revisedIds -contains $id) { 'revised' }
        elseif (($null -eq $definition) -and ($null -eq $currentDefinition)) { 'unresolved' }
        else { 'retained' }
    [object[]]$backendIds = @()
    if ($backendByStory.ContainsKey($id)) { [object[]]$backendIds = @($backendByStory[$id] | Sort-Object -Unique) }
    [object[]]$serviceOwners = @($backendIds | ForEach-Object { if ($_ -match '^AU-') { 'auth-service/auth-app' } elseif ($_ -match '^AC-') { 'academic-service/academic-app' } elseif ($_ -match '^SC-') { 'schedule-service/schedule-app' } elseif ($_ -match '^AT-') { 'attendance-service/attendance-app' } elseif ($_ -match '^NT-') { 'notification-service/notification-app' } elseif ($_ -match '^MAP-') { 'unassigned-map-service' } else { 'cross-service/unassigned' } } | Sort-Object -Unique)
    [object[]]$continuationEvidence = @()
    [object[]]$acceptedContinuation = @()
    if ($null -ne $definition) {
        foreach ($entry in @($definition.block | Select-Object -Skip 1)) {
            $kind = Get-ContinuationKind $entry.text
            $continuationEvidence += [ordered]@{ source='design:knowledge/job-stories.md'; line=$entry.line; kind=$kind; text=$entry.text }
            if ($kind -like 'accepted-*') { $acceptedContinuation += $entry.text }
        }
    }
    $acceptedParts = @()
    if ($null -ne $definition) { $acceptedParts += $definition.text }
    $acceptedParts += $acceptedContinuation
    $acceptedText = if ($acceptedParts.Count -gt 0) { (($acceptedParts -join "`n") -replace '~~[^~]+~~', '').Trim() } else { $null }
    if ($decision -eq 'cancelled') { $acceptedText = $null }
    $decisionEvidence = @()
    if ($id -eq 'JS-HEADMAN-21') {
        $acceptedText = "$acceptedText Constraint R-25: future lessons only; one atomic server operation returns both lessons, moves linked homework, and preserves attendance."
        $decisionEvidence = @([ordered]@{ source='docs/architecture/reference-rutcampustrack-design/backend-conflicts.md'; line='329-350'; owner='project owner'; date='2026-08-12'; status='closed R-25'; supersedes='broader pre-decision transfer semantics' })
    }
    [object[]]$criteria = @()
    if ($null -ne $definition -and $decision -ne 'cancelled') {
        $criteria = @($definition.text -replace '~~[^~]+~~', '').Trim()
        $criteria += @($acceptedContinuation | ForEach-Object { ($_ -replace '~~[^~]+~~', '').Trim() })
    } else { $criteria = @('Unresolved or cancelled: recover/confirm a deliverable statement before implementation.') }
    $sourceAction = Get-SourceAction $acceptedText
    [object[]]$domainCommands = @()
    if ($null -ne $sourceAction) {
        $domainCommands = @([ordered]@{
            action = $sourceAction
            source = 'design:knowledge/job-stories.md'
            line = $line
            linked_backend_requirement_ids = $backendIds
            api_operation = $null
            contract_status = 'pending: exact API operation/form is not inferred before contract freeze'
        })
    }
    [object[]]$sourceNamedData = @(Get-SourceNamedData $acceptedText $line)
    [object[]]$supersedes = @()
    $replacedBy = $null
    if ($decision -eq 'revised') { $supersedes = @("repo-history:docs/product/job-stories.md#$id") }
    if ($decision -eq 'cancelled') { $replacedBy = "design:knowledge/job-stories.md:$line" }
    [ordered]@{
        id = $id
        legacy_ids = @($id)
        role = $role
        surface = $surface
        original_when_want_so = $currentRaw
        source_statement_raw = $raw
        source_definition_occurrences = if ($null -ne $definition) { @($definition.occurrences) } else { @() }
        current_accepted_text = $acceptedText
        repository_history_text = $currentRaw
        repository_history_definition_occurrences = if ($null -ne $currentDefinition) { @($currentDefinition.occurrences) } else { @() }
        decision_status = $decision
        delivery_status = 'not-started'
        evidence = @([ordered]@{
            source = 'design:knowledge/job-stories.md'
            target_copy = 'docs/research/reference-rutcampustrack-design/knowledge/job-stories.md'
            line = $line
            date = $null
            owner = $null
            supersedes = $supersedes
            replaced_by = $replacedBy
        }, [ordered]@{
            source = 'repo-history:docs/product/job-stories.md'
            target_copy = 'docs/product/job-stories.md'
            line = if ($null -ne $currentDefinition) { $currentDefinition.line } else { $null }
            date = $null
            owner = $null
            supersedes = @()
            replaced_by = if ($decision -eq 'revised') { "design:knowledge/job-stories.md:$line" } else { $replacedBy }
        })
        decision_evidence = $decisionEvidence
        source_continuation_evidence = $continuationEvidence
        acceptance_criteria = $criteria
        figma_nodes = @()
        required_states = @('not-enumerated: design packet is required before UI implementation')
        required_data = $sourceNamedData
        data_link_status = if ($backendIds.Count -gt 0) { 'Backend request IDs are linked below; exact DTO fields await contract freeze.' } else { 'No backend request ID was linked from the source table.' }
        commands = $domainCommands
        verification_commands = @('See docs/implementation/checks-catalog.md; choose scenario check in the first-story packet.')
        rights = @("Role boundary: $role; exact effective permissions require contract freeze.")
        backend_requirement_ids = $backendIds
        contract_revision = $null
        service_owner = $serviceOwners
        test_paths = @('unassigned: link a source-derived backend test path in the first-story packet')
        implementation_notes = @(
            'Preparation registry only. Static source evidence is not runtime verification.',
            'current_accepted_text is a derived rendering of the latest source statement with struck-through Markdown removed; source_statement_raw remains verbatim.',
            $(if ($decision -eq 'unresolved') { 'The ID is only a source cross-reference; its direct statement must be recovered or decided before implementation.' } else { 'The design source and repository history are distinct evidence; equal IDs are not assumed byte-identical.' })
        )
    }
}

$document = [ordered]@{
    schema = 'rutcampustrack.job-stories.v1'
    generated_at = $date
    generated_by = '.agent/migration/generate-preparation-registry.ps1'
    canonical_source = [ordered]@{
        source = 'design:knowledge/job-stories.md'
        source_path = $source
        copied_path = 'docs/research/reference-rutcampustrack-design/knowledge/job-stories.md'
        source_sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        copied_sha256 = (Get-FileHash -LiteralPath $sourceArchiveCopy -Algorithm SHA256).Hash.ToLowerInvariant()
        repository_history = [ordered]@{
            path = 'docs/product/job-stories.md'
            sha256 = (Get-FileHash -LiteralPath $currentCopy -Algorithm SHA256).Hash.ToLowerInvariant()
            status = 'retained-history-not-a-copy-of-design-source'
        }
    }
    status_legend = [ordered]@{
        decision_status = @('retained','revised','new','superseded','cancelled','unresolved')
        delivery_status = @('not-started','in-progress','verified')
    }
    source_coverage = $coverage
    stories = $stories
    known_limits = @(
        'The registry preserves every stable ID but does not infer a release or surface from an identifier.',
        'commands are source-derived domain actions, not shell checks; exact API operation/form remains null and contract-pending until the first story packet.',
        'required_data records only source-named field groups where a source phrase names them; it is not a DTO specification.',
        'Figma nodes, concrete tests and contract revision remain unassigned until the first story packet.',
        'No source text is modified by this generator; design source and repository history remain separate evidence.'
    )
}

$document | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $target -Encoding utf8
Write-Output "stories=$($stories.Count); source_definitions=$($definitions.Count); current_definitions=$($currentDefinitions.Count); output=$target"
