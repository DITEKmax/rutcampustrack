---
phase: 37-mini-app-features
plan: 01
subsystem: ui
tags: [react, tanstack-query, typescript, telegram-mini-app, phosphor-icons]

# Dependency graph
requires:
  - phase: 36-mini-app-scaffold-auth
    provides: AuthProvider, axios apiClient, SDK mocks in setup.ts, mini-app scaffold
provides:
  - useBackButton hook (Telegram BackButton lifecycle management)
  - useMainButton hook (Telegram MainButton params + click handler)
  - BottomNav component (3-tab fixed navigation)
  - schedule/types.ts + schedule/api.ts + schedule/StatusBadge.tsx
  - checkin/types.ts + checkin/api.ts (useCheckin + mapCheckinError)
  - stats/types.ts + stats/api.ts (useStudentStats + RED_ZONE_THRESHOLD)
  - homework/types.ts + homework/api.ts (useActiveSemester, useHomeworkList, useToggleHomework)
affects: [37-02-mini-app-feature-pages]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Feature API hooks co-located with feature types in features/{feature}/api.ts"
    - "Optimistic mutations with onMutate/onError/onSettled lifecycle in TanStack Query"
    - "Telegram SDK hooks wrap component logic: show on mount, cleanup on unmount"

key-files:
  created:
    - frontends/mini-app/src/shared/hooks/useBackButton.ts
    - frontends/mini-app/src/shared/hooks/useMainButton.ts
    - frontends/mini-app/src/shared/hooks/__tests__/useBackButton.test.ts
    - frontends/mini-app/src/shared/components/BottomNav.tsx
    - frontends/mini-app/src/features/schedule/types.ts
    - frontends/mini-app/src/features/schedule/api.ts
    - frontends/mini-app/src/features/schedule/StatusBadge.tsx
    - frontends/mini-app/src/features/checkin/types.ts
    - frontends/mini-app/src/features/checkin/api.ts
    - frontends/mini-app/src/features/stats/types.ts
    - frontends/mini-app/src/features/stats/api.ts
    - frontends/mini-app/src/features/homework/types.ts
    - frontends/mini-app/src/features/homework/api.ts
  modified:
    - frontends/mini-app/src/test/setup.ts (added backButton + mainButton SDK mocks)

key-decisions:
  - "backButton/mainButton mocks added to shared setup.ts so all future hook tests get them automatically"
  - "RED_ZONE_THRESHOLD = 60 hardcoded as constant pending backend threshold API"

patterns-established:
  - "SDK hook pattern: check isAvailable() before calling SDK method, return unsubscribe from onClick, hide in cleanup"
  - "Feature API: co-located types.ts + api.ts per feature, @/shared/lib/axios for apiClient"

requirements-completed: [TMA-06, TMA-07, TMA-08, TMA-09, TMA-10, TMA-11]

# Metrics
duration: 20min
completed: 2026-04-07
---

# Phase 37 Plan 01: Shared Hooks, API Layers, Types & Bottom Navigation Summary

**Telegram Mini App foundation layer: 2 SDK hooks, 4 feature API modules (schedule/checkin/stats/homework), StatusBadge, and BottomNav — complete data and navigation layer for feature pages**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-07T07:00:00Z
- **Completed:** 2026-04-07T07:10:00Z
- **Tasks:** 6/6
- **Files modified:** 14 (13 created, 1 modified)

## Accomplishments

- useBackButton and useMainButton hooks wrap Telegram SDK with proper isAvailable guards and cleanup
- All 4 feature API modules created: schedule (today's lessons), checkin (geo POST + error mapping), stats (student stats + red zone constant), homework (active semester + list + optimistic toggle)
- BottomNav component with 3 tabs, Phosphor fill/regular icon toggle, iOS safe area padding
- StatusBadge renders 8 status values (4 lesson + 4 attendance) with correct Russian labels and Tailwind classes
- All 12 tests pass (3 new useBackButton + 9 Phase 36 regression)

## Task Commits

1. **Task 37-01-01: Shared hooks + tests** - `49f869a` (feat)
2. **Task 37-01-02: Schedule types, API, StatusBadge** - `35a1a7c` (feat)
3. **Task 37-01-03: Checkin types and API** - `6cb4d0a` (feat)
4. **Task 37-01-04: Stats types and API** - `a8ccc47` (feat)
5. **Task 37-01-05: Homework types and API** - `bb27b6a` (feat)
6. **Task 37-01-06: BottomNav component** - `a0e9cc7` (feat)

## Files Created/Modified

- `src/shared/hooks/useBackButton.ts` - Shows/hides Telegram BackButton, navigates -1 on click
- `src/shared/hooks/useMainButton.ts` - Sets MainButton params, registers click handler, cleans up
- `src/shared/hooks/__tests__/useBackButton.test.ts` - 3 tests: show on mount, onClick registration, hide on unmount
- `src/test/setup.ts` - Extended with backButton and mainButton SDK mocks
- `src/shared/components/BottomNav.tsx` - Fixed 3-tab nav: /, /stats, /homework
- `src/features/schedule/types.ts` - LessonResponse, LessonStatus, AttendanceStatus, SubjectResponse
- `src/features/schedule/api.ts` - useTodaySchedule (today-only, sorted by lessonNumber), useSubjectName
- `src/features/schedule/StatusBadge.tsx` - 8-status badge with Russian labels and color classes
- `src/features/checkin/types.ts` - CheckinRequest {lat, lng}
- `src/features/checkin/api.ts` - useCheckin mutation + mapCheckinError for 403/404/409/422/429
- `src/features/stats/types.ts` - SubjectStats, OverallStats, StudentStatsResponse
- `src/features/stats/api.ts` - useStudentStats + RED_ZONE_THRESHOLD = 60
- `src/features/homework/types.ts` - HomeworkResponse, SemesterResponse
- `src/features/homework/api.ts` - useActiveSemester, useHomeworkList, useToggleHomework (optimistic)

## Decisions Made

- Extended `setup.ts` with backButton/mainButton mocks using `Object.assign(vi.fn(), { isAvailable: ... })` pattern so methods remain callable while the `isAvailable` property exists — needed to match SDK's function-with-property API shape.
- `RED_ZONE_THRESHOLD = 60` exported as named constant (not hardcoded inline) so Plan 37-02 pages can import it consistently.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added backButton and mainButton to SDK mock in setup.ts**
- **Found during:** Task 37-01-01 (useBackButton hook test)
- **Issue:** setup.ts mock didn't include backButton or mainButton — tests would fail with "backButton.show is not a function"
- **Fix:** Added both to the vi.mock() factory with show/hide/onClick/setParams mocks using the function-with-isAvailable pattern
- **Files modified:** frontends/mini-app/src/test/setup.ts
- **Verification:** All 3 useBackButton tests pass
- **Committed in:** 49f869a (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 2 — missing test infrastructure)
**Impact on plan:** Required for test correctness. No scope creep.

## Issues Encountered

- vitest not in PATH when running `npx vitest run` directly — needed `npm install` first, then ran via `./node_modules/.bin/vitest`. Tests passed correctly after install.

## Known Stubs

- `RED_ZONE_THRESHOLD = 60` in `stats/api.ts` — hardcoded constant, plan intentionally notes "TODO: use backend threshold when available". Plan 37-02 consumes this constant; a future plan should wire it to the threshold API.

## Next Phase Readiness

- All data hooks and types ready for Plan 37-02 feature pages (SchedulePage, CheckinPage, StatsPage, HomeworkPage)
- BottomNav ready to be wired into App.tsx layout
- StatusBadge ready for use in SchedulePage lesson cards
- No blockers

## Self-Check: PASSED

All 13 created files found on disk. All 6 commits verified in git log. 12/12 tests pass.

---
*Phase: 37-mini-app-features*
*Completed: 2026-04-07*
