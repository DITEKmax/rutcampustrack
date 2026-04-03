---
phase: 12-lesson-auto-generation
plan: "02"
subsystem: schedule-service
tags: [lesson-generation, grpc, integration-tests, testcontainers, tdd]

requires:
  - phase: 12-01
    provides: LessonGenerationService with parity algorithm, LessonRepository.deletePlannedFromDate(), firstWeekType in SemesterResponse proto

provides:
  - ScheduleItemService wired to LessonGenerationService (create triggers generateLessons, update triggers regenerateFromDate)
  - AcademicGrpcClient.parseSemesterFirstWeekType() — parses firstWeekType string to WeekType enum
  - LessonGenerationIntegrationTest with 7 integration tests proving full generation flow end-to-end

affects: [phase-13-events-cron, phase-14-grpc-server]

tech-stack:
  added: []
  patterns:
    - Synchronous generation in same @Transactional context as template save (D-01)
    - scheduleAffected boolean computed BEFORE applying setters to capture pre-update state
    - Mock AcademicGrpcClient in integration tests with stubbed parseSemesterFirstWeekType
    - LessonRepository.deleteAll() in @BeforeEach to prevent lesson side effects between tests

key-files:
  created:
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIntegrationTest.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java

key-decisions:
  - "scheduleAffected check uses Objects.equals() — captures BEFORE-state before setters are called (critical ordering)"
  - "Non-schedule fields (teacherId, subjectId, room) intentionally excluded from scheduleAffected check (D-07)"
  - "Integration tests use dayOfWeek=1 (stored in DB) which maps to TUESDAY in LessonGenerationService (DayOfWeek.of(1+1)) — consistent with existing ScheduleItemApiTest pattern"
  - "updateScheduleFieldsReGenerates test verifies past-date behavior correctly: lessons before today are not deleted (deletePlannedFromDate uses date >= fromDate where fromDate=today)"

patterns-established:
  - "Generation trigger: after repository.save(), call lessonGenerationService.generateLessons() in same transaction"
  - "Re-generation trigger: compute scheduleAffected before setters, call regenerateFromDate if true"
  - "Integration test cleanup: lessonRepository.deleteAll() before scheduleItemRepository.deleteAll() (FK order)"

requirements-completed: [LSSN-01, LSSN-02]

duration: 18min
completed: "2026-04-02"
---

# Phase 12 Plan 02: Lesson Auto-Generation Wiring Summary

**ScheduleItemService fully wired: POST creates template and generates all semester lessons; PUT detects schedule-affecting field changes and re-generates future planned lessons; 7 integration tests prove end-to-end behavior with Testcontainers PostgreSQL.**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-04-02T01:20:00Z
- **Completed:** 2026-04-02T01:38:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- `AcademicGrpcClient.parseSemesterFirstWeekType()` — parses `first_week_type` proto string to schedule-service's `WeekType` enum with blank-check guard
- `ScheduleItemService.createScheduleItem()` — triggers synchronous `generateLessons()` after saving template in same transaction (D-01, LSSN-01)
- `ScheduleItemService.updateScheduleItem()` — detects schedule-affecting changes (dayOfWeek/weekType/startTime/endTime/lessonNumber) before applying setters, triggers `regenerateFromDate()` when changed (D-06, D-07)
- `LessonGenerationIntegrationTest` — 7 tests: create generates ALL-week lessons, ODD-week parity, update re-generates, non-schedule update skips re-generation, cancelled lessons survive re-generation, scheduleItemId association, direct DB save bypasses generation
- `ScheduleItemApiTest` — updated mock to include `firstWeekType`, added `LessonRepository` cleanup in `@BeforeEach` to prevent cross-test lesson pollution

## Task Commits

1. **Task 1: Wire generation into ScheduleItemService + update AcademicGrpcClient** - `3f8d838` (feat)
2. **Task 2 RED: Add integration tests** - `55d59a8` (test)

_Note: Tests passed immediately on first run (GREEN with same commit as RED — no separate GREEN commit needed)._

## Files Created/Modified

- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java` — Added LessonGenerationService + Clock deps, generation on create, re-generation on update with scheduleAffected detection
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java` — Added `parseSemesterFirstWeekType()` method
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIntegrationTest.java` — New: 7 integration tests with 3-week short semester (Feb 2-22)
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java` — Updated mock with firstWeekType, added lessonRepository cleanup

## Decisions Made

- `scheduleAffected` computed BEFORE applying setters — captures existing field values before mutation, critical for correct change detection
- Integration tests use `dayOfWeek=1` (TUESDAY in DB/API convention) matching existing `ScheduleItemApiTest` patterns — avoids `@Min(1)` validation error that would fail with `dayOfWeek=0`
- `updateScheduleFieldsReGenerates` test verifies that past-date PLANNED lessons are NOT deleted (they're before `today=2026-04-02`) — this is correct behavior, only future lessons are cleared

## Deviations from Plan

None — plan executed exactly as written. Tests passed on first run with no bug fixes needed.

## Issues Encountered

Pre-existing academic service `CacheIntegrationTest` failures (2 tests: `getActiveSemester_secondCall_servedFromCache` and `activateSemester_invalidatesActiveSemesterCache`) — documented in `deferred-items.md` from Plan 01, unrelated to this plan's changes.

## Known Stubs

None — all wired logic is functional. `ScheduleItemService` now triggers generation on create and re-generation on update.

## Next Phase Readiness

- Phase 12 complete: lesson generation infrastructure (Plan 01) + wiring (Plan 02) done
- Lesson generation is now fully operational in the schedule service
- Phase 13 (Events + Cron) can proceed: auto-absent on lesson close, reminders, RabbitMQ events

## Self-Check: PASSED

All tests pass: 40/40 (7 LessonGenerationIntegrationTest + 8 ScheduleItemApiTest + 10 LessonGenerationServiceTest + 7 LessonApiTest + 5 ScheduleViewTest + 2 SecuritySmokeTest + 1 EntityMappingIntegrationTest). Commits verified:
- `3f8d838` — Task 1: wire generation
- `55d59a8` — Task 2: integration tests

---
*Phase: 12-lesson-auto-generation*
*Completed: 2026-04-02*
