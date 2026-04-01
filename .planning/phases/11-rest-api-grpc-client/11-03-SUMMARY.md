---
phase: 11-rest-api-grpc-client
plan: 03
subsystem: schedule-service
tags: [lesson-operations, schedule-view, hateoas, integration-tests, state-machine]
dependency_graph:
  requires: [11-01-PLAN, 11-02-PLAN]
  provides: [LessonService, LessonController, LessonAssembler, LessonWithItem, LessonApiTest, ScheduleViewTest]
  affects: [phase-12-grpc-server, attendance-service]
tech_stack:
  added: []
  patterns:
    - State machine enforcement for lesson cancel/restore transitions (422 on violation)
    - LessonWithItem app-internal record for joining Lesson + ScheduleItem without N+1
    - In-memory pagination via PageImpl after batch fetch
    - Native @Query with status::text cast to bypass PostgreSQL enum vs varchar operator issue
    - V2 Flyway migration adding implicit varchar/text -> enum casts
    - HATEOAS conditional action links based on current lesson status
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonWithItem.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonAssembler.java
    - services/schedule-service/schedule-app/src/main/resources/db/migration/V2__add_enum_casts.sql
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewTest.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
decisions:
  - "Native @Query with status::text cast preferred over derived query method for PostgreSQL enum IN clause — implicit cast alone insufficient for = operator with IN"
  - "In-memory pagination chosen for getLessonsForGroup — avoids complex JPQL join with ScheduleItem, acceptable for typical group schedule size"
  - "V2 Flyway migration adds varchar/text -> enum implicit casts for both INSERT assignment and future SELECT compatibility"
metrics:
  duration: "~40 minutes"
  completed: "2026-04-02"
  tasks: 2
  files: 7
---

# Phase 11 Plan 03: Lesson Operations + Schedule View Summary

Lesson management state machine (cancel/restore/mass-cancel/geo-block) and group schedule view endpoint with 12 integration tests passing.

## What Was Built

### Task 1: Core Implementation

**LessonWithItem** — app-internal record pairing `Lesson` with its `ScheduleItem` parent. Avoids N+1 lookups by fetching once and passing both entities together through the service/assembler chain.

**LessonService** — 5 business methods:
- `cancelLesson`: state guard (PLANNED only), sets status=CANCELLED + reason
- `restoreLesson`: state guard (CANCELLED only), resets to PLANNED + clears reason
- `massCancelLessons`: validates headman ownership BEFORE any DB access, bulk-cancels all PLANNED lessons in date range
- `toggleGeoBlock`: sets is_geo_blocked without state restriction
- `getLessonsForGroup`: default status filter excludes CANCELLED (D-18), no isActive filter on ScheduleItem lookup (Pitfall 3), in-memory pagination

**LessonAssembler** — HATEOAS assembler with conditional links: `cancel` only when PLANNED, `restore` only when CANCELLED, `geo-block` always present.

**LessonController** — implements `LessonApi`, `@RequireRole({ADMIN, STUDENT})` on all write ops, no `@RequireRole` on `getLessons` (any authenticated user per VIEW-01).

### Task 2: Integration Tests (12 tests, all passing)

**LessonApiTest** (7 tests):
- `cancelLesson_planned_success` — 200, status=CANCELLED, cancelReason set
- `cancelLesson_activeLesson_returns422` — state guard on non-PLANNED
- `cancelLesson_missingReason_returns400` — @NotBlank validation
- `restoreLesson_cancelled_success` — 200, status=PLANNED, cancelReason=null
- `restoreLesson_planned_returns422` — state guard on non-CANCELLED
- `massCancelLessons_success` — only 2 PLANNED affected, ACTIVE untouched
- `toggleGeoBlock_success` — toggles true then false

**ScheduleViewTest** (5 tests):
- `anyRoleCanView_student` — 200 without headman role
- `anyRoleCanView_teacher` — 200 with TEACHER role
- `responseContainsAllFields` — groupId, subjectId, teacherId, room, dayOfWeek, startTime, endTime from ScheduleItem
- `defaultStatusFilter_excludesCancelled` — 2 results (PLANNED + ACTIVE), not 3
- `explicitStatusFilter_includesCancelled` — 3 results when CANCELLED included

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PostgreSQL enum IN clause operator error**
- **Found during:** Task 2 (massCancelLessons_success test)
- **Issue:** `findByScheduleItemIdInAndDateBetweenAndStatusIn` generated `WHERE status IN (?)` with `?` bound as `character varying`, but PostgreSQL cannot find `=` operator for `lesson_status = character varying` even with implicit cast
- **Fix:** Changed repository methods to use native `@Query` with `status::text IN :statuses`, and updated LessonService to pass lowercase string values (`"planned"` etc.) matching PostgreSQL enum storage
- **Also added:** V2 Flyway migration with `CREATE CAST (varchar AS week_type) WITH INOUT AS IMPLICIT` and `CREATE CAST (varchar AS lesson_status) WITH INOUT AS IMPLICIT` for INSERT assignment compatibility
- **Files modified:** `LessonRepository.java`, `LessonService.java`, `V2__add_enum_casts.sql`
- **Commit:** e76ee0b

## Known Stubs

None — all endpoints fully implemented and wired to the database.

## Self-Check: PASSED

Files created/verified:
- LessonWithItem.java: FOUND
- LessonService.java: FOUND
- LessonController.java: FOUND
- LessonAssembler.java: FOUND
- V2__add_enum_casts.sql: FOUND
- LessonApiTest.java: FOUND
- ScheduleViewTest.java: FOUND

Commits:
- 7fe29ab (feat(11-03): implement lesson operations): FOUND
- e76ee0b (test(11-03): add LessonApiTest + ScheduleViewTest): FOUND

All 12 integration tests: PASSED (BUILD SUCCESSFUL)
