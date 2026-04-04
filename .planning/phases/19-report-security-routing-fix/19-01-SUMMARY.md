---
phase: 19-report-security-routing-fix
plan: 01
subsystem: attendance-service
tags: [security, routing, report, gap-closure]
requirements: [RPRT-01, RPRT-02, RPRT-03, RPRT-04]
gaps_closed: [INT-01, INT-02]
files_modified:
  - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java
  - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java
  - services/api-gateway/src/main/resources/application.yml
  - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIntegrationTest.java
test_count: 6
status: complete
dependency_graph:
  requires: [18-read-path-reports]
  provides: [secured-report-api, aligned-gateway-routing]
  affects: [api-gateway, attendance-api-contract, attendance-app]
tech_stack:
  patterns: ["@RequireRole AOP security on all report endpoints", "Gateway StripPrefix=1 aligned to /attendance/reports"]
key_files:
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java
    - services/api-gateway/src/main/resources/application.yml
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIntegrationTest.java
decisions:
  - "@RequireRole({STUDENT, TEACHER}) on getLessonAttendance and getJournal — headman is STUDENT+is_headman, teacher needs journal access; AOP gate blocks unauthenticated callers before business-level authorizeHeadmanOrTeacher runs"
  - "ReportApi @RequestMapping changed from /reports to /attendance/reports — aligns with /api/attendance/** gateway predicate after StripPrefix=1"
  - "Removed /api/reports/** gateway predicate — redundant after path fix; single /api/attendance/** predicate now covers all attendance endpoints"
metrics:
  duration_minutes: 10
  completed_date: "2026-04-04"
  tasks_completed: 2
  files_changed: 4
---

# Phase 19 Plan 01: Report Security and Routing Fix Summary

**One-liner:** AOP @RequireRole on all 4 report endpoints (INT-01) and gateway URL path aligned from /reports to /attendance/reports (INT-02), closing both v4.0 audit gaps.

## Objective

Close INT-01 and INT-02 from the v4.0 milestone audit:
- INT-01 (medium): Report endpoints were accessible without authentication — @RequireRole now blocks unauthenticated callers via RoleCheckAspect before business logic runs.
- INT-02 (low): Report API mapped to /reports (gateway exposed as /api/reports/**) inconsistent with documented /api/attendance/reports/* convention — fixed via @RequestMapping change and gateway predicate cleanup.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add @RequireRole to all 4 ReportController methods | fdf945b | ReportController.java |
| 2 | Fix report URL path — @RequestMapping and gateway routing | bbb44c8 | ReportApi.java, application.yml, ReportIntegrationTest.java |

## Key Changes

### Task 1 — @RequireRole on all 4 methods (INT-01)

`ReportController.java` now has:
- `getLessonAttendance`: `@RequireRole({UserRole.STUDENT, UserRole.TEACHER})`
- `getJournal`: `@RequireRole({UserRole.STUDENT, UserRole.TEACHER})`
- `getStudentStats`: `@RequireRole(UserRole.STUDENT)`
- `getStudentRecords`: `@RequireRole(UserRole.STUDENT)`

STUDENT covers headman (STUDENT + is_headman=true). TEACHER added for getLessonAttendance and getJournal since teachers need journal/lesson view access (authorizeHeadmanOrTeacher business logic handles finer-grained teacher checks).

### Task 2 — URL path fix (INT-02)

- `ReportApi.java`: `@RequestMapping` changed from `"/reports"` to `"/attendance/reports"`
- `application.yml`: attendance-service predicate is now `Path=/api/attendance/**` only — `/api/reports/**` removed
- `ReportIntegrationTest.java`: all 6 test URLs updated from `/reports/...` to `/attendance/reports/...`

Gateway routing after fix:
- Receives: `/api/attendance/reports/lesson/1`
- StripPrefix=1 drops `/api`
- Forwards to: `/attendance/reports/lesson/1`
- ReportApi handles: `@RequestMapping("/attendance/reports")` + `@GetMapping("/lesson/{lessonId}")`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed pre-existing test assertion: `cells` → `records` in getJournal_headman_returnsGridShape**
- **Found during:** Task 2 (running ReportIntegrationTest)
- **Issue:** `JournalStudentRow` has getter `getRecords()` which Jackson serializes as `records` in JSON, but test assertion used `$.students[0].cells[0].symbol` (wrong field name `cells`)
- **Fix:** Changed test assertion to `$.students[0].records[0].symbol`
- **Files modified:** `ReportIntegrationTest.java`
- **Commit:** bbb44c8

**Note:** `EventConsumerIntegrationTest.semesterArchived_refreshesSemesterCache()` is a pre-existing failure (AMQP null map, unrelated to report security/routing). Documented in deferred items below.

## Test Results

- ReportIntegrationTest: 6/6 tests PASSED
- Full attendance-service suite: 95 tests, 1 pre-existing failure (EventConsumerIntegrationTest — out of scope)

## Known Stubs

None — all report endpoints are fully wired.

## Deferred Issues

- `EventConsumerIntegrationTest.semesterArchived_refreshesSemesterCache()` — pre-existing failure (NullPointerException in EventConsumer when processing semester.archived event; AMQP null map). Scope: Phase 16 event consumer code, not related to this plan. Needs investigation in a separate fix task.

## Self-Check: PASSED

- [x] `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java` — modified with 4 @RequireRole
- [x] `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` — @RequestMapping("/attendance/reports")
- [x] `services/api-gateway/src/main/resources/application.yml` — /api/reports/** removed
- [x] `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIntegrationTest.java` — 6 tests updated
- [x] Commit fdf945b exists (Task 1)
- [x] Commit bbb44c8 exists (Task 2)
