---
phase: 17-write-path-geo-checkin-manual-marking
plan: 03
subsystem: api
tags: [mongodb, spring-boot, grpc, rabbitmq, hateoas, testcontainers, mockito]

requires:
  - phase: 17-01-PLAN
    provides: MarkingApi contract, MarkRequest/MarkResponse DTOs, AttendanceDocument, AttendanceEventPublisher, AcademicGrpcClient, ScheduleGrpcClient, RequestContext

provides:
  - MarkingService: headman authorization, group membership check, MongoTemplate upsert, event publishing
  - MarkingController: implements MarkingApi, RequireRole(STUDENT), HATEOAS EntityModel<MarkResponse>
  - MarkingServiceTest: 7 unit tests covering all authorization branches and happy path
  - MarkingIntegrationTest: 8 integration tests via MockMvc + MongoDB Testcontainer

affects:
  - phase-18-read-path-reports (reads attendances collection written here)

tech-stack:
  added: []
  patterns:
    - "MongoTemplate upsert with $set/$setOnInsert for idempotent marking — mutable fields always overwritten, immutable only on insert"
    - "@MockitoSpyBean AttendanceEventPublisher for event verification in integration tests"
    - "@RequireRole(STUDENT) on controller — headman IS a student, headman-specific check in service layer"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingController.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIntegrationTest.java
  modified: []

key-decisions:
  - "@RequireRole(STUDENT) guards the marking endpoint — headman check (isHeadman()) is done inside MarkingService because headman is a student role with is_headman=true flag, not a separate role"
  - "CANCELLED status is statically excluded from ALLOWED_STATUSES set — fails fast before any gRPC calls"
  - "@MockitoSpyBean used for AttendanceEventPublisher in integration tests — allows verifying publishMarked() was called while still using the real implementation for other beans"

patterns-established:
  - "Status validation before any I/O: check ALLOWED_STATUSES first, then headman check, then gRPC calls — fail fast order"
  - "Upsert then findOne read-back pattern: upsert does not return the doc, so findOne is called after to get the persisted state for event publishing and response"

requirements-completed: [MARK-01, MARK-02, INFRA-06]

duration: 6min
completed: 2026-04-04
---

# Phase 17 Plan 03: Manual Marking Write Path Summary

**Headman manual attendance marking via MongoTemplate $set/$setOnInsert upsert with group membership authorization and RabbitMQ event publishing**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-04T11:13:24Z
- **Completed:** 2026-04-04T11:19:24Z
- **Tasks:** 2
- **Files modified:** 4 created

## Accomplishments

- MarkingService orchestrates 5-step authorization pipeline: status validation, headman check, lesson group match, student membership check, then upsert
- MongoTemplate upsert with $set/$setOnInsert preserves immutable fields (group_id, subject_id, semester_id, lesson_date) on second mark
- MarkingController implements MarkingApi, returns HATEOAS EntityModel<MarkResponse> with self link
- 7 unit tests prove each authorization branch (not headman, wrong group, student not in group, CANCELLED rejected, happy path, event published, upsert idempotency)
- 8 integration tests via MockMvc + real MongoDB Testcontainer prove MARK-01, MARK-02, INFRA-06

## Task Commits

Each task was committed atomically:

1. **Task 1: MarkingService + MarkingController + MarkingServiceTest** - `0bde841` (feat)
2. **Task 2: MarkingIntegrationTest** - `47c593c` (feat)

## Files Created/Modified

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java` - Headman authorization, MongoTemplate upsert, event publishing
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingController.java` - REST controller implementing MarkingApi, RequireRole(STUDENT), HATEOAS response
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java` - 7 unit tests (Mockito, all auth branches)
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIntegrationTest.java` - 8 integration tests (MockMvc, MongoDB Testcontainer)

## Decisions Made

- `@RequireRole(STUDENT)` on controller: headman is a student with is_headman=true, not a separate role. Headman-specific check lives in MarkingService.
- CANCELLED status is excluded from `ALLOWED_STATUSES` set — validation fails fast before any gRPC calls to external services.
- `@MockitoSpyBean` for AttendanceEventPublisher in integration tests — wraps the real bean to allow Mockito verify() calls without mocking the actual behavior.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle binary output file lock when running both test classes together with `--tests "*MarkingServiceTest" --tests "*MarkingIntegrationTest"` — caused by another process holding the test result binary file. Both test classes pass when run individually. This is an OS-level file locking issue on Windows, not a test failure.

## Known Stubs

None - all data flows are wired end-to-end.

## Next Phase Readiness

- Manual marking write path complete (MARK-01, MARK-02, INFRA-06)
- attendance collection has documents written by headman marking with source=HEADMAN
- Phase 18 (Read Path — Reports) can now query the attendances collection for journal and stats

---
*Phase: 17-write-path-geo-checkin-manual-marking*
*Completed: 2026-04-04*
