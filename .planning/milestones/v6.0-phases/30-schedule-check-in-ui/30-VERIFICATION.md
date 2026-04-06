---
phase: 30-schedule-check-in-ui
verified: 2026-04-06T12:47:00Z
status: human_needed
score: 6/6 must-haves verified
gaps: []
human_verification:
  - test: "Open http://localhost:5173/home — verify lesson cards render with real backend data (time, subject name, room, status badge)"
    expected: "Today's lessons appear with correct data from schedule-service and subject names resolved from academic-service"
    why_human: "Requires running backend services (schedule-service, academic-service, auth-service, gateway) to verify real API integration"
  - test: "Swipe left/right on day tabs and tap week navigation arrows"
    expected: "Day tabs switch without page reload; week arrows change the displayed week range; swipe on tab strip changes week"
    why_human: "Touch gesture behavior and visual transitions cannot be verified programmatically"
  - test: "Tap 'Отметиться' on an active lesson card"
    expected: "Browser GPS prompt appears, spinner shows on button, check-in submits, success toast slides up and auto-dismisses after 3s"
    why_human: "Requires running backend with active lesson in attendance-service and physical GPS or GPS mock"
  - test: "Have two browsers open with different student accounts in the same group; check in from one"
    expected: "The other browser's lesson card attendance count updates in real-time via STOMP WebSocket"
    why_human: "Real-time STOMP WebSocket behavior requires running notification-service and two simultaneous sessions"
  - test: "Navigate to /checkin tab when no active lesson exists"
    expected: "Empty state shows 'Сейчас нет активных пар' with next lesson hint or 'На сегодня пар больше нет'"
    why_human: "Requires verifying visual layout and correct lesson status filtering against real data"
  - test: "Disconnect network (airplane mode or DevTools offline) while on schedule page"
    expected: "Offline stale notice appears with 'Офлайн * обновлено N мин назад'; check-in button becomes disabled; cached schedule data remains visible"
    why_human: "Offline behavior requires manual network state manipulation and visual verification"
---

# Phase 30: Schedule + Check-in UI Verification Report

**Phase Goal:** Students can view their daily and weekly schedule and submit a geo check-in from an active lesson card
**Verified:** 2026-04-06T12:47:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Student opens the app and sees today's lessons listed with time, subject name, room, and status badge | VERIFIED | SchedulePage.tsx renders LessonCard with time (formatTime), subject (useSubjectName), room ("Ауд."), StatusBadge; auto-scrolls to current lesson (D-03); useWeekSchedule fetches from /api/schedule/groups/{groupId}/lessons |
| 2 | Student swipes or taps tabs to navigate to any day of the current week without a full page reload | VERIFIED | WeekDayTabs.tsx has Mon-Sat tabs (D-01) with onClick handler, motion drag for week swipe (80px threshold); SchedulePage has day swipe (50px threshold) with dragDirectionLock; week navigation via CaretLeft/CaretRight buttons; no page reload |
| 3 | When offline, the schedule screen shows the last-fetched data (up to 1 hour stale) rather than an error | VERIFIED | useWeekSchedule sets staleTime: 3600000 and refetchOnReconnect: true (D-14, D-16); OfflineStaleNotice renders "Офлайн * обновлено N мин назад" with 60s interval refresh; renders "Нет данных" when no cache |
| 4 | Student taps "Отметиться" on an active lesson card; the app captures GPS coordinates and submits them; a success toast appears within 3 seconds on a good connection | VERIFIED | CheckInButton.tsx calls getCurrentPosition with timeout:10000/maximumAge:30000, submits via useCheckin mutation to /attendance/checkin; CheckInToast shows "Отметка принята" with 3000ms auto-dismiss; LessonCard renders CheckInButton when status=ACTIVE and no personalStatus |
| 5 | When check-in fails (not in zone, already marked, or no active lesson), the student sees the specific failure reason rather than a generic error | VERIFIED | mapCheckinError maps 404/409/422/403/429 to specific Russian messages; GPS denial shows exact D-09 text "Нет доступа к GPS. Разрешите доступ в настройках браузера"; CheckInToast shows error with 5000ms auto-dismiss and role="alert" |
| 6 | When another student in the same group checks in, the current student's lesson card updates its attendance count in real time via the STOMP WebSocket | VERIFIED | StompProvider creates shared context at AppShell level (D-11); useStompCheckin connects via SockJS to /api/ws?token= with getAccessToken factory (Pitfall 7 mitigation); subscribes to /topic/group/{groupId}; attendance.marked events increment attendanceCounts state; LessonCard renders motion.span with key={attendanceCount} and spring animation (stiffness:300, damping:30) |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/pwa/src/features/schedule/types.ts` | LessonResponse, SubjectResponse types | VERIFIED | 27 lines, LessonResponse with all fields, LessonStatus, AttendanceStatus, SubjectResponse |
| `frontends/pwa/src/features/schedule/api.ts` | useWeekSchedule, useSubjectName hooks | VERIFIED | 49 lines, useWeekSchedule with staleTime 1hr, useSubjectName with 24hr, usePrefetchSubjects for waterfall avoidance (D-05) |
| `frontends/pwa/src/features/schedule/SchedulePage.tsx` | Root schedule page | VERIFIED | 264 lines, week nav, day tabs, lesson list, auto-scroll, floating "Сегодня" pill (D-04), stagger animation, empty state, loading spinner |
| `frontends/pwa/src/features/schedule/WeekDayTabs.tsx` | Mon-Sat tab strip | VERIFIED | 54 lines, 6 day labels, drag for week swipe, ARIA tablist/tab, 44px touch targets, sticky positioning |
| `frontends/pwa/src/features/schedule/LessonCard.tsx` | Lesson card with check-in | VERIFIED | 101 lines, time/subject/room/status, CheckInButton for ACTIVE, Check icon after check-in (D-08), motion.span count animation, opacity-60 for cancelled |
| `frontends/pwa/src/features/schedule/StatusBadge.tsx` | Status pill badge | VERIFIED | 29 lines, 8 status variants (4 lesson + 4 attendance) with correct Russian labels and colors |
| `frontends/pwa/src/features/schedule/OfflineStaleNotice.tsx` | Offline stale banner | VERIFIED | 49 lines, dynamic relative timestamp with 60s refresh, "Нет данных" fallback, AnimatePresence |
| `frontends/pwa/src/features/checkin/types.ts` | CheckinRequest, AttendanceMarkedPayload | VERIFIED | 12 lines, correct fields matching backend contracts |
| `frontends/pwa/src/features/checkin/api.ts` | useCheckin, mapCheckinError | VERIFIED | 29 lines, POST /attendance/checkin, error mapping for 403/404/409/422/429 |
| `frontends/pwa/src/features/checkin/CheckInButton.tsx` | GPS capture + submit button | VERIFIED | 61 lines, getCurrentPosition, useCheckin, useNetworkStatus, aria-busy, spinner, exact D-09 GPS denial text |
| `frontends/pwa/src/features/checkin/CheckInToast.tsx` | Success/failure toast | VERIFIED | 39 lines, 3000ms/5000ms auto-dismiss, role=status/alert, bottom-16 positioning, motion.div animation |
| `frontends/pwa/src/features/checkin/useStompCheckin.ts` | STOMP WebSocket hook | VERIFIED | 45 lines, @stomp/stompjs Client, SockJS, getAccessToken factory, /topic/group/{groupId}, attendance.marked filter, deactivate cleanup |
| `frontends/pwa/src/features/checkin/StompProvider.tsx` | Shared STOMP context | VERIFIED | 64 lines, createContext, attendanceCounts/personalStatuses state, accessTokenRef pattern, useStompEvents export |
| `frontends/pwa/src/features/checkin/CheckInScreen.tsx` | Dedicated /checkin tab | VERIFIED | 136 lines, active lesson card with subject/time/room/check-in, empty state "Сейчас нет активных пар", next lesson hint, OfflineStaleNotice, uses useStompEvents (not direct useStompCheckin) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| SchedulePage.tsx | /api/schedule/groups/{groupId}/lessons | useWeekSchedule hook | WIRED | Line 82: `useWeekSchedule(groupId, weekStart, weekEnd)` |
| LessonCard.tsx | /api/academic/subjects/{id} | useSubjectName hook | WIRED | Line 30: `useSubjectName(lesson.subjectId)` |
| main.tsx | SchedulePage.tsx | lazy import for /home and /schedule | WIRED | Lines 19, 39-52: lazy import + Suspense at /home and /schedule routes |
| CheckInButton.tsx | /api/attendance/checkin | useCheckin mutation | WIRED | Line 12: `useCheckin()`, line 24: `checkinMutation.mutate({ lat, lng })` |
| StompProvider.tsx | /api/ws (STOMP over SockJS) | @stomp/stompjs Client | WIRED | useStompCheckin called at line 45 with factory; useStompCheckin.ts line 20: `new SockJS('/api/ws?token=...')` |
| LessonCard.tsx | CheckInButton.tsx | import and render when ACTIVE | WIRED | Line 5: import CheckInButton; line 80: `<CheckInButton>` rendered when `isActive && !personalStatus` |
| main.tsx | CheckInScreen.tsx | lazy import for /checkin | WIRED | Line 20: lazy import; line 57: Suspense wrapping at /checkin route |
| main.tsx | StompProvider.tsx | wraps ProtectedRoute children | WIRED | Line 12: import; line 32: `<StompProvider>` wraps `<AppShell />` |
| SchedulePage.tsx | StompProvider context | useStompEvents | WIRED | Line 7: import useStompEvents; line 67: destructured attendanceCounts, personalStatuses, markPersonalStatus |
| CheckInScreen.tsx | StompProvider context | useStompEvents | WIRED | Line 7: import useStompEvents; line 26: used in ActiveLessonCard subcomponent |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| SchedulePage.tsx | lessons | useWeekSchedule -> apiClient.get(/schedule/groups/{groupId}/lessons) | Yes (DB query via schedule-service) | FLOWING |
| LessonCard.tsx | subjectName | useSubjectName -> apiClient.get(/academic/subjects/{id}) | Yes (DB query via academic-service) | FLOWING |
| CheckInButton.tsx | checkinMutation | useCheckin -> apiClient.post(/attendance/checkin) | Yes (creates attendance record) | FLOWING |
| StompProvider.tsx | attendanceCounts | useStompCheckin -> STOMP /topic/group/{groupId} -> attendance.marked events | Yes (real-time from notification-service) | FLOWING |
| CheckInScreen.tsx | lessons | useWeekSchedule (same hook, scoped to today) | Yes (reuses same data flow) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compiles | `npx tsc -b` (frontends/pwa) | Zero errors after npm install | PASS |
| All tests pass | `npx vitest run` (frontends/pwa) | 31 tests pass, 6 test files, 0 failures | PASS |
| Production build | `npm run build` (frontends/pwa) | Build succeeds, dist/ output generated | PASS |
| Module exports CheckInButton | grep export CheckInButton.tsx | `export function CheckInButton` found | PASS |
| Module exports StompProvider + useStompEvents | grep export StompProvider.tsx | Both exported | PASS |
| Module exports CheckInScreen | grep export CheckInScreen.tsx | `export function CheckInScreen` found | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SCHED-01 | 30-01 | User can view today's schedule (lessons with time, subject, room, status) | SATISFIED | SchedulePage renders LessonCard with all fields; useWeekSchedule fetches from backend; useSubjectName resolves names |
| SCHED-02 | 30-01 | User can navigate weekly schedule (swipe/tab between days) | SATISFIED | WeekDayTabs with Mon-Sat tabs, drag swipe for week change, arrow buttons, day swipe with dragDirectionLock |
| SCHED-03 | 30-01 | Schedule is cached offline (stale-while-revalidate, 1hr max stale) | SATISFIED | staleTime: 3600000 on useWeekSchedule; refetchOnReconnect: true; OfflineStaleNotice displays staleness |
| CHKIN-01 | 30-02 | User can tap check-in on active lesson card, GPS coords captured and submitted | SATISFIED | CheckInButton with getCurrentPosition + useCheckin mutation; LessonCard renders button for ACTIVE status |
| CHKIN-02 | 30-02 | User sees immediate success/failure feedback with reason | SATISFIED | mapCheckinError maps HTTP status codes; CheckInToast with success/error variants; GPS denial exact D-09 text |
| CHKIN-03 | 30-02 | Check-in UI updates in real-time via STOMP WebSocket on attendance.marked event | SATISFIED | useStompCheckin subscribes to /topic/group/{groupId}; StompProvider increments attendanceCounts; LessonCard animates count |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No TODO/FIXME/placeholder/stub patterns found in any phase 30 source files |

### Human Verification Required

### 1. Full-Stack Schedule Rendering

**Test:** Open http://localhost:5173/home with backend services running
**Expected:** Today's lessons appear with correct times, resolved subject names, room numbers, and status badges
**Why human:** Requires running schedule-service, academic-service, auth-service, and gateway simultaneously

### 2. Touch Gesture Navigation

**Test:** Swipe left/right on day tabs and between days; tap week arrows
**Expected:** Smooth transitions, no page reload, correct day/week switching, 80px threshold for week swipe, 50px for day swipe
**Why human:** Touch gesture behavior and visual transition quality cannot be verified programmatically

### 3. GPS Check-in End-to-End

**Test:** Tap "Отметиться" on an active lesson card
**Expected:** GPS prompt appears, spinner shows, check-in submits to backend, success toast slides up with "Отметка принята" and auto-dismisses after 3 seconds
**Why human:** Requires active lesson in attendance-service, physical GPS or mock, running backend

### 4. Real-Time STOMP Attendance Updates

**Test:** Two browser sessions with different students in same group; check in from one
**Expected:** Other session's lesson card attendance count updates in real-time without page refresh
**Why human:** STOMP WebSocket real-time behavior requires running notification-service

### 5. CheckInScreen Empty State

**Test:** Navigate to /checkin when no active lesson exists
**Expected:** "Сейчас нет активных пар" with next lesson hint or "На сегодня пар больше нет"
**Why human:** Visual layout verification against real schedule data

### 6. Offline Behavior

**Test:** Enable airplane mode or DevTools offline on schedule page
**Expected:** Cached schedule remains visible, stale notice with dynamic timestamp appears, check-in button disables
**Why human:** Requires manual network manipulation and visual verification

### Gaps Summary

No code-level gaps found. All 6 roadmap success criteria are fully implemented in code. All 6 requirement IDs (SCHED-01 through SCHED-03, CHKIN-01 through CHKIN-03) are satisfied. All artifacts exist, are substantive, wired, and have flowing data paths to real backend APIs.

The only issue discovered during verification was that `@stomp/stompjs` and `sockjs-client` packages were declared in package.json and package-lock.json but not present in node_modules -- this is standard behavior for a git checkout (node_modules is .gitignored). After `npm install`, all tests pass (31/31), TypeScript compiles cleanly, and production build succeeds.

Key design decisions verified as honored:
- **D-01:** Mon-Sat only tabs (no Sunday)
- **D-03:** Auto-scroll to current/next lesson on mount
- **D-04:** Floating "Сегодня" pill for non-current week
- **D-08:** Check icon replaces button after check-in
- **D-09:** Exact GPS denial text verified
- **D-11:** Single shared StompProvider at AppShell level
- **D-14:** 1hr staleTime override on schedule queries
- **D-15:** OfflineStaleNotice with dynamic relative timestamp
- **Pitfall 7:** getAccessToken factory function pattern (not direct token value)

---

_Verified: 2026-04-06T12:47:00Z_
_Verifier: Claude (gsd-verifier)_
