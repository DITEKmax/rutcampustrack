---
phase: 12-lesson-auto-generation
plan: "01"
subsystem: academic-service + schedule-service
tags: [grpc, proto, flyway, lesson-generation, parity-algorithm]
dependency_graph:
  requires: [phase-11 schedule-service REST API + gRPC client]
  provides: [first_week_type in gRPC SemesterResponse, LessonGenerationService with parity algorithm]
  affects: [schedule-service lesson auto-generation (plan 02)]
tech_stack:
  added: []
  patterns:
    - Semester-anchored week parity (anchor = previousOrSame(MONDAY) of semester start)
    - ChronoUnit.WEEKS.between for zero-based week index computation
    - Plain String for firstWeekType in Academic entity (avoids cross-service enum coupling)
    - JPA default value ("odd") on entity field to prevent null violations in tests
    - status::text cast in native JPA queries (established pattern from phase 11)
key_files:
  created:
    - proto/academic.proto (added field 5 to SemesterResponse)
    - services/academic-service/academic-app/src/main/resources/db/migration/V6__add_semester_first_week_type.sql
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonGenerationService.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonGenerationServiceTest.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
decisions:
  - "Store firstWeekType as String (not enum) in Semester entity — avoids cross-service enum coupling, Schedule Service parses to its own WeekType"
  - "Add implicit CAST (varchar AS week_type) in V6 migration — same pattern as V5 for JPA varchar binding"
  - "Java-level default firstWeekType = 'odd' in Semester entity — prevents null violation when tests create Semester without setting the field"
  - "Test data uses Feb 2 (Monday) as semester start — plan's Feb 3 was incorrect (Feb 3, 2026 = Tuesday)"
metrics:
  duration_minutes: 13
  completed_date: "2026-04-01"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 4
  tests_added: 10
---

# Phase 12 Plan 01: Lesson Auto-Generation Foundation Summary

Week-parity algorithm in LessonGenerationService with semester-anchored ODD/EVEN computation, plus firstWeekType field propagated from Academic DB through proto/gRPC to Schedule Service.

## What Was Built

### Task 1: Academic Service — firstWeekType in SemesterResponse

- **Proto**: Added `string first_week_type = 5` to `SemesterResponse` in `proto/academic.proto`
- **V6 migration**: Created `week_type` enum ('odd','even') in academic_db, added implicit varchar cast, added `first_week_type week_type NOT NULL DEFAULT 'odd'` column to semesters table
- **Semester entity**: Added `String firstWeekType = "odd"` field (Java default prevents null violations when existing tests create Semester objects)
- **AcademicGrpcServiceImpl**: Returns `firstWeekType` in `getActiveSemester()` with null-safe fallback to "odd"
- **AcademicGrpcIntegrationTest**: Added assertion for `getFirstWeekType() == "odd"` on existing test

### Task 2: LessonGenerationService + LessonRepository

- **LessonRepository**: Added `deletePlannedFromDate()` with `@Modifying` native query using `status::text = 'planned'` cast
- **LessonGenerationService**: New `@Service` with three public methods:
  - `computeLessonDates()` — pure date iteration with semester-anchored week parity
  - `generateLessons()` — builds and persists Lesson entities via `saveAll()`
  - `regenerateFromDate()` — deletes planned lessons from date, then regenerates
- **LessonGenerationServiceTest**: 10 unit tests covering ALL/ODD/EVEN parity variants, semester starting on Sunday, empty range, and entity construction

## Parity Algorithm

The algorithm anchors week numbering to the Monday of the week containing `semesterStart`:

```
anchor = semesterStart.with(previousOrSame(MONDAY))
weeksSinceStart = WEEKS.between(anchor, currentWeekMonday)
currentParity = (weeksSinceStart % 2 == 0) ? firstWeekType : opposite(firstWeekType)
```

This correctly handles semesters starting mid-week (e.g., on a Tuesday or Sunday).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Entity default value for firstWeekType**
- **Found during:** Task 1 verification (academic tests)
- **Issue:** Adding `firstWeekType NOT NULL` column without a Java-level default caused existing tests that create `new Semester()` without setting `firstWeekType` to fail with null constraint violation (JPA sends NULL overriding DB DEFAULT)
- **Fix:** Added `= "odd"` as Java field initializer in `Semester.java`
- **Files modified:** `Semester.java`
- **Commit:** 8de1d90

**2. [Rule 1 - Bug] Implicit varchar cast missing in V6 migration**
- **Found during:** Task 1 analysis (comparing to V5 pattern)
- **Issue:** JPA binds enum fields as `character varying`; without `CREATE CAST (varchar AS week_type) WITH INOUT AS IMPLICIT`, writes would fail
- **Fix:** Added implicit cast statement to V6 migration (same pattern as V5 for other enum types)
- **Files modified:** `V6__add_semester_first_week_type.sql`
- **Commit:** 8de1d90

**3. [Rule 1 - Bug] Incorrect test dates in plan (Feb 3 is Tuesday, not Monday)**
- **Found during:** Task 2 test execution (8 of 10 tests failed on first run)
- **Issue:** Plan's test data claimed Feb 3, 2026 is Monday — it is actually Tuesday. Tests were finding Sundays/wrong days.
- **Fix:** Changed test data to use Feb 2, 2026 (actual Monday) as semester start throughout LessonGenerationServiceTest
- **Files modified:** `LessonGenerationServiceTest.java`
- **Commit:** cb01b03

## Out of Scope — Pre-existing Failures

2 pre-existing `CacheIntegrationTest` failures existed before this plan's changes:
- `getActiveSemester_secondCall_servedFromCache()`
- `activateSemester_invalidatesActiveSemesterCache()`

These fail with `NOT_FOUND: Semester с isActive=true не найден` due to suspected test isolation issue in the cache test suite (unrelated to V6 migration). Documented in `deferred-items.md`.

## Known Stubs

None — all wired logic is functional. `LessonGenerationService` is not yet called by `ScheduleItemService` (that integration is Plan 02).

## Self-Check: PASSED

All required files exist and both task commits verified:
- `8de1d90` — Task 1: first_week_type in Academic Service
- `cb01b03` — Task 2: LessonGenerationService + tests
