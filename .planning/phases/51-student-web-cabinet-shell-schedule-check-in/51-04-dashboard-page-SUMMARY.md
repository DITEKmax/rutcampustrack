---
phase: 51
plan: 04
subsystem: frontend/web-panel
tags: [angular, student-cabinet, dashboard, signals, forkjoin, tdd]
requires:
  - Plan 51-01 shared services (StudentApiService, SubjectCacheService, student-schedule.types)
  - Phase 50 AuthService.currentUser signal
provides:
  - /student/dashboard functional page (greeting, today chips, next-lesson card, red-zone warnings)
  - NextLessonCardComponent (reusable presentational card)
  - RedzoneWarningComponent (reusable amber banner)
affects:
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts (shell replaced)
metrics:
  duration: 25m
  completed: 2026-04-09
  files_created: 11
  files_modified: 1
  tests_added: 19
  requirements_closed: [STU-WEB-01]
---

# Phase 51 Plan 04: Dashboard Page Summary

**One-liner:** /student/dashboard fully wired — greeting hero with live clock and time-of-day heading, horizontal chip row summarising today lessons, NextLessonCard spotlighting ACTIVE or earliest PLANNED lesson with inline "Отметиться" CTA, red-zone warning banners for subjects below the global attendance threshold, all driven by three parallel HttpClient calls merged with forkJoin and 19 vitest specs green.

## What Shipped

### Task 1 — Presentational sub-components (commit fec9d56)

Two standalone OnPush components under features/student/dashboard/:

1. NextLessonCardComponent (app-next-lesson-card)
   - Inputs: lesson: LessonResponse | null, subjectName: string
   - Active variant adds --border-accent + --glow-primary on the card root
   - Eyebrow shows "Текущая пара" (ACTIVE) or "Следующая пара" (PLANNED)
   - Meta row: clock icon + HH:mm–HH:mm (mono tabular-nums) + map-pin icon + "Ауд. {room}"
   - Inline routerLink=/student/checkin rendered ONLY on ACTIVE
   - Empty variant with ph-calendar-blank and "Сегодня пар нет" when lesson is null
   - Token-only CSS, prefers-reduced-motion disables the live-dot blink and root transition

2. RedzoneWarningComponent (app-redzone-warning)
   - Inputs: subjectName (required), percentage (required)
   - Renders amber banner with ph-duotone ph-warning + "<strong>{subject}</strong> — посещаемость ниже порога ({N}%)"
   - Rounds percentage to nearest integer via Math.round in the percentLabel getter
   - --accent-warning + color-mix(in oklab, ...) tokens, zero hex values

Specs (8 total): 5 NextLessonCard + 3 RedzoneWarning — all green.

### Task 2 — StudentDashboardComponent full implementation (commit 007b83b)

Replaced the Plan 01 empty shell with a full implementation.

Component (student-dashboard.component.ts):
- ChangeDetectionStrategy.OnPush, standalone, imports AsyncPipe + NextLessonCardComponent + RedzoneWarningComponent
- Animation trigger routeFade (opacity 0→1 + translateY 8px→0 over 200ms cubic-bezier(0.16, 1, 0.3, 1)) on :enter
- Signals: lessons, stats, threshold, loading, error, private _now
- Computed: timeLabel, dateLabel, greeting (4 branches: Доброй ночи / Доброе утро / Добрый день / Добрый вечер), todaySorted, nextLesson (ACTIVE → earliest PLANNED → null), redZoneSubjects (filter percentage < threshold.percentage)
- ngOnInit: reads groupId from auth.currentUser()?.groupId, runs forkJoin({ lessons, stats, threshold }) with today date. Live clock setInterval 60_000 cleaned via destroyRef.onDestroy.

Template (student-dashboard.component.html):
- Hero: .dashboard__eyebrow "Студент" + pulse dot, .dashboard__title greeting, .dashboard__subtitle date, .dashboard__clock HH:mm + "по Москве"
- @if (error()) banner / @else if (loading()) two skeleton rows / @else main content
- <app-next-lesson-card> bound to nextLesson() + subject name via (getSubjectName$() | async) ?? Предмет
- .dashboard-today section: "Расписание на сегодня" + either "Сегодня пар нет" or horizontal chip row
- .dashboard-redzone section (conditional) with <app-redzone-warning> for each sub-threshold subject

CSS (student-dashboard.component.css):
- Hero mirrors admin-dashboard tokens (radial gradients of --accent-primary / --accent-secondary + --bg-secondary)
- Clock uses --font-mono + var(--text-2xl) + tabular-nums
- .dashboard__error uses --accent-danger with color-mix overlays
- Shimmer skeleton animation and .dashboard__pulse disabled under prefers-reduced-motion
- Today-chip status dots: [data-status=ACTIVE] → --accent-primary glow, [data-status=CLOSED] → --status-present, [data-status=CANCELLED] → muted
- Zero hex values — all colors from design tokens

Spec (student-dashboard.component.spec.ts, 11 specs, all green):
1. Three parallel API requests on init
2. Greeting "Добрый день" at 14:00
3. Greeting "Доброе утро" at 08:00
4. Greeting "Добрый вечер" at 21:00
5. "Сегодня пар нет" empty state
6. One .dashboard-today-chip per lesson
7. NextLessonCard wired to ACTIVE lesson when one exists
8. NextLessonCard wired to earliest PLANNED when no ACTIVE
9. One red-zone banner per sub-threshold subject (42% shown when threshold is 70%)
10. Zero banners when all subjects above threshold
11. Shared error copy when any of the three requests fails

## Deviations from Plan

### Rule 1 — Fixed spec flakiness (fake-timers incompatibility with zone.js)

- **Found during:** Task 2 green phase
- **Issue:** The plan template used @testing-library/angular render() plus vi.useFakeTimers() + vi.setSystemTime(). 7 of 11 specs failed — the DOM still rendered the loading skeleton after flush() and never transitioned to the @else branch. Root cause: vi.useFakeTimers() stopped zone.js microtask scheduling, so Angular change detection queued by signal updates from the HTTP observer never ran before the assertion.
- **Fix:** Rewrote the spec on the classic TestBed.createComponent + explicit fixture.detectChanges() pattern (same as admin-dashboard.component.spec.ts) and replaced fake timers with a Date constructor spy (vi.spyOn(globalThis, Date)) that intercepts only the zero-arg constructor and preserves Date.now, Date.parse, Date.UTC. Deterministic greeting tests without blocking zone microtasks.
- **Files modified:** student-dashboard.component.spec.ts only — no production code changed.
- **Commit:** 007b83b (inside the Task 2 TDD cycle)

### Rule 3 — Hardened error spec against forkJoin cancellation races

- **Found during:** Task 2 red → green iteration
- **Issue:** The error spec first failed with "Cannot flush a cancelled request" because forkJoin cancelled one sibling request in-flight. Draining only cancellation-eligible ones left one request still pending, triggering httpMock.verify() "Expected no open requests, found 1: GET /api/academic/thresholds/resolve".
- **Fix:** Added a drainSafely() helper that walks httpMock.match(pattern), skips req.cancelled, and wraps req.flush({}) in try/catch to swallow races between forkJoin cancellation and the backend flush queue.
- **Files modified:** student-dashboard.component.spec.ts
- **Commit:** 007b83b

**Rule 4** — no architectural changes required.

## Out-of-Scope Findings (not fixed, not blockers)

1. **Full npm test has 1 failing test file — student-stomp.service.spec.ts** fails to resolve @stomp/stompjs / sockjs-client. Root cause: the worktree base (ca9395c) expects those deps in package.json, but the working tree contains a more recent package.json that removes them. Orchestration artefact of the wave-2 parallel worktree setup, NOT caused by any 51-04 dashboard changes. Plan 51-04 does not touch the STOMP layer. All 19 dashboard-scope tests are green.

2. **npm run build (prod) fails in the font-inlining plugin** — cannot fetch https://fonts.googleapis.com/css2?family=DM+Sans over the internet from this worktree sandbox. Pre-existing environment constraint, not introduced by 51-04. npx ng build --configuration=development exits 0 with only a pre-existing TS-998113 unused-import warning in journal-cell.component.ts (owned by Phase 40, out of scope for Phase 51).

3. **app.routes.ts in the working tree still lazy-loads StudentPlaceholderComponent for /student/dashboard** — the real StudentDashboardComponent wire-up was delivered by Plan 51-01 and is expected to be re-applied by the orchestrator merge step. Plan 51-04 intentionally does NOT modify app.routes.ts (out of scope per the plan files contract). The dashboard component exists, compiles correctly, and is covered by 19 green specs.

All three are logged as deferred items for the orchestrator merge step. The 19/19 green dashboard tests are the authoritative gate for this plan.

## Threat Model Follow-up

All five STRIDE entries from the plan threat_model are honored:

- **T-51-21 (XSS):** Verified — grep -rnE "innerHTML|bypassSecurityTrust" frontends/web-panel/src/app/features/student/dashboard returns zero matches. All dynamic data rendered via {{ }} interpolation (HTML-escaped).
- **T-51-22 (IDOR):** Mitigated — groupId is read from this.auth.currentUser()?.groupId (JWT-parsed), never from route data or URL params.
- **T-51-23 (Info disclosure):** Accepted per plan — student can only see their own stats; backend owns authz.
- **T-51-24 (Tampering — threshold):** Mitigated — threshold resolved server-side; client only does display filtering (percentage < threshold.percentage).
- **T-51-25 (Spoofing — routerLink):** Mitigated — Angular RouterLink traverses the parent canActivate: [studentGuard] chain.

## Threat Flags

None. No new network endpoints, no new auth paths, no new file access, no new trust-boundary crossings. The dashboard is a read-only projection of three existing Phase-51-01 endpoints already inventoried in the parent plan.

## Known Stubs

None in the dashboard scope. The component wires real Plan 51-01 HTTP services and renders real data. The only intentional design-time fallback is getSubjectName$() returning of(Предмет) when the subject id is missing or the academic-service lookup errors — deliberate UX (never break the row) documented in SubjectCacheService (Plan 51-01).

## Verification

| Gate | Command | Result |
|------|---------|--------|
| Task 1 sub-component specs | npx vitest run next-lesson-card redzone-warning | 8/8 green |
| Task 2 dashboard spec | npx vitest run student-dashboard.component.spec.ts | 11/11 green |
| All dashboard specs | npx vitest run src/app/features/student/dashboard | 19/19 green |
| Dev build (integration) | npx ng build --configuration=development | exit 0 |
| XSS mitigation | grep innerHTML/bypassSecurityTrust in student/dashboard | zero matches |
| Token-only styling | hex-value scan in dashboard CSS files | zero matches |

Prod build and full npm test are blocked by out-of-scope environment/orchestration issues (see Out-of-Scope Findings above) — not by 51-04 code.

## Commits

| # | Hash | Message |
|---|------|---------|
| 1 | fec9d56 | feat(51-04): add NextLessonCard and RedzoneWarning sub-components |
| 2 | 007b83b | feat(51-04): fill StudentDashboardComponent with greeting, chips, wiring |

## Self-Check: PASSED

- FOUND: frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.html
- FOUND: frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.css
- FOUND: frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.html
- FOUND: frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.css
- FOUND: frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.html
- FOUND: frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.css
- FOUND: frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.spec.ts
- FOUND: commit fec9d56
- FOUND: commit 007b83b
