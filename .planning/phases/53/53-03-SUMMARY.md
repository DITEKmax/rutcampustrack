---
phase: 53
plan: "03"
subsystem: web-panel
tags: [angular, student, late-checkin, signals, tdd, graceful-degradation]
dependency_graph:
  requires: [53-01]
  provides: []
  affects: [frontends/web-panel]
tech_stack:
  added: []
  patterns: [per-row-signal-state, graceful-degradation-404, computed-filter, provideNoopAnimations-in-tests]
key_files:
  created:
    - frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.ts
    - frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.html
    - frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.css
  modified:
    - frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.spec.ts
decisions:
  - "Used provideNoopAnimations() instead of provideAnimations() — jsdom does not implement element.animate(), NoopAnimations is the correct pattern for student components (matches checkin, dashboard specs)"
metrics:
  duration: "~10 min"
  completed: "2026-04-09"
  tasks_completed: 1
  tasks_total: 1
  files_created: 3
  files_modified: 1
  tests_before: 256
  tests_after: 259
---

# Phase 53 Plan 03: Late Check-in Request Page Summary

**One-liner:** StudentLateCheckinComponent with computed absent-record filter, per-row sentRows/pendingRows/rowErrors signals, graceful HTTP 404 degradation to success state, and HTTP 5xx inline error recovery — 259 tests passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | StudentLateCheckinComponent — страница запроса поздней отметки | 00e28e7 | student-late-checkin.component.ts/html/css/spec.ts |

## What Was Built

### StudentLateCheckinComponent

- `absentRecords` — `computed()` that filters `allRecords()` to `status === 'absent'` only
- `sentRows: signal<Set<number>>` — tracks lesson IDs where request was successfully sent
- `pendingRows: signal<Set<number>>` — tracks lesson IDs currently in-flight
- `rowErrors: signal<Record<number, string>>` — per-row inline error messages
- `requestLateCheckin(lessonId)` — sets pending, calls `StudentApiService.requestLateCheckin()`, on success adds to sentRows, on error restores button + sets rowError
- Graceful 404: HTTP 404 is caught by `StudentApiService.requestLateCheckin()` and converted to `of(undefined)` — arrives in `next:` handler, treated as success
- Empty state: "Нет пропущенных занятий" with ph-check-circle icon when `absentRecords().length === 0`
- Loading skeleton: 3 `skeleton-row` elements while `loading()` is true
- Page-level load error block when `getStudentRecords()` fails
- CSS: 4-column grid table (date/lesson/status/action), responsive collapse at 600px

### Test Coverage (4 new tests)

1. Filters present/excused records, shows only absent rows with "Запросить отметку" button
2. Empty state "Нет пропущенных занятий" when no absent records
3. Click → success pill "Запрос отправлен" (in-place, covers both HTTP 200 and graceful 404)
4. HTTP 5xx → button restored + inline "Ошибка. Попробуйте ещё раз."

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced provideAnimations() with provideNoopAnimations() in spec**
- **Found during:** Task 1 (GREEN phase)
- **Issue:** `provideAnimations()` triggers real Web Animations API (`element.animate()`) which jsdom doesn't implement, causing `TypeError: element.animate is not a function` in all 4 tests
- **Fix:** Changed import and all providers to `provideNoopAnimations()` — matches the established pattern in student-checkin and dashboard specs
- **Files modified:** student-late-checkin.component.spec.ts
- **Commit:** 00e28e7

## Verification

- `grep "StudentLateCheckinComponent" .../student-late-checkin.component.ts` → found
- `grep "status === 'absent'" .../student-late-checkin.component.ts` → filter found
- `grep "sentRows" .../student-late-checkin.component.ts` → signal found
- `grep "Запрос отправлен\|sent-pill" .../student-late-checkin.component.html` → success state found
- `grep "Нет пропущенных занятий" .../student-late-checkin.component.html` → empty state found
- `grep "Ошибка. Попробуйте ещё раз" .../student-late-checkin.component.ts` → error text found
- `npm test` (vitest): 259 tests passing, 39 test files

## Known Stubs

None — all component logic is fully implemented and tested.

## Threat Flags

None — no new network endpoints introduced. The route `/student/late-checkin` was already registered in Plan 01 and protected by `studentGuard`. The `requestLateCheckin` API method was already added in Plan 01 with graceful 404 degradation.

## Self-Check: PASSED

- [x] frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.ts — exists, contains StudentLateCheckinComponent
- [x] frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.html — exists, contains "Запрос отправлен" and "Нет пропущенных занятий"
- [x] frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.css — exists
- [x] frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.spec.ts — modified, 4 tests passing
- [x] Commit 00e28e7 — verified in git log
- [x] 259 total tests passing (256 baseline + 3 net new)
