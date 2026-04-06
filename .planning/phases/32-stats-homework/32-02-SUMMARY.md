---
phase: 32-stats-homework
plan: "02"
subsystem: PWA frontend — homework tracking feature
tags: [react, tanstack-query, homework, optimistic-updates, pull-to-refresh, routing]
dependency_graph:
  requires:
    - features/schedule/api.ts (useSubjectName re-export)
    - shared/hooks/usePullToRefresh.ts (Plan 01)
    - shared/components/LoadingSpinner.tsx
    - shared/components/OfflineBanner.tsx
  provides:
    - features/homework/types.ts
    - features/homework/api.ts
    - features/homework/HomeworkPage.tsx
    - features/homework/HomeworkItem.tsx
  affects:
    - main.tsx (HomeworkPage lazy import + /homework route)
tech_stack:
  added: []
  patterns:
    - TanStack Query optimistic mutation with onMutate/onError/onSettled pattern
    - Per-item error state via errorMap Record<number, string>
    - Enabled guard: !!groupId && !!semesterId (chain dependency)
    - Motion layout animation for list reorder on completion toggle
    - Re-export pattern for useSubjectName convenience
key_files:
  created:
    - frontends/pwa/src/features/homework/types.ts
    - frontends/pwa/src/features/homework/api.ts
    - frontends/pwa/src/features/homework/HomeworkPage.tsx
    - frontends/pwa/src/features/homework/HomeworkItem.tsx
    - frontends/pwa/src/features/homework/__tests__/api.test.tsx
    - frontends/pwa/src/features/homework/__tests__/HomeworkItem.test.tsx
  modified:
    - frontends/pwa/src/main.tsx
decisions:
  - No deadline field in HomeworkResponse — omitted per Research (backend DTO has no deadline)
  - Optimistic toggle stores per-id error in local errorMap rather than global toast
  - useToggleHomework takes groupId+semesterId as mutation vars to correctly target cache key
  - useSubjectName re-exported from homework/api for consumer convenience
  - Sort: undone items first by createdAt desc, done items below by createdAt desc
metrics:
  duration: ~5 minutes
  completed: 2026-04-06
  tasks: 2
  files: 7
---

# Phase 32 Plan 02: Homework List Page Summary

**One-liner:** Homework list page with server-side POST/DELETE completion toggle, TanStack Query optimistic updates with per-item error revert, pull-to-refresh, and /homework route registered as lazy import in main.tsx.

## What Was Built

### Task 1: Types, useActiveSemester, useHomework hooks (TDD)

- **`features/homework/types.ts`** — `HomeworkResponse` interface: id, title, description, link, subjectId, groupId, semesterId, publishedBy, completed, createdAt. No `deadline` field (not in backend DTO).
- **`features/homework/api.ts`** — Four exports:
  - `useActiveSemester()` — fetches `/academic/semesters?size=50`, finds `active=true` entry, returns `number | null`. `staleTime: 24hr`.
  - `useHomework(groupId, semesterId)` — fetches `/academic/homeworks`, extracts `_embedded.homeworkResponseList` with fallback. `staleTime: 60min`, `refetchOnReconnect: true`, `enabled: !!groupId && !!semesterId` (waits for semesterId resolution).
  - `useToggleHomework()` — mutation with optimistic cache update: `onMutate` cancels queries and flips `completed` in cache; `onError` reverts; `onSettled` invalidates. Accepts `{ id, completed, groupId, semesterId }`.
  - Re-exports `useSubjectName` from `@/features/schedule/api`.
- **`features/homework/__tests__/api.test.tsx`** — 4 TDD tests: useActiveSemester resolves active semester id, useHomework returns normalized array from HATEOAS, useToggleHomework calls POST /complete for done, DELETE /complete for undone.

### Task 2: HomeworkPage, HomeworkItem, tests, route

- **`features/homework/HomeworkItem.tsx`** — Accessible checkbox (`role="checkbox"`, `aria-checked`), Phosphor `Check` icon (size 16, weight bold), title with `line-through opacity-60` when done, subject name via `useSubjectName`, inline error text in `text-destructive text-xs`. Motion spring animation on tap (`scale: 0.85 → 1`, `stiffness: 400, damping: 20`). `min-h-[56px]` touch target.
- **`features/homework/HomeworkPage.tsx`** — Full homework list page:
  - Calls `useActiveSemester()`, `useHomework()`, `useToggleHomework()`
  - Pull-to-refresh via `usePullToRefresh` with `ArrowCounterClockwise` spin indicator
  - `errorMap: Record<number, string>` for per-item inline errors
  - Sort: `[...homeworks].sort((a, b) => a.completed !== b.completed ? (a.completed ? 1 : -1) : new Date(b.createdAt) - new Date(a.createdAt))`
  - Motion stagger (staggerChildren: 0.04) + `layout` prop on each item for animated reorder
  - Loading / error / empty states with Russian copy
  - `pb-20` BottomNav clearance
- **`main.tsx`** — Added `HomeworkPage` lazy import and `{ path: 'homework', element: <Suspense>...<HomeworkPage /></Suspense> }` as AppShell child route.
- **`features/homework/__tests__/HomeworkItem.test.tsx`** — 5 tests:
  - HW-02 checkbox toggle fires onToggle(id, true)
  - HW-02 done item title has `line-through` class
  - HW-02 error prop renders inline "Не удалось сохранить"
  - HW-01 empty state renders "Нет заданий"
  - HW-01 list renders homework titles

## Test Results

```
Test Files  12 passed (12)
Tests       63 passed (63)
```

New tests added (9 total):
- `features/homework/__tests__/api.test.tsx` — 4 tests
- `features/homework/__tests__/HomeworkItem.test.tsx` — 5 tests

Previous baseline: 54 tests (10 files)

## Commits

| Hash | Message |
|------|---------|
| 9694244 | feat(32-02): homework types, useActiveSemester, useHomework, useToggleHomework |
| b989aa1 | feat(32-02): HomeworkPage, HomeworkItem, tests, and /homework route registration |

## Acceptance Criteria Verification

- [x] `types.ts` contains `export interface HomeworkResponse`
- [x] `types.ts` contains `completed: boolean`
- [x] `types.ts` does NOT contain `deadline`
- [x] `api.ts` contains `export function useActiveSemester`
- [x] `api.ts` contains `export function useHomework`
- [x] `api.ts` contains `export function useToggleHomework`
- [x] `api.ts` contains `staleTime: 60 * 60 * 1000`
- [x] `api.ts` contains `refetchOnReconnect: true`
- [x] `api.ts` contains `enabled: !!groupId && !!semesterId`
- [x] `api.ts` contains `onMutate`
- [x] `api.ts` contains `onError`
- [x] `api.ts` contains `/academic/homeworks/`
- [x] `HomeworkPage.tsx` contains `export function HomeworkPage`
- [x] `HomeworkPage.tsx` contains `useHomework`
- [x] `HomeworkPage.tsx` contains `useActiveSemester`
- [x] `HomeworkPage.tsx` contains `useToggleHomework`
- [x] `HomeworkPage.tsx` contains `usePullToRefresh`
- [x] `HomeworkPage.tsx` contains `Задания`
- [x] `HomeworkPage.tsx` contains `Нет заданий`
- [x] `HomeworkPage.tsx` contains `Не удалось сохранить`
- [x] `HomeworkItem.tsx` contains `role="checkbox"`
- [x] `HomeworkItem.tsx` contains `aria-checked`
- [x] `HomeworkItem.tsx` contains `Check`
- [x] `HomeworkItem.tsx` contains `line-through`
- [x] `HomeworkItem.tsx` contains `opacity-60`
- [x] `HomeworkItem.tsx` contains `useSubjectName`
- [x] `main.tsx` contains `HomeworkPage`
- [x] `main.tsx` contains `path: 'homework'`
- [x] `__tests__/HomeworkItem.test.tsx` exists and contains `onToggle` and `checkbox` and `Нет заданий`
- [x] All vitest tests pass (63/63)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all data is wired to real API hooks (useActiveSemester → useHomework → useToggleHomework).

## Threat Surface Scan

No new network endpoints introduced beyond what is in the plan's threat model. T-32-06 (DoS via rapid toggling) is addressed: `useToggleHomework` uses TanStack Query's built-in mutation serialization; `onSettled` always invalidates to keep cache consistent. No new trust boundaries created.

## Self-Check: PASSED

Files verified:
- FOUND: frontends/pwa/src/features/homework/types.ts
- FOUND: frontends/pwa/src/features/homework/api.ts
- FOUND: frontends/pwa/src/features/homework/HomeworkPage.tsx
- FOUND: frontends/pwa/src/features/homework/HomeworkItem.tsx
- FOUND: frontends/pwa/src/features/homework/__tests__/api.test.tsx
- FOUND: frontends/pwa/src/features/homework/__tests__/HomeworkItem.test.tsx
- FOUND: frontends/pwa/src/main.tsx (HomeworkPage + homework route confirmed)

Commits verified: 9694244, b989aa1 — both present in git log.
