# Phase 16: Event Consumers - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Attendance Service reacts to lesson lifecycle events from Schedule Service via RabbitMQ. Auto-absent unmarked students on `lesson.closed`, propagate cancellation on `lesson.cancelled`, refresh semester cache on `semester.archived`. DLQ protects against silent event loss. No REST endpoints, no user-facing features -- pure event-driven backend logic.

</domain>

<decisions>
## Implementation Decisions

### Auto-Absent Strategy (lesson.closed)
- **D-01:** Call `AcademicGrpcClient.getGroupMembers(groupId)` to get the full student list for the group. Call `ScheduleGrpcClient.getLessonById(lessonId)` to get `lesson_number`, `lesson_date`, `subject_id` for denormalization. `semester_id` from `SemesterCacheService`.
- **D-02:** Use MongoDB `bulkWrite` with `UpdateOneModel` per student, `upsert=true`, `$setOnInsert` for all fields. Single round-trip, atomic batch, race-safe. If a student already has a checkin, `$setOnInsert` is a no-op for that student -- existing status preserved (MARK-04).
- **D-03:** Auto-absent docs get `status=ABSENT`, `source=AUTO_SCHEDULER`, `marked_by=null`, `created_at=now`, `updated_at=now`.

### Cancellation Semantics (lesson.cancelled)
- **D-04:** Update existing docs only -- `updateMany({lesson_id: X}, {$set: {status: "cancelled", updated_at: now}})`. Students with no attendance record for that lesson simply have no record. Matches MARK-05 wording exactly.
- **D-05:** Cancellation overwrites any current status (including absent from auto-absent). If `lesson.closed` fired first (auto-absent ran), then `lesson.cancelled` arrives later, those docs become `cancelled`. This is correct because cancelled lessons are excluded from all statistics.

### Error Handling & Retry
- **D-06:** On gRPC failure during event processing, let the exception propagate. Spring AMQP nacks the message, RabbitMQ routes it to DLQ (infrastructure from Phase 15). No retry logic -- auto-absent is not time-critical. Manual intervention via DLQ dashboard.
- **D-07:** All event handlers are naturally idempotent. Auto-absent uses `$setOnInsert` (replaying won't overwrite). Cancellation uses `updateMany` (idempotent). Semester refresh is idempotent. No event_id dedup tracking needed -- safe to replay from DLQ.

### lesson.started Handling
- **D-08:** No-op with debug log. Attendance Service does not act on `lesson.started` -- notification services handle start reminders. Keep the stub case in the switch for future extensibility.

### semester.archived Handling
- **D-09:** Wire `EventConsumer.handleSemesterArchived()` to call `SemesterCacheService.refresh()`. Already built in Phase 15 -- just needs the one-liner connection.

### Claude's Discretion
- Whether to extract event handler logic into a separate service class or keep in EventConsumer
- MongoTemplate vs. BulkOperations API for the bulkWrite
- Exact error logging format and DLQ message enrichment
- Test structure: how many integration tests, which scenarios to cover

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Schemas
- `event-schemas/lesson.closed.json` -- Payload: `{lesson_id, group_id, subject_id}`
- `event-schemas/lesson.cancelled.json` -- Payload: `{lesson_id, group_id, subject_id, date, cancel_reason}`
- `event-schemas/semester.archived.json` -- Semester archived event schema

### Proto Contracts (gRPC calls needed)
- `proto/schedule.proto` -- `LessonResponse` fields: id, group_id, subject_id, date, lesson_number, status
- `proto/academic.proto` -- `GroupMembersResponse` with `StudentInfo` (user_id, display_name, is_headman)

### Existing Code (Phase 15 output)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java` -- Stub handlers to fill in
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/RabbitConfig.java` -- DLQ infrastructure already declared
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java` -- GetLessonById for denormalization data
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java` -- GetGroupMembers for student list
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/semester/SemesterCacheService.java` -- semester_id cache, needs wiring
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceDocument.java` -- MongoDB entity with all denormalized fields
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java` -- MongoRepository base

### Architecture & Conventions
- `CLAUDE.md` -- Coding rules, enum conventions, package structure
- `docs/database-schema.md` -- attendance_db MongoDB schema
- `.planning/phases/15-infrastructure-foundation/15-CONTEXT.md` -- Phase 15 decisions (D-01 through D-13)

### Requirements
- `.planning/REQUIREMENTS.md` -- MARK-03, MARK-04, MARK-05

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EventConsumer.java` -- Already has the `@RabbitListener` and switch-case routing. Stubs just need implementation.
- `ScheduleGrpcClient.getLessonById()` -- Returns full `LessonResponse` proto with all denormalization fields
- `AcademicGrpcClient.getGroupMembers()` -- Returns `GroupMembersResponse` with student user_ids
- `SemesterCacheService.getActiveSemesterId()` -- Cached semester_id, `refresh()` method ready
- `AttendanceDocument` -- Full entity with all 12 fields, `@Builder` for construction
- `MongoConfig` -- Indexes already created (unique on lesson_id+user_id handles idempotency)

### Established Patterns
- gRPC: 3-second deadline, `StatusRuntimeException` catch -> domain exception
- RabbitMQ: Jackson2JsonMessageConverter, generic `Map<String, Object>` envelope
- MongoDB: `MongoTemplate` for programmatic operations (already injected in `MongoConfig`)
- Testing: Testcontainers (MongoDB + RabbitMQ), `@MockitoBean` for gRPC clients in integration tests

### Integration Points
- `EventConsumer` stub methods -> new service class (or inline logic)
- `MongoTemplate` / `BulkOperations` for bulkWrite operations
- `AttendanceRepository` for simple queries (findByLessonId for cancellation updateMany)

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- standard event processing patterns following established codebase conventions.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope.

</deferred>

---

*Phase: 16-event-consumers*
*Context gathered: 2026-04-04*
