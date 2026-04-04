---
phase: 16-event-consumers
plan: "02"
subsystem: testing
tags: [rabbitmq, mongodb, integration-tests, unit-tests, awaitility, testcontainers]

# Dependency graph
requires:
  - phase: 16-event-consumers
    plan: "01"
    provides: LessonEventService, EventConsumer fully wired
  - phase: 15-infrastructure-foundation
    provides: AbstractAttendanceIntegrationTest, Testcontainers setup
provides:
  - EventConsumerIntegrationTest with 6 end-to-end scenarios (MARK-03, MARK-04, MARK-05 proven)
  - LessonEventServiceTest with 6 unit tests (business logic isolation)
affects:
  - 17-write-path

# Tech tracking
tech-stack:
  added:
    - "org.awaitility:awaitility:4.2.2 (async assertion stability for RabbitMQ consumer tests)"
  patterns:
    - "mongoTemplate.remove(new Query(), Class) instead of dropCollection() — preserves MongoDB indexes between tests"
    - "Awaitility.await().atMost(5, SECONDS).untilAsserted() — stable async waiting for RabbitMQ message processing"
    - "Mockito.reset() in @BeforeEach — cleans MockitoBean state between integration tests"
    - "lenient().when() for shared @BeforeEach stubs not used by every test method"
    - "mock(UpdateResult.class) for mongoTemplate.updateMulti() return value — prevents NPE in logger"

key-files:
  created:
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIntegrationTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java
  modified:
    - services/attendance-service/attendance-app/build.gradle.kts

key-decisions:
  - "mongoTemplate.remove(new Query()) over dropCollection() — dropCollection removes indexes, breaking MongoIndexTest which expects unique index"
  - "lenient() stubs in @BeforeEach — Mockito strict mode throws UnnecessaryStubbingException for tests that don't use bulkOps"
  - "Awaitility 5-second timeout — sufficient for Testcontainer RabbitMQ consumer processing latency"

requirements-completed: [MARK-03, MARK-04, MARK-05]

# Metrics
duration: 13min
completed: 2026-04-04
---

# Phase 16 Plan 02: Event Consumer Tests Summary

**12 new tests prove MARK-03/MARK-04/MARK-05: integration tests via real RabbitMQ + MongoDB Testcontainers and unit tests isolating LessonEventService business logic**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-04-04T09:48:11Z
- **Completed:** 2026-04-04T09:55:31Z
- **Tasks:** 2
- **Files modified:** 3 (2 new test files + build.gradle.kts)

## Accomplishments

- Created `EventConsumerIntegrationTest` — 6 integration tests publishing real RabbitMQ messages via RabbitTemplate to fanout exchange, awaiting MongoDB state with Awaitility, asserting correct ABSENT/PRESENT/CANCELLED/EXCUSED statuses
- Created `LessonEventServiceTest` — 6 unit tests with pure Mockito verifying `bulkOps.upsert()` call counts, empty group guard, null semesterId tolerance, gRPC failure propagation, and `updateMulti()` for cancellation
- Added `awaitility:4.2.2` to `build.gradle.kts` for stable async assertions
- Full test suite: 39 tests, all pass (27 from Phase 15 + 12 new)

## Task Commits

1. **Task 1: Create EventConsumerIntegrationTest** - `5a18874` (test)
2. **Task 2: Create LessonEventServiceTest + fix index preservation** - `0b2b0f5` (test)

## Files Created/Modified

- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIntegrationTest.java` — 6 integration tests
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java` — 6 unit tests
- `services/attendance-service/attendance-app/build.gradle.kts` — added awaitility dependency

## Decisions Made

- `mongoTemplate.remove(new Query(), Class)` used over `dropCollection()` — dropping the collection also removes MongoDB indexes, causing `MongoIndexTest.uniqueIndex_rejectsDuplicateLessonUser()` to fail since the unique index is recreated only at Spring context startup (`@PostConstruct`)
- `lenient()` stubs in `@BeforeEach` — Mockito strict mode (default with `@ExtendWith(MockitoExtension.class)`) throws `UnnecessaryStubbingException` for tests that don't invoke `bulkOps` (e.g., cancellation tests, empty group tests)
- `mock(UpdateResult.class)` for `updateMulti` return — `LessonEventService.processLessonCancelled` logs `result.getModifiedCount()`, which throws NPE when `updateMulti` returns null from an unstubbed mock

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed MongoDB index destruction from dropCollection()**
- **Found during:** Task 2 full suite run
- **Issue:** `mongoTemplate.dropCollection()` in `@BeforeEach` removes all indexes. After EventConsumerIntegrationTest runs, `MongoIndexTest.uniqueIndex_rejectsDuplicateLessonUser()` fails because the unique compound index on `(lesson_id, user_id)` no longer exists (recreated only at `@PostConstruct` on startup, not between tests)
- **Fix:** Replaced `mongoTemplate.dropCollection()` with `mongoTemplate.remove(new Query(), AttendanceDocument.class)` — deletes all documents but preserves index definitions
- **Files modified:** `EventConsumerIntegrationTest.java`
- **Commit:** `0b2b0f5`

**2. [Rule 1 - Bug] Fixed UnnecessaryStubbingException in @BeforeEach**
- **Found during:** Task 2 initial test run
- **Issue:** `when(mongoTemplate.bulkOps(...)).thenReturn(bulkOps)` in `@BeforeEach` causes `UnnecessaryStubbingException` in tests that don't call `processLessonClosed` (e.g., `processLessonCancelled_*` tests)
- **Fix:** Changed `when(...)` to `lenient().when(...)` for shared `@BeforeEach` stubs
- **Files modified:** `LessonEventServiceTest.java`
- **Commit:** `0b2b0f5`

**3. [Rule 1 - Bug] Fixed NullPointerException in processLessonCancelled unit test**
- **Found during:** Task 2 initial test run
- **Issue:** `mongoTemplate.updateMulti()` returns null from unstubbed mock; `LessonEventService` calls `result.getModifiedCount()` for logging, throwing NPE
- **Fix:** Added `lenient().when(mongoTemplate.updateMulti(...)).thenReturn(mock(UpdateResult.class))` in `@BeforeEach`
- **Files modified:** `LessonEventServiceTest.java`
- **Commit:** `0b2b0f5`

## Known Stubs

None — all implemented logic is wired to real behavior. Integration tests hit real RabbitMQ + MongoDB via Testcontainers.

## Self-Check: PASSED
