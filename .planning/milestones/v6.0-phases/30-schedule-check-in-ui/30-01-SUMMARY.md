---
phase: 30-schedule-check-in-ui
plan: 01
subsystem: pwa-schedule
tags: [pwa, schedule, react, tanstack-query, stomp]
dependency_graph:
  requires: [29-pwa-scaffold-auth]
  provides: [schedule-types, schedule-api-hooks, schedule-page, checkin-types, checkin-api-hooks]
  affects: [frontends/pwa]
tech_stack:
  added: ["@stomp/stompjs", "sockjs-client", "@types/sockjs-client"]
  patterns: ["TanStack Query hooks with staleTime overrides", "motion/react drag gestures for swipe nav", "prefetch subjects to avoid N+1 waterfall"]
key_files:
  created:
    - frontends/pwa/src/features/schedule/types.ts
    - frontends/pwa/src/features/schedule/api.ts
    - frontends/pwa/src/features/schedule/StatusBadge.tsx
    - frontends/pwa/src/features/schedule/LessonCard.tsx
    - frontends/pwa/src/features/schedule/OfflineStaleNotice.tsx
    - frontends/pwa/src/features/schedule/WeekDayTabs.tsx
    - frontends/pwa/src/features/schedule/SchedulePage.tsx
    - frontends/pwa/src/features/checkin/types.ts
    - frontends/pwa/src/features/checkin/api.ts
    - frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx
    - frontends/pwa/src/features/schedule/__tests__/OfflineStaleNotice.test.tsx
    - frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx
    - frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts
  modified:
    - frontends/pwa/package.json
    - frontends/pwa/src/test/setup.ts
    - frontends/pwa/src/main.tsx
decisions:
  - "Used static import for SchedulePage in tests (not dynamic) to avoid timeout issues with jsdom requestAnimationFrame"
  - "Added scrollIntoView mock to test setup since jsdom doesn't support it"
  - "Added explicit vi import in setup.ts for tsc -b compatibility (vitest globals not visible to TypeScript build)"
metrics:
  duration: "~9 min"
  completed: "2026-04-06"
  tasks: 3
  files_created: 13
  files_modified: 3
  tests_added: 11
  tests_total: 20
---

# Phase 30 Plan 01: Schedule View Feature Summary

Schedule page with types, API hooks, all UI components (SchedulePage, WeekDayTabs, LessonCard, StatusBadge, OfflineStaleNotice), and router wiring at /home and /schedule with offline caching, swipe navigation, and auto-scroll to current lesson.

## One-liner

Schedule view with Mon-Sat day tabs, week navigation, lesson cards with subject name resolution, offline stale notice with dynamic timestamps, and swipe gestures for day/week changes.

## What Was Built

### Task 1: Types, API hooks, test infrastructure, and npm deps
- **Types**: `LessonResponse` and `SubjectResponse` matching backend Java contracts exactly
- **Schedule API hooks**: `useWeekSchedule` (staleTime 1hr, refetchOnReconnect), `useSubjectName` (staleTime 24hr), `usePrefetchSubjects` (dedup via queryKey)
- **Checkin types**: `CheckinRequest`, `AttendanceMarkedPayload`
- **Checkin API**: `useCheckin` mutation, `mapCheckinError` with all HTTP status mappings
- **Test setup**: Navigator geolocation mock, onLine mock, scrollIntoView mock, explicit vi import
- **STOMP deps**: @stomp/stompjs + sockjs-client installed
- **Test stubs**: Scaffold tests for SchedulePage, OfflineStaleNotice, CheckInButton, useStompCheckin

### Task 2: StatusBadge, LessonCard, and OfflineStaleNotice components
- **StatusBadge**: 8 status variants (present/absent/excused/free_attendance/ACTIVE/PLANNED/CANCELLED/CLOSED) with correct colors and Russian labels
- **LessonCard**: Time range (HH:mm format), subject name (resolved via useSubjectName hook), room, status badge, check-in slot for ACTIVE lessons, opacity-60 for cancelled, data-lesson-id for auto-scroll targeting, motion.div mount animation
- **OfflineStaleNotice**: Dynamic relative timestamp "Офлайн · обновлено N мин назад" with 60-second interval refresh, "Нет данных" when no cache, hidden when online, AnimatePresence transitions

### Task 3: SchedulePage with WeekDayTabs, swipe navigation, auto-scroll, and router wiring
- **WeekDayTabs**: Mon-Sat only (no Sunday per D-01), date numbers, active tab highlight, swipe-to-change-week (80px threshold), ARIA tablist/tab roles, 44px touch targets, sticky positioning
- **SchedulePage**: Week arrow navigation with CaretLeft/CaretRight, formatted week range in Russian, auto-scroll to current/next lesson on mount (D-03), swipe between days (50px threshold) with dragDirectionLock, floating "Сегодня" pill for non-current weeks (D-04), OfflineStaleNotice with dataUpdatedAt from query (D-15), staggerChildren animation (0.04s), empty state "Занятий нет", loading spinner, subject prefetch to avoid waterfall (D-05)
- **Router**: /home and /schedule both render SchedulePage, /checkin remains HomePlaceholder for Plan 02

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 (RED) | e3ad349 | test(30-01): add failing tests for schedule types, API hooks, and checkin stubs |
| 2 | dda22f9 | feat(30-01): add StatusBadge, LessonCard, and OfflineStaleNotice components |
| 3 (GREEN) | 1331708 | feat(30-01): add SchedulePage with WeekDayTabs, swipe nav, auto-scroll, router wiring |

## Verification Results

1. `npx tsc --noEmit` - zero type errors
2. `npm run test` - 20 tests pass (6 test files)
3. `npm run build` - production build succeeds

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added scrollIntoView mock to test setup**
- **Found during:** Task 3
- **Issue:** jsdom does not implement Element.scrollIntoView, causing test errors
- **Fix:** Added `Element.prototype.scrollIntoView = vi.fn()` to test/setup.ts
- **Files modified:** frontends/pwa/src/test/setup.ts

**2. [Rule 3 - Blocking] Added explicit vi import in setup.ts**
- **Found during:** Task 3 build verification
- **Issue:** `tsc -b` does not see vitest globals, causing build failure on `vi.fn()` calls in setup.ts
- **Fix:** Added `import { vi } from 'vitest'` at top of setup.ts
- **Files modified:** frontends/pwa/src/test/setup.ts

**3. [Rule 1 - Bug] Fixed unused imports in CheckInButton.test.tsx**
- **Found during:** Task 3 build verification
- **Issue:** `noUnusedLocals` flag caught unused render/screen/userEvent imports
- **Fix:** Removed unused imports
- **Files modified:** frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx

**4. [Rule 1 - Bug] Used static import for SchedulePage in tests**
- **Found during:** Task 3 test execution
- **Issue:** Dynamic import (`await import('../SchedulePage')`) combined with requestAnimationFrame caused 5s timeout in jsdom
- **Fix:** Changed to static import at module level
- **Files modified:** frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx

## Known Stubs

None. All components render real data from API hooks. CheckInButton and useStompCheckin test files are intentional stubs per plan (Plan 02 will implement those components).

## Threat Flags

None. No new network endpoints introduced beyond those specified in the plan. React auto-escapes all subject name rendering (T-30-02 mitigated). Subject prefetch deduplication via queryKey prevents N+1 waterfall (T-30-03 mitigated).

## Self-Check: PASSED

All 13 created files verified on disk. All 3 commit hashes (e3ad349, dda22f9, 1331708) found in git log.
