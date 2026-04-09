---
phase: 53
plan: "01"
subsystem: web-panel
tags: [angular, student, types, api, routes, sidebar, pwa]
dependency_graph:
  requires: [52-04]
  provides: [53-02, 53-03, 53-04]
  affects: [frontends/web-panel]
tech_stack:
  added: []
  patterns: [graceful-degradation-404, defensive-hateoas-embedded-key, wave-0-spec-stubs]
key_files:
  created:
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts
    - frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.spec.ts
    - frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
decisions:
  - "ExcuseSubmitRequest not imported in service (only in types file) — removed to avoid unused import lint error"
  - "submitExcuse takes raw params (lessonIds, comment, files) not ExcuseSubmitRequest — FormData construction inline"
  - "Wave 0 stubs use minimal imports (no render) to avoid component-not-found compilation errors"
metrics:
  duration: "~15 min"
  completed: "2026-04-09"
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 4
  tests_before: 253
  tests_after: 256
---

# Phase 53 Plan 01: Foundation — Types, API, Routes, Sidebar Summary

**One-liner:** Extended student-schedule.types.ts with AttendanceRecord/ExcuseTicket/ExcuseSubmitRequest, added getStudentRecords/submitExcuse/requestLateCheckin with graceful 404 degradation to StudentApiService, registered lazy routes for /student/excuses and /student/late-checkin, added two STUDENT nav items to sidebar, and created three Wave 0 spec stubs — 256 tests passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Расширить типы и API-сервис (Wave 0 + Wave 1) | 2d0cfec | student-schedule.types.ts, student-api.service.ts, 3× spec stubs |
| 2 | Добавить маршруты и sidebar nav items | fe876c8 | app.routes.ts, sidebar.component.ts |

## What Was Built

### student-schedule.types.ts
- `AttendanceRecord` — DTO for GET /api/attendance/reports/student/records
- `ExcuseTicketStatus` — union type: pending | approved | rejected | cancelled
- `ExcuseTicket` — excuse ticket with lessonIds, subjectNames, status
- `ExcuseSubmitRequest` — submit payload (lessonIds + comment; files via FormData)

### student-api.service.ts
- `getStudentRecords(subjectId?)` — fetches attendance records with defensive embedded key fallback
- `submitExcuse(lessonIds, comment, files)` — POST FormData with graceful HTTP 404 degradation
- `requestLateCheckin(lessonId)` — POST late check-in with graceful HTTP 404 degradation

### app.routes.ts
- `/student/excuses` → lazy `StudentExcusesComponent`
- `/student/late-checkin` → lazy `StudentLateCheckinComponent`

### sidebar.component.ts
- `Пропуски` (ph-file-text) → /student/excuses, roles: ['STUDENT']
- `Запрос отметки` (ph-clock-countdown) → /student/late-checkin, roles: ['STUDENT']

### Wave 0 spec stubs
- `student-excuses.component.spec.ts` — placeholder for STU-WEB-07 tests
- `student-late-checkin.component.spec.ts` — placeholder for STU-WEB-08 tests
- `student-pwa-banner.component.spec.ts` — placeholder for STU-WEB-10 tests

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Removed unused ExcuseSubmitRequest import from student-api.service.ts**
- **Found during:** Task 1
- **Issue:** Plan specified importing ExcuseSubmitRequest into the service, but the submitExcuse method takes raw params (lessonIds, comment, files) and constructs FormData inline — the type is only used in the types file itself
- **Fix:** Removed ExcuseSubmitRequest from service imports to prevent unused-import TypeScript/lint errors
- **Files modified:** student-api.service.ts

**2. [Rule 3 - Blocking] Simplified Wave 0 spec stubs to avoid missing component errors**
- **Found during:** Task 1
- **Issue:** Plan's original excuses stub imported `render` from `@testing-library/angular` and `StudentExcusesComponent` — but the component doesn't exist yet (Wave 2 creates it), causing compilation errors
- **Fix:** Used minimal stubs (import { describe, it, expect } from 'vitest' only) for all three specs — matches the other two stubs in the plan
- **Files modified:** student-excuses.component.spec.ts

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `student-excuses.component.spec.ts` | Wave 0 placeholder | Component created in Plan 02 |
| `student-late-checkin.component.spec.ts` | Wave 0 placeholder | Component created in Plan 03 |
| `student-pwa-banner.component.spec.ts` | Wave 0 placeholder | Component created in Plan 04 |

These stubs are intentional — the plan explicitly marks them as "Wave 0" to be replaced in plans 02, 03, 04.

## Verification

- `npm test` (vitest): 256 tests passing, 39 test files
- `grep getStudentRecords\|submitExcuse\|requestLateCheckin student-api.service.ts` → 3 methods found
- `grep AttendanceRecord\|ExcuseTicket\|ExcuseSubmitRequest student-schedule.types.ts` → 3 types found
- `grep student/excuses\|student/late-checkin app.routes.ts` → 2 routes found
- 3 Wave 0 spec files exist and pass

## Self-Check: PASSED

- [x] frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts — exists, contains AttendanceRecord
- [x] frontends/web-panel/src/app/features/student/shared/student-api.service.ts — exists, contains getStudentRecords
- [x] frontends/web-panel/src/app/app.routes.ts — exists, contains student/excuses
- [x] frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts — exists, contains ph-file-text
- [x] 3 Wave 0 spec files — exist and pass
- [x] Commits 2d0cfec and fe876c8 — verified in git log
