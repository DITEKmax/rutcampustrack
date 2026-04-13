---
phase: 55
plan: 01
subsystem: attendance-service
tags: [backend, dto, journal, lessonId, report]
dependency_graph:
  requires: []
  provides: [JournalCell.lessonId, ReportService.getJournal.lessonId]
  affects: [attendance-api-contract, attendance-app, web-panel headman journal]
tech_stack:
  added: []
  patterns: [contract-first, plain-Java DTO no-Lombok in contract, TDD]
key_files:
  created: []
  modified:
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalCell.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportServiceTest.java
decisions:
  - lessonId added as FIRST constructor param in JournalCell to preserve linking-key reading order
metrics:
  duration: ~15 min
  completed: 2026-04-10
  tasks_completed: 2
  files_modified: 3
requirements:
  - HEAD-WEB-05
---

# Phase 55 Plan 01: Add lessonId to JournalCell backend DTO

One-liner: Extended JournalCell DTO with Long lessonId field + ReportService mapping so Angular headman journal can call PUT /attendance/lessons/{lessonId}/students/{userId} on cell click.

## What Was Built

Added `lessonId` as the first field in `JournalCell.java` (attendance-api-contract) — plain Java, no Lombok per contract module rule. Updated `ReportService.getJournal()` to pass `r.lessonId()` as first constructor argument in the stream map lambda. Added `getJournal_cellIncludesLessonId` test in `ReportServiceTest` asserting the field is non-null and equals the known stub value (42L).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extend JournalCell DTO with lessonId | 66e65ec | JournalCell.java, ReportService.java |
| 2 | Extend ReportServiceTest with lessonId assertion | 9d65ec0 | ReportServiceTest.java |

## Verification

- `./gradlew :services:attendance-service:attendance-app:compileJava` — BUILD SUCCESSFUL
- `./gradlew :services:attendance-service:attendance-app:test --tests "*.ReportServiceTest"` — BUILD SUCCESSFUL, 9 tests pass (8 existing + 1 new)

## Deviations from Plan

**1. [Rule 1 - Bug] Fixed getter name references in test**
- **Found during:** Task 2
- **Issue:** Plan referenced `.getStudentRows()` / `.getCells()` but actual DTOs expose `getStudents()` / `getRecords()`
- **Fix:** Used correct getter names from `JournalResponse.getStudents()` and `JournalStudentRow.getRecords()`
- **Files modified:** ReportServiceTest.java
- **Commit:** 9d65ec0

## Known Stubs

None — this plan makes no UI stubs. The `lessonId` field is fully wired from `AttendanceRecord` through `ReportService` to `JournalCell` JSON response.

## Threat Flags

None — threat model reviewed. `lessonId` is non-secret schedule metadata already gated by `authorizeHeadmanOrTeacher()`. No new trust boundaries introduced.

## Self-Check: PASSED

- JournalCell.java found with `private final Long lessonId` field and `getLessonId()` getter
- ReportService.java found with `r.lessonId()` in getJournal lambda
- ReportServiceTest.java found with `getJournal_cellIncludesLessonId` test method
- Commits 66e65ec and 9d65ec0 exist in git log
