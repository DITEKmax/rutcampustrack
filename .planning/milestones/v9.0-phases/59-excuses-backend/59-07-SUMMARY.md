---
phase: 59-excuses-backend
plan: 07
subsystem: frontends/web-panel (student cabinet)
tags: [frontend, angular, excuse-tickets, student, ui, vitest]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-01 (ExcuseTicketResponse DTO — field names, status lowercase)
    - 59-02 (POST /api/attendance/excuses live endpoint, JSON body)
    - 59-02 (GET /api/attendance/excuses/me live endpoint, HATEOAS paged)
  provides:
    - StudentApiService.getExcuseTickets() → GET /excuses/me
    - StudentApiService.submitExcuse(lessonIds, excuseType, comment) → JSON POST /excuses
    - ExcuseFormDialogComponent with ExcuseType dropdown (6 Russian labels)
    - StudentExcusesComponent with live ticket list (backend-aligned statuses)
    - EXCUSE_TYPE_LABELS map (reusable for headman plan 59-08)
  affects:
    - plan 59-08 (headman UI — can reuse EXCUSE_TYPE_LABELS and status mapping)
    - plan 59-09 (final regression — 358-test baseline established)

tech-stack:
  added:
    - @angular/material MatSelectModule (already transitively present)
  patterns:
    - Angular v19 standalone + OnPush + signals (inject(), signal(), update())
    - ReactiveForms with FormControl + Validators.required/maxLength
    - HATEOAS paged response unwrap with defensive key fallback
    - JSON POST (replaces FormData) — aligned with contract-first convention

key-files:
  created:
    - (none — all edits in-place)
  modified:
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts
    - frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.html
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.html
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.css
    - frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts

decisions:
  - "ExcuseType type union uses lowercase strings ('illness' | 'summons' | ...) — matches Mongo/event payload storage and eliminates a UI→enum conversion layer"
  - "EXCUSE_TYPE_LABELS exported from shared types module (not dialog-local) so headman plan 59-08 can reuse the same Russian labels without duplication"
  - "File-upload block removed from ExcuseFormDialog (drop zone, size/count limits, file-chip list) per D-03 scope boundary — would be dead code waiting for deferred Telegram attachment flow"
  - "Comment field maxLength raised 500 → 1000 to match backend @Size(max=1000) from Phase 59-01 CreateExcuseRequest"
  - "Status enum aligned to backend: removed legacy 'pending' and 'cancelled', added 'submitted' and 'draft'. Old UI label 'pending → На рассмотрении' preserved under the new key 'submitted' to avoid UX regression"
  - "Ticket-list column 'Предметы' replaced with 'Причина' + 'Занятий' count — subjectNames is not in the backend response DTO (would require a separate gRPC resolve step; deferred)"
  - "submitExcuse() kept HTTP 404 → graceful degradation branch as defensive belt-and-suspenders; should never fire now that backend is live, but costs nothing and avoids breaking existing error-boundary tests"

metrics:
  tasks: 2
  commits: 2
  files_created: 0
  files_modified: 8
  duration: ~20 min
  tests_baseline: 346
  tests_after: 358
  tests_new: 12
---

# Phase 59 Plan 07: Student Excuse Form UI — Summary

One-liner: Connected the student-cabinet excuse form and ticket list to the Phase 59-02 live backend — added an `ExcuseType` dropdown with 6 Russian labels, switched the submit payload from FormData to JSON, rewired `getExcuseTickets()` to `/api/attendance/excuses/me`, and brought `statusLabels` in line with the actual `ExcuseTicketStatus` enum.

## What Was Built

### Shared types (`student-schedule.types.ts`)

- **`ExcuseType`** union: 6 lowercase values matching the backend enum
  (`illness | summons | university_order | exemption | free_attendance | other`).
- **`ExcuseTicketStatus`** union updated to `draft | submitted | approved | rejected`
  (legacy `pending | cancelled` removed — backend doesn't emit them).
- **`ExcuseTicket`** interface expanded to the full Phase 59-01 response DTO:
  `id: string` (Mongo ObjectId), `studentId`, `groupId`, `studentName`,
  `lessonIds`, `excuseType`, `comment`, `status`, `decisionBy`, `decisionComment`,
  `decisionAt`, `createdAt`, `updatedAt`.
- **`EXCUSE_TYPE_LABELS`** map (Russian) exported for reuse by the form dialog,
  the ticket list, and the upcoming headman view (plan 59-08).

### Student API service (`student-api.service.ts`)

- `getExcuseTickets()` now calls `GET /api/attendance/excuses/me?page=0&size=20`,
  unwrapping `_embedded.excuseTicketResponseList` (with defensive fallback to
  `excuseTicketList` and the first embedded array).
- `submitExcuse(lessonIds, excuseType, comment)` — new 3-arg signature; posts
  JSON `{lessonIds, excuseType, comment}` to `POST /api/attendance/excuses`.
  FormData and the `files: File[]` parameter are gone (D-03 scope).
- Both methods retain `catchError` → `of([])` / `of(undefined)` on 404 as a
  graceful-degradation safety net.

### Dialog (`excuse-form-dialog.component.ts/html`)

- Added `MatSelectModule` + a `<mat-select formControlName="excuseType">` with
  `@for (type of excuseTypes)` rendering all six `EXCUSE_TYPE_LABELS` entries.
- `form: FormGroup` now has two controls: `excuseType` (required) and `comment`
  (maxLength 1000). The dialog wires `selectedLessonIds` via a signal-backed
  `Set<number>` as before.
- `submit()` validates in order: lessons selected → type selected → submit.
  Validation errors (`Выберите хотя бы одно занятие`, `Выберите причину
  пропуска`) are surfaced via `validationError` signal.
- Removed: `onDragOver/Leave/Drop`, `onFileInputChange`, `addFiles`,
  `removeFile`, `formatFileSize`, `files`/`fileErrors`/`dragOver` signals, and
  the entire drop-zone + file-chip template block. The `MAX_FILE_SIZE_BYTES` /
  `MAX_FILE_COUNT` constants are also gone.

### Ticket list (`student-excuses.component.ts/html/css`)

- `statusLabels` rewritten: `submitted: 'На рассмотрении'`, `approved: 'Одобрено'`,
  `rejected: 'Отклонено'`, `draft: 'Черновик'`.
- New helper `labelForExcuseType(type)` + `excuseTypeLabels` alias for template.
- Table layout moved to a 4-column grid (`120px 1fr 80px 140px`): **Дата ·
  Причина · Занятий · Статус**. Mobile breakpoint collapses to 3 columns and
  hides the date. Added `.status-chip--submitted` / `.status-chip--draft` CSS.

### Tests (`student-excuses.component.spec.ts`)

| Group | Test | AC |
|-------|------|----|
| StudentExcusesComponent | empty state on 404 (graceful) | AC-9 |
| StudentExcusesComponent | empty state on empty list + confirms `/excuses/me` call | AC-9 |
| StudentExcusesComponent | «Подать тикет» button in DOM | AC-9 |
| StudentExcusesComponent | renders ticket with Russian `excuseType` + status label | AC-9 |
| StudentExcusesComponent | `statusLabels.submitted === 'На рассмотрении'` | D-21 |
| ExcuseFormDialogComponent | dropdown renders all 6 `ExcuseType` values + `<mat-select>` present | AC-9 |
| ExcuseFormDialogComponent | missing `excuseType` → validation error | — |
| ExcuseFormDialogComponent | missing lesson selection → validation error | — |
| ExcuseFormDialogComponent | valid submit calls `submitExcuse([101], 'illness', 'Болел ОРВИ')` | AC-9 |
| ExcuseFormDialogComponent | whitespace-only comment → `null` | — |

## Verification

- `npx vitest run --reporter=dot` — **358/358 tests green** (baseline 346 + 12 new).
- `grep "excuses/me" student-api.service.ts` → 2 matches (URL + JSDoc).
- `grep "excuseType" excuse-form-dialog.component.ts` → 6 matches
  (FormControl, dropdown iteration, submit payload).
- `grep "submitExcuse" excuse-form-dialog.component.ts` → 1 call with
  `(ids, excuseType, comment)` signature.
- `grep "'submitted'" student-excuses.component.ts` → 1 match in `statusLabels`.

Pre-existing TypeScript errors (unrelated spec files: `auth.interceptor`,
`admin-api.service`, `users-page`, `headman/{excuses,late-checkin}`,
`login.component`, `student-dashboard`) remain — they are outside this plan's
scope per the deviation-rule scope boundary.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Missing correctness] Removed `ticket.subjectNames` from ticket list template**
- **Found during:** Task 2 — after updating the `ExcuseTicket` type to match
  Phase 59-01 DTO, the existing template still referenced
  `ticket.subjectNames.join(', ')`. That field does not exist in
  `ExcuseTicketResponse` (backend does not resolve subject names).
- **Fix:** Replaced the "Предметы" column with "Причина" (showing the Russian
  excuseType label via `labelForExcuseType()`) and added a compact "Занятий"
  column that prints `lessonIds.length`. Adjusted CSS grid to 4 columns.
- **Commit:** `833d05b`

**2. [Rule 1 — Bug] Comment maxLength mismatch**
- **Found during:** Task 2 review — old form used `Validators.maxLength(500)`
  but the backend validates `@Size(max=1000)` (Phase 59-01 CreateExcuseRequest).
  Users could silently fail validation server-side.
- **Fix:** Raised the form validator and the textarea's `maxlength` attribute
  to 1000.
- **Commit:** `833d05b`

**3. [Rule 3 — Blocking] Pre-existing enum breakage on import**
- **Found during:** Task 1 tsc run — `student-excuses.component.ts` declared
  `Record<ExcuseTicketStatus, string>` with a `pending` key that no longer
  existed in the updated union, blocking compilation.
- **Fix:** Rewrote the map to use the real statuses (`submitted`, `approved`,
  `rejected`, `draft`) in Task 2.
- **Commit:** `833d05b`

## Known Stubs

None. All UI components are now wired to live backend endpoints with real HTTP
calls. Tests mock `StudentApiService` only — HTTP is exercised indirectly via
the service spec (which lives in a separate file and was not touched).

## Threat Flags

None. All trust-boundary concerns from the plan's threat model are mitigated:
- **T-59-07-01 (tampering):** `excuseType` is picked from a closed list of 6
  typed options in the Angular union; backend enforces the same enum.
- **T-59-07-02 (info disclosure):** `getExcuseTickets()` calls `/excuses/me`
  which is JWT-scoped; no cross-user data is ever requested client-side.

## Commits

- `f49daf3` — feat(59-07): wire StudentApiService to excuse-tickets backend
- `833d05b` — feat(59-07): add ExcuseType dropdown and live ticket list UI

## Self-Check: PASSED

All declared artifacts verified on disk:
- FOUND: frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts
- FOUND: frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.html
- FOUND: frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts
- FOUND: frontends/web-panel/src/app/features/student/excuses/student-excuses.component.html
- FOUND: frontends/web-panel/src/app/features/student/excuses/student-excuses.component.css
- FOUND: frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-api.service.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
- FOUND commit f49daf3
- FOUND commit 833d05b
- TESTS: 358/358 green (baseline 346 + 12 new)

## Notes for Plan 59-08 (Headman UI)

- `EXCUSE_TYPE_LABELS` is exported from `student-schedule.types.ts` and can be
  imported into the headman feature as-is.
- `ExcuseTicket` interface is the full backend DTO — can be reused directly by
  `headman-api.service.ts` (which already has its own `getGroupExcuses`).
- `statusLabels` pattern (`submitted → На рассмотрении` etc.) should match
  between student and headman views.
- CSS status chip variants (`--submitted`, `--approved`, `--rejected`,
  `--draft`) are currently scoped to `student-excuses.component.css`; consider
  promoting them to a shared stylesheet if the headman view needs them.
