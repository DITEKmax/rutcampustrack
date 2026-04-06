---
phase: 32-stats-homework
plan: "01"
subsystem: PWA frontend — attendance stats feature
tags: [react, tanstack-query, attendance, stats, red-zone, pull-to-refresh, routing]
dependency_graph:
  requires: []
  provides:
    - features/attendance/types.ts
    - features/attendance/api.ts
    - features/attendance/AttendanceStatsPage.tsx
    - features/attendance/SubjectStatRow.tsx
    - features/attendance/RedZoneBadge.tsx
    - features/attendance/AttendanceRecordsPage.tsx
    - features/attendance/AttendanceRecordRow.tsx
    - shared/hooks/usePullToRefresh.ts
  affects:
    - shared/components/BottomNav.tsx
    - main.tsx
tech_stack:
  added: []
  patterns:
    - TanStack Query hooks with 1hr staleTime + refetchOnReconnect
    - usePullToRefresh shared hook with touchstart/touchmove/touchend
    - Motion stagger animation (staggerChildren: 0.04)
    - Route state for cross-page subject name passing
key_files:
  created:
    - frontends/pwa/src/features/attendance/types.ts
    - frontends/pwa/src/features/attendance/api.ts
    - frontends/pwa/src/features/attendance/AttendanceStatsPage.tsx
    - frontends/pwa/src/features/attendance/SubjectStatRow.tsx
    - frontends/pwa/src/features/attendance/RedZoneBadge.tsx
    - frontends/pwa/src/features/attendance/AttendanceRecordsPage.tsx
    - frontends/pwa/src/features/attendance/AttendanceRecordRow.tsx
    - frontends/pwa/src/features/attendance/__tests__/api.test.tsx
    - frontends/pwa/src/features/attendance/__tests__/AttendanceStatsPage.test.tsx
    - frontends/pwa/src/shared/hooks/usePullToRefresh.ts
  modified:
    - frontends/pwa/src/shared/components/BottomNav.tsx
    - frontends/pwa/src/main.tsx
decisions:
  - useThreshold returns null on 404 — no red zone indicators shown per D-06
  - HomeworkPage route deferred to Plan 02 (component does not exist yet)
  - subjectName passed as route state to avoid extra API call in AttendanceRecordsPage
  - usePullToRefresh hook is reusable — Plan 02 HomeworkPage will use it too
metrics:
  duration: ~25 minutes
  completed: 2026-04-06
  tasks: 3
  files: 12
---

# Phase 32 Plan 01: Attendance Stats Pages Summary

**One-liner:** Attendance stats per subject with red zone threshold indicators, pull-to-refresh, 5-tab BottomNav, and /stats + /stats/:subjectId routing using TanStack Query hooks with 1hr staleTime.

## What Was Built

### Task 1: Foundation (types, hooks, infrastructure)

- **`features/attendance/types.ts`** — TypeScript interfaces: `SubjectStats`, `OverallStats`, `StudentStatsResponse`, `AttendanceRecordEntry`, `ResolvedThresholdResponse`
- **`features/attendance/api.ts`** — Three TanStack Query hooks:
  - `useStudentStats()` — fetches `/attendance/reports/student/stats`, `staleTime: 60min`, `refetchOnReconnect: true`
  - `useThreshold(groupId)` — fetches `/academic/thresholds/resolve`, returns `null` on 404 (per D-06), `staleTime: 24hr`
  - `useAttendanceRecords(subjectId)` — fetches `/attendance/reports/student/records`, extracts from `_embedded` with fallback, `staleTime: 60min`
- **`shared/hooks/usePullToRefresh.ts`** — Reusable pull-to-refresh hook using touchstart/touchmove/touchend event listeners; returns `containerRef`, `isRefreshing`, `pullDistance`
- **`shared/components/BottomNav.tsx`** — Restructured from 4 to 5 tabs: Статистика (`ChartBar`), Расписание (`Calendar`), Отметка (`Fingerprint`), Задания (`ClipboardText`), Профиль (`User`). Removed `House`/`/home`
- **`main.tsx`** — Index redirect changed from `/home` to `/schedule`. `/home` route removed. `/stats` and `/stats/:subjectId` routes added as lazy imports. HomeworkPage route deferred to Plan 02

### Task 2: AttendanceStatsPage components

- **`RedZoneBadge.tsx`** — Pill badge with `WarningCircle` icon, `bg-red-100 text-red-700`, text "Красная зона"
- **`SubjectStatRow.tsx`** — Full-width clickable card showing subject name, percentage (right-aligned with `CaretRight`), count breakdown (`б/н/у`). Red zone: `border-l-4 border-destructive`, percentage in `text-destructive`, `RedZoneBadge` inline. `min-h-[56px]`
- **`AttendanceStatsPage.tsx`** — Subject list page with: pull-to-refresh via `usePullToRefresh`, loading/error/empty states, Motion stagger animation, `OfflineBanner`, navigates to `/stats/:subjectId` with `subjectName` as route state

### Task 3: AttendanceRecordsPage components

- **`AttendanceRecordRow.tsx`** — Row with Russian date (`dd MMM` using `MONTH_ABBREV` array), lesson number (`N-я пара`), `StatusBadge` right-aligned. `min-h-[44px]`
- **`AttendanceRecordsPage.tsx`** — Per-subject record list sorted by `lessonDate` descending. Header with `CaretLeft` back button (`aria-label="Назад к статистике"`), subject name from route state. Loading/error/empty states. `pb-20` for BottomNav clearance

## Test Results

```
Test Files  10 passed (10)
Tests       54 passed (54)
```

New tests added:
- `features/attendance/__tests__/api.test.tsx` — 4 tests covering useStudentStats, useThreshold (null on 404, minPercentage on success), useAttendanceRecords
- `features/attendance/__tests__/AttendanceStatsPage.test.tsx` — 4 tests covering ATT-01 (stats display with counts), ATT-02 (red zone shown when below threshold), ATT-02 (hidden when threshold 404), empty state

## Commits

| Hash | Message |
|------|---------|
| 811f62c | feat(32-01): types, API hooks, usePullToRefresh, BottomNav 5 tabs, routing |
| d57e15a | feat(32-01): AttendanceStatsPage, SubjectStatRow, RedZoneBadge with red zone logic |
| 10c5433 | feat(32-01): AttendanceRecordsPage and AttendanceRecordRow with StatusBadge |

## Acceptance Criteria Verification

- [x] `types.ts` contains `SubjectStats`, `AttendanceRecordEntry`, `ResolvedThresholdResponse`
- [x] `api.ts` contains `useStudentStats`, `useThreshold`, `useAttendanceRecords`
- [x] `api.ts` contains `staleTime: 60 * 60 * 1000` and `refetchOnReconnect: true`
- [x] `usePullToRefresh.ts` contains `touchstart`, `touchmove`, `touchend`
- [x] `BottomNav.tsx` contains `ChartBar`, `ClipboardText`, `/stats`, `/homework`
- [x] `BottomNav.tsx` does NOT contain `House` or `/home`
- [x] `main.tsx` contains `Navigate to="/schedule"`, `path: 'stats'`, `path: 'stats/:subjectId'`
- [x] `main.tsx` does NOT contain lazy import for `HomeworkPage` or `path: 'home'`
- [x] `AttendanceStatsPage.tsx` contains `useStudentStats`, `useThreshold`, `usePullToRefresh`, `Посещаемость`, `Нет данных`, `navigate(`
- [x] `SubjectStatRow.tsx` contains `border-destructive`, `RedZoneBadge`, `CaretRight`, `threshold`
- [x] `RedZoneBadge.tsx` contains `Красная зона`, `WarningCircle`
- [x] `AttendanceRecordsPage.tsx` contains `useAttendanceRecords`, `useParams`, `CaretLeft`, `aria-label="Назад к статистике"`, `Нет записей`
- [x] `AttendanceRecordRow.tsx` contains `StatusBadge`, `MONTH_ABBREV`, `lessonNumber`
- [x] All vitest tests pass (54/54)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all data is wired to real API hooks.

## Threat Surface Scan

No new network endpoints or auth paths introduced beyond what is in the plan's threat model. The three frontend pages only read data from existing backend endpoints (`/attendance/reports/student/stats`, `/attendance/reports/student/records`, `/academic/thresholds/resolve`) and backend enforces JWT-based access control server-side (T-32-01, T-32-02, T-32-03 accepted per threat model).

## Self-Check: PASSED

Files verified:
- FOUND: frontends/pwa/src/features/attendance/types.ts
- FOUND: frontends/pwa/src/features/attendance/api.ts
- FOUND: frontends/pwa/src/features/attendance/AttendanceStatsPage.tsx
- FOUND: frontends/pwa/src/features/attendance/SubjectStatRow.tsx
- FOUND: frontends/pwa/src/features/attendance/RedZoneBadge.tsx
- FOUND: frontends/pwa/src/features/attendance/AttendanceRecordsPage.tsx
- FOUND: frontends/pwa/src/features/attendance/AttendanceRecordRow.tsx
- FOUND: frontends/pwa/src/shared/hooks/usePullToRefresh.ts
- FOUND: frontends/pwa/src/features/attendance/__tests__/api.test.tsx
- FOUND: frontends/pwa/src/features/attendance/__tests__/AttendanceStatsPage.test.tsx

Commits verified: 811f62c, d57e15a, 10c5433 — all present in git log.
