---
phase: 51-student-web-cabinet-shell-schedule-check-in
verified: 2026-04-09T14:25:00Z
status: human_needed
score: 8/8 must-haves verified (automated gates)
re_verification: null
human_verification:
  - test: "Login as STUDENT and visually inspect /student/dashboard"
    expected: "Greeting hero with time-based heading (Доброе утро/день/вечер) + live clock + today's lesson chips row + NextLessonCard + red-zone banners when applicable. Hero animation routeFade (200ms) triggers on route enter."
    why_human: "Visual appearance, animation feel, greeting correctness for current time-of-day, and hero layout cannot be verified programmatically."
  - test: "Navigate to /student/schedule, click prev/next week buttons"
    expected: "Week label updates to correct Russian format ('6-11 апр' or '30 мар - 4 апр'), lesson list re-fetches, 'Сегодня' floating pill appears when away from current week, daySlide (150ms) animation triggers when switching day tabs."
    why_human: "Animation smoothness, floating pill positioning, and week-range copy correctness require visual verification."
  - test: "Click a lesson row on /student/schedule"
    expected: "Inline detail panel expands below the row showing lesson number, type, room, teacher id, cancel reason. Clicking again collapses. aria-expanded reflects state. Only one panel open at a time."
    why_human: "Expand/collapse interaction feel and subway-rail visual language (time rail + station dot) require visual inspection."
  - test: "Navigate to /student/checkin during a lesson window (backend has ACTIVE lesson for the student's group)"
    expected: "Hero card renders with blinking live dot + 'Идёт сейчас' pill + subject name (2xl font) + HH:mm–HH:mm time + 'Ауд. {room}' + 'Отметиться' CTA button (min-height 48px) + 'N отметилось' counter (aria-live='polite')."
    why_human: "Requires backend to have an ACTIVE lesson; cannot be triggered without running services. Blinking dot CSS animation and visual hero layout need human verification."
  - test: "Click 'Отметиться' CTA with browser geolocation permission granted"
    expected: "Button label transitions 'Определяем координаты…' → 'Отправляем отметку…' → 'Вы отметились' badge on 2xx. POST /api/attendance/checkin sent with {lat, lng}."
    why_human: "Browser geolocation API flow is not triggered in unit tests (stubbed), requires real browser permission dialog."
  - test: "Click 'Отметиться' with geolocation denied in browser settings"
    expected: "Inline error 'Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова.' appears under the button. Button re-enables."
    why_human: "Browser permission denial dialog cannot be programmatically tested."
  - test: "While /student/checkin is open, trigger another student check-in from a second session on the same ACTIVE lesson"
    expected: "STOMP attendance.marked envelope arrives, 'N отметилось' counter increments in real-time WITHOUT page reload. Button state remains 'Отметиться' (not confirmed — different user_id)."
    why_human: "Requires live STOMP broker + attendance-service + second authenticated session; real-time WebSocket propagation cannot be verified programmatically."
  - test: "While /student/checkin is open, have the SAME student check in from a second device"
    expected: "STOMP attendance.marked with matching user_id AND lesson_id arrives, state auto-transitions to 'confirmed' badge WITHOUT a second HTTP POST."
    why_human: "Requires live STOMP broker + second session running; end-to-end real-time auto-confirm cannot be verified without infrastructure."
  - test: "Visit /student/checkin when no ACTIVE lesson exists today"
    expected: "Empty state renders: circle icon + 'Нет активной пары' heading + 5-minute-window body text. If a PLANNED lesson is next today, a 'Следующая пара' hint card shows the subject and start time. Otherwise 'На сегодня пар больше нет' fallback."
    why_human: "Visual appearance and state transition between three empty sub-variants (hint, no-more, during-lesson window) require human inspection."
  - test: "Verify Sidebar renders three STUDENT nav items + 'Студент' role chip when logged in as plain STUDENT"
    expected: "Sidebar shows 'Главная / Расписание / Отметиться' under the 'Учёба' section label, user-role chip below avatar shows 'Студент'. No TEACHER/ADMIN items visible."
    why_human: "Visual sidebar layout, nav item ordering, and role chip appearance need human verification."
  - test: "Verify prefers-reduced-motion on OS level disables checkin live-blink and dashboard pulse"
    expected: "With system-level 'reduce motion' enabled, the blinking dot on /student/checkin hero stops animating, and the dashboard pulse dot is disabled. Route fade-in animations are also muted."
    why_human: "Requires OS-level accessibility setting change and visual inspection."
  - test: "Red-zone warnings: verify at least one amber banner appears when a subject's attendance is below threshold"
    expected: "Dashboard renders one <app-redzone-warning> per sub-threshold subject with format '{subjectName} — посещаемость ниже порога ({N}%)' (percentage rounded to integer)."
    why_human: "Requires real backend data (stats + threshold) and visual banner layout verification. Color (amber), icon (ph-warning duotone), and copy formatting need human check."
---

# Phase 51: Student Web Cabinet — Shell + Schedule + Check-in Verification Report

**Phase Goal:** A student who logs in at `/student/dashboard` sees their daily overview, can view the week schedule, and can geo check-in for an active lesson with real-time STOMP status updates — all within the Angular web-panel

**Verified:** 2026-04-09T14:25:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria + PLAN frontmatter must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `/student/dashboard` renders today's schedule summary, next upcoming active lesson card, and red-zone subject warnings (SC1) | ✓ VERIFIED | `student-dashboard.component.ts:78-180` signals+forkJoin wire getWeekLessons+getStudentStats+resolveGlobalThreshold; template renders dashboard-today chips, `<app-next-lesson-card>`, conditional `<app-redzone-warning>` per sub-threshold subject. 11 vitest specs green. |
| 2 | `/student/schedule` shows weekly calendar with prev/next week nav; clicking a lesson reveals subject/time/room/status — functionally equivalent to PWA view (SC2) | ✓ VERIFIED | `student-schedule.component.ts:89-186` has prevWeek/nextWeek/jumpToToday/selectDay/toggleLesson; template has 6-day tablist, week nav strip, floating today pill, 4-skeleton loading, inline LessonRow detail panel. 36 vitest specs green (18 week-utils + 8 LessonRow + 10 component). |
| 3 | `/student/checkin` shows active lesson card with "Отметиться" button; GPS capture + real-time STOMP status update without page reload (SC3) | ✓ VERIFIED | `student-checkin.component.ts:94-263` implements 6-state discriminated union (idle/ready/gps_pending/submitting/confirmed/error), connects StudentStompService in ngOnInit, calls `navigator.geolocation.getCurrentPosition` with 10s timeout, POSTs `{lat,lng}` to `/api/attendance/checkin`, auto-transitions to confirmed on STOMP attendance.marked matching user.id+lesson.id. 19 vitest specs green (8 error-mapper + 11 component). |
| 4 | A student with no active lesson sees "Нет активной пары" empty state on `/student/checkin` (SC4) | ✓ VERIFIED | `student-checkin.component.html:61-87` `@else` branch renders empty state with circle icon, "Нет активной пары" heading, 5-minute-window body text, "Следующая пара" hint or "На сегодня пар больше нет" fallback. Spec #1-2 verify both sub-variants. |
| 5 | STOMP toolchain installed and shared services exist (Plan 01) | ✓ VERIFIED | `package.json:26,30,43` declares @stomp/stompjs ^7.3.0 + sockjs-client ^1.6.1 + @types/sockjs-client ^1.5.4. `student-stomp.service.ts:71-92` builds Client with SockJS factory at `/api/ws?token=`, subscribes to `/topic/group/{groupId}`, emits attendance.marked via marked$ Subject. |
| 6 | app.routes.ts lazy-loads all three student routes to the real components (no placeholder remains) | ✓ VERIFIED | `app.routes.ts:90-121` student parent with `canActivate: [studentGuard]` has three children (dashboard/schedule/checkin) each `loadComponent` importing the real implementation. `student-placeholder/` directory deleted, grep returns 0 matches. |
| 7 | Sidebar renders Главная/Расписание/Отметиться nav items and "Студент" role chip for STUDENT role | ✓ VERIFIED | `sidebar.component.ts:74-79` primaryItems has Главная→/student/dashboard with roles:['STUDENT']; lines 117-128 allNavItems has Расписание→/student/schedule + Отметиться→/student/checkin. `sidebar.component.html:82` `@case ('STUDENT') { Студент }` role chip. |
| 8 | 162→253 vitest regression passes + dev build exit 0 + prod build exit 0 | ✓ VERIFIED | Re-ran full suite: **253 passed (253)** in 36 test files (29s). `ng build --configuration=development` exit 0. `ng build` (prod) exit 0. Only pre-existing warnings (TS-998113 NgIf journal-cell, bundle budget +155kB) + two new informational CJS-not-ESM notes for @stomp/stompjs and sockjs-client (documented as acceptable in the phase prompt). |

**Score:** 8/8 truths verified (all automated gates pass)

### Required Artifacts (cross-plan)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/web-panel/package.json` | @stomp/stompjs + sockjs-client + @types/sockjs-client | ✓ | Lines 26, 30, 43 |
| `.../student/shared/student-schedule.types.ts` | 10+ DTO interfaces | ✓ | 108 lines, 10 exported types incl. LessonResponse, StudentStatsResponse, AttendanceMarkedPayload, StompEnvelope |
| `.../student/shared/student-api.service.ts` | HttpClient wrapper with 5 methods | ✓ | 80 lines, methods: getWeekLessons, getStudentStats, resolveGlobalThreshold, resolveGroupThreshold, checkin |
| `.../student/shared/subject-cache.service.ts` | shareReplay cache | ✓ | 50 lines, Map<number, Observable<string>> + shareReplay(1) + catchError fallback to "Предмет" |
| `.../student/shared/student-stomp.service.ts` | SockJS+STOMP client lifecycle | ✓ | 104 lines, connect/disconnect + marked$ Subject + idempotent per groupId + onStompError only logs headers['message'] |
| `.../student/schedule/student-schedule.component.ts` | Full week-view page | ✓ | 186 lines, signals+computed+animations, prev/next/jumpToToday, 6-tab day selector, lesson expand |
| `.../student/schedule/week-utils.ts` | Pure date helpers | ✓ | 7 exports: getMonday, addDays, formatDate, getTodayDayIndex, formatWeekRange, isSameWeek, MONTH_ABBREV |
| `.../student/schedule/lesson-row/lesson-row.component.ts` | Presentational LessonRow | ✓ | Exists with ts/html/css/spec |
| `.../student/checkin/student-checkin.component.ts` | 6-state machine + GPS + STOMP | ✓ | 263 lines, CheckinState union exported, auto-confirm via marked$ subscription |
| `.../student/checkin/checkin-error-mapper.ts` | Pure status→Russian map | ✓ | 40 lines, exported mapCheckinError + GPS_DENIED_MESSAGE const |
| `.../student/dashboard/student-dashboard.component.ts` | Greeting+chips+NextLessonCard+redzone | ✓ | 180 lines, forkJoin + live clock + 4-branch greeting |
| `.../student/dashboard/next-lesson-card/next-lesson-card.component.ts` | Presentational card | ✓ | Exists with ts/html/css/spec |
| `.../student/dashboard/redzone-warning/redzone-warning.component.ts` | Amber banner | ✓ | Exists with ts/html/css/spec |

All 36 files declared across Plans 01-04 exist and contain substantive implementation.

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| app.routes.ts | StudentDashboardComponent | loadComponent | ✓ WIRED | `app.routes.ts:96-102` |
| app.routes.ts | StudentScheduleComponent | loadComponent | ✓ WIRED | `app.routes.ts:103-110` |
| app.routes.ts | StudentCheckinComponent | loadComponent | ✓ WIRED | `app.routes.ts:111-118` |
| sidebar.component.ts | /student/dashboard | primaryItems STUDENT | ✓ WIRED | Line 77 |
| sidebar.component.ts | /student/schedule | allNavItems STUDENT | ✓ WIRED | Line 120 |
| sidebar.component.ts | /student/checkin | allNavItems STUDENT | ✓ WIRED | Line 126 |
| student-stomp.service.ts | /api/ws?token= | new SockJS | ✓ WIRED | Line 72: `new SockJS(\`/api/ws?token=${getAccessToken() ?? ''}\`)` |
| student-schedule.component.ts | StudentApiService.getWeekLessons | inject+subscribe | ✓ WIRED | Lines 90, 138-151 (loadWeek) |
| student-schedule.component.ts | SubjectCacheService.getName | inject + async pipe | ✓ WIRED | Line 91, template line 78 `(getSubjectName$(lesson.subjectId) \| async)` |
| student-schedule.component.ts | AuthService.currentUser | inject + read groupId | ✓ WIRED | Line 92, 123-124 |
| student-checkin.component.ts | StudentApiService.checkin | inject+subscribe | ✓ WIRED | Line 95, 199-213 (onCheckinClick) |
| student-checkin.component.ts | StudentApiService.getWeekLessons | inject+subscribe today | ✓ WIRED | Line 163 (fetchToday) |
| student-checkin.component.ts | StudentStompService.connect/marked$ | inject+subscribe marked$ | ✓ WIRED | Line 97, 142-153 |
| student-checkin.component.ts | navigator.geolocation.getCurrentPosition | browser API | ✓ WIRED | Line 196-219 |
| student-dashboard.component.ts | StudentApiService.getWeekLessons | forkJoin | ✓ WIRED | Line 158 |
| student-dashboard.component.ts | StudentApiService.getStudentStats | forkJoin | ✓ WIRED | Line 159 |
| student-dashboard.component.ts | StudentApiService.resolveGlobalThreshold | forkJoin | ✓ WIRED | Line 160 |
| next-lesson-card template | /student/checkin | routerLink | ✓ WIRED | `next-lesson-card.component.html:18`: `<a routerLink="/student/checkin" ...>` (only rendered when isActive) |

All 18 key links wired.

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Real Data | Status |
|----------|---------------|--------|-----------|--------|
| student-dashboard.component.ts | `lessons()` | `studentApi.getWeekLessons(groupId, today, today)` forkJoin branch | ✓ Real HTTP to schedule-service `/api/schedule/groups/{id}/lessons` | ✓ FLOWING |
| student-dashboard.component.ts | `stats()` | `studentApi.getStudentStats()` forkJoin branch | ✓ Real HTTP to attendance-service `/api/attendance/reports/student/stats` | ✓ FLOWING |
| student-dashboard.component.ts | `threshold()` | `studentApi.resolveGlobalThreshold()` forkJoin branch | ✓ Real HTTP to academic-service `/api/academic/thresholds/resolve` | ✓ FLOWING |
| student-dashboard.component.ts | `redZoneSubjects()` | computed from stats + threshold | ✓ Derived from real data | ✓ FLOWING |
| student-schedule.component.ts | `lessons()` | `studentApi.getWeekLessons(...)` in loadWeek | ✓ Real HTTP, populated on mount + each week change | ✓ FLOWING |
| student-schedule.component.ts | subject names per row | `SubjectCacheService.getName(subjectId)` async pipe | ✓ Real HTTP to academic-service with cache | ✓ FLOWING |
| student-checkin.component.ts | `lessons()` | `studentApi.getWeekLessons(today, today)` in fetchToday | ✓ Real HTTP on mount | ✓ FLOWING |
| student-checkin.component.ts | `activeLesson()` | computed from lessons().find(status==='ACTIVE') | ✓ Derived from real data | ✓ FLOWING |
| student-checkin.component.ts | `attendeeCount()` | `stomp.marked$` subscription | ✓ Real STOMP Subject from Plan 01 StudentStompService | ✓ FLOWING (needs human to verify real broker push) |
| student-checkin.component.ts | `state()` transition on marked | `stomp.marked$` payload matching user.id+lesson.id | ✓ Compared against `auth.currentUser().id` (JWT claim) | ✓ FLOWING (needs human to verify real broker push) |

No hollow data — every rendered signal traces to a real HttpClient call or STOMP subscription, not a hardcoded value.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full vitest regression | `cd frontends/web-panel && npx vitest run` | 253 passed (253) in 36 test files, 29s | ✓ PASS |
| Dev build succeeds | `cd frontends/web-panel && npx ng build --configuration=development` | exit 0, bundle complete, `student-checkin-component`, `student-schedule-component`, `student-dashboard-component` appear as lazy chunks | ✓ PASS |
| Prod build succeeds | `cd frontends/web-panel && npx ng build` | exit 0, bundle complete. Only pre-existing + 2 new informational CJS-not-ESM warnings | ✓ PASS |
| STOMP deps installed | `grep @stomp/stompjs package.json` | `"@stomp/stompjs": "^7.3.0"` + `"sockjs-client": "^1.6.1"` + `"@types/sockjs-client": "^1.5.4"` | ✓ PASS |
| Placeholder component deleted | `ls frontends/web-panel/src/app/features/student/student-placeholder/` | No such file or directory | ✓ PASS |
| No placeholder references remain | `grep -r student-placeholder frontends/web-panel/src/` | 0 matches | ✓ PASS |
| No skipped tests | `grep -r '.skip(\|.only(\|xit(\|xdescribe(' student/` | 0 matches | ✓ PASS |
| All 9 commits present in history | `gsd-tools verify commits` | 9/9 valid | ✓ PASS |
| Live running app smoke test (nav to all 3 routes, render, interact) | n/a — no runnable `ng serve` + backend stack in sandbox | cannot run | ? SKIP (routed to human) |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|---------------|-------------|--------|----------|
| STU-WEB-01 | 51-01, 51-04 | `/student/dashboard` — overview: today's schedule, next active lesson, red-zone warnings, recent notifications | ✓ SATISFIED (partial — notifications deferred to Phase 52 STU-WEB-06) | StudentDashboardComponent shipped with greeting hero + today chips + NextLessonCard + RedzoneWarning. "Recent notifications" is explicitly part of Phase 52 STU-WEB-06. |
| STU-WEB-02 | 51-01, 51-02 | `/student/schedule` — week/day nav, lesson details, PWA parity | ✓ SATISFIED | StudentScheduleComponent with week nav, 6 day tabs, LessonRow detail expand, skeleton/empty/error states, routeFade+daySlide animations. 36 specs green. |
| STU-WEB-03 | 51-01, 51-03 | `/student/checkin` — active lesson + geo check-in + STOMP real-time | ✓ SATISFIED | StudentCheckinComponent with 6-state machine, navigator.geolocation, POST /api/attendance/checkin, StudentStompService marked$ auto-confirm. 19 specs green. |

**No orphaned requirements:** REQUIREMENTS.md maps STU-WEB-01..03 to Phase 51 (lines 143-145); all three are claimed by Phase 51 plans and verified in the codebase. STU-WEB-04, STU-WEB-05 are explicitly mapped to Phase 52 (lines 146-147) and out of scope.

**Note on STU-WEB-01 "recent notifications":** The REQUIREMENTS.md full text mentions "recent notifications" on the dashboard, but ROADMAP Phase 51 SC1 only requires "today's schedule summary, next upcoming active lesson card, red-zone subject warning". Recent notifications are addressed by Phase 52 STU-WEB-06 (`/student/notifications` scrollable log). The ROADMAP contract for Phase 51 is fully met. This split is the intentional scope boundary between Phase 51 (shell + 3 core pages) and Phase 52 (homework + stats + notifications + profile).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| student-checkin.component.ts | 113 | `return null;` in `nextPlannedLesson` computed | ℹ️ Info | Not a stub — legitimate early return when an ACTIVE lesson exists (nextPlanned only meaningful in empty state) |
| student-dashboard.component.ts | 135 | `return [];` in `redZoneSubjects` computed | ℹ️ Info | Not a stub — guard clause while `stats` or `threshold` signal hasn't resolved yet (initial render before forkJoin completes) |
| student-dashboard.component.ts | 56 | docstring mention of "skeleton placeholder" | ℹ️ Info | Docstring describing the loading skeleton element, not a code placeholder |

No blocker or warning anti-patterns. No TODO/FIXME/XXX/HACK markers in the student feature tree. No stub grep hits on rendered state variables.

### Human Verification Required

12 items require human testing — see `human_verification` in frontmatter above. Summary:

1. **Dashboard visual** — greeting, live clock, chips, NextLessonCard, red-zone banners, routeFade animation
2. **Schedule prev/next week** — week label correctness, day-slide animation, 'Сегодня' pill
3. **Schedule row expand** — inline detail panel, one-at-a-time behavior
4. **Checkin hero during ACTIVE lesson** — blinking dot, live pill, 48px CTA, counter layout
5. **Checkin GPS permission granted** — 3-stage button label, POST body, 2xx confirmation badge
6. **Checkin GPS denied** — inline error copy appears, button re-enables
7. **STOMP real-time counter** — second session triggers counter increment without reload
8. **STOMP self-confirm** — same-user second device auto-confirms without second HTTP POST
9. **Checkin empty states** — three variants (empty, next-hint, no-more)
10. **Sidebar STUDENT nav layout** — 3 items, role chip, 'Учёба' section label
11. **prefers-reduced-motion** — OS-level reduce motion disables animations
12. **Red-zone warning real data** — amber banner with correct copy and rounded percentage

These require a live backend stack (schedule-service, attendance-service, academic-service, notification-web STOMP broker) and a real browser for visual and real-time verification. No automated substitute exists.

### Gaps Summary

**Zero functional gaps.** All ROADMAP Success Criteria satisfied by substantive implementation, all 18 key links wired, all 36 artifact files present with real logic (no stubs), all STU-WEB-01..03 requirements closed per plan frontmatter and verified in code. 253/253 vitest tests pass on main. Dev and prod builds exit 0.

The phase status is `human_needed` (not `passed`) exclusively because frontend phases of this kind require human validation of visual appearance, animations, GPS browser flows, and real-time STOMP propagation — none of which can be verified programmatically in the sandbox. The automated gates (code existence, wiring, key links, data-flow, unit tests, builds, anti-patterns) all pass with no reservations.

### Deferred Items (for reference)

- **STU-WEB-01 "recent notifications" sub-clause** — explicitly scoped to Phase 52 STU-WEB-06 per REQUIREMENTS.md line 146, not a Phase 51 gap.

---

*Verified: 2026-04-09T14:25:00Z*
*Verifier: Claude (gsd-verifier) — Opus 4.6 (1M context)*
