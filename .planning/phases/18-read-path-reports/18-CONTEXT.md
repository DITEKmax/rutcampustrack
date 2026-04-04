# Phase 18: Read Path -- Reports - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Headmen and teachers can view lesson attendance (all group members + status) and a journal grid (students x dates for group+subject). Students can view their own attendance stats (% per subject, excluding cancelled) and raw attendance records filterable by subject. The report domain accesses checkin data only through `AttendanceReadPort` -- no direct imports from `checkin/`.

No export (PDF/Excel), no semester summary, no top-skippers, no weekly trend charts, no excuse tickets.

</domain>

<decisions>
## Implementation Decisions

### Report API Design
- **D-01:** Separate `/reports/*` prefix for all report endpoints. Clean separation from write path (`/attendance/*`).
- **D-02:** Four endpoints in scope:
  - `GET /reports/lesson/{lessonId}` (RPRT-01) -- lesson attendance list, all group members with status
  - `GET /reports/journal` (RPRT-02) -- journal grid, query params: groupId, subjectId, dateFrom, dateTo
  - `GET /reports/student/stats` (RPRT-03) -- own attendance stats per subject
  - `GET /reports/student/records` (RPRT-04) -- own raw attendance list, filterable by subject
- **D-03:** Deferred from phases-plan.md: `GET /reports/semester/{id}/summary`, `GET /reports/group/{id}/top-skippers`, `GET /reports/export`. These belong in v4.1+.
- **D-04:** Lesson attendance (RPRT-01) returns ALL group members, not just those with records. Call `academicGrpcClient.getGroupMembers()` to get full roster, left-join with attendance records. Students with no record appear as status=absent.
- **D-05:** Authorization: role + group check for both headman and teacher. Headman: `requestContext.groupId` must match lesson's groupId. Teacher: verify via `academicGrpcClient` that teacher teaches that subject for that group. Prevents data leakage.
- **D-06:** Student endpoints (stats, records) use `requestContext.getUserId()` -- students can only see their own data. No path param for userId (prevents enumeration).

### Journal Grid Shape
- **D-07:** Nested structure: top-level `dates[]` array + `students[]` array where each student has `records[]` with date, lessonNumber, status. Frontend renders rows=students, cols=dates.
- **D-08:** Status representation: BOTH enum name and Russian symbol. Each record includes `{ status: "present", symbol: "б" }`. Symbol mapping: present=б, absent=н, excused=у, free_attendance=сп, cancelled=-- (should not appear in journal but defensive).
- **D-09:** Student names resolved via gRPC: `academicGrpcClient.getGroupMembers()` returns member IDs + names. ReportService joins with attendance data. Consistent with RPRT-01 approach.

### Stats Calculation
- **D-10:** "Attended" = present + excused + free_attendance. Only `absent` counts against the student. Matches CLAUDE.md status table where excused/free_attendance are "да (уважит.)".
- **D-11:** Cancelled lessons excluded from denominator entirely. A cancelled lesson does not count as total or attended.
- **D-12:** On-the-fly MongoDB aggregation at query time. No pre-materialized views. Fine for current scale (500-5000 students). Optimize later if needed.
- **D-13:** Stats response per subject: `{ subjectId, subjectName, total, attended, absent, excused, percentage }`. Plus `overall` object with totals across all subjects. Subject names resolved via gRPC to Schedule/Academic service.

### Domain Isolation (RPRT-05)
- **D-14:** `AttendanceReadPort` interface in `shared/port/` package. Implementation `AttendanceReadPortImpl` in `checkin/` package (where `AttendanceRepository` and `AttendanceDocument` live).
- **D-15:** Port exposes query-oriented methods: `findByLessonId(Long)`, `findByUserId(Long, Long semesterId)`, `findByGroupAndSubject(Long groupId, Long subjectId, LocalDate from, LocalDate to)`.
- **D-16:** `AttendanceRecord` -- read-only DTO in `shared/port/`. Relevant subset of fields: lessonId, userId, groupId, subjectId, lessonDate, lessonNumber, status, source. Omits id, markedBy, createdAt, updatedAt.
- **D-17:** ArchUnit test enforces: no class in `report/` package imports any class from `checkin/` package. `AttendanceReadPort` in `shared/port/` is the only bridge.

### Claude's Discretion
- ReportService internal structure and method decomposition
- MongoDB aggregation pipeline specifics for stats computation
- Contract DTO naming and exact field names (ReportApi interfaces)
- Test structure: how many integration tests, which scenarios to prioritize
- Whether to use single ReportService or separate LessonReportService/JournalService/StatsService
- Pagination strategy for student records endpoint

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Business Rules & Requirements
- `CLAUDE.md` -- status table (б/н/у/сп mapping), attendance statuses, package structure rules, contract-first patterns
- `.planning/REQUIREMENTS.md` -- RPRT-01..05 acceptance criteria
- `docs/job-stories.md` -- JS-TEACHER-01..07 (journal/stats), JS-STUDENT-07 (student stats), JS-HEADMAN-01..03 (marking context)
- `docs/phases-plan.md` -- Phase 4 section 5 "Модуль report/" for original endpoint design

### Architecture & Patterns
- `docs/architecture.md` -- service architecture, gRPC contracts
- `docs/database-schema.md` -- MongoDB attendances collection schema
- `.planning/phases/17-write-path-geo-checkin-manual-marking/17-CONTEXT.md` -- write path decisions (D-01..D-22)

### Existing Code (read for patterns)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceDocument.java` -- MongoDB document shape
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java` -- existing repository
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java` -- getGroupMembers() signature
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java` -- getLessonById() signature
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java` -- auth context pattern
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequireRole.java` -- role annotation

### Event & Proto Contracts
- `event-schemas/attendance.marked.json` -- event payload (context for what data flows)
- `proto/academic.proto` -- GetGroupMembers RPC (member IDs + names)
- `proto/schedule.proto` -- GetLessonById, LessonResponse fields

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AttendanceRepository` (MongoRepository): existing CRUD, can add custom query methods
- `AcademicGrpcClient.getGroupMembers()`: returns group roster with IDs -- needed for RPRT-01 (full roster) and RPRT-02 (student names)
- `ScheduleGrpcClient.getLessonById()`: validates lesson exists and returns groupId for auth check
- `RequestContext`: provides userId, groupId, role, isHeadman from gateway headers
- `GlobalExceptionHandler`: existing 403/404/400 handlers
- `AbstractAttendanceIntegrationTest`: base class with MongoDB + Redis + RabbitMQ Testcontainers

### Established Patterns
- Contract-first: `*-api-contract` (interfaces + DTOs) + `*-app` (controllers implement interfaces)
- `@RequireRole` AOP for authorization, controller-level or method-level
- HATEOAS: `EntityModel<T>` for single items, `PagedModel<EntityModel<T>>` for collections
- RFC 7807 error responses via `ErrorResponse` record
- gRPC clients with `@Service` for inter-service calls
- Volatile caching pattern (SemesterCacheService, GeofenceService) for rarely-changing data

### Integration Points
- New `report/` package: ReportController(s), ReportService(s)
- New `shared/port/` package: AttendanceReadPort interface + AttendanceRecord DTO
- `checkin/` package gets AttendanceReadPortImpl (wires AttendanceRepository to port)
- Contract module gets report API interfaces and response DTOs
- Gateway routing: `/api/reports/**` -> attendance-service:9093

</code_context>

<specifics>
## Specific Ideas

- Journal grid returns both enum name AND Russian symbol per status cell: `{ status: "present", symbol: "б" }`
- Stats include both per-subject breakdown AND overall aggregation
- Student endpoints use implicit userId from RequestContext (no path param) to prevent enumeration
- Full group roster with left-join for lesson attendance -- no "invisible absent" students

</specifics>

<deferred>
## Deferred Ideas

- Semester summary endpoint (`GET /reports/semester/{id}/summary`) -- admin dashboard feature, v4.1+
- Top-skippers endpoint (`GET /reports/group/{id}/top-skippers`) -- v4.1+
- PDF/Excel export (`GET /reports/export?format=...`) -- v4.1+
- Weekly trend charts for student stats -- v4.1+ (JS-STUDENT-07 mentions "график тренда по неделям")
- Redis caching for stats queries -- optimize later if scale demands it
- Subject rating (most/least skipped) -- v4.1+

</deferred>

---

*Phase: 18-read-path-reports*
*Context gathered: 2026-04-04*
