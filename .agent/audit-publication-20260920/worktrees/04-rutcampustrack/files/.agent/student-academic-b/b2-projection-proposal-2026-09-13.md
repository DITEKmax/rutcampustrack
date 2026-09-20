# B2 attendance/statistics projection proposal

Status: **PROPOSAL — ROOT ACCEPTANCE REQUIRED**  
Date: 2026-09-13  
Risk: S3 (student authorization, historical membership, cross-service lineage)  
Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`  
Ownership for this task: root is the sole writer of this document. Product source,
proto, OpenAPI, generated TypeScript and existing evidence are read-only.

## 1. Goal

Freeze a reviewable technical proposal for five read-only student projections:

1. attendance overview with day rows;
2. attendance by subjects;
3. attendance graph for a selected server bucket range;
4. statistics overview with own rank and semester series;
5. statistics subject detail for a selected range and lesson-type set.

The proposal defines the external REST contract, Java ownership, internal gRPC
boundary, authorization and terminal read-only behavior, lineage dependencies,
typed failures and acceptance tests. It does not authorize implementation. The
accepted numeric calculators and the accepted 16 FE13 feature files are inputs;
their bytes and behavior are not rewritten here.

## 2. Context/evidence

The following originals were read at the baseline or in the accepted nested UI
source:

- `.agent/worktrees/student-academic-ui/frontends/mobile-core/src/features/attendance/attendance-view-model.ts`:
  `AttendanceViewModel`, server-owned `requestOptions`, dense `MetricSet`, day,
  subject/history and day/week graph shapes.
- `.agent/worktrees/student-academic-ui/frontends/mobile-core/src/features/statistics/statistics-view-model.ts`:
  `StatisticsOverviewData`, `StatisticsSubjectDetailData`, own rank, server-bucketed
  series and server-computed selected-type aggregate.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java`:
  accepted occurrence/mark validation, dense planned and held counts, four metric
  values and exact two-decimal percentages.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java`:
  accepted competition rank over an authoritative roster without implicit self
  insertion or peer leakage.
- `proto/attendance.proto`: current signed-context student boundary has snapshot by
  physical lesson IDs and check-in only; it has no semester projection reads.
- `proto/schedule.proto`: `GetLessonsByGroup` and `GetOccurrenceHistory` expose
  lifecycle fields, but no auth-bound read that guarantees one current physical
  instance per logical occurrence.
- `proto/academic.proto`: group members and subject reads exist. Current
  `GroupMembersResponse` lacks semester membership/grant status and therefore is
  not an authoritative rank roster or terminal historical-scope response.
- `services/mobile-bff/mobile-bff-api-contract/.../StudentApi.java` and
  `StudentApiModels.java`: Java-first REST/OpenAPI pattern, decimal-string public
  IDs, typed Problem Details and `Cache-Control` conventions.
- `.agent/student-academic-b/foundation-decision-result.md`, especially the
  ownership and lifecycle decisions: Schedule owns binding/location; Academic
  owns Homework content/completion; Schedule transfer updates bindings in the
  same transaction; roster and occurrence lineage remain gates.
- `.agent/student-academic-b/union/contract.md` and
  `.agent/student-academic-b/union/implementation-addendum.md`: accepted B0 SQL
  and wire ledger, with B2 five projection routes explicitly left open.
- `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql`:
  Schedule-owned `lesson_homework_bindings` and immutable lifecycle constraints.

Accepted evidence is intentionally not replayed: numeric foundation focused tests
were 21 PASS with a fresh independent recheck; B1a session/today/schedule/homework/
completion/check-in is separately accepted. Neither acceptance establishes these
five live projections.

### Recorded source inconsistency

The canonical decision says Schedule owns `LessonHomeworkBinding` and changes its
location in the same transaction as transfer/cancel. V17 stores
`lesson_homework_bindings` in Schedule. However, accepted `academic.proto` currently
declares `ReserveHomeworkBinding`, `ConfirmHomeworkBinding` and
`GetHomeworkBindings` on `AcademicGrpcService`, and the B0 addendum lists their
messages under Academic additions. No application implementation or released
consumer makes this inversion safe or authoritative. Section 4 records the minimum
contract correction required before a lifecycle writer starts.

The shared checkout is already dirty outside this document, including proto,
OpenAPI and generated files. Those bytes are neither the baseline nor owned by
this proposal task.

## 3. Relevant scope

### Proposed public REST diff

Add the following methods to the existing `@RequestMapping("/api/v1/student")`
Java-first `StudentApi`. All IDs are positive decimal strings on public JSON and
query/path boundaries. Query enum values are uppercase.

| # | Proposed method | Inputs | Output |
|---|---|---|---|
| 1 | `GET /attendance` | required `semesterId` | `StudentAttendanceOverviewResponse` |
| 2 | `GET /attendance/subjects` | required `semesterId` | `StudentAttendanceSubjectsResponse` |
| 3 | `GET /attendance/graph` | required `semesterId`, required `range=DAYS\|WEEKS` | `StudentAttendanceGraphResponse` |
| 4 | `GET /statistics` | required `semesterId`, required `range=DAYS\|WEEKS` | `StudentStatisticsOverviewResponse` |
| 5 | `GET /statistics/subjects/{subjectId}` | required `semesterId`, required `range=DAYS\|WEEKS`, repeated optional `type=LECTURE\|PRACTICE\|LAB` | `StudentStatisticsSubjectDetailResponse` |

For route 5, omitted `type` means all types actually available for that subject.
An explicitly supplied list must be non-empty, unique, supported by the subject
and returned in canonical `LECTURE`, `PRACTICE`, `LAB` order. Date windows and
bucket boundaries are server-owned; clients do not send `from`, `to`, user ID,
group ID, held counts, percentages, rank or eligibility.

Every success is `200 application/json` with `Cache-Control: private, no-store`.
An authorized semester with no occurrences returns a valid empty projection, not
404. No ETag is proposed until a cross-service representation revision exists.

### Proposed public Java model diff

Add nested Java records/enums to `StudentApiModels` rather than hand-writing an
OpenAPI schema. Existing `LessonType` can be reused. Proposed public names and
fields are:

- `ProjectionRange { DAYS, WEEKS }`, `ProjectionPointState { DATA, NO_DATA,
  FUTURE }`, `ProjectionDayState { PAST, CURRENT, FUTURE }`,
  `ProjectionLessonStatus { PRESENT, ABSENT, EXCUSED, ACTIVE, FUTURE, NO_DATA,
  CANCELLED }`, `AttendanceHistoryStatus { PRESENT, ABSENT, EXCUSED, FUTURE,
  NO_DATA }`, and `AttendanceRequestKind { EXCUSE, LATE_CHECKIN }`.
- `AttendanceMetricValue(int count, BigDecimal percent)` where `percent` is
  nullable only when `held == 0`; `AttendanceMetricSet(present,
  presentOrExcused, excused, absent, int held, int planned)`.
- `AttendanceRequestOption(String id, AttendanceRequestKind kind, String label,
  boolean enabled, String reason)`; `reason` is nullable.
- `AttendanceSubjectRef(String id, String name)` and
  `AttendanceLessonSchedule(LocalTime startsAt, LocalTime endsAt, String room)`.
- `StudentAttendanceLesson(String id, LocalDate date, String number,
  AttendanceSubjectRef subject, LessonType type, AttendanceLessonSchedule
  schedule, ProjectionLessonStatus status, List<AttendanceRequestOption>
  requestOptions)`.
- `StudentAttendanceDay(LocalDate date, String weekday, String dayNumber,
  ProjectionDayState state, List<StudentAttendanceLesson> lessons)`.
- `AttendanceHistorySegment(String id, AttendanceHistoryStatus status)`,
  `AttendanceSubjectTypeSummary(LessonType type, AttendanceMetricSet metrics,
  List<AttendanceHistorySegment> history)`, and
  `AttendanceSubjectProjection(String id, String name,
  List<AttendanceSubjectTypeSummary> typeCards)`.
- `AttendanceSeriesPoint(String id, String label, LocalDate dateFrom,
  LocalDate dateTo, ProjectionPointState state, AttendanceMetricSet metrics)`.
- `StudentAttendanceOverviewResponse(String semesterId, Instant serverNow,
  AttendanceMetricSet metrics, List<StudentAttendanceDay> days)`.
- `StudentAttendanceSubjectsResponse(String semesterId, Instant serverNow,
  List<AttendanceSubjectProjection> subjects)`.
- `StudentAttendanceGraphResponse(String semesterId, Instant serverNow,
  ProjectionRange range, List<AttendanceSeriesPoint> points)`.
- `StatisticsOwnRank(Integer position, int participantCount, boolean available)`;
  `StatisticsSubjectSummary(String id, String name, AttendanceMetricSet metrics)`.
- `StudentStatisticsOverviewResponse(String semesterId, Instant serverNow,
  ProjectionRange range, AttendanceMetricSet metrics, StatisticsOwnRank ownRank,
  List<AttendanceSeriesPoint> semesterSeries,
  List<StatisticsSubjectSummary> subjects)`.
- `StatisticsTypeCard(LessonType type, AttendanceMetricSet metrics,
  List<AttendanceHistorySegment> history)` and
  `StudentStatisticsSubjectDetailResponse(String semesterId, Instant serverNow,
  String subjectId, String name, ProjectionRange range,
  List<LessonType> availableTypes, List<LessonType> selectedTypes,
  AttendanceMetricSet selectedAggregate, List<AttendanceSeriesPoint> series,
  List<StatisticsTypeCard> typeCards)`.

All returned collections are required and non-null. Empty collections are `[]`.
Percent is emitted as a JSON number with at most two decimal places. Public
records never contain peer IDs, peer metrics, raw attendance documents, actor IDs
or internal revisions.

Add `SUBJECT_NOT_FOUND` and `STUDENT_SCOPE_UNRESOLVED` to public `ProblemCode`.
Reuse `INVALID_REQUEST`, `INVALID_SESSION`, `WRONG_ROLE`, `OUT_OF_SCOPE` and
`DEPENDENCY_UNAVAILABLE`.

### Proposed Java ownership and files

Future implementation, only after root accepts this proposal, has these bounded
owners:

- Mobile BFF contract/export writer: `StudentApi.java`, `StudentApiModels.java`,
  `docs/openapi/mobile-bff.json` and generated
  `frontends/mobile-core/src/api/generated/mobile-bff.ts`.
- Mobile BFF application writer: `StudentApiController.java`, a new
  `student/StudentProjectionService.java`, and additive projection methods in
  `grpc/MobileAttendanceClient.java`. Existing `StudentQueryService` and B1a
  methods remain unchanged.
- Attendance domain writer: `proto/attendance.proto`, additive handlers in
  `AttendanceStudentGrpcServiceImpl.java`, a new
  `report/studentprojection/StudentProjectionQueryService.java`, additive methods
  in `AcademicGrpcClient.java` and `ScheduleGrpcClient.java`, plus focused
  repositories/adapters for occurrence-generation attendance reads.
- Academic scope writer: additive `ResolveStudentProjectionScope` proto and
  `AcademicGrpcServiceImpl` handler backed by authoritative semester membership,
  subject and ACTIVE STUDENT grant data.
- Schedule lineage writer: additive auth-bound current-occurrence proto and
  `ScheduleGrpcServiceImpl` handler backed by V17 occurrence/current-instance
  state. This owner also performs the separately accepted binding-RPC correction.

Shared proto, Java contracts, OpenAPI and generated TypeScript each have one writer
and one frozen revision. E owns later frontend transport, routing and screen
wiring; those files are not part of the B2 backend packet.

### Proposed internal proto diff

Append five methods to `AttendanceStudentGrpcService`; all caller identity remains
absent from messages and comes only from the validated signed internal JWT:

```proto
rpc GetStudentAttendanceOverview (StudentProjectionScopeRequest)
    returns (StudentAttendanceOverviewProjection);
rpc GetStudentAttendanceSubjects (StudentProjectionScopeRequest)
    returns (StudentAttendanceSubjectsProjection);
rpc GetStudentAttendanceGraph (StudentAttendanceGraphRequest)
    returns (StudentAttendanceGraphProjection);
rpc GetStudentStatisticsOverview (StudentStatisticsOverviewRequest)
    returns (StudentStatisticsOverviewProjection);
rpc GetStudentStatisticsSubjectDetail (StudentStatisticsSubjectDetailRequest)
    returns (StudentStatisticsSubjectDetailProjection);

message StudentProjectionScopeRequest { int64 semester_id = 1; }
message StudentAttendanceGraphRequest {
  int64 semester_id = 1;
  StudentProjectionRange range = 2;
}
message StudentStatisticsOverviewRequest {
  int64 semester_id = 1;
  StudentProjectionRange range = 2;
}
message StudentStatisticsSubjectDetailRequest {
  int64 semester_id = 1;
  int64 subject_id = 2;
  StudentProjectionRange range = 3;
  repeated StudentProjectionLessonType lesson_types = 4;
}

enum StudentProjectionRange {
  STUDENT_PROJECTION_RANGE_UNSPECIFIED = 0;
  STUDENT_PROJECTION_RANGE_DAYS = 1;
  STUDENT_PROJECTION_RANGE_WEEKS = 2;
}
enum StudentProjectionLessonType {
  STUDENT_PROJECTION_LESSON_TYPE_UNSPECIFIED = 0;
  STUDENT_PROJECTION_LESSON_TYPE_LECTURE = 1;
  STUDENT_PROJECTION_LESSON_TYPE_PRACTICE = 2;
  STUDENT_PROJECTION_LESSON_TYPE_LAB = 3;
}
```

The exact response wire ledger proposed for append-only messages is:

- `StudentProjectionMetricValue`: `count=1`, optional
  `percent_basis_points=2` in `0..10000`; the BFF converts basis points to the
  public decimal percent.
- `StudentProjectionMetricSet`: `present=1`, `present_or_excused=2`, `excused=3`,
  `absent=4`, `held=5`, `planned=6`.
- `StudentProjectionRequestOption`: `id=1`, `kind=2`, `label=3`, `enabled=4`,
  optional `reason=5`.
- `StudentProjectionLesson`: `lesson_id=1`, `occurrence_id=2`, `date=3`,
  `number=4`, `subject_id=5`, `subject_name=6`, `lesson_type=7`, `starts_at=8`,
  `ends_at=9`, optional `room=10`, `status=11`, repeated `request_options=12`.
- `StudentProjectionDay`: `date=1`, `weekday=2`, `day_number=3`, `state=4`,
  repeated `lessons=5`.
- `StudentProjectionHistorySegment`: `occurrence_id=1`, `generation=2`,
  `status=3`. The BFF forms an opaque public ID from the pair; clients never
  parse it.
- `StudentProjectionSubjectType`: `lesson_type=1`, `metrics=2`, repeated
  `history=3`; `StudentProjectionSubject`: `subject_id=1`, `name=2`, repeated
  `type_cards=3`.
- `StudentProjectionSeriesPoint`: `bucket_id=1`, `label=2`, `date_from=3`,
  `date_to=4`, `state=5`, `metrics=6`.
- `StudentAttendanceOverviewProjection`: `semester_id=1`, `server_now=2`,
  `metrics=3`, repeated `days=4`.
- `StudentAttendanceSubjectsProjection`: `semester_id=1`, `server_now=2`,
  repeated `subjects=3`.
- `StudentAttendanceGraphProjection`: `semester_id=1`, `server_now=2`,
  `range=3`, repeated `points=4`.
- `StudentProjectionOwnRank`: optional `position=1`, `participant_count=2`,
  `available=3`; `StudentProjectionSubjectSummary`: `subject_id=1`, `name=2`,
  `metrics=3`.
- `StudentStatisticsOverviewProjection`: `semester_id=1`, `server_now=2`,
  `range=3`, `metrics=4`, `own_rank=5`, repeated `semester_series=6`, repeated
  `subjects=7`.
- `StudentProjectionTypeCard`: `lesson_type=1`, `metrics=2`, repeated
  `history=3`.
- `StudentStatisticsSubjectDetailProjection`: `semester_id=1`, `server_now=2`,
  `subject_id=3`, `name=4`, `range=5`, repeated `available_types=6`, repeated
  `selected_types=7`, `selected_aggregate=8`, repeated `series=9`, repeated
  `type_cards=10`.

The supporting status enums are new prefixed enums matching the accepted UI
values: lesson `PRESENT/ABSENT/EXCUSED/ACTIVE/FUTURE/NO_DATA/CANCELLED`, history
`PRESENT/ABSENT/EXCUSED/FUTURE/NO_DATA`, point `DATA/NO_DATA/FUTURE`, day
`PAST/CURRENT/FUTURE`, and request kind `EXCUSE/LATE_CHECKIN`; every enum has an
`UNSPECIFIED=0` value and an unspecified value is rejected before projection.

Add `StudentProjectionErrorDetail { code=1; }` and prefixed error enum values for
`INVALID_REQUEST`, `INVALID_SESSION`, `WRONG_ROLE`, `OUT_OF_SCOPE`,
`SUBJECT_NOT_FOUND`, `STUDENT_SCOPE_UNRESOLVED`, `DEPENDENCY_UNAVAILABLE` and
`INCONSISTENT_SOURCE`. The BFF maps these codes without parsing description text.

Add one auth-bound Academic RPC:

```proto
rpc ResolveStudentProjectionScope (StudentProjectionScopeRequest)
    returns (StudentProjectionScopeResponse);
message StudentProjectionScopeRequest { int64 semester_id = 1; }
message StudentProjectionScopeResponse {
  int64 student_id = 1;
  int64 group_id = 2;
  int64 semester_id = 3;
  string date_from = 4;
  string date_to = 5;
  bool terminal_read_only = 6;
  repeated int64 active_roster_user_ids = 7;
  repeated AcademicSubjectInfo subjects = 8;
}
```

The Academic handler resolves the caller from signed context, verifies the
semester membership and returns only ACTIVE STUDENT grant IDs for rank. It never
accepts user or group identity from the request.

Add one auth-bound Schedule RPC whose response is one logical occurrence row with
its current physical location, rather than a list containing transferred sources:

```proto
rpc GetStudentProjectionOccurrences (StudentProjectionOccurrencesRequest)
    returns (StudentProjectionOccurrencesResponse);
message StudentProjectionOccurrencesRequest { int64 semester_id = 1; }
message StudentProjectionOccurrence {
  int64 occurrence_id = 1;
  int64 current_lesson_id = 2;
  int64 assignment_id = 3;
  int64 semester_id = 4;
  int64 group_id = 5;
  int64 subject_id = 6;
  int64 assigned_teacher_id = 7;
  string lesson_type = 8;
  int64 generation = 9;
  int64 revision = 10;
  string date = 11;
  int32 lesson_number = 12;
  string start_time = 13;
  string end_time = 14;
  optional string room = 15;
  string lifecycle_state = 16;
}
message StudentProjectionOccurrencesResponse {
  repeated StudentProjectionOccurrence occurrences = 1;
  optional string updated_at = 2;
}
```

The Schedule handler obtains group authority from signed context and returns each
logical occurrence once. Transfer returns only its current target location;
cancelled occurrence remains identifiable with `CANCELLED` state so the UI may
show it while the calculator excludes it from planned/held denominators.

## 4. Required behavior

### Projection and lineage rules

- Attendance is keyed by `(occurrenceId, studentId, generation)`. Physical
  `lessonId` is returned for current request/check-in actions, but it is not the
  metric identity. A deterministic migration/adapter from existing lesson-keyed
  marks is required before live acceptance.
- Recurring and one-off lessons use the same occurrence projection. A transfer
  contributes one logical occurrence at the target; its source is history only.
  Cancelled occurrences are visible as `CANCELLED` where the day UI needs them and
  excluded by `AttendanceMetricCalculator`. Restore increments generation; stale
  prior-generation marks remain historical and do not populate the restored row.
- `serverNow` and all day/current/future classifications use the Attendance
  service clock with `Europe/Moscow` calendar boundaries. Day buckets are one
  calendar day. Week buckets are Monday through Sunday, clipped to semester
  bounds. Bucket IDs and labels are server-generated and opaque to clients.
- Every aggregate, type aggregate, series point and subject total is computed from
  the same validated occurrence set through the accepted calculator. Client sums,
  status-based percentage guesses and name-based subject grouping are forbidden.
- Subjects are grouped only by stable subject ID. Lesson type comes from immutable
  assignment/occurrence snapshot, not a current mutable subject or schedule row.
- Rank uses only the Academic-provided authoritative ACTIVE STUDENT roster and
  exact accepted peer metrics. The response exposes only own position,
  participant count and availability. Missing self or zero held count yields
  `position=null, available=false`; self is never inserted.
- `selectedAggregate`, detail series and type cards are calculated server-side
  from exactly the validated selected type set. A client does not filter an
  all-types aggregate and present it as selected data.
- `requestOptions` are server-owned. B2 may honestly return `[]` until the separate
  Requests eligibility contract is accepted. It must never enable EXCUSE or
  LATE_CHECKIN from lesson status alone. Enabling or publishing the Requests slot
  remains outside this proposal.

### Authorization and terminal read-only

- The BFF validates the external bearer token. It forwards the signed internal
  identity; BFF request parameters contain no user/group override.
- All five methods require active role `STUDENT`. Another active role returns 403
  `WRONG_ROLE`. Missing, invalid or expired identity returns 401 `INVALID_SESSION`.
- Academic is authoritative for caller, group, semester membership, terminal
  state, subjects and active rank roster. Schedule independently checks that its
  returned occurrence scope matches the signed caller and Academic-resolved
  semester/group. Attendance rejects any source mismatch instead of partially
  projecting it.
- An active STUDENT may read an authorized current or historical semester. A
  terminal signed STUDENT session with `readOnly=true` may call all five GETs only
  for its own historically authorized semester/group. It gets no enabled request
  options. It never gains peer rows; rank is unavailable when the authoritative
  active roster does not contain self.
- Suspended or otherwise unresolved membership returns 403
  `STUDENT_SCOPE_UNRESOLVED`. A semester or subject belonging to another scope
  returns 403 `OUT_OF_SCOPE` without disclosing its contents.

### Errors

| Condition | HTTP/code |
|---|---|
| malformed/zero ID, missing or unspecified range, duplicate/unsupported type | 400 `INVALID_REQUEST` |
| invalid or expired identity | 401 `INVALID_SESSION` |
| active role is not STUDENT | 403 `WRONG_ROLE` |
| foreign semester/group/subject scope | 403 `OUT_OF_SCOPE` |
| suspended or indeterminate historical membership | 403 `STUDENT_SCOPE_UNRESOLVED` |
| authorized subject ID absent from the authorized semester catalog | 404 `SUBJECT_NOT_FOUND` |
| Academic, Schedule or required attendance storage unavailable | 503 `DEPENDENCY_UNAVAILABLE` |
| duplicate occurrence, revision/generation mismatch, missing immutable assignment snapshot, incomplete roster | 503 `DEPENDENCY_UNAVAILABLE`, logged internally as `INCONSISTENT_SOURCE` |

No route returns a partial 200 when a mandatory source fails. Problem responses
also carry `Cache-Control: no-store` and do not reveal peer IDs or internal data.

### Binding ownership correction required before implementation

Canonical ownership and call direction are:

```text
Academic Homework publication use case
  -> ScheduleGrpcService.ReserveHomeworkBinding
  -> Academic persists immutable Homework content as PENDING
  -> ScheduleGrpcService.ConfirmHomeworkBinding
  -> Academic promotes its publication row to ACTIVE
  -> Academic emits published notification after durable ACTIVE

Academic week/read/reminder adapter
  -> ScheduleGrpcService.GetHomeworkBindings
  -> resolves current lesson/location and archive state
```

Schedule exclusively owns binding ID, occurrence/current lesson, binding state and
revision. Academic exclusively owns Homework content, completion and publication
state. Neither service writes the other's database. Schedule transfer/cancel
updates its PENDING/ACTIVE bindings in the same Schedule transaction.

The minimum correction to the accepted B0 wire ledger is:

1. explicitly supersede the affected binding-RPC placement in the B0 addendum;
2. remove the three methods from `AcademicGrpcService` and move
   `HomeworkBindingState`, `ReserveHomeworkBindingRequest`,
   `ConfirmHomeworkBindingRequest`, `HomeworkBindingResponse`,
   `HomeworkBindingsRequest` and `HomeworkBindingsResponse` from
   `academic.proto` to `schedule.proto`;
3. preserve every existing field name, field number and enum numeric value; within
   the Schedule package `current_lesson` uses `LessonInfo` directly;
4. keep `HomeworkInfo.binding_id`, `occurrence_id`, `current_lesson_id`,
   `binding_revision` and `archived` in `academic.proto`; they are Academic's
   public content projection of Schedule-owned identity/location;
5. generate clients only after one frozen proto correction. No compatibility
   forwarding RPC or dual authority is proposed because the methods have no
   application implementation or released consumer.

Until root accepts this supersession, lifecycle implementation is blocked. This
document does not edit either proto.

### Minimum lifecycle prerequisites for honest B2 acceptance

Mandatory before the five reads can be accepted as live business behavior:

- V17-backed occurrence/current-instance application logic, including recurring,
  one-off, transfer, cancel, restore, monotonic revision and generation;
- an auth-bound Schedule read that returns one current row per occurrence and no
  transferred source duplicate;
- immutable assignment, subject, semester, teacher and lesson-type snapshots on
  every occurrence;
- Academic historical membership resolution and an authoritative ACTIVE STUDENT
  semester roster; current `GetGroupMembers` alone is insufficient;
- attendance read identity by occurrence and generation, with explicit treatment
  of pre-migration physical-lesson marks and stale restored generations;
- fail-closed cross-service mismatch handling and stable signed-context forwarding.

The full Homework publication/notification lifecycle is not a data dependency of
these five read payloads. Its ownership correction must be frozen before the same
shared proto/lifecycle writer proceeds, but B2 acceptance must not be presented as
Homework lifecycle acceptance. Likewise, synthetic FE13 fixtures remain UI
evidence rather than backend business evidence.

## 5. Constraints

- This file is a proposal. Root must accept or amend it before any product writer.
- REST is external, gRPC is internal, and Java-first contracts remain the single
  OpenAPI source. Generated TypeScript is never edited by hand.
- Accepted calculator and FE13 source hashes remain unchanged.
- Long internal IDs map to positive decimal strings in public JSON. User/group
  identity is never accepted from query, path or body.
- Reads are fail-closed for authz, roster, lifecycle and source inconsistency.
- No cross-database SQL, name identity, client aggregation, peer payload, hidden
  fallback to legacy `ReportApi`, or second cache/query owner.
- The existing B1a endpoints and exact accepted checks are not reopened.
- Shared contract, proto, OpenAPI and generated artifacts have one writer after a
  frozen revision and ownership release from C/E as applicable.
- No production migration, deploy, secret change, data deletion or runtime action
  is authorized by this proposal.

## 6. Existing patterns

- `StudentApi` supplies Spring annotations and Java records, then the repository
  exports `docs/openapi/mobile-bff.json` and generated frontend types.
- `StudentApiController` delegates to focused services. A separate
  `StudentProjectionService` keeps the accepted B1a `StudentQueryService` stable.
- `MobileAttendanceClient` forwards signed internal context and maps typed gRPC
  details to public Problem Details without parsing exception text.
- `AttendanceStudentGrpcServiceImpl` already terminates the signed student gRPC
  boundary and depends on focused Academic and Schedule client wrappers.
- `AttendanceMetricCalculator` and `OwnRankCalculator` are pure accepted domain
  functions. The new query service assembles validated inputs; it does not copy
  their arithmetic.
- Schedule V17 and the frozen foundation decision define logical occurrence,
  current physical instance, generation and revision. Academic defines stable
  subject identity, semester scope and role grants.
- Terminal sessions remain readable and mutation guards remain enforced at BFF,
  gRPC ingress and domain boundaries. UI read-state wrappers are frontend state;
  successful REST DTOs contain data only.

## 7. Acceptance criteria

Contract and generation:

- The Java-first contract exports exactly five new GET operations with the inputs,
  outputs, requiredness, decimal-string IDs, enum values and no-store headers in
  section 3; generated TypeScript represents every accepted FE13 field without
  handwritten widening.
- Proto compilation proves all new fields/tags, typed error details and the binding
  ownership correction. No old tag/name is reused.

Authorization and privacy:

- Invalid token is 401; wrong role, foreign scope and unresolved terminal scope
  are distinct typed 403 responses; authorized missing subject is typed 404.
- Active and historically authorized terminal STUDENT reads work. Terminal reads
  contain no enabled request option. No response/log exposes peer identity or peer
  metrics; rank exposes only own position/count/availability.

Lineage and numeric behavior:

- Recurring and one-off rows both appear. A transferred occurrence is counted once
  at the target. Cancel is excluded from denominators but can be displayed as
  cancelled. Restore generation starts without inherited marks; stale events do
  not alter it.
- Overall, subject, type, selected-type and every series bucket match the accepted
  calculator for present, present-or-excused, excused, absent, held and planned.
  Zero held gives null percentages. Rank ties use competition rank; absent self is
  not inserted.
- Day/week bucket boundaries use Moscow dates, are clipped to the semester, ordered
  deterministically and contain explicit DATA/NO_DATA/FUTURE state.
- Same-name subjects remain distinct by ID. Selected types are validated, ordered
  canonically and recomputed on the server.
- Empty authorized data returns 200 with zero metrics and empty arrays. Any missing
  mandatory dependency or inconsistent lineage fails the whole response with 503.

Independence:

- FE13 fixtures can validate adapter shape but cannot satisfy backend acceptance.
  Live acceptance requires focused service integration and a cross-service runtime
  over real Schedule, Academic and Attendance stores, followed by fresh independent
  Sol high review of the stable diff and evidence.

## 8. Verification

This docs-only preparation runs no Gradle, npm, Docker, Java, database, service or
browser command. Runtime is `N/A`. No accepted numeric or B1a check is repeated.

After root accepts the proposal and implementation exists, the verification plan
is:

1. pure Attendance tests for occurrence validation, all metric slices, Moscow
   day/week buckets, selected types, same-name subjects, rank ties/zero data/missing
   self and no peer leakage;
2. Academic tests for signed-context semester scope, ACTIVE STUDENT roster,
   historical terminal read-only and suspended/unresolved rejection;
3. Schedule tests for one current instance per recurring/one-off occurrence,
   transfer/cancel/restore revision and generation, and no source double count;
4. proto compilation and Academic/Schedule/Attendance contract tests, including
   the corrected Schedule-owned binding methods;
5. Mobile BFF contract-export test, generated-type check and five HTTP↔gRPC tests
   covering success, empty, malformed input, 401, each 403 class, 404 and 503;
6. cross-service runtime with real stores for recurring and one-off data, transfer,
   cancel, restore, 1/2/3 types, same-name subjects, terminal history, roster rank
   and dependency outage; synthetic fixtures are supplementary only;
7. fresh Sol high review using the accepted contract, stable diff, command/exits,
   environment and runtime evidence.

Each future command must record revision, exact command, environment, exit code and
evidence. An unrun or skipped command is not PASS.

## 9. Do not

- Do not implement from this proposal before root acceptance or describe proposed
  URLs, DTOs or proto messages as previously approved.
- Do not reopen the accepted numeric calculators, B1a, Auth/B0/Gateway, FE13 source
  or unrelated E work.
- Do not implement binding storage in Academic, access the Schedule database from
  Academic, keep two binding authorities, or use an async event as the atomic
  Schedule transfer update.
- Do not use current `GetGroupMembers` as a semester/status roster, infer terminal
  access from a client flag, group subjects by name, insert self into rank, or count
  transferred physical lessons twice.
- Do not synthesize request eligibility, percentages, selected aggregates, buckets
  or rank in the frontend/BFF.
- Do not return partial success on a mandatory dependency failure, expose peer
  rows, or treat legacy `ReportApi` and fixture harnesses as B2 business PASS.
- Do not hand-edit OpenAPI/generated TypeScript, add a second exporter, expand
  shared ownership without a frozen packet, run heavy checks without the owner
  lease, or use Terra without the separate evidence gate.
