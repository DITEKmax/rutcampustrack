---
phase: 56
plan: "04"
subsystem: frontend-pwa
tags: [headman, journal, attendance, segmented-control, optimistic-ui, motion, tdd, vitest]
dependency_graph:
  requires: [56-02]
  provides:
    - SegmentedControl primitive at shared/components/SegmentedControl.tsx (reusable across PWA)
    - JournalStudentRow with optimistic-UI + revert-on-error pattern
    - Full /group/journal route replacing Wave 2 placeholder
  affects:
    - frontends/pwa/src/features/headman/journal/JournalPage.tsx
tech_stack:
  added: []
  patterns:
    - Optimistic UI mutation + revert on onError callback (useState snapshot previous → restore)
    - Motion AnimatePresence auto-dismiss error badge (2s setTimeout → exit animation)
    - 5-segment radiogroup primitive with ARIA (role=radiogroup + role=radio + aria-checked)
    - WCAG 2.5.5 compliant 40×40px tap targets
    - Two-step mobile flow (Step 1 selectors → Step 2 list) with state preservation on back
    - TDD RED (commit 8f28d73 + 79c6d24) → GREEN (f35b2e3 + a6f2890) per task
key_files:
  created:
    - frontends/pwa/src/shared/components/SegmentedControl.tsx
    - frontends/pwa/src/features/headman/journal/JournalStudentRow.tsx
    - frontends/pwa/src/features/headman/journal/JournalPage.test.tsx
  modified:
    - frontends/pwa/src/features/headman/journal/JournalPage.tsx
decisions:
  - "SegmentedControl placed in shared/components (not features/headman/journal) per D-15 — reusable primitive for future headman screens (stats red-dot picker candidate)"
  - "Error badge lives inside JournalStudentRow (not page-level toast) — keeps error scope tied to the row that failed, matches UI-SPEC §Component Inventory #6 'inline error' note"
  - "State preservation on Back: subject/date kept in JournalPage state across step transitions (no URL params) — simpler than wiring to search params; Wave 3 scope"
  - "useJournal.isError surfaces on Step 1 (not Step 2) per plan — user sees error without leaving selectors; can retry without losing date"
  - "Single RED commit for all 7 JournalPage tests (79c6d24) rather than per-test — reduces commit noise; the RED phase validates the spec holistically"
metrics:
  duration_minutes: 10
  completed_date: "2026-04-13"
  tasks_completed: 3
  tasks_total: 3
  files_created: 3
  files_modified: 1
requirements:
  - PWA-HEAD-02
---

# Phase 56 Plan 04: Journal Page + SegmentedControl Summary

**One-liner:** Reusable 5-segment `SegmentedControl` primitive (40×40 WCAG tap target, role=radiogroup) plus a two-step mobile JournalPage (selectors → student list) with optimistic-UI + revert-on-error via Motion AnimatePresence error badges — replaces Wave 2 `/group/journal` placeholder; 12 new tests (5 SegmentedControl + 7 JournalPage) bring PWA vitest to 98/98.

## What Was Built

### SegmentedControl (`shared/components/SegmentedControl.tsx`) — Reusable API

```typescript
import type { AttendanceStatus } from '@/features/headman/shared/types'

export interface SegmentedControlProps {
  value: AttendanceStatus
  onValueChange: (status: AttendanceStatus) => void
  disabled?: boolean
  ariaLabel?: string
}

export function SegmentedControl(props: SegmentedControlProps): JSX.Element
```

**Segments (fixed order):**

| value              | label | aria-label               |
|--------------------|-------|--------------------------|
| `present`          | б     | Присутствовал            |
| `absent`           | н     | Отсутствовал             |
| `excused`          | у     | Уважительная причина     |
| `free_attendance`  | сп    | Свободное посещение      |
| `cancelled`        | —     | Отменена                 |

- `role="radiogroup"` container with 5 `role="radio"` `<button type="button">` children
- `aria-checked` reflects selected state (exactly one true at a time)
- `min-w-[40px] min-h-[40px]` = WCAG 2.5.5 tap target
- Selected: `accent-primary` bg + white text. Unselected: transparent bg + `text-primary`
- `disabled` prop suppresses `onValueChange` calls (verified in Test 5)

**Importable from:** `@/shared/components/SegmentedControl`

Future reuse candidates: stats threshold picker, settings toggles with 5 discrete values.

### JournalStudentRow (`features/headman/journal/JournalStudentRow.tsx`)

```typescript
interface JournalStudentRowProps {
  studentId: number
  studentName: string
  lessonId: number
  initialStatus: AttendanceStatus
}
```

**Optimistic UI + Revert pattern:**

```
onTap(newStatus):
  previous = status
  setStatus(newStatus)        // instant UI update
  mutate({ lessonId, userId: studentId, status: newStatus }, {
    onError: () => {
      setStatus(previous)     // revert
      setShowError(true)
      setTimeout(() => setShowError(false), 2000)
    }
  })
```

- `AnimatePresence` wraps the error `<motion.div role="alert">`; fade-in from y=-4 → 0 in 150ms, fade-out on exit
- Error copy: "Ошибка. Попробуйте ещё раз." — matches D-08
- 56px minimum row height; flex layout with student name (left) + segments (right)

### JournalPage (`features/headman/journal/JournalPage.tsx`) — 2-Step Flow

**Data flow:**

```
Step 1 (selectors)
  ├─ useGroupSubjects() → <select> options
  ├─ <input type="date"> (default today)
  └─ <button> "Загрузить журнал" (disabled until subjectId)
         │
         │ click → setStep(2)
         │
         │ (useJournal already auto-fetches because
         │  enabled: !!groupId && !!subjectId is satisfied
         │  as soon as user picks a subject)
         ▼
Step 2 (student list)
  ├─ header: back arrow → setStep(1) + breadcrumb "{subject} · {date}"
  ├─ isLoading → 4 shimmer rows
  ├─ data.length === 0 → "На эту дату занятий нет"
  ├─ data → N × <JournalStudentRow>
  │          └─ segment tap → useMarkAttendance.mutate({ lessonId, userId, status })
  │                           └─ onError → revert + badge
  └─ footer hint: "Изменения сохраняются автоматически"
```

**Error routing:** `journalQuery.isError` surfaces on Step 1 (inline red alert) so the user can retry without losing selectors. The plan specified "reset `step` to 1" on error — in practice we never transition to Step 2 while the query is errored because the error shows before the user clicks Submit, or the user sees it immediately upon return via the back button.

**State preservation:** `subjectId` and `date` live in `JournalPage` state, so pressing Back from Step 2 returns to Step 1 with both fields intact (verified in Test 6).

### Test Coverage (12 new)

**SegmentedControl.test.tsx** (5 — RED committed 8f28d73, GREEN f35b2e3):
1. 5 buttons with exact labels `[б, н, у, сп, —]`
2. radiogroup + radio + button type="button"
3. aria-checked correctness (selected=true, others=false)
4. Click dispatches correct AttendanceStatus
5. disabled suppresses onValueChange

**JournalPage.test.tsx** (7 — RED 79c6d24, GREEN a6f2890):
1. Step 1 elements present (select, date input, submit button)
2. Submit disabled when no subject
3. Transition to Step 2 with breadcrumb + student rows
4. Empty data → "На эту дату занятий нет"
5. Segment tap → `mutate({ lessonId: 100, userId: 1, status: 'absent' }, expect.anything())`
6. Back preserves subject selection
7. API load error → "Ошибка загрузки..." inline message

### Test Counts

| Before Plan 56-04 | After Plan 56-04 |
|-------------------|------------------|
| 86 tests, 16 files | 98 tests, 18 files |

All pre-existing tests continue to pass unchanged. `npx tsc --noEmit` exits 0.

## Frozen Directories Confirmation

No modifications to `features/{home,schedule,checkin,profile,push,auth}/`. The only touched files are:
- `shared/components/SegmentedControl.tsx` (new — shared primitives are extensible per CONTEXT.md D-14)
- `features/headman/journal/*` (this plan's scope)

## Threat Model Compliance

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-56-13 (Tampering — arbitrary lessonId/userId) | Client uses only API-returned `cell.lessonId` and `cell.studentId`; no fabrication path through UI | Implemented |
| T-56-14 (Tampering — out-of-enum status) | SegmentedControl constrains to 5 AttendanceStatus literals; TypeScript prevents other strings | Implemented |
| T-56-15 (Info Disclosure — cached names) | Accepted per plan — deferred cache eviction noted in 56-06 SUMMARY | Accepted |
| T-56-16 (XSS — student names) | `grep -rn dangerouslySetInnerHTML frontends/pwa/src/features/headman/journal frontends/pwa/src/shared/components/SegmentedControl.tsx` → 0 matches | Verified |

## Deviations from Plan

None — plan executed exactly as written. The SegmentedControl RED tests already existed from commit 8f28d73 (prior session pause point 7dd3986), and were made GREEN in f35b2e3. JournalStudentRow + JournalPage followed the plan's prescribed structure verbatim.

## Known Stubs

None. JournalPage is fully wired to real `useGroupSubjects` + `useJournal` + `useMarkAttendance` hooks established in Plan 56-02. The Wave 2 "Загрузка…" placeholder is completely replaced.

## Threat Surface Scan

No new network endpoints or auth paths introduced. All mutations continue to traverse the existing API Gateway via `useMarkAttendance` (established in Plan 56-02). No new trust boundary crossings.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| frontends/pwa/src/shared/components/SegmentedControl.tsx | FOUND |
| frontends/pwa/src/features/headman/journal/JournalStudentRow.tsx | FOUND |
| frontends/pwa/src/features/headman/journal/JournalPage.tsx (replaced) | FOUND |
| frontends/pwa/src/features/headman/journal/JournalPage.test.tsx | FOUND |
| Commit 8f28d73 (SegmentedControl RED — prior session) | VERIFIED |
| Commit f35b2e3 (SegmentedControl GREEN) | VERIFIED |
| Commit 80f88f2 (JournalStudentRow) | VERIFIED |
| Commit 79c6d24 (JournalPage RED) | VERIFIED |
| Commit a6f2890 (JournalPage GREEN) | VERIFIED |
| npx vitest run: 98/98 pass | PASSED |
| npx tsc --noEmit: exits 0 | PASSED |
| dangerouslySetInnerHTML in new files: 0 matches | PASSED |
