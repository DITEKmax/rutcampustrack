---
phase: 17-write-path-geo-checkin-manual-marking
plan: "02"
subsystem: attendance-service
tags: [geo-checkin, rate-limit, geofence, mongodb, redis, rabbitmq, hateoas, tdd, integration-tests]
dependency_graph:
  requires:
    - phase: 17-01
      provides: CheckinApi, CheckinRequest, CheckinResponse, GeofenceService, CheckinRateLimiter, AttendanceEventPublisher, exceptions
  provides:
    - attendance-app:CheckinService
    - attendance-app:CheckinController
    - attendance-app:CheckinServiceTest (7 unit tests)
    - attendance-app:CheckinIntegrationTest (8 integration tests)
  affects:
    - phase-18-read-path-reports (CheckinService writes documents that reports read)
tech_stack:
  added: []
  patterns:
    - CheckinService orchestration order: rate-limit -> lesson -> time-window -> geo-block -> geofence -> dedup -> save -> publish
    - Integration test pattern: MockitoBean GeofenceService + pre-set Redis keys for rate/dedup testing
    - HATEOAS controller: EntityModel.of + withSelfRel + ResponseEntity.status(CREATED)
    - Test queue declared in @BeforeEach for RabbitMQ event verification in integration tests
key_files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinController.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/checkin/CheckinServiceTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/CheckinIntegrationTest.java
  modified: []
key_decisions:
  - "@RequireRole is method-level only (ElementType.METHOD) — placed on checkin() method, not class level"
  - "Test queue declared/purged in @BeforeEach (not static) for event verification — avoids stale messages from previous tests"
  - "Integration test rate-limit scenario uses pre-set Redis key at count=3 (max), then sends request to increment to 4 (exceeds limit)"
  - "Duplicate test flushes Redis dedup after first successful checkin to force second attempt through to MongoDB unique index"
requirements-completed: [CHKN-01, CHKN-02, CHKN-03, CHKN-04, CHKN-05, CHKN-06, CHKN-07]
duration: 12min
completed: "2026-04-04"
---

# Phase 17 Plan 02: CheckinService + Controller + Integration Tests Summary

**Geo-checkin write path with 7-step orchestration (rate-limit -> lesson -> time-window -> geo-block -> geofence -> Redis dedup -> MongoDB save -> RabbitMQ publish), CheckinController returning 201 with HATEOAS EntityModel, 7 unit tests, and 8 integration tests covering all CHKN-01..07 + INFRA-06.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-04T11:08:00Z
- **Completed:** 2026-04-04T11:20:04Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- CheckinService orchestrates all CHKN requirements in correct order with clear exception semantics
- CheckinController implements CheckinApi interface, returns 201 Created with EntityModel + self link
- 7 unit tests (CheckinServiceTest) verify each validation branch in isolation using Mockito
- 8 integration tests (CheckinIntegrationTest) verify full HTTP path with Testcontainers (MongoDB, Redis, RabbitMQ)

## Task Commits

Each task was committed atomically:

1. **Task 1: CheckinService + CheckinController + CheckinServiceTest** - `138b130` (feat)
2. **Task 2: CheckinIntegrationTest** - `088a108` (test)

_Note: Task 1 used TDD pattern (RED compile fail -> GREEN implementation)_

## Files Created/Modified

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinService.java` - Orchestration service with 8-step checkin flow
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinController.java` - REST controller implementing CheckinApi
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/checkin/CheckinServiceTest.java` - 7 unit tests with Mockito
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/CheckinIntegrationTest.java` - 8 integration tests with Testcontainers

## Decisions Made

- `@RequireRole` annotation targets `ElementType.METHOD` only — placed on the `checkin()` method, not the class. Plan suggested class-level but the annotation definition doesn't support it.
- Test queue for INFRA-06 event verification declared and purged in `@BeforeEach` rather than once statically. This prevents message contamination between tests.
- Rate limit integration test pre-sets Redis key to value "3" (at limit), then the actual request increments to 4 (exceeds), which triggers the 429.
- Duplicate checkin test flushes Redis dedup keys after first request so the second request bypasses Redis dedup and reaches MongoDB unique index for 409.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] @RequireRole annotation only supports ElementType.METHOD**
- **Found during:** Task 1 (CheckinController implementation)
- **Issue:** Plan specified `@RequireRole(UserRole.STUDENT)` at class level, but the `@RequireRole` annotation is defined with `@Target(ElementType.METHOD)` — Java would reject it at compile time on a class.
- **Fix:** Placed `@RequireRole(UserRole.STUDENT)` on the `checkin()` method instead of the class.
- **Files modified:** CheckinController.java
- **Verification:** Compilation passes, SecuritySmokeTest still passes via the method-level annotation.
- **Committed in:** 138b130 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Non-invasive fix — method-level @RequireRole has identical runtime behavior to class-level for a single-method controller. No scope creep.

## Issues Encountered

- File lock error on `build/test-results/test/binary` when running both test classes together in one Gradle invocation. Resolved by deleting the binary directory before re-running.

## Known Stubs

None — all implementations are complete functional code with no placeholders.

## Next Phase Readiness

- CheckinService and CheckinController are production-ready
- 7 unit tests + 8 integration tests all passing
- CHKN-01..07 and INFRA-06 requirements fully verified
- Ready for Phase 17 Plan 03: Manual Marking (MarkingController wrapping MarkingService)

## Self-Check: PASSED
