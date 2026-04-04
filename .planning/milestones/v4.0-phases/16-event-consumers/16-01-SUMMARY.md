---
phase: 16-event-consumers
plan: "01"
subsystem: messaging
tags: [rabbitmq, mongodb, grpc, bulk-operations, event-consumer]

# Dependency graph
requires:
  - phase: 15-infrastructure-foundation
    provides: EventConsumer skeleton, ScheduleGrpcClient, AcademicGrpcClient, SemesterCacheService, AttendanceDocument, enum converters
provides:
  - LessonEventService with processLessonClosed (bulk upsert $setOnInsert) and processLessonCancelled (updateMulti)
  - EventConsumer fully wired to LessonEventService and SemesterCacheService — no stubs remaining
affects:
  - 17-write-path
  - 18-read-path

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BulkOperations.UNORDERED for multi-student upsert — does not stop on first duplicate"
    - "$setOnInsert via Update.setOnInsert() — existing docs untouched, new docs get ABSENT/AUTO_SCHEDULER"
    - "((Number) value).longValue() for safe Jackson Integer-to-Long extraction from Map<String,Object>"
    - "No try/catch in event service — exceptions propagate to Spring AMQP, which nacks and routes to DLQ"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java

key-decisions:
  - "BulkMode.UNORDERED chosen over ORDERED — one student error must not block rest of group"
  - "No @Transactional on LessonEventService — MongoDB bulkOps and RabbitMQ do not share a transaction manager"
  - "No exception catching in LessonEventService — propagation is intentional so AMQP nacks to DLQ"

patterns-established:
  - "Event service pattern: gRPC calls → guard empty list → bulk MongoDB op → log count"
  - "Safe numeric extraction: ((Number) value).longValue() for all Map<String,Object> event payloads"

requirements-completed: [MARK-03, MARK-04, MARK-05]

# Metrics
duration: 3min
completed: 2026-04-04
---

# Phase 16 Plan 01: Event Consumers Summary

**RabbitMQ event consumers fully wired: bulk upsert $setOnInsert auto-absent on lesson.closed and updateMulti cancellation on lesson.cancelled, with safe Integer-to-Long extraction**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-04-04T09:42:53Z
- **Completed:** 2026-04-04T09:44:50Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Created `LessonEventService` with `processLessonClosed` using `BulkOperations.UNORDERED` + `$setOnInsert` — existing PRESENT records are untouched, new records get `status=ABSENT, source=AUTO_SCHEDULER`
- Created `LessonEventService.processLessonCancelled` using `mongoTemplate.updateMulti` to set `status=CANCELLED` on all attendance docs for the lesson
- Replaced all Phase 15 stubs in `EventConsumer` with real delegation to `LessonEventService` and `SemesterCacheService`, using `((Number) value).longValue()` for safe payload extraction

## Task Commits

1. **Task 1: Create LessonEventService with auto-absent and cancellation logic** - `a1abd54` (feat)
2. **Task 2: Wire EventConsumer to delegate to LessonEventService and SemesterCacheService** - `9c71810` (feat)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java` - New service with auto-absent bulk upsert and cancellation updateMulti
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java` - Stubs replaced with real delegation; @Slf4j, @RequiredArgsConstructor added

## Decisions Made

- `BulkMode.UNORDERED` selected — if one student's upsert fails it must not block the remaining students from being auto-absented
- No `@Transactional` on `LessonEventService` — MongoDB and RabbitMQ have no shared transaction manager; adding it would be misleading
- No try/catch in `LessonEventService` — intentional propagation so Spring AMQP nacks the message to DLQ on gRPC failure

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Event consumer business logic complete — lesson lifecycle events (started/closed/cancelled/semester.archived) all handled
- Ready for Phase 17 (Write Path): geo-checkin and manual marking will write to the same `attendances` collection that event consumers maintain
- No blockers

---
*Phase: 16-event-consumers*
*Completed: 2026-04-04*
