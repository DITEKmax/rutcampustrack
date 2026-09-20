$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$requestsPath = Join-Path $repo 'docs\architecture\reference-rutcampustrack-design\backend-requests.md'
$storiesPath = Join-Path $repo 'docs\research\reference-rutcampustrack-design\knowledge\job-stories.md'
$targetPath = Join-Path $repo 'docs\implementation\story-backend-map.yaml'
$requestLines = [System.IO.File]::ReadAllText($requestsPath, [System.Text.Encoding]::UTF8) -split "`r?`n"
$storyLines = [System.IO.File]::ReadAllText($storiesPath, [System.Text.Encoding]::UTF8) -split "`r?`n"

[object[]]$links = @()
$seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
function Get-StoryDefinitionLine([string]$StoryId) {
    $escapedId = [regex]::Escape($StoryId)
    $pattern = '^\s*(?:[^\p{L}\p{N}\*]+\s*)?\*\*' + $escapedId + '\*\*(?:\s*\([^)]*\))?\s*:'
    for ($index = 0; $index -lt $storyLines.Count; $index++) {
        if ($storyLines[$index] -match $pattern) { return $index + 1 }
    }
    return $null
}

function Assert-EdgeEvidence($Link) {
    $requestLine = 0
    $storyLine = 0
    if (-not [int]::TryParse([string]$Link.request_evidence.line, [ref]$requestLine) -or $requestLine -lt 1 -or $requestLine -gt $requestLines.Count) { throw "invalid request evidence line for $($Link.request_id)|$($Link.story_id)" }
    if (-not [int]::TryParse([string]$Link.story_evidence.line, [ref]$storyLine) -or $storyLine -lt 1 -or $storyLine -gt $storyLines.Count) { throw "invalid story evidence line for $($Link.request_id)|$($Link.story_id)" }
    $requestPattern = '^\|\s*\*\*' + [regex]::Escape($Link.request_id) + '\*\*'
    if ($requestLines[$requestLine - 1] -notmatch $requestPattern) { throw "request evidence does not match exact ID for $($Link.request_id)|$($Link.story_id)" }
    $storyPattern = '^\s*(?:[^\p{L}\p{N}\*]+\s*)?\*\*' + [regex]::Escape($Link.story_id) + '\*\*(?:\s*\([^)]*\))?\s*:'
    if ($storyLines[$storyLine - 1] -notmatch $storyPattern) { throw "story evidence does not match exact definition ID for $($Link.request_id)|$($Link.story_id)" }
}

function Add-Link {
    param([string]$RequestId, [string]$StoryId, [string]$RequestLine, [string]$StoryLine, [string]$EvidenceKind, [string]$AssociationBasis)
    $key = "$RequestId|$StoryId"
    if (-not $seen.Add($key)) { return }
    $script:links += [ordered]@{
        request_id = $RequestId
        story_id = $StoryId
        request_evidence = [ordered]@{ source='docs/architecture/reference-rutcampustrack-design/backend-requests.md'; line=$RequestLine }
        story_evidence = [ordered]@{ source='docs/research/reference-rutcampustrack-design/knowledge/job-stories.md'; line=$StoryLine }
        evidence_kind = $EvidenceKind
        association_basis = $AssociationBasis
    }
}

$requestLineById = @{}
for ($index = 0; $index -lt $requestLines.Count; $index++) {
    $line = $requestLines[$index]
    if ($line -notmatch '^\|\s*\*\*((?:AU|AC|SC|AT|NT|MAP|X)-\d{2})\*\*') { continue }
    $requestId = $matches[1]
    $requestLineById[$requestId] = $index + 1
    foreach ($story in [regex]::Matches($line, 'JS-(?:SYSTEM|STUDENT|HEADMAN|TEACHER|ADMIN)(?:-WEB)?-\d+')) {
        $storyLine = Get-StoryDefinitionLine $story.Value
        Add-Link $requestId $story.Value ($index + 1) $storyLine 'direct-request-row' 'literal stable-story citation in the request row'
    }
}

$reconciledCandidates = @{
    'SC-01'=@('JS-HEADMAN-21','JS-HEADMAN-16'); 'AC-07'=@('JS-HEADMAN-12','JS-TEACHER-09','JS-TEACHER-10');
    'AT-11'=@('JS-HEADMAN-24','JS-STUDENT-02'); 'AT-12'=@('JS-STUDENT-02','JS-STUDENT-06','JS-HEADMAN-05'); 'AT-26'=@('JS-STUDENT-02','JS-STUDENT-06','JS-HEADMAN-05');
    'AT-09'=@('JS-HEADMAN-01','JS-HEADMAN-WEB-05'); 'AT-25'=@('JS-STUDENT-02','JS-STUDENT-12'); 'SC-08'=@('JS-STUDENT-02','JS-STUDENT-12');
    'AT-31'=@('JS-HEADMAN-24','JS-STUDENT-23'); 'AT-41'=@('JS-STUDENT-02','JS-STUDENT-WEB-07'); 'AT-50'=@('JS-STUDENT-23','JS-HEADMAN-43','JS-HEADMAN-44');
    'AT-15'=@('JS-STUDENT-27','JS-STUDENT-WEB-07'); 'AT-42'=@('JS-SYSTEM-16','JS-HEADMAN-19'); 'AU-16'=@('JS-HEADMAN-23','JS-ADMIN-20');
    'AC-13'=@('JS-HEADMAN-23'); 'AC-05'=@('JS-HEADMAN-12','JS-TEACHER-14'); 'AC-09'=@('JS-ADMIN-19','JS-ADMIN-26','JS-SYSTEM-14');
    'AU-10'=@('JS-ADMIN-19','JS-ADMIN-26','JS-SYSTEM-14'); 'AC-16'=@('JS-ADMIN-15'); 'AC-35'=@('JS-ADMIN-06','JS-ADMIN-07','JS-ADMIN-15');
    'X-01'=@('JS-ADMIN-13','JS-TEACHER-07','JS-HEADMAN-19','JS-STUDENT-16'); 'X-02'=@('JS-HEADMAN-30','JS-TEACHER-13');
    'SC-16'=@('JS-STUDENT-12','JS-HEADMAN-02'); 'MAP-07'=@('JS-STUDENT-24','JS-ADMIN-23','JS-ADMIN-24','JS-ADMIN-25')
}
foreach ($requestId in $reconciledCandidates.Keys) {
    foreach ($storyId in $reconciledCandidates[$requestId]) {
        $storyLine = Get-StoryDefinitionLine $storyId
        $kind = if ($requestId -eq 'SC-01') { 'closed-decision-R-25' } else { 'reconciliation-semantic-candidate' }
        $basis = if ($requestId -eq 'SC-01') { 'closed R-25 decision; request and story scopes are explicitly connected' } else { 'reconciliation candidate; request row and story definition are cited, but a contract packet must preserve any remaining semantic gap' }
        Add-Link $requestId $storyId $requestLineById[$requestId] $storyLine $kind $basis
    }
}

foreach ($link in $links) { Assert-EdgeEvidence $link }

$closedRequestIds = @('AC-05','AC-06','MAP-07','AT-09','AT-11','AT-12','AT-15','AT-25','AT-26','AT-31','AT-41','AT-42','AT-50','AU-10','AU-16','AC-09','AC-13','AC-16','AC-35','SC-08','SC-16','X-01','X-02','AC-07','SC-01','AC-38','SC-03','AT-18','MAP-01','X-07')
$closedTrace = foreach ($requestId in $closedRequestIds) {
    $linkedStories = @($links | Where-Object request_id -eq $requestId | ForEach-Object story_id)
    [ordered]@{
        request_id = $requestId
        story_ids = $linkedStories
        trace_status = if ($linkedStories.Count -gt 0) { 'linked' } else { 'no-explicit-story-link'
        }
        reason = if ($linkedStories.Count -gt 0) { 'See links for direct or semantic-candidate evidence.' } else { 'Closed source decision is retained in backend-delta; no stable story association is asserted without evidence.' }
    }
}

$document = [ordered]@{
    schema = 'rutcampustrack.story-backend-map.v1'
    generated_at = '2026-09-06'
    generated_by = '.agent/migration/generate-story-backend-map.ps1'
    source_policy = 'Links distinguish literal request-row citations from reconciliation semantic candidates. Every edge cites both its request row and story definition. Adjacent prose never carries a prior request ID; service or role alone never creates a link.'
    links = @($links | Sort-Object request_id,story_id)
    closed_request_trace = @($closedTrace | Sort-Object request_id)
}

$document | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $targetPath -Encoding utf8
Write-Output "links=$($links.Count); output=$targetPath"
