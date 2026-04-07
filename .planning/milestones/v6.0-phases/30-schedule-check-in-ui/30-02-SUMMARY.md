---
phase: 30-schedule-check-in-ui
plan: 02
subsystem: pwa-checkin
tags: [pwa, checkin, stomp, websocket, gps, react]
dependency_graph:
  requires: [30-01]
  provides: [checkin-button, checkin-toast, stomp-provider, checkin-screen, use-stomp-checkin]
  affects: [frontends/pwa]
tech_stack:
  added: []
  patterns: ["STOMP over SockJS with token factory for reconnect safety", "Shared StompProvider context at AppShell level", "GPS capture with geolocation API timeout/maximumAge", "motion.span key-based number flip animation"]
key_files:
  created:
    - frontends/pwa/src/features/checkin/CheckInButton.tsx
    - frontends/pwa/src/features/checkin/CheckInToast.tsx
    - frontends/pwa/src/features/checkin/useStompCheckin.ts
    - frontends/pwa/src/features/checkin/StompProvider.tsx
    - frontends/pwa/src/features/checkin/CheckInScreen.tsx
  modified:
    - frontends/pwa/src/features/schedule/LessonCard.tsx
    - frontends/pwa/src/features/schedule/SchedulePage.tsx
    - frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx
    - frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx
    - frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts
    - frontends/pwa/src/main.tsx
decisions:
  - "Attendance count starts at 0 and increments only via STOMP events (no backend count endpoint exists per RESEARCH.md Open Question 1)"
  - "StompProvider placed inside ProtectedRoute wrapping AppShell so single STOMP connection serves all tabs per D-11"
  - "useStompCheckin uses getAccessToken factory function (not direct token) to avoid stale token on reconnect per Pitfall 7"
  - "Added StompProvider mock to SchedulePage tests to avoid context-missing errors"
metrics:
  duration: "~10 min"
  completed: "2026-04-06"
  tasks: 3
  files_created: 5
  files_modified: 6
  tests_added: 13
  tests_total: 31
---

# Phase 30 Plan 02: Check-in Flow Feature Summary

Geo check-in flow with CheckInButton (GPS capture + submit), CheckInToast feedback, shared StompProvider for real-time attendance updates via single STOMP WebSocket, and CheckInScreen dedicated /checkin tab with active lesson or empty state.

## One-liner

GPS check-in with D-09 exact error copy, shared STOMP context at AppShell level for real-time attendance count animation across schedule and check-in tabs, and dedicated CheckInScreen with active/empty states.

## What Was Built

### Task 1: CheckInButton with GPS capture and CheckInToast feedback (TDD)
- **CheckInButton**: Captures GPS via `getCurrentPosition` with timeout:10000 maximumAge:30000, submits to useCheckin mutation, shows spinner with aria-busy during capture, disabled when offline via useNetworkStatus
- **GPS error handling**: Permission denied shows exact D-09 text "Нет доступа к GPS. Разрешите доступ в настройках браузера"
- **API error mapping**: 404 (no active lesson), 409 (already marked), 422 (not in zone) via mapCheckinError
- **CheckInToast**: Slide-up motion.div with success (3000ms auto-dismiss, role=status) and error (5000ms, role=alert), positioned at bottom-16 above bottom nav, green/destructive styling
- **Tests**: 10 tests covering GPS capture, spinner, offline disable, success/error callbacks, toast auto-dismiss timers

### Task 2: StompProvider shared context, useStompCheckin hook, and LessonCard/SchedulePage integration (TDD)
- **useStompCheckin**: Creates @stomp/stompjs Client with SockJS webSocketFactory reading token via factory function at connect time (Pitfall 7 mitigation), subscribes to /topic/group/{groupId}, filters attendance.marked events, calls onMarked callback, deactivates on cleanup
- **StompProvider**: React context at AppShell level (per D-11 single connection), tracks attendanceCounts and personalStatuses per lesson, handles STOMP events to increment counts and detect current user check-ins
- **LessonCard**: Renders CheckInButton for ACTIVE lessons without personalStatus, shows Check icon + StatusBadge after check-in (D-08), motion.span count animation with stiffness:300 damping:30
- **SchedulePage**: Consumes useStompEvents (not useStompCheckin directly), passes attendance data to LessonCard, renders CheckInToast with AnimatePresence
- **main.tsx**: StompProvider wraps AppShell inside ProtectedRoute
- **Tests**: 5 tests for useStompCheckin (factory, subscribe, onMarked, ignore non-matching events, cleanup)

### Task 3: CheckInScreen dedicated tab consuming shared StompProvider
- **CheckInScreen**: Shows active lesson with subject name, time, room, status badge, CheckInButton, and attendance count; or empty state "Сейчас нет активных пар" with next lesson hint or "На сегодня пар больше нет"
- **Consumes shared StompProvider**: Uses useStompEvents (no separate STOMP connection per D-11)
- **OfflineStaleNotice**: Rendered at top with dataUpdatedAt from useWeekSchedule
- **Router**: Lazy-loaded CheckInScreen replaces HomePlaceholder at /checkin route

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 0f6ebc8 | feat(30-02): CheckInButton with GPS capture, CheckInToast feedback, TDD tests |
| 2 | 5732f8e | feat(30-02): StompProvider shared context, useStompCheckin hook, LessonCard + SchedulePage integration |
| 3 | 7311d35 | feat(30-02): CheckInScreen dedicated tab consuming shared StompProvider |

## Verification Results

1. `npx tsc --noEmit` - zero type errors
2. `npx vitest run` - 31 tests pass (6 test files)
3. `npm run build` - production build succeeds

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added StompProvider mock to SchedulePage tests**
- **Found during:** Task 2
- **Issue:** SchedulePage now calls useStompEvents() which requires StompProvider context, causing existing tests to throw
- **Fix:** Added `vi.mock('@/features/checkin/StompProvider')` returning stub attendanceCounts/personalStatuses/markPersonalStatus
- **Files modified:** frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx
- **Commit:** 5732f8e

## Known Stubs

None. All components render real data from API hooks and STOMP events. Attendance count starts at 0 per session (intentional -- no backend endpoint for initial count per RESEARCH.md Open Question 1).

## Threat Flags

None. All network endpoints match plan threat model (GPS API, attendance check-in via gateway, STOMP /api/ws with token query param). Token factory pattern mitigates T-30-07 (stale token on reconnect). PWA only subscribes to user's own groupId from JWT (T-30-06).

## Self-Check: PASSED

All 5 created files and 6 modified files verified on disk. All 3 commit hashes (0f6ebc8, 5732f8e, 7311d35) found in git log.
