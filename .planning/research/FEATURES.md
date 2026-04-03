# Feature Research

**Domain:** University attendance tracking service — geo-checkin, manual marking, auto-absent, basic reports
**Researched:** 2026-04-04
**Project:** RutCampusTrack v4.0 Attendance Service MVP
**Confidence:** HIGH — based on existing system contracts, event schemas, proto files, job stories, and DB schema already designed for this milestone.

---

## Context: What Already Exists (Must Not Re-Implement)

These capabilities are fully operational in the existing system and are consumed by Attendance Service:

| Feature | Where | Relevant to Attendance Service |
|---------|-------|-------------------------------|
| JWT auth, role + headman injection | API Gateway | X-User-Role, X-Is-Headman, X-User-Id headers arrive on every request |
| Groups + students (GetGroupMembers) | Academic Service gRPC | Required for auto-absent (who was in the group) and journal (who to show) |
| Campus geofence (GetCampusGeofence) | Academic Service gRPC | Required on every geo-checkin for radius validation |
| IsHeadman check | Academic Service gRPC | Required for manual marking authorization |
| GetActiveSemester | Academic Service gRPC | Required to populate `semester_id` on attendance docs |
| Lesson lifecycle (PLANNED→ACTIVE→CLOSED) | Schedule Service + Cron | lesson.started/closed/cancelled events already published |
| GetActiveLesson gRPC | Schedule Service | Called on every geo-checkin to verify group has active lesson |
| GetLessonById gRPC | Schedule Service | Called to populate denormalized fields on write path |
| GetLessonsByGroup gRPC | Schedule Service | Called by journal/report to get full lesson list |
| lesson.started / lesson.closed / lesson.cancelled events | RabbitMQ | Attendance Service must consume all three |
| attendance.marked event schema | event-schemas/ | Already defined; Attendance Service must publish it |
| MongoDB attendance collection + indexes | database-schema.md | Full document structure, unique index, query indexes already designed |
| Redis keys for checkin rate limit and dedup lock | database-schema.md | rate:checkin:{user_id} and checkin:lock:{lesson_id}:{user_id} already specified |
| AttendanceStatus, AttendanceSource, ExcuseType enums | attendance-api-contract | Already defined in contract module |
| @RequireRole AOP, contract-first conventions | Platform | Must follow same conventions as Auth + Academic + Schedule |

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features the system cannot ship v4.0 without. Missing = attendance system does not function at all.

| Feature | Why Expected | Complexity | Dependencies |
|---------|--------------|------------|--------------|
| **Geo-checkin endpoint (POST /checkin)** | Students check in via Telegram Mini App. This is the primary student-facing action — without it, the whole user value of the service is missing (JS-STUDENT-01). | MEDIUM | Validates: lesson is `active` (GetActiveLesson gRPC), `is_geo_blocked=false`, student belongs to group (GetGroupMembers or header), within 5-min window before/after lesson, within campus radius (GetCampusGeofence + Haversine). Writes MongoDB doc with `marked_by=student_geo`. Rate-limited via Redis (3/min). Dedup lock via Redis 5-sec TTL. Idempotent via `{lesson_id, user_id}` unique index (return 409 on duplicate). Publishes `attendance.marked` event after successful write. |
| **lesson.closed consumer + auto-absent** | Journal must always be complete. Headman should not manually mark every absent student — the system does it automatically (JS-HEADMAN-03, JS-SYSTEM-07). | MEDIUM | Consumes `lesson.closed` from RabbitMQ. Calls GetGroupMembers gRPC to find all students in the group. Calls GetLessonById gRPC to populate denormalized fields. Bulk-inserts absent docs for every student without an existing attendance doc for that lesson. Source = `auto_scheduler`. Must be idempotent (MongoDB unique index will reject duplicate inserts naturally). Does NOT publish `attendance.marked` per individual absent — would flood; headman notification comes via separate lesson.closed handling. |
| **lesson.cancelled consumer** | Cancelled lessons must not count in stats. Any attendance docs already created for the lesson (e.g., some students checked in before cancellation) must be updated to `cancelled` status so stats queries skip them (business rule from CLAUDE.md). | LOW | Consumes `lesson.cancelled` from RabbitMQ. Updates all attendance docs WHERE `lesson_id = ?` to set `status = cancelled`. The `AttendanceStatus.CANCELLED` enum already exists in the contract. Queries already have `cancelled` lesson filtering responsibility. |
| **lesson.started consumer** | Stores lesson context for the checkin window duration; warm-up of denormalized lesson data for the write path during the active lesson period. | LOW | Consumes `lesson.started`. Can cache lesson context (subject_id, teacher_id, group_id, lesson_number, date) keyed by group_id in Redis for the duration of the lesson. Optional for MVP but reduces gRPC calls during the hot checkin window. Payload already contains all needed denormalized fields. |
| **Manual attendance marking (PUT /lessons/{lessonId}/attendance/{userId})** | Headman must be able to mark any student present, absent, excused, or free_attendance directly (JS-HEADMAN-01). Autosave per click — one endpoint per student, not batch. | MEDIUM | Validates: caller is headman or assistant with `mark_attendance` permission (IsHeadman gRPC or header X-Is-Headman). Student belongs to the group. Lesson exists and is not cancelled. Sets or replaces the attendance doc with `marked_by=headman`. Does not require lesson to be `active` — headman can mark retroactively for current or same-day lessons. Publishes `attendance.marked` event. |
| **Lesson attendance view (GET /lessons/{lessonId}/attendance)** | Headman needs a live view of who is marked and who is not during the lesson (JS-HEADMAN-01). | LOW | Returns all group members from GetGroupMembers gRPC merged with existing attendance docs for the lesson. Students without a doc appear as `null` status (not yet marked). Headman and teacher can both see this. |
| **Journal view (GET /groups/{groupId}/subjects/{subjectId}/journal)** | Headman and teacher need the grid: rows = students, columns = lesson dates, cells = status (б/н/у/сп) (JS-TEACHER-03, JS-HEADMAN-18). | MEDIUM | Queries MongoDB `{group_id, semester_id, subject_id}` index for all attendance docs. Calls GetLessonsByGroup gRPC for the full lesson list (including lessons with no attendance docs yet). Merges both: every student × every lesson = one cell. Absent/null cells become gaps. Query filtered by semesterId (required). Excludes cancelled-status docs from display (shows as cancelled, not counted). |
| **Student attendance stats (GET /students/{userId}/attendance/stats)** | Students expect to see their percentage per subject, not just raw data (JS-STUDENT-07). | LOW | MongoDB aggregation on `{user_id, semester_id}` index. Groups by `subject_id`. For each subject: total counted lessons (excluding `cancelled` status), lessons with present/excused/free_attendance status. Returns percentage per subject. No trend charts in v4.0. |
| **Student attendance list (GET /students/{userId}/attendance)** | Student must see their own raw record — what lessons they attended, missed, or were excused from (JS-STUDENT-07). | LOW | Simple query on `{user_id, semester_id}` index with optional `subject_id` filter. Returns list of attendance docs ordered by lesson_date DESC. |
| **gRPC client infrastructure (Schedule + Academic)** | Every feature above requires gRPC calls. Without working gRPC client beans, nothing functions. | MEDIUM | Two gRPC channels: schedule-service:19092 and academic-service:19091 (or configured ports). Stubs for ScheduleGrpcService and AcademicGrpcService. Error mapping: GRPC NOT_FOUND → 404, UNAVAILABLE → 503. Must be separate from gRPC server if Attendance Service exposes one later. |
| **RabbitMQ consumer infrastructure** | The auto-absent and cancelled-lesson flows are event-driven. Without consumer setup, these critical paths do not run. | LOW | Spring AMQP `@RabbitListener` on `rut-uit.events` fanout exchange. Each listener filters by `event_type` field. Three consumers: lesson.started, lesson.closed, lesson.cancelled. Same pattern as Academic Service event consumers. |

---

### Differentiators (Competitive Advantage)

Features that make this attendance system more reliable and trustworthy than basic CRUD attendance.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Geo-blocked lesson enforcement** | Headman can disable geo-checkin for a specific lesson when fraud is suspected (JS-HEADMAN-02). System respects the `is_geo_blocked` flag returned by GetActiveLesson gRPC. | LOW | No extra storage needed. Checkin endpoint reads `is_geo_blocked` from LessonResponse and returns 403 with `reason: geo_checkin_disabled` if true. Headman still marks manually. |
| **Redis deduplication lock for geo-checkin** | Prevents double-submission from network retry or impatient double-tap in the Mini App. | LOW | Redis key `checkin:lock:{lesson_id}:{user_id}` with 5-second TTL. On checkin: SET NX. If key already exists, return 409 `duplicate_checkin`. Already specified in database-schema.md. |
| **Redis rate limiting for geo-checkin** | Prevents abuse (student scripting rapid fake checkin attempts). | LOW | Redis key `rate:checkin:{user_id}` with 60-second TTL, incremented on each attempt. Reject with 429 after 3 attempts/minute. Already specified in database-schema.md. |
| **Denormalized MongoDB documents** | Journal and stats queries run entirely against MongoDB without needing gRPC calls at read time. Fast, independent reports. | MEDIUM | On every write (checkin, manual mark, auto-absent), populate: `semester_id`, `group_id`, `subject_id`, `teacher_id`, `lesson_date`, `lesson_number` from the lesson context obtained via gRPC. This is the critical write-path discipline that enables read-path simplicity. Errors here are expensive to fix later (noted in database-schema.md "expensive to change"). |
| **attendance.marked event publishing** | Notification pipeline (notification-web WebSocket + notification-bot Telegram) receives real-time signal that a student was marked. Student gets confirmation push (JS-SYSTEM-10). | LOW | Publish after successful MongoDB write. Only for `student_geo` and `headman` sources — skip for `auto_scheduler` bulk inserts to avoid flooding. Event schema already in `event-schemas/attendance.marked.json`. Use @TransactionalEventListener(AFTER_COMMIT) pattern from Academic Service. |
| **Idempotent auto-absent** | If `lesson.closed` event is delivered twice (RabbitMQ at-least-once), no duplicate absent records are created. | LOW | MongoDB unique index `{lesson_id, user_id}` rejects the duplicate insert. Catch DuplicateKeyException and log, do not rethrow — this is expected behavior on retry. |
| **Checkin window computed from lesson times** | The 5-minute before/after window is derived from lesson start/end, not stored as a separate field. Clean, no schema drift. | LOW | `checkinWindowStart = start_time - 5 minutes`, `checkinWindowEnd = end_time + 5 minutes`. Computed at request time from LessonResponse. Reject checkin outside this window with 403 `outside_checkin_window`. |

---

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Real-time WebSocket in Attendance Service** | Headman wants live marking board during class | Attendance Service should be stateless. WebSocket connections are stateful. notification-web already exists for this. | Publish `attendance.marked` events; notification-web consumes them and pushes to connected headman clients. |
| **Eager group member validation on every checkin** | Verify student belongs to group before marking | 30 students checking in simultaneously = 30 parallel gRPC calls to Academic Service just for group membership. | Trust X-User-Id and X-User-Role headers from Gateway. Read `group_id` from lesson context. Academic Service already caches `group:{group_id}:members` with 5-min TTL for the actual auto-absent path. |
| **Teacher attendance correction (teacher_override source)** | Teachers want to fix errors they notice | Teacher role is read-only by design (JS-TEACHER-04). Adding write access blurs role boundaries and creates authorization complexity. `AttendanceSource.TEACHER_OVERRIDE` exists in the enum but the flow is not in scope. | Deferred to v4.1+. Headman can correct via manual marking as the authorized attendance manager. |
| **Pagination on journal endpoint** | Seems necessary for large datasets | Journal is bounded: one semester × one subject × one group = at most ~20 lessons × 30 students = 600 MongoDB docs per query. Pagination adds complexity without measurable benefit at this scale. | Return full journal in one response. Revisit if groups exceed 60 students or semester exceeds 30 lessons per subject. |
| **Batch manual marking (all students at once)** | Seems efficient for headman marking whole class | Single-student autosave (one endpoint per click) is the stated UX requirement. Batch endpoint has different transactional semantics and partial-failure handling. | One endpoint per student, as per JS-HEADMAN-01 "autosave per click". If partial batch is needed, implement as loop of single-mark calls on the client side. |
| **Configurable checkin time window** | Seems flexible to allow different windows per subject | Premature generalization. The 5-minute window is a business rule, not a per-lesson configuration. Storing it per-lesson or per-subject adds schema complexity. | Hardcode as a service constant or application.yml property (`attendance.checkin.window-minutes=5`). Can be made configurable via a single property later without schema changes. |
| **Excuse management in v4.0** | Students need to submit excuses immediately | Excuse flow requires file attachments, Telegram forwarding to headman, approval workflow, headman confirmation — too much scope. The `excuse` subdocument is already modeled in MongoDB schema. | Deferred to v4.1 explicitly. The schema already supports it; only the API write path is deferred. Reading `excuse` data is not blocked. |
| **Late checkin ("forgot to mark") in v4.0** | Students who were present but forgot need a way out | Late checkin requires headman approval notification, a pending state, and status update on confirmation. Non-trivial workflow. | Deferred to v4.1. `late_checkin_request` subdocument already in MongoDB schema. `AttendanceSource.LATE_CHECKIN` already in enum. |

---

## Feature Dependencies

```
[gRPC client infrastructure]
    └──required-by──> ALL features (checkin, manual mark, auto-absent, journal)

[RabbitMQ consumer infrastructure]
    └──required-by──> [lesson.closed → auto-absent]
    └──required-by──> [lesson.cancelled → cancel existing docs]
    └──optional-for──> [lesson.started → cache warmup]

[lesson.started consumer]
    └──enables──> [checkin window is open for the group]
    └──caches──> lesson context for denormalization on writes

[geo-checkin endpoint]
    └──requires──> [GetActiveLesson gRPC] (lesson is active, not geo-blocked, group_id)
    └──requires──> [GetCampusGeofence gRPC] (lat, lng, radius_m)
    └──requires──> [Redis dedup lock] (idempotency)
    └──requires──> [Redis rate limiter] (abuse prevention)
    └──writes──> [MongoDB attendance doc] with full denormalized fields
    └──publishes──> [attendance.marked event]

[lesson.closed consumer → auto-absent]
    └──requires──> [GetGroupMembers gRPC] (who was in the group)
    └──requires──> [GetLessonById gRPC] (subject_id, teacher_id, lesson_number for denormalization)
    └──writes──> [MongoDB absent docs] for all unmarked students
    └──does-not-publish──> attendance.marked (bulk, would flood)

[lesson.cancelled consumer]
    └──updates──> [MongoDB attendance docs] status = cancelled WHERE lesson_id = ?

[manual marking endpoint]
    └──requires──> [IsHeadman check] (X-Is-Headman header OR gRPC fallback)
    └──requires──> [GetLessonById gRPC] (validates lesson exists, gets denormalized fields)
    └──writes──> [MongoDB attendance doc] with marked_by=headman
    └──publishes──> [attendance.marked event]

[lesson attendance view]
    └──requires──> [GetGroupMembers gRPC] (full student list)
    └──reads──> [MongoDB: lesson_id index]

[journal view]
    └──requires──> [GetLessonsByGroup gRPC] (full lesson list including lessons with no docs)
    └──reads──> [MongoDB: {group_id, semester_id, subject_id} index]

[student attendance stats]
    └──reads──> [MongoDB: {user_id, semester_id} index]
    └──aggregates──> per subject_id, excluding cancelled status

[attendance.marked event]
    └──consumed-by──> notification-web (WebSocket push to headman/student)
    └──consumed-by──> notification-bot (Telegram confirmation to student)
```

### Dependency Notes

- **Denormalized fields are a write-path discipline, not a schema luxury.** Every MongoDB insert must populate `semester_id`, `group_id`, `subject_id`, `teacher_id`, `lesson_date`, `lesson_number` — even the bulk auto-absent path. Failure to populate these fields at creation time is expensive to fix later (cannot be backfilled without a full replay of Schedule Service data). This is the highest-risk implementation detail.
- **Auto-absent requires both gRPC calls.** The `lesson.closed` event only provides `lesson_id` and `group_id`. GetGroupMembers gives the student list; GetLessonById gives the lesson details for denormalization. Both must succeed before any absent docs are written.
- **lesson.cancelled does not delete docs — it marks them cancelled.** This preserves the historical record. Stats queries must filter `WHERE status != 'cancelled'`. Journal queries show cancelled cells distinctly.
- **Checkin does not validate group membership via gRPC.** Group membership is implicit: the student calls GetActiveLesson for their group (derived from X-User-Id + Academic Service's group assignment). If no active lesson exists for the group, 404. The lesson's `group_id` is used for all downstream writes.
- **journal view is the most complex read path** because it must merge two data sources: MongoDB docs (what was recorded) and GetLessonsByGroup response (what lessons existed, including those with no attendance). This merge is done in application memory for MVP; no special indexing is needed beyond what already exists.

---

## MVP Definition

### Launch With (v4.0)

The absolute minimum for the attendance system to be functional end-to-end.

- [ ] gRPC client setup (Schedule + Academic channels and stubs) — everything else blocks on this
- [ ] RabbitMQ consumer setup (lesson.started, lesson.closed, lesson.cancelled listeners)
- [ ] lesson.closed consumer + auto-absent bulk write — journal completeness
- [ ] lesson.cancelled consumer + status update — stats correctness
- [ ] Geo-checkin endpoint with all validation layers (active lesson, geo-blocked, time window, Haversine, dedup, rate limit)
- [ ] Manual attendance marking endpoint (headman/assistant only)
- [ ] Lesson attendance view (GET by lesson, merged with group members)
- [ ] Journal view (GET by group+subject+semester, merged with lesson list)
- [ ] Student stats endpoint (aggregation by subject, % attended)
- [ ] Student attendance list (GET by student+semester)
- [ ] attendance.marked event publishing (for checkin and manual mark sources only)

### Add After Validation (v4.1)

- [ ] Excuse ticket creation + headman approval flow — requires file attachment pipeline and Telegram forwarding
- [ ] Late checkin request ("byl no zabyl") + headman confirmation
- [ ] Teacher attendance override (teacher_override source) — requires explicit role expansion decision

### Future Consideration (v4.2+)

- [ ] Red zone alerts pushed to admin/headman — depends on threshold config in Academic Service
- [ ] Attendance trend chart (by week) for student view
- [ ] PDF/Excel export — depends on web panel consuming journal API first
- [ ] Bulk attendance correction API for admin

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| gRPC client infrastructure | HIGH | MEDIUM | P1 — everything blocks on this |
| RabbitMQ consumer infrastructure | HIGH | LOW | P1 — auto-absent and cancellation depend on it |
| lesson.closed + auto-absent | HIGH | MEDIUM | P1 — journal completeness without headman effort |
| lesson.cancelled → mark docs | MEDIUM | LOW | P1 — stats correctness |
| Geo-checkin endpoint | HIGH | MEDIUM | P1 — primary student action |
| Manual marking endpoint | HIGH | LOW | P1 — headman core workflow |
| Lesson attendance view (per lesson) | MEDIUM | LOW | P1 — headman live view |
| Journal view (per group+subject) | HIGH | MEDIUM | P1 — teacher + headman reports |
| Student stats endpoint | MEDIUM | LOW | P1 — student self-service |
| Student attendance list | MEDIUM | LOW | P1 — student self-service |
| attendance.marked event publishing | MEDIUM | LOW | P1 — notification pipeline |
| Redis dedup lock | MEDIUM | LOW | P2 — correctness under rapid taps |
| Redis rate limiter | LOW | LOW | P2 — abuse prevention |
| lesson.started consumer (warmup) | LOW | LOW | P2 — reduces gRPC calls during checkin window |
| Excuse management API | HIGH | HIGH | P3 — deferred to v4.1 |
| Late checkin request API | MEDIUM | MEDIUM | P3 — deferred to v4.1 |

**Priority key:**
- P1: Must have for v4.0 launch — no partial attendance service is acceptable
- P2: Correctness and reliability features, add in same or next phase
- P3: Deferred milestone, schema already supports it but API is not needed now

---

## Edge Case Inventory

| Edge Case | Description | Handling Strategy |
|-----------|-------------|-------------------|
| **Checkin before lesson is ACTIVE** | Student checks in 3 minutes before lesson (within 5-min window), but lesson is still PLANNED status. GetActiveLesson returns null (only returns ACTIVE). | Attendance Service must also check if a PLANNED lesson for the group starts within 5 minutes. Either call GetLessonsByGroup with current date + time range, or Schedule Service adds `GetUpcomingLesson` RPC in future. For MVP: only allow checkin when lesson is ACTIVE. Document the 5-min pre-start window as deferred behavior. |
| **Geo-checkin double-tap (rapid retry)** | Student taps checkin button twice within 5 seconds due to slow response. Second request arrives before first completes. | Redis dedup lock `checkin:lock:{lesson_id}:{user_id}` with 5-sec TTL. First request sets the lock. Second request finds lock present, returns 409. MongoDB unique index is the final guarantee. |
| **auto-absent fired twice** | lesson.closed event is redelivered by RabbitMQ (at-least-once delivery). Auto-absent consumer runs again for same lesson. | MongoDB unique index `{lesson_id, user_id}` rejects duplicate inserts. Catch DuplicateKeyException (Spring Data wraps as DuplicateKeyException), log at DEBUG, continue. Do not rethrow. |
| **Student checked in, then lesson.cancelled fires** | Student was geo-present, then headman cancels the lesson mid-class. | lesson.cancelled consumer updates all attendance docs for that lesson to `status = cancelled`. Student's PRESENT record becomes CANCELLED. This is correct behavior — cancelled lessons do not count in stats. Student's PRESENT was valid at the time but the lesson itself was voided. |
| **manual mark on closed lesson** | Headman tries to mark a student after lesson.closed auto-absent already ran. | Allow it. Headman can always override. UPSERT semantics: if doc exists (auto_scheduler absent), update it to headman's value. If doc does not exist (somehow), insert it. Source changes to `headman`. |
| **Student not in group at checkin time** | Student was transferred to another group but still tries to check in. GetActiveLesson returns the active lesson for their current group_id. If they transferred mid-semester, their old lessons may not match. | Lesson's `group_id` determines authorization. If student's current `group_id` (from Academic Service) matches the lesson's `group_id`, checkin is valid. If not, return 403. Student transfer history is Academic Service's concern. |
| **No active semester during auto-absent** | lesson.closed fires but GetActiveSemester returns error. Cannot populate `semester_id` on absent docs. | For auto-absent, `semester_id` must come from either the lesson details (GetLessonById should include semester_id — but current schedule.proto LessonResponse does not include it). Need to call GetActiveSemester separately as fallback, or cache it on lesson.started. |
| **Partial group fetch failure during auto-absent** | GetGroupMembers gRPC fails mid-auto-absent. Some absent docs written, some not. | Do not write partial results. GetGroupMembers must succeed fully before any writes. If it fails, log error and do not process. The absent docs will not be created for that lesson — this is an incomplete journal entry. Monitoring/alert should surface this. |
| **GetActiveSemester not in schedule.proto LessonResponse** | Current `LessonResponse` proto does not include `semester_id`. Denormalization on write requires it. | Must call `GetActiveSemester` (Academic Service gRPC) during write path OR cache the active semester on service startup. The `semester_id` is available from Academic Service. This is a known gap to address in implementation. |

---

## Endpoint Inventory by Role

### STUDENT endpoints (Attendance)

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| POST | /api/attendance/checkin | Geo-checkin with {lat, lng, lessonId} | MEDIUM |
| GET | /api/attendance/my | Own attendance list (?semesterId=&subjectId=) | LOW |
| GET | /api/attendance/my/stats | Own stats by subject (% attended) | LOW |

### HEADMAN / ASSISTANT endpoints (Attendance)

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| PUT | /api/attendance/lessons/{lessonId}/students/{userId} | Mark single student (body: status) | MEDIUM |
| GET | /api/attendance/lessons/{lessonId} | Lesson attendance state (all students) | LOW |

### HEADMAN / TEACHER endpoints (Reports)

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| GET | /api/attendance/journal | Journal view (?groupId=&subjectId=&semesterId=) | MEDIUM |
| GET | /api/attendance/students/{userId}/stats | Student stats for headman/teacher view | LOW |

---

## Sources

- `docs/database-schema.md` — MongoDB attendance document model, Redis key patterns, indices, denormalized fields (HIGH confidence — primary source)
- `docs/job-stories.md` — JS-HEADMAN-01..07, JS-STUDENT-01..08, JS-SYSTEM-05..10 — all attendance business rules (HIGH confidence — authoritative spec)
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json`, `attendance.marked.json` — RabbitMQ event payload contracts (HIGH confidence — contract files)
- `proto/schedule.proto` — GetActiveLesson, GetLessonById, GetLessonsByGroup RPC signatures and LessonResponse shape (HIGH confidence — contract file)
- `proto/academic.proto` — GetGroupMembers, GetCampusGeofence, IsHeadman, GetActiveSemester RPC signatures (HIGH confidence — contract file)
- `.planning/PROJECT.md` — v4.0 milestone scope definition, deferred features list, Out of Scope list (HIGH confidence — project charter)
- `CLAUDE.md` — AttendanceStatus enum values, domain package isolation rules (checkin/ vs report/ vs shared/port/), @RequireRole AOP, @TransactionalEventListener(AFTER_COMMIT) pattern (HIGH confidence — project standards)
- `services/attendance-service/attendance-api-contract/` — existing enum definitions (AttendanceStatus, AttendanceSource, ExcuseType) (HIGH confidence — already in codebase)

---

*Feature research for: Attendance Service MVP (geo-checkin, manual marking, auto-absent, basic reports)*
*Researched: 2026-04-04*
