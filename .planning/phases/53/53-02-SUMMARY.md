---
phase: 53
plan: "02"
subsystem: web-panel
tags: [angular, student, excuses, dialog, drag-drop, graceful-degradation, pwa]
dependency_graph:
  requires: [53-01]
  provides: [53-03, 53-04]
  affects: [frontends/web-panel]
tech_stack:
  added: []
  patterns: [graceful-degradation-404, mat-dialog, drag-drop-file-upload, OnPush-signals]
key_files:
  created:
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.html
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.css
    - frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts
    - frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.html
    - frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.css
  modified:
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts
decisions:
  - "provideNoopAnimations() instead of provideAnimations() in spec — jsdom lacks element.animate(), same pattern as other student specs"
  - "getExcuseTickets() added to StudentApiService with 404 graceful degradation to empty array"
  - "ExcuseFormDialogComponent uses Angular signals (not RxJS) for all mutable UI state"
  - "File validation is client-side only (T-53-02-01); backend must validate on endpoint activation"
metrics:
  duration: "~20 min"
  completed: "2026-04-09"
  tasks_completed: 2
  tasks_total: 2
  files_created: 6
  files_modified: 2
  tests_before: 256
  tests_after: 259
---

# Phase 53 Plan 02: Excuses Page + Form Dialog Summary

**One-liner:** StudentExcusesComponent with graceful-404 empty state and btn-brand CTA, plus ExcuseFormDialogComponent with Mat-checkbox lesson selection, drag-drop file zone (10 MB / 5 files), comment textarea, and graceful-degradation submit — 259 tests passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | StudentExcusesComponent — страница со списком тикетов | d08a860 | student-excuses.component.ts/html/css, student-api.service.ts, spec.ts |
| 2 | ExcuseFormDialogComponent — диалог подачи тикета | 27548f4 | excuse-form-dialog.component.ts/html/css |

## What Was Built

### StudentExcusesComponent (`/student/excuses`)
- Page with `page-header` (eyebrow + title) and `btn-brand` CTA "Подать тикет"
- `ngOnInit` calls `getExcuseTickets()` — HTTP 404 → empty array (no `.page-error`)
- Empty state: `ph-duotone ph-file-text` icon + "Нет тикетов о пропуске"
- Ticket table: createdAt date | subjectNames joined | status chip (pending/approved/rejected/cancelled)
- Status labels: `pending→'На рассмотрении'`, `approved→'Одобрено'`, `rejected→'Отклонено'`, `cancelled→'Отменено'`
- `openExcuseForm()` loads student records then opens `ExcuseFormDialogComponent` via `MatDialog`; on dialog close with `true` → reloads ticket list

### getExcuseTickets() (StudentApiService)
- `GET /api/attendance/excuses` with defensive HATEOAS embedded key fallback
- `catchError`: 404 → `of([])`, other errors propagate

### ExcuseFormDialogComponent
- Receives `data.lessons: AttendanceRecord[]` via `MAT_DIALOG_DATA`
- Filters to last 30 days as `recentLessons`
- Mat-checkbox lesson selection with `selectedLessonIds` signal (Set<number>)
- Drag-drop zone: `dragover/dragleave/drop` events + hidden `<input type="file">`
- File validation: `size > 10 MB` → error message, combined list sliced to max 5
- Comment field: `mat-form-field` outline, maxlength 500
- Submit validation: `selectedLessonIds.size === 0` → "Выберите хотя бы одно занятие"
- Submit success (incl. graceful 404): `dialogRef.close(true)` + snackbar "Запрос отправлен. Подтверждение придёт в Telegram."
- Submit HTTP 5xx: inline `.page-error` "Не удалось подать тикет. Попробуйте ещё раз." — dialog stays open
- Cancel: `dialogRef.close(false)`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used provideNoopAnimations() instead of provideAnimations() in spec**
- **Found during:** Task 1 (test run)
- **Issue:** `provideAnimations()` uses WebAnimationsPlayer which calls `element.animate()` — not available in jsdom test environment → `TypeError: element.animate is not a function`
- **Fix:** Replaced all 3 occurrences of `provideAnimations()` with `provideNoopAnimations()` in the spec — consistent with all other student component specs in this project
- **Files modified:** student-excuses.component.spec.ts

## Known Stubs

None — all components fully implemented with real logic.

Wave 0 stubs from Plan 01 replaced:
- `student-excuses.component.spec.ts` — replaced with 3 real tests (graceful 404, empty list, CTA button)

## Threat Surface Scan

No new network endpoints introduced. File upload boundary covered by T-53-02-01 in plan threat model:
- Client-side file size validation implemented (`file.size > 10 MB → reject`)
- Angular auto-escaping prevents XSS from `file.name` display (T-53-02-02)
- Backend validation TODO comment added in source code for when endpoint is activated

## Verification

- `npm test`: 259 tests passing, 39 test files
- `grep StudentExcusesComponent student-excuses.component.ts` → found
- `grep "page-empty\|Нет тикетов о пропуске\|btn-brand\|openExcuseForm" student-excuses.component.html` → all found
- `grep getExcuseTickets student-api.service.ts` → found
- `grep "ExcuseFormDialogComponent\|Выберите хотя бы одно занятие\|MAX_FILE_SIZE_BYTES\|Не удалось подать тикет\|Запрос отправлен" excuse-form-dialog.component.ts` → all found
- `grep "mat-dialog-title\|drop-zone" excuse-form-dialog.component.html` → both found

## Self-Check: PASSED

- [x] frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts — exists
- [x] frontends/web-panel/src/app/features/student/excuses/student-excuses.component.html — exists, contains page-empty and btn-brand
- [x] frontends/web-panel/src/app/features/student/excuses/student-excuses.component.css — exists
- [x] frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts — exists, contains all required strings
- [x] frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.html — exists
- [x] frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.css — exists
- [x] Commits d08a860 and 27548f4 — verified in git log
- [x] 259 tests passing
