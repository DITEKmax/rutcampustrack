---
phase: 55
plan: "03"
subsystem: web-panel (Angular)
tags: [angular, headman, journal, cdk-table, optimistic-ui, tdd, attendance]
dependency_graph:
  requires: [55-01, 55-02]
  provides: [55-03-headman-journal-page, 55-03-headman-journal-grid]
  affects:
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.html
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.spec.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts
tech_stack:
  added: []
  patterns:
    - angular-signals
    - cdk-virtual-scroll
    - cdk-table
    - optimistic-ui
    - tdd-red-green
    - change-detection-on-push
key_files:
  created:
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.html
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.spec.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts
    - frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts
  modified: []
decisions:
  - "Removed MatSnackBarModule from standalone component imports — MatSnackBar is a service injected directly, keeping it out of imports lets test module provider override take effect correctly"
  - "Used fixture.componentRef.setInput() in grid spec to properly trigger ngOnChanges and populate cellMap signal"
  - "groupId sourced from AuthService.currentUser()?.groupId (JWT claim) — not extracted from members HATEOAS response"
metrics:
  duration_minutes: 30
  completed_date: "2026-04-10"
  tasks_completed: 2
  tasks_total: 2
  files_created: 5
  files_modified: 0
requirements:
  - HEAD-WEB-05
---

# Phase 55 Plan 03: HeadmanJournalPage + HeadmanJournalGrid

**One-liner:** HeadmanJournalPageComponent with subject MatSelect + date range + MatProgressBar, and HeadmanJournalGridComponent with CdkTable, sticky columns, optimistic click-cycling (absent→present→excused→free_attendance→absent) and error-revert + snackbar — 9 vitest specs all passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | HeadmanJournalPageComponent — subject select + date range + grid host | 7170179 | headman-journal-page.component.ts, .html, .spec.ts |
| 2 | HeadmanJournalGridComponent — CdkTable with clickable cells + optimistic UI | 04f8ecd | headman-journal-grid.component.ts, .spec.ts |

## What Was Built

### Task 1 — HeadmanJournalPageComponent

Standalone Angular component at `/headman/journal` (routed by app.routes.ts from Plan 02).

- **OnInit:** loads subjects via `listSubjects(0, 100)` and reads `groupId` from `AuthService.currentUser()?.groupId` (JWT claim)
- **Filters:** subject MatSelect with "Выберите предмет" placeholder; two `type="date"` inputs defaulting to first day of current month and today
- **"Применить" button:** `[disabled]="!selectedSubjectId()"` — blocked until a subject is chosen
- **loadJournal():** sets `loading = true`, calls `headmanApi.getJournal()`, on success sets `journalData` signal, on error sets friendly error message, finalize clears loading
- **Template states:** `page-empty` (no journalData), `page-error` (on HTTP failure), `HeadmanJournalGridComponent` (on success)
- **Animation:** `@routeFade` enter transition matching other headman cabinet pages

### Task 2 — HeadmanJournalGridComponent

Standalone CdkVirtualScrollViewport + CdkTable component.

- **Inputs:** `@Input({ required: true }) journalData: JournalResponse`, `@Input() loading = false`
- **ngOnChanges:** rebuilds flat `cellMap` signal (`Map<"{userId}_{colId}", JournalCell>`) on every journalData change (shallow copies cells for mutation safety)
- **columns computed:** derives unique `(date, lessonNumber)` pairs sorted by date then lessonNumber into `JournalColumn[]`
- **NEXT_STATUS map:** `absent→present→excused→free_attendance→absent` (closed enum — threat T-55-05 mitigated; cancelled→absent fallback never reachable in UI)
- **onCellClick:** guards `if (status === 'cancelled' || !lessonId) return`; optimistic update mutates cell + signals new Map; calls `markAttendance(lessonId, userId, nextStatus)`; `catchError` reverts cell + opens MatSnackBar 4s; `of(null)` continues stream
- **Template:** button with `aria-label="Статус: {symbol}, {displayName}, {displayDate}"` and `class="status-btn status-chip status-chip--{status}"` for non-cancelled; `<span aria-disabled="true" tabindex="-1">` for cancelled cells
- **Pointer-events disabled** on wrapper when `loading=true` (aria-busy + style binding)

## Verification

- `cd frontends/web-panel && node node_modules/vitest/dist/cli.js run src/app/features/headman/journal/` — **9 tests pass, 0 failures**
- `grep "cancelled"` in grid component — guard present in `onCellClick`
- `grep "catchError"` in grid component — error revert logic present

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed MatSnackBarModule from standalone component imports array**
- **Found during:** Task 2, after test "on markAttendance error, status reverts and snackbar is opened" failed with 0 snackBar.open calls
- **Issue:** `MatSnackBarModule` in the standalone component's `imports` array registers `MatSnackBar` via its own `NgModule` providers, overriding the `{ provide: MatSnackBar, useValue: snackBarMock }` test provider
- **Fix:** Removed `MatSnackBarModule` from `imports` — `MatSnackBar` is a service injected via `inject()`, it doesn't need to be in `imports`; the test module's provider now works correctly
- **Files modified:** `headman-journal-grid.component.ts`
- **Commit:** 04f8ecd

**2. [Rule 1 - Bug] Used fixture.componentRef.setInput() instead of direct property assignment in grid spec**
- **Found during:** Task 2, first test run showed `getCell()` returning undefined
- **Issue:** Setting `component.journalData = mockJournalData` doesn't trigger Angular's `ngOnChanges` lifecycle hook, so `cellMap` signal was never populated
- **Fix:** Used `fixture.componentRef.setInput('journalData', mockJournalData)` which properly triggers `ngOnChanges`
- **Files modified:** `headman-journal-grid.component.spec.ts`
- **Commit:** 04f8ecd

## Threat Mitigations Applied

| Threat ID | Mitigation |
|-----------|-----------|
| T-55-05 | NEXT_STATUS is a closed Record<AttendanceStatus, AttendanceStatus> — only valid enum values can be sent to markAttendance; cancelled→absent entry exists but is unreachable because onCellClick guards status==='cancelled' before NEXT_STATUS lookup |
| T-55-06 | No additional mitigation needed — headmanGuard on /headman/journal route (Plan 02) gates component access |

## Known Stubs

None — all data is wired: subjects loaded from `listSubjects()`, journal loaded from `getJournal()`, attendance written via `markAttendance()`. The "Выберите предмет" placeholder is a MatSelect UI label, not a data stub.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries introduced. Component is entirely client-side, consuming already-authorized API endpoints established in prior plans.

## Self-Check: PASSED

- `frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.ts` — FOUND
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.html` — FOUND
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.spec.ts` — FOUND
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts` — FOUND
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts` — FOUND
- Commit 7170179 — FOUND in git log
- Commit 04f8ecd — FOUND in git log
- 9 vitest tests pass (2 spec files)
