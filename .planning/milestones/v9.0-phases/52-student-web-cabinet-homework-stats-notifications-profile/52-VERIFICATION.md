---
phase: 52-student-web-cabinet-homework-stats-notifications-profile
verified: 2026-04-09T16:30:00Z
status: gaps_found
score: 3/4 must-haves verified
re_verification: false
gaps:
  - truth: "/student/homework lists all assignments and optimistic toggle works"
    status: failed
    reason: "ng build exits with error code non-zero due to 7 NG5002 Angular template parser errors in homework-item.component.ts line 46. The aria-label binding uses backslash-escaped single quotes (\\') inside an Angular template string expression, which the Angular template parser does not support. The ternary expression breaks into malformed tokens. The app cannot be compiled, so the homework page cannot be served at all."
    artifacts:
      - path: "frontends/web-panel/src/app/features/student/homework/homework-item/homework-item.component.ts"
        issue: "Line 46: [attr.aria-label] binding uses \\' escape sequences inside a ternary expression within a template attribute binding. Angular's template parser treats \\' as two tokens (backslash + quote), breaking the ternary. Fix: replace escaped quotes with &apos; HTML entity, or remove the surrounding literal quotes from the title entirely, or move the aria-label string construction to a getter method in the component class."
    missing:
      - "Fix aria-label ternary in homework-item.component.ts to use &apos; instead of \\', or move string construction to a TypeScript getter. After the fix ng build must exit 0."
---

# Phase 52: Student Web Cabinet — Homework + Stats + Notifications + Profile — Verification Report

**Phase Goal:** A student can complete all self-service information tasks: view and toggle homework, inspect attendance statistics with charts, browse a notification log, and change their password
**Verified:** 2026-04-09T16:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | `/student/homework` lists assignments; optimistic toggle reverts on server error | ✗ FAILED | Build fails (NG5002 × 7 in homework-item.component.ts) — page cannot be served |
| 2 | `/student/stats` shows per-subject attendance with red-zone indicators and ng2-charts | ✓ VERIFIED | StudentStatsComponent, StudentSubjectChartComponent, StudentOverallCardComponent all exist with correct colors, forkJoin, threshold signal, `[@routeFade]`, mat-progress-bar, BaseChartDirective import; all patterns confirmed |
| 3 | `/student/notifications` shows scrollable event log labeled by type and timestamp | ✓ VERIFIED | StudentNotificationsComponent with sessionStorage (rct-notifications), MAX_ITEMS=100, sortedItems computed, badge reset, STORED_TYPES; NotificationItemComponent with per-type icon/heading/bodyText getters, `is-unread` border-left binding, min-height 64px |
| 4 | `/student/profile` shows password change form; success banner; inline 401 error | ✓ VERIFIED | StudentProfileComponent: ReactiveFormsModule, ChangeDetectionStrategy.OnPush, hintMinLength/hintUppercase/hintDigit computed signals, changePassword() wired to AuthApi, setTimeout 3 s, wrongPassword setErrors, autocomplete attributes, aria-live="assertive" |

**Score:** 3/4 truths verified

---

## Required Artifacts

### Plan 01 — Shared Foundation

| Artifact | Status | Evidence |
|----------|--------|---------|
| `student/shared/student-notification-badge.service.ts` | ✓ VERIFIED | Exists; `providedIn: 'root'`, `readonly unreadCount`, `increment()`, `reset()` |
| `app.routes.ts` — 4 new student routes | ✓ VERIFIED | `path: 'homework'`, `'stats'`, `'notifications'`, `'profile'` all present with lazy loadComponent imports |
| `sidebar.component.ts` — 4 new nav items + badge | ✓ VERIFIED | `ph-notebook`, `ph-bell`, `ph-user-circle`, `ph-chart-bar`; `StudentNotificationBadgeService` injected; `unreadCount` exposed |
| `sidebar.component.html` — badge overlay | ✓ VERIFIED | `notification-badge` class, `9+` conditional, aria-label |
| `student-schedule.types.ts` — HomeworkItem + NotificationItem | ✓ VERIFIED | Both interfaces at lines 110 and 124 |
| `student-stomp.service.ts` — onAnyEvent$, STORED_TYPES, badge increment | ✓ VERIFIED | `STORED_TYPES` constant at line 11; `onAnySubject`/`onAnyEvent$` at lines 55/62; `badgeService.increment()` at line 87 |
| `student-api.service.ts` — 4 homework methods | ✓ VERIFIED | `getHomeworks`, `markHomeworkComplete`, `unmarkHomeworkComplete`, `getActiveSemesterId` all present |
| `auth.api.ts` — changePassword | ✓ VERIFIED | `changePassword(currentPassword, newPassword)` at line 27 |

### Plan 02 — Homework Page

| Artifact | Status | Evidence |
|----------|--------|---------|
| `homework/homework-item/homework-item.component.ts` | ✗ STUB (build-broken) | File exists and contains correct logic (OnPush, toggleComplete, aria-label intent), but the aria-label binding at line 46 uses `\'` escape sequences that Angular's template parser rejects (7 × NG5002). The component cannot compile. |
| `homework/student-homework.component.ts` | ⚠️ ORPHANED (build-broken) | File exists with correct implementation (incompleteItems/completeItems computed, onToggleComplete, switchMap chain, loading/error/items signals), but is unreachable at runtime because the project won't compile |
| `homework/student-homework.component.html` | ⚠️ ORPHANED | Exists with app-homework-item, [@routeFade], page-empty "Заданий нет", homework-skeleton |
| `homework/student-homework.component.css` | ⚠️ ORPHANED | Exists with `dashboard-shimmer` animation |

### Plan 03 — Stats Page

| Artifact | Status | Evidence |
|----------|--------|---------|
| `stats/student-stats.component.ts` | ✓ VERIFIED | OnPush, forkJoin, threshold signal(75), resolveGroupThreshold/resolveGlobalThreshold branching |
| `stats/student-stats.component.html` | ✓ VERIFIED | [@routeFade], mat-progress-bar, app-student-overall-card, app-student-subject-chart, "Данных пока нет" |
| `stats/student-stats.component.css` | ✓ VERIFIED | `stats-grid`, responsive @media min-width 1024px |
| `stats/student-overall-card/student-overall-card.component.ts` | ✓ VERIFIED | aria-live="polite", `is-good` / `is-warning` classes, monospace text-2xl |
| `stats/student-subject-chart/student-subject-chart.component.ts` | ✓ VERIFIED | BaseChartDirective, all 4 RGBA colors exact per UI-SPEC, `role="img"`, `Посещаемость ниже порога`, freeAttendance calc, ngOnChanges |

### Plan 04 — Notifications + Profile Pages

| Artifact | Status | Evidence |
|----------|--------|---------|
| `notifications/notification-item/notification-item.component.ts` | ✓ VERIFIED | OnPush, per-type icon/heading/bodyText getters, `is-unread` border-left binding, min-height 64px, role="listitem", aria-label |
| `notifications/student-notifications.component.ts` | ✓ VERIFIED | sessionStorage key 'rct-notifications', MAX_ITEMS=100, STORED_TYPES, badgeService.reset(), onAnyEvent$ subscription with takeUntilDestroyed, sortedItems computed (receivedAt desc), allRead() |
| `notifications/student-notifications.component.html` | ✓ VERIFIED | ph-bell-slash, "Уведомлений нет", all-read-pill, notifications-list role="list" |
| `profile/student-profile.component.ts` | ✓ VERIFIED | ReactiveFormsModule, OnPush, hintMinLength/hintUppercase/hintDigit computed signals, avatarInitials (last 2 digits of id), changePassword() → 401 wrongPassword / 3 s success timeout |
| `profile/student-profile.component.html` | ✓ VERIFIED | autocomplete="current-password"/"new-password", aria-live="assertive", "Неверный текущий пароль.", "Пароль успешно изменён", "Web Cabinet" badge, "Студент" role label |

---

## Key Link Verification

| From | To | Via | Status | Evidence |
|------|----|-----|--------|---------|
| app.routes.ts | StudentHomeworkComponent | lazy loadComponent import | ✓ WIRED | `import('./features/student/homework/student-homework.component')` |
| app.routes.ts | StudentStatsComponent | lazy loadComponent import | ✓ WIRED | `import('./features/student/stats/student-stats.component')` |
| app.routes.ts | StudentNotificationsComponent | lazy loadComponent import | ✓ WIRED | `import('./features/student/notifications/student-notifications.component')` |
| app.routes.ts | StudentProfileComponent | lazy loadComponent import | ✓ WIRED | `import('./features/student/profile/student-profile.component')` |
| sidebar.component.ts | StudentNotificationBadgeService | inject() + unreadCount signal | ✓ WIRED | Lines 60–61 |
| student-stomp.service.ts | onAnyEvent$ Subject | STOMP subscription fan-out | ✓ WIRED | `onAnySubject.next(envelope)` + `badgeService.increment()` at lines 86–87 |
| StudentHomeworkComponent | StudentApiService.getHomeworks + getActiveSemesterId | switchMap chain | ✓ WIRED | Lines confirmed in component (build-blocked at runtime but logic is correct) |
| StudentStatsComponent | StudentApiService.getStudentStats + resolveGroupThreshold | forkJoin | ✓ WIRED | `forkJoin([this.apiService.getStudentStats(), threshold$])` |
| StudentNotificationsComponent | StudentStompService.onAnyEvent$ | inject + takeUntilDestroyed | ✓ WIRED | `this.stompService.onAnyEvent$.pipe(takeUntilDestroyed(...))` |
| StudentNotificationsComponent | StudentNotificationBadgeService.reset() | inject + reset() in ngOnInit | ✓ WIRED | `this.badgeService.reset()` at line 52 |
| StudentProfileComponent | AuthApi.changePassword | inject(AuthApi) + subscribe | ✓ WIRED | `this.authApi.changePassword(currentPassword, newPassword).subscribe(...)` |
| HomeworkItemComponent | toggleComplete EventEmitter | Output + onToggle() | ✗ BUILD BROKEN | Logic correct but template won't parse (NG5002 in aria-label binding) |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| StudentHomeworkComponent | items() signal | `getActiveSemesterId()` → `getHomeworks(groupId, semesterId)` → `/api/academic/homeworks` | Yes — live HTTP GET to academic-service | ✓ FLOWING (logic correct; runtime blocked by build failure) |
| StudentStatsComponent | stats() signal | `forkJoin([getStudentStats(), threshold$])` → `/api/attendance/stats/student/me` + threshold API | Yes — live HTTP calls | ✓ FLOWING |
| StudentNotificationsComponent | items() signal | sessionStorage 'rct-notifications' + `onAnyEvent$` STOMP subscription | Yes — live STOMP + persisted session data | ✓ FLOWING |
| StudentProfileComponent | avatarInitials() | `authService.currentUser()?.id` (JWT claim) | Yes — from JWT | ✓ FLOWING |

---

## Behavioral Spot-Checks

| Behavior | Result | Status |
|----------|--------|--------|
| `ng build --configuration development` exits 0 | Build fails with 7 × NG5002 in homework-item.component.ts line 46 | ✗ FAIL |
| `grep "path: 'homework'"` in app.routes.ts | Match found (line 120) | ✓ PASS |
| `grep "onAnyEvent"` in student-stomp.service.ts | Match found (line 62) | ✓ PASS |
| `grep "getHomeworks"` in student-api.service.ts | Match found (line 82) | ✓ PASS |
| `grep "changePassword"` in auth.api.ts | Match found (line 27) | ✓ PASS |
| `grep "BaseChartDirective"` in student-subject-chart | Match found (line 3) | ✓ PASS |
| `grep "badgeService.reset"` in notifications component | Match found (line 52) | ✓ PASS |
| All 8 commits from summaries exist in git log | All 8 hashes confirmed: f09ac65, e31fc58, c735673, 6c8d8c8, 1493793, 3f8e023, 989c5ee, 0df2439 | ✓ PASS |

---

## Requirements Coverage

| Requirement | Plan | Status | Evidence |
|-------------|------|--------|---------|
| STU-WEB-04 (Homework list + toggle) | 52-01, 52-02 | ✗ BLOCKED | Implementation is correct but app won't compile due to NG5002 in homework-item.component.ts |
| STU-WEB-05 (Stats with charts) | 52-01, 52-03 | ✓ SATISFIED | StudentStatsComponent + ng2-charts, threshold coloring, red-zone badge, forkJoin |
| STU-WEB-06 (Notification log) | 52-01, 52-04 | ✓ SATISFIED | StudentNotificationsComponent, sessionStorage persistence, STOMP subscription, badge reset |
| STU-WEB-09 (Profile + password change) | 52-01, 52-04 | ✓ SATISFIED | StudentProfileComponent, ReactiveFormsModule, 401 inline error, 3 s success banner |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|---------|--------|
| `homework/homework-item/homework-item.component.ts` | 46–48 | `[attr.aria-label]` binding uses `\'` (backslash-escaped single quotes) in a ternary expression inside an Angular template | Blocker | Angular's template parser treats `\'` as two tokens, not an escaped quote. This cascades into 7 NG5002 errors affecting every binding that follows in the template. The entire `HomeworkItemComponent` fails to compile, which blocks compilation of `StudentHomeworkComponent` (which imports it), which blocks the full app build. |

---

## Human Verification Required

### 1. Homework Page Functionality (after build fix)

**Test:** After fixing homework-item.component.ts line 46 and confirming `ng build` exits 0, navigate to `/student/homework` while logged in as a student.
**Expected:** Homework assignments for the student's group are listed; clicking the checkbox immediately shows the item as completed (optimistic toggle); if the API call fails (simulate by throttling network), the item reverts to its previous state.
**Why human:** Cannot verify routing and optimistic-toggle UX behavior without running the browser.

### 2. Stats Chart Rendering

**Test:** Navigate to `/student/stats` as a student with at least one lesson in the current semester.
**Expected:** Each subject shows a stacked bar chart with 4 colored segments (green/amber/purple/red); subjects below threshold show an amber "Посещаемость ниже порога" badge; overall percentage changes color depending on whether it meets the threshold.
**Why human:** Chart rendering (ng2-charts canvas output) and threshold color correctness require visual inspection.

### 3. Notification Badge Count

**Test:** While connected (STOMP active), trigger a `lesson.started` event (or simulate by subscribing to the STOMP topic from another client). Observe the sidebar bell badge.
**Expected:** Badge counter increments by 1. Navigate to `/student/notifications` — badge resets to 0; the new notification appears in the list with correct icon and body text.
**Why human:** Requires a live STOMP broker and real-time event delivery.

---

## Gaps Summary

One blocker prevents full phase goal achievement: **the Angular build fails**. All 7 compiler errors point to the same root cause — the `aria-label` attribute binding in `HomeworkItemComponent` uses backslash-escaped single quotes (`\'`) inside a ternary expression in an Angular template string. The Angular template parser does not support this escape sequence; it breaks the ternary into malformed sub-expressions.

**Root cause:** Line 46 of `homework-item.component.ts`:
```
[attr.aria-label]="item.completed
  ? 'Снять отметку с задания \'' + item.title + '\''
  : 'Отметить задание \'' + item.title + '\' выполненным'"
```

**Fix required (one option):** Remove the literal Russian quotes from the label, or use a component getter method instead of an inline template expression.

This single file change blocks compilation of the entire web-panel application. Plans 03 and 04 (stats, notifications, profile) are fully correct and would work correctly once the build is fixed.

---

_Verified: 2026-04-09T16:30:00Z_
_Verifier: Claude (gsd-verifier)_
