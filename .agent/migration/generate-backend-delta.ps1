$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sourcePath = Join-Path $repo 'docs\architecture\reference-rutcampustrack-design\backend-requests.md'
$targetPath = Join-Path $repo 'docs\implementation\backend-delta.yaml'
$sourceLines = [System.IO.File]::ReadAllText($sourcePath, [System.Text.Encoding]::UTF8) -split "`r?`n"

function New-Ids([string] $prefix, [int] $last) {
    1..$last | ForEach-Object { '{0}-{1:D2}' -f $prefix, $_ }
}

$expectedIds = @(
    (New-Ids 'AU' 17); (New-Ids 'AC' 54); (New-Ids 'SC' 27); (New-Ids 'AT' 52);
    (New-Ids 'NT' 3); (New-Ids 'MAP' 10); (New-Ids 'X' 12)
)

$rowById = @{}
for ($i = 0; $i -lt $sourceLines.Count; $i++) {
    if ($sourceLines[$i] -match '^\|\s*\*\*((?:AU|AC|SC|AT|NT|MAP|X)-\d{2})\*\*') {
        $rowById[$matches[1]] = [ordered]@{ line = $i + 1; text = $sourceLines[$i].Trim() }
    }
}

$superseded = @('AC-05','AC-06','MAP-07')
$revised = @('AT-09','AT-11','AT-12','AT-15','AT-25','AT-26','AT-31','AT-41','AT-42','AT-50','AU-10','AU-16','AC-09','AC-13','AC-16','AC-35','SC-08','SC-16','X-01','X-02')
$accepted = @('AC-07','SC-01','AC-38','SC-03','AT-18','MAP-01','X-07')
$closedDecisionEvidence = [ordered]@{
    source = 'docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:6,68-72,405-523'
    decision_count = 31
    status = 'all R-01..R-31 closed by owner source'
    request_row_note = 'A closed decision can affect zero, one, or several request rows; request-row classification is not a count of owner decisions.'
}
$closedDecisionByRequest = @{
    'AC-07'='R-26'; 'SC-01'='R-25'; 'SC-03'='R-27'; 'AT-18'='R-28'; 'X-07'='R-29'; 'MAP-01'='R-30'; 'AC-38'='R-31'
}
$storyBackendMapPath = Join-Path $repo 'docs\implementation\story-backend-map.yaml'
if (-not (Test-Path -LiteralPath $storyBackendMapPath)) { throw "Canonical story/backend map is missing: $storyBackendMapPath. Run generate-story-backend-map.ps1 first." }
$storyBackendMap = [System.IO.File]::ReadAllText($storyBackendMapPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$storyLinksByRequest = @{}
foreach ($link in @($storyBackendMap.links)) {
    if (-not $storyLinksByRequest.ContainsKey($link.request_id)) { $storyLinksByRequest[$link.request_id] = @() }
    if ($storyLinksByRequest[$link.request_id] -notcontains $link.story_id) { $storyLinksByRequest[$link.request_id] += $link.story_id }
}

function Get-Owner([string] $id) {
    switch -Regex ($id) {
        '^AU-' { return 'auth-service/auth-app' }
        '^AC-' { return 'academic-service/academic-app' }
        '^SC-' { return 'schedule-service/schedule-app' }
        '^AT-' { return 'attendance-service/attendance-app' }
        '^NT-' { return 'notification-service/notification-app' }
        '^MAP-' { return $null }
        '^X-' { return 'cross-service/unassigned' }
    }
}

function Get-Paths([string] $id) {
    switch -Regex ($id) {
        '^AU-' { return @('services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthApi.java:32-114','services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/WsTicketApi.java:20-30') }
        '^AC-' { return @('services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java:42-159','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java:32-128','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SemesterApi.java:35-103','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SubjectApi.java:29-104','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssignmentApi.java:27-66','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java:29-97','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssistantApi.java:27-67','services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/DashboardApi.java:17-26') }
        '^SC-' { return @('services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java:38-107','services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java:31-83','services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/OneOffLessonApi.java:30-60') }
        '^AT-' { return @('services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/CheckinApi.java:24-45','services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/MarkingApi.java:28-68','services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/LateCheckinApi.java:35-103','services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ExcuseApi.java:35-143','services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java:36-141') }
        '^NT-' { return @('services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/api/PushApi.java:27-43','services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/api/NotificationPreferencesApi.java:16-27','services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/api/NotificationApi.java:29-60') }
        '^MAP-' { return @() }
        '^X-' { return @('services/**/src/main/**','services/**/src/test/**') }
    }
}

$requests = foreach ($id in $expectedIds) {
    $row = $rowById[$id]
    $status = if ($superseded -contains $id) { 'superseded' } elseif ($revised -contains $id) { 'revised' } elseif ($accepted -contains $id) { 'accepted-decision' } else { 'request-or-question' }
    $productDecisionStatus = if ($status -eq 'request-or-question') { 'open' } else { 'closed' }
    $action = if ($status -eq 'superseded') { 'retire-request' } elseif ($status -eq 'accepted-decision') { 'add-semantics' } elseif ($status -eq 'revised') { $null } else { 'needs-decision' }
    $implementationAssessment = if ($status -eq 'revised') {
        [ordered]@{ status='pending-scoped-code-comparison'; reason='Closed source policy; do not infer an implementation change or request owner reapproval.' }
    } elseif ($status -eq 'superseded') {
        [ordered]@{ status='pending-consumer-audit'; reason='Retiring a request is not authorization to remove a server capability; actual consumers and delete scope are next-scope work.' }
    } elseif ($status -eq 'accepted-decision') {
        [ordered]@{ status='pending-contract-and-code-comparison'; reason='Closed source semantics still need a selected contract revision and scoped implementation assessment.' }
    } else {
        [ordered]@{ status='not-started'; reason='Open source request/question; a contract technical gate does not promote it to a product decision.' }
    }
    [object[]]$storyLinks = @()
    if ($storyLinksByRequest.ContainsKey($id)) { [object[]]$storyLinks = @($storyLinksByRequest[$id]) }
    [ordered]@{
        request_id = $id
        source = [ordered]@{
            path = 'docs/architecture/reference-rutcampustrack-design/backend-requests.md'
            line = if ($null -ne $row) { $row.line } else { $null }
            row = if ($null -ne $row) { $row.text } else { 'not-found: verify source row before implementation' }
            conflict_evidence = 'docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:42-71,566-585'
        }
        source_status = $status
        product_decision_status = $productDecisionStatus
        closed_decision_id = if ($closedDecisionByRequest.ContainsKey($id)) { $closedDecisionByRequest[$id] } else { $null }
        related_story_ids = $storyLinks
        related_story_link_kind = if ($storyLinks.Count -gt 0) { 'canonical evidence-backed story/backend map; see docs/implementation/story-backend-map.yaml' } else { 'no explicit stable-story evidence; not inferred from adjacent prose, service, or role' }
        service_owner = Get-Owner $id
        target_contract_revision = $null
        action_classification = $action
        implementation_assessment = $implementationAssessment
        static_code_paths = Get-Paths $id
        static_code_evidence = 'candidate static symbols; individual request semantics and runtime are not verified'
        static_test_paths = @()
        static_test_evidence = 'not-checked per individual request; test association must be selected by an implementation packet'
        runtime_status = 'not-run'
        invariants = @('authz and role scope','transaction boundaries','outbox/dedup for emitted events','privacy and audit for personal data')
        implementation_gate = 'Do not implement or generate a contract until the story packet assigns one canonical artifact and contract owner.'
    }
}

$document = [ordered]@{
    schema = 'rutcampustrack.backend-delta.v1'
    generated_at = '2026-09-06'
    source_scope = '175 request IDs only: AU01-17, AC01-54, SC01-27, AT01-52, NT01-03, MAP01-10, X01-12. X-13 is a cited rule, not a request row.'
    closed_decision_evidence = $closedDecisionEvidence
    status_legend = @('accepted-decision','revised','superseded','request-or-question')
    action_legend = @('existing-verified','add-semantics','refactor','optimize','retire-request','needs-decision')
    contract_freeze = [ordered]@{
        state = 'technical-gate'
        reason = 'No selected canonical artifact or owner proves spec-first or Java-first/exported OpenAPI. Existing per-service Java contracts are static evidence only; no BFF module was located.'
        candidate_options = @('spec-first with generated types','Java-first with exported OpenAPI')
        required_before_parallel = @('one contract owner','one revision','request/response/error/authz/null/timezone/idempotency semantics','derived mocks and generated types owned by the contract owner')
        source_policy_effect = 'This technical gate does not change product_decision_status: closed source decisions stay closed and open requests stay open.'
    }
    requests = $requests
    preparation_deltas = @(
        [ordered]@{ id='BE-MOBILE-GEO-COOLDOWN'; related_story_ids=@('JS-STUDENT-02'); source='docs/archive/rutcampustrack-kit/journal/to-owner.md:802-808'; status='question'; action_classification='needs-decision'; static_evidence='CheckinResponse has no requestId/requestStatus/retryAt/retryAfter in scanned public contract'; runtime_status='not-run' },
        [ordered]@{ id='BE-MOBILE-HEADMAN-MULTIDIFF'; related_story_ids=@('JS-HEADMAN-01'); source='docs/archive/rutcampustrack-kit/journal/to-owner.md:879-886'; status='superseded'; action_classification='retire-request'; superseded_reason='The owner source cancels staged/global review and atomic multi-diff as irrelevant.'; successor=[ordered]@{ id='BE-MOBILE-HEADMAN-PER-ROW-CONCURRENCY'; status='accepted-source-scope'; source='docs/archive/rutcampustrack-kit/journal/to-owner.md:879-886'; semantics=@('per-row revision or ETag','idempotency','stale conflict response'); contract_revision=$null; implementation_assessment='pending scoped contract/code comparison' }; static_evidence='No revision/ETag/If-Match/idempotency fields found in scanned public academic/schedule/attendance contract scope'; runtime_status='not-run' },
        [ordered]@{ id='BE-MOBILE-SESSION-PROJECTION'; related_story_ids=@('JS-SYSTEM-14','JS-ADMIN-01'); source='docs/architecture/reference-rutcampustrack-design/backend-requests.md:100; docs/archive/rutcampustrack-kit/journal/to-owner.md:853-865'; status='question'; action_classification='needs-decision'; static_evidence='Current UserResponse is scalar role/status plus headman flag; no BFF module located'; runtime_status='not-run' }
    )
    legacy_candidates = @([ordered]@{
        id = 'LEGACY-SCHEDULE-MASS-CANCEL'
        related_story_ids = @('JS-HEADMAN-09','JS-HEADMAN-10')
        source = 'design:knowledge/job-stories.md:495-504'
        decision = 'The source explicitly removes POST /schedule/lessons/mass-cancel and cancels JS-HEADMAN-10.'
        static_paths = @('services/schedule-service/schedule-api-contract/**/LessonApi.java:64-72','services/schedule-service/schedule-app/**/LessonService.java:159-195','services/schedule-service/schedule-app/src/test/**/LessonApiIT.java:34-36,231,245')
        action_classification = 'retire-request'
        runtime_status = 'not-run'
        implementation_assessment = [ordered]@{ status='pending-consumer-audit'; reason='The closed source policy retires the request, but it does not automatically remove an existing server capability.' }
        prerequisites = @('consumer/import/route/build audit in a separate next-scope packet','replacement coverage and regression contract tests','rollback plan','separate reviewed delete diff')
    })
    known_gaps = @(
        'AU-10 + AC-09 require a session projection with roles/statuses/effective permissions/group/active semester; current static UserResponse is scalar role/status plus headman flag, so this is not a BFF-only DTO change.',
        'Geo-failure cooldown/escalation fields are owner questions. Headman atomic multi-diff is superseded; the accepted source scope is per-row revision/ETag, idempotency and stale conflict semantics.',
        'The source table API cell and code paths are static historical evidence. No row is runtime verified by this preparation.'
    )
}

$document | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $targetPath -Encoding utf8
Write-Output "requests=$($requests.Count); source_rows=$($rowById.Count); output=$targetPath"
