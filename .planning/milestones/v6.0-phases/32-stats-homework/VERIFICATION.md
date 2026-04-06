---
phase: 32-stats-homework
verified: 2026-04-06T20:20:00Z
status: human_needed
score: 9/9 must-haves verified
re_verification: false
human_verification:
  - test: "Pull-to-refresh gesture on AttendanceStatsPage"
    expected: "Pulling down from the top of the subject list triggers a spinner indicator and refreshes the data"
    why_human: "Touch gesture simulation not covered by vitest (JSDOM); usePullToRefresh attaches touchstart/touchmove/touchend listeners — requires a real device or browser"
  - test: "Pull-to-refresh gesture on HomeworkPage"
    expected: "Pulling down from the top of the homework list triggers the ArrowCounterClockwise spinner and refreshes the data"
    why_human: "Same reason as above — touch event behavior requires real device or browser"
  - test: "Red zone visual styling on AttendanceStatsPage"
    expected: "A subject row below the threshold shows a red left border (border-l-4 border-destructive), red percentage text, and the 'Красная зона' badge inline next to the subject name"
    why_human: "Visual CSS rendering cannot be verified programmatically in vitest"
  - test: "Optimistic checkbox revert on network error (HW-02)"
    expected: "When the POST /complete request fails, the checkbox snaps back to its previous state and 'Не удалось сохранить' appears below the title"
    why_human: "Requires simulating a real network failure mid-flight; vitest mock does not exercise the TanStack Query onError -> cache revert -> re-render cycle end-to-end"
  - test: "BottomNav active tab indicator"
    expected: "Navigating to /stats highlights the Статистика tab; navigating to /homework highlights the Задания tab; the small dot indicator appears below the active tab icon"
    why_human: "React Router NavLink isActive class switching requires a real browser with URL context"
  - test: "Default landing after login is /schedule"
    expected: "After a successful login the user is redirected to the schedule page, not /home"
    why_human: "End-to-end login flow requires a running app and real auth"
---

# Phase 32: Stats + Homework Verification Report

**Phase Goal:** Deliver attendance statistics pages (ATT-01, ATT-02, ATT-03) and homework list with optimistic toggle (HW-01, HW-02) for the PWA frontend.
**Verified:** 2026-04-06T20:20:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Student opens /stats and sees subject list with percentage and counts | VERIFIED | `AttendanceStatsPage.tsx` renders `subjects.map(stats => <SubjectStatRow>)` fed by `useStudentStats()`. Test ATT-01 asserts "Математика", "75%", "б: 15", "н: 3", "у: 2". |
| 2 | Red zone indicator shown when subject percentage < threshold | VERIFIED | `SubjectStatRow.tsx` computes `isRedZone = threshold !== null && stats.percentage < threshold`, applies `border-l-4 border-destructive`, renders `<RedZoneBadge />`. Test ATT-02 (red zone shown) asserts "Красная зона" text. |
| 3 | No red zone indicators when threshold API returns 404 | VERIFIED | `useThreshold` catches 404 and returns `null`; `SubjectStatRow` only renders red zone styling when `threshold !== null`. Test ATT-02 (hidden on 404) asserts "Красная зона" not present. |
| 4 | Student navigates to /stats/:subjectId and sees records with date and StatusBadge | VERIFIED | `AttendanceRecordsPage.tsx` uses `useParams` + `useAttendanceRecords`, renders `AttendanceRecordRow` with `MONTH_ABBREV` date format and `<StatusBadge>`. Route registered as `path: 'stats/:subjectId'` in `main.tsx`. |
| 5 | Student opens /homework and sees items with title, subject name, and completion status | VERIFIED | `HomeworkPage.tsx` chains `useActiveSemester` -> `useHomework`, renders `HomeworkItem` with title, `useSubjectName` lookup, and `role="checkbox"` / `aria-checked`. Tests HW-01 assert "Задача по матану", "Лабораторная по физике", "Нет заданий". |
| 6 | Checkbox toggle is optimistic (immediate UI update), persists via POST/DELETE /complete, reverts on error with inline message | VERIFIED | `useToggleHomework` uses `onMutate` (optimistic cache flip), `onError` (revert + errorMap set), `onSettled` (invalidate). `HomeworkItem` renders `error` prop as `text-destructive text-xs`. Tests HW-02 assert toggle call, line-through class, error text. |
| 7 | BottomNav shows 5 tabs: Статистика, Расписание, Отметка, Задания, Профиль | VERIFIED | `BottomNav.tsx` defines `tabs` array with exactly 5 entries: `/stats`→ChartBar, `/schedule`→Calendar, `/checkin`→Fingerprint, `/homework`→ClipboardText, `/profile`→User. `House`/`/home` not present. |
| 8 | Default landing after login is /schedule | VERIFIED | `main.tsx` index route: `{ index: true, element: <Navigate to="/schedule" replace /> }`. No `/home` route exists. |
| 9 | Pull-to-refresh available on AttendanceStatsPage and HomeworkPage (D-08) | VERIFIED (partial — implementation confirmed, runtime behavior needs human) | `usePullToRefresh` hook attaches `touchstart`/`touchmove`/`touchend` listeners. Both pages import and use the hook with `containerRef` on the scrollable div. Runtime gesture requires human test. |

**Score:** 9/9 truths verified (implementation-level). 6 items require human/device confirmation.

---

## Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `frontends/pwa/src/features/attendance/types.ts` | VERIFIED | Exports `SubjectStats`, `OverallStats`, `StudentStatsResponse`, `AttendanceRecordEntry`, `ResolvedThresholdResponse` |
| `frontends/pwa/src/features/attendance/api.ts` | VERIFIED | Exports `useStudentStats`, `useThreshold` (null on 404), `useAttendanceRecords`. staleTime 60min, refetchOnReconnect. |
| `frontends/pwa/src/features/attendance/AttendanceStatsPage.tsx` | VERIFIED | Named export, uses all three hooks, pull-to-refresh, loading/error/empty states, motion stagger, navigate to /stats/:id |
| `frontends/pwa/src/features/attendance/SubjectStatRow.tsx` | VERIFIED | border-destructive, RedZoneBadge, CaretRight, threshold prop, Math.round(percentage)%, б/н/у counts |
| `frontends/pwa/src/features/attendance/RedZoneBadge.tsx` | VERIFIED | "Красная зона" text, WarningCircle icon |
| `frontends/pwa/src/features/attendance/AttendanceRecordsPage.tsx` | VERIFIED | useAttendanceRecords, useParams, CaretLeft, aria-label="Назад к статистике", "Нет записей", sorted descending |
| `frontends/pwa/src/features/attendance/AttendanceRecordRow.tsx` | VERIFIED | StatusBadge, MONTH_ABBREV, lessonNumber, "N-я пара" format |
| `frontends/pwa/src/shared/hooks/usePullToRefresh.ts` | VERIFIED | touchstart, touchmove, touchend listeners on containerRef. Returns containerRef, isRefreshing, pullDistance. |
| `frontends/pwa/src/shared/components/BottomNav.tsx` | VERIFIED | 5 tabs, ChartBar, ClipboardText, no House, no /home |
| `frontends/pwa/src/features/homework/types.ts` | VERIFIED | HomeworkResponse with completed boolean, no deadline field |
| `frontends/pwa/src/features/homework/api.ts` | VERIFIED | useActiveSemester (24hr staleTime), useHomework (enabled: !!groupId && !!semesterId), useToggleHomework (onMutate/onError/onSettled), re-exports useSubjectName |
| `frontends/pwa/src/features/homework/HomeworkPage.tsx` | VERIFIED | useActiveSemester chain, useHomework, useToggleHomework, usePullToRefresh, errorMap, sort undone-first, "Нет заданий", pb-20 |
| `frontends/pwa/src/features/homework/HomeworkItem.tsx` | VERIFIED | role="checkbox", aria-checked, Check icon, line-through + opacity-60 when done, useSubjectName, inline error |
| `frontends/pwa/src/main.tsx` | VERIFIED | Navigate to="/schedule", path 'stats', path 'stats/:subjectId', path 'homework', lazy imports for all pages |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AttendanceStatsPage.tsx` | `/attendance/reports/student/stats` | `useStudentStats` | WIRED | Hook called at line 16; data fed to SubjectStatRow |
| `AttendanceStatsPage.tsx` | `/academic/thresholds/resolve` | `useThreshold` | WIRED | Hook called at line 17; threshold passed to SubjectStatRow |
| `AttendanceRecordsPage.tsx` | `/attendance/reports/student/records` | `useAttendanceRecords` | WIRED | Hook called at line 17; records mapped to AttendanceRecordRow |
| `main.tsx` | `AttendanceStatsPage.tsx` | React Router lazy import | WIRED | path: 'stats', Suspense wrapper confirmed |
| `main.tsx` | `AttendanceRecordsPage.tsx` | React Router lazy import | WIRED | path: 'stats/:subjectId', Suspense wrapper confirmed |
| `HomeworkPage.tsx` | `/academic/homeworks` | `useHomework` | WIRED | Hook called at line 22; data fed to HomeworkItem |
| `HomeworkItem.tsx` | `/academic/homeworks/{id}/complete` | `useToggleHomework` mutation | WIRED | POST when completed=true, DELETE when completed=false; onMutate flips cache immediately |
| `api.ts (homework)` | `/academic/semesters` | `useActiveSemester` | WIRED | Finds active=true entry, returns its id as semesterId |
| `main.tsx` | `HomeworkPage.tsx` | React Router lazy import | WIRED | path: 'homework', Suspense wrapper confirmed |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `AttendanceStatsPage.tsx` | `data.subjects` | `apiClient.get('/attendance/reports/student/stats')` | Yes — real HTTP call, no hardcoded fallback | FLOWING |
| `AttendanceStatsPage.tsx` | `threshold` | `apiClient.get('/academic/thresholds/resolve')` | Yes — real HTTP call, null only on 404 (by design) | FLOWING |
| `AttendanceRecordsPage.tsx` | `records` | `apiClient.get('/attendance/reports/student/records', { params: { subjectId } })` | Yes — real HTTP call with subjectId param | FLOWING |
| `HomeworkPage.tsx` | `homeworks` | `apiClient.get('/academic/homeworks', { params: { groupId, semesterId } })` | Yes — real HTTP call, enabled only when both params resolved | FLOWING |
| `HomeworkItem.tsx` | `completed` | `homework` prop from parent (optimistic cache) | Yes — cache update + revert wired via onMutate/onError | FLOWING |

---

## Behavioral Spot-Checks

Step 7b SKIPPED — no runnable dev server. Tests are the behavioral proxy; all 63 pass.

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ATT-01 | 32-01 | Subject stats list at /stats with percentage and present/absent/excused counts | SATISFIED | AttendanceStatsPage + SubjectStatRow + useStudentStats. Test ATT-01 passes. |
| ATT-02 | 32-01 | Red zone indicator when subject % < threshold; hidden when 404 | SATISFIED | useThreshold returns null on 404; SubjectStatRow conditionally applies red zone styling. Tests ATT-02 (both cases) pass. |
| ATT-03 | 32-01 | Navigate to /stats/:subjectId, see records with date and StatusBadge | SATISFIED | AttendanceRecordsPage + AttendanceRecordRow. Route wired. MONTH_ABBREV formatting confirmed. |
| HW-01 | 32-02 | Homework list at /homework with title, subject name, completion status | SATISFIED | HomeworkPage chains useActiveSemester + useHomework. HomeworkItem shows title, useSubjectName, aria-checked checkbox. Tests HW-01 pass. |
| HW-02 | 32-02 | Optimistic checkbox toggle, persists via POST/DELETE /complete, reverts on error with inline message | SATISFIED (code) / NEEDS HUMAN (runtime) | useToggleHomework implements onMutate/onError/onSettled correctly. HomeworkItem renders error prop. Tests HW-02 pass. Runtime revert flow needs human test. |
| D-03 | 32-01 | BottomNav shows 5 tabs (Статистика, Расписание, Отметка, Задания, Профиль) | SATISFIED | BottomNav.tsx confirmed: exactly 5 tabs, correct icons and routes. |
| D-04 | 32-01 | Default landing after login is /schedule | SATISFIED | main.tsx index redirect confirmed: `<Navigate to="/schedule" replace />`. No /home route. |
| D-08 | 32-01 / 32-02 | Pull-to-refresh on AttendanceStatsPage and HomeworkPage | SATISFIED (code) / NEEDS HUMAN (runtime) | usePullToRefresh hook implemented with touchstart/touchmove/touchend. Both pages wire containerRef and render pull indicator. Gesture behavior requires device. |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `attendance/api.ts` | 29 | `return null` | Info | Intentional — 404 from threshold API means "no threshold configured". Not a stub. |

No blockers, warnings, or real stubs found.

---

## Human Verification Required

### 1. Pull-to-refresh on AttendanceStatsPage

**Test:** Open the PWA on a mobile device or Chrome DevTools mobile emulation. Navigate to /stats (with real data loaded). Pull down from the top of the subject list.
**Expected:** An `ArrowCounterClockwise` spinner appears, animates, and the subject list refreshes.
**Why human:** Touch events (touchstart/touchmove/touchend) are not exercised by vitest/JSDOM.

### 2. Pull-to-refresh on HomeworkPage

**Test:** Navigate to /homework. Pull down from the top of the homework list.
**Expected:** Pull indicator appears, data refreshes.
**Why human:** Same reason as above.

### 3. Red zone visual styling

**Test:** Log in as a student whose group has a threshold configured (e.g. 60%), with at least one subject below it. Navigate to /stats.
**Expected:** That subject's card shows a red left border, the percentage appears in red, and the "Красная зона" pill badge is visible inline after the subject name.
**Why human:** CSS class rendering (`border-destructive`, `text-destructive`) requires a real browser with the design system applied.

### 4. Optimistic checkbox revert on network error (HW-02)

**Test:** Navigate to /homework. Disable network (airplane mode). Click the checkbox on an incomplete homework item.
**Expected:** The checkbox toggles immediately (optimistic), then reverts after a moment, and "Не удалось сохранить" appears below the homework title.
**Why human:** Requires simulating a real network failure during a live mutation; the mock-based tests do not exercise the full TanStack Query revert cycle with re-render.

### 5. BottomNav active tab highlighting

**Test:** Navigate to /stats, then /homework, then /schedule.
**Expected:** The corresponding tab in BottomNav highlights (text-primary, filled icon, small dot indicator) each time.
**Why human:** React Router NavLink `isActive` class switching requires real URL changes in a browser context.

### 6. Default landing after login is /schedule

**Test:** Log in as a student. Observe which page loads after authentication.
**Expected:** The schedule page is shown, not a home page or blank screen.
**Why human:** End-to-end login flow with real auth redirects requires a running app.

---

## Gaps Summary

No automated gaps found. All 9 observable truths are supported by real, wired, data-flowing implementation. All 63 vitest tests pass. The `human_needed` status reflects 6 visual/gesture/runtime behaviors that cannot be verified programmatically.

---

_Verified: 2026-04-06T20:20:00Z_
_Verifier: Claude (gsd-verifier)_
