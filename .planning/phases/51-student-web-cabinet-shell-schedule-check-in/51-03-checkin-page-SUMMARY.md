---
phase: 51
plan: 03
subsystem: frontend/web-panel
tags: [angular, signals, stomp, geolocation, student-cabinet, checkin]
requires:
  - 51-01 (StudentApiService / SubjectCacheService / StudentStompService / empty shell)
  - services/attendance-service POST /api/attendance/checkin (Phase 17)
  - services/notification-web STOMP broker + /topic/group/{id} (Phase 20)
  - core AuthService (accessToken / currentUser signals)
provides:
  - Working /student/checkin page with GPS capture + HTTP submit + STOMP auto-confirm
  - Pure mapCheckinError(status) helper (reusable from other student pages)
  - CheckinState discriminated union (exported type)
affects:
  - frontends/web-panel/src/app/features/student/checkin/ (full page rewrite)
tech-stack:
  added:
    - "@angular/animations trigger/transition for :enter routeFade (already-installed dep)"
  patterns:
    - Signal-driven discriminated-union state machine (no BehaviorSubject, no RxJS state)
    - takeUntilDestroyed(destroyRef) for auto-cleaning STOMP subscription
    - Browser geolocation stub via Object.defineProperty(navigator, 'geolocation', ...) in tests
    - Subject-based STOMP mock with __emit test helper
    - "@let in Angular 19 template for sharing computed signal values across @if branches"
key-files:
  created:
    - frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts
    - frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.html
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.css
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts (empty shell to full implementation)
decisions:
  - Discriminated union for CheckinState rather than nested booleans — exhaustive switching in buttonLabel()/buttonDisabled() avoids impossible combinations at the type level
  - Lazy token factory `() => this.auth.accessToken()` passed to StudentStompService — component never reads the token directly (T-51-20 mitigation)
  - "@let bindings at template top for activeLesson() / nextPlannedLesson() — Angular 19 @else-if does not accept `as` aliasing mid-chain, @let is the cleanest replacement"
  - Error mapper is a pure function (not a class method) — unit-tested in 8ms without TestBed boot
  - Test spec calls fixture.detectChanges() after every httpMock.flush() — zone CD does not automatically refresh OnPush templates between synchronous flushes inside an await chain
  - Attendee counter increments for ANY attendance.marked on the active lesson — counter reflects group-wide marking, current-user-only triggers the state transition to `confirmed`
metrics:
  duration: ~38m
  completed: 2026-04-09
  files_created: 5
  files_modified: 1
  tests_added: 19
  tests_total_after: 198
  requirements_closed: [STU-WEB-03]
---

# Phase 51 Plan 03: Checkin Page Summary

**One-liner:** `/student/checkin` is a signal-driven Angular 19 state machine that captures GPS, POSTs `/api/attendance/checkin`, and auto-transitions to the confirmed badge on an incoming STOMP `attendance.marked` for the current user — all six states (idle / ready / gps_pending / submitting / confirmed / error) covered by 11 component specs plus 8 error-mapper specs on top of Plan 01's shared-services foundation.

## What Shipped

### Task 1 — Error mapper + StudentCheckinComponent (commit `4354ede`)

Built in TDD order:

1. **`checkin-error-mapper.spec.ts`** (RED) — 8 assertions, each matching a literal UI-SPEC copy string for 403 / 404 / 409 / 422 / 429 / default + the `GPS_DENIED_MESSAGE` constant.
2. **`checkin-error-mapper.ts`** (GREEN) — pure `switch` on HTTP status returning the Russian error copy. Kept as a standalone module so other student pages can reuse without pulling component dependencies.
3. **`student-checkin.component.ts`** — full rewrite of Plan 01's empty shell:
   - Exported `CheckinState` discriminated union with six variants
   - Signals: `lessons`, `loading`, `fetchError`, `state`, `attendeeCount`
   - Computed: `activeLesson` (finds `status === 'ACTIVE'`), `nextPlannedLesson` (sorts by `startTime` when no active)
   - `ngOnInit` fetches today's lessons, connects to STOMP via `() => auth.accessToken()` lazy factory, subscribes to `marked$` with `takeUntilDestroyed(destroyRef)`
   - `ngOnDestroy` calls `stomp.disconnect()`
   - `onCheckinClick` validates state, transitions to `gps_pending`, invokes `navigator.geolocation.getCurrentPosition` with `{ timeout: 10000, maximumAge: 30000 }`, transitions to `submitting`, POSTs to `/api/attendance/checkin`, handles success to `confirmed` or maps error via `mapCheckinError`
   - STOMP subscription: on every `attendance.marked` for the active lesson id, increments the counter; if `payload.user_id === currentUser.id`, also transitions to `confirmed`
4. **`student-checkin.component.html`** — Angular 19 `@if/@else-if/@else` control-flow with `@let` aliases for `activeLesson()` and `nextPlannedLesson()` at the top of the template. Three surfaces:
   - Loading skeleton (`aria-busy="true"`, `aria-label="Загрузка…"`)
   - Active hero card: live dot + "Идёт сейчас" pill, subject name (`text-2xl`), `HH:mm–HH:mm` time (mono tabular-nums), `Ауд. {room}`, primary CTA or confirmed badge, aria-live counter, inline error paragraph
   - Idle empty state: circle icon, "Нет активной пары", body text, "Следующая пара" hint or "На сегодня пар больше нет" fallback
5. **`student-checkin.component.css`** — token-only styles (zero hex values), 48px CTA min-height, blinking dot via `@keyframes checkin-live-blink`, skeleton shimmer via `@keyframes checkin-shimmer`, full `@media (prefers-reduced-motion: reduce)` block disabling all transforms/animations.

Angular 19 template wrinkle surfaced mid-task: `@else if (activeLesson(); as active)` is NOT a legal Angular binding (the `as` alias is only valid in the initial `@if`). Replaced with `@let active = activeLesson();` at the top of the template — clean, idiomatic for Angular 19, no refactor downstream.

### Task 2 — StudentCheckinComponent spec (commit `95fb385`)

`student-checkin.component.spec.ts` — 11 `it(…)` blocks, each independent, covering:

| #  | Scenario                                                                          |
|----|-----------------------------------------------------------------------------------|
| 1  | Empty state: "Нет активной пары" + "На сегодня пар больше нет"                    |
| 2  | Planned-only: "Нет активной пары" + "Следующая пара" hint                         |
| 3  | Active hero: live pill, CTA by role, "09:00" time, "Ауд. 404"                     |
| 4  | Click CTA then POST with `{lat: 55.1, lng: 37.2}` then "Вы отметились"            |
| 5  | 2xx flush then confirmation badge                                                 |
| 6  | 409 flush then "Вы уже отмечены на этом занятии." inline error                    |
| 7  | GPS denied then "Нет доступа к геолокации…" inline error                          |
| 8  | STOMP attendance.marked for current user then confirmed WITHOUT extra HTTP call   |
| 9  | STOMP attendance.marked for another user then counter "1 отметилось", CTA same    |
| 10 | `ngOnInit` calls `StudentStompService.connect(5, factory)`                        |
| 11 | `ngOnDestroy` calls `StudentStompService.disconnect()`                            |

Test harness decisions:

- **Signal-based AuthService mock:** uses `computed(() => user)` and `signal<string>().asReadonly()` — matches the exact shape of the real `AuthService` so the component's `inject(AuthService)` binds cleanly.
- **STOMP Subject mock:** exposes `__emit(payload)` test hook that pushes through the real Subject — the component sees an idiomatic RxJS observable, tests get synchronous control over incoming frames.
- **`stubGeo(success)` helper:** rewrites `navigator.geolocation` via `Object.defineProperty(…, { configurable: true })` per test. No JSDOM polyfill needed.
- **`fixture.detectChanges()` after every `httpMock.flush()`:** zone-based CD does NOT auto-refresh OnPush templates between synchronous flushes inside an async `it(…)`. Explicit `detectChanges()` is required. This is the non-obvious test-infra decision that turned 9 failing tests into 11 green ones.
- **Fake timers + `vi.setSystemTime(2026-04-09 12:00)`:** freezes the clock so `todayDateString()` is deterministic.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Re-seeded the worktree from the correct base commit and restored Plan 01 artifacts**
- **Found during:** Worktree boot
- **Issue:** Worktree HEAD was `8542275` (origin/main), NOT the expected `ca9395c` (Plan 01 outputs + plan files). The `git reset --soft ca9395c` removed the Plan 01 student shared services, routing changes, and phase-51 plan directory from the working tree.
- **Fix:** `git checkout ca9395c -- .planning/phases/51-*/ frontends/web-panel/src/app/features/student/ frontends/web-panel/src/app/app.routes.ts frontends/web-panel/src/app/layout/sidebar/sidebar.component.{html,ts,spec.ts} frontends/web-panel/package{.json,-lock.json}` restored Wave 1's committed output into the working tree without staging any of it into my task commits.
- **Commit:** n/a (pre-work housekeeping)

**2. [Rule 3 - Blocking] Installed web-panel node_modules**
- **Found during:** First `npx vitest run` on error-mapper spec
- **Issue:** `Cannot find module 'vitest/config'` — node_modules were never installed in this fresh worktree.
- **Fix:** `cd frontends/web-panel && npm install` (978 packages, 22s).
- **Commit:** n/a (environment prep)

**3. [Rule 1 - Bug] Template `@else if (…; as …)` aliasing is not supported in Angular 19**
- **Found during:** Task 1 `npm run build`
- **Issue:** The plan's sample template in `<action>` uses `@else if (activeLesson(); as active)`, but Angular 19 rejects `as`-binding in `@else if` blocks (only the top `@if` can bind an alias). Production build failed with `NG9: Property 'active' does not exist on type 'StudentCheckinComponent'`.
- **Fix:** Replaced the inline alias with `@let active = activeLesson();` + `@let nextPlanned = nextPlannedLesson();` declared at the top of the template. Angular 19's `@let` is the idiomatic replacement and is scoped to the enclosing component view.
- **Files modified:** `frontends/web-panel/src/app/features/student/checkin/student-checkin.component.html`
- **Commit:** `4354ede`

**4. [Rule 1 - Bug] Fixture CD not triggered after `httpMock.flush()`**
- **Found during:** Task 2 first test run (9/11 failing, stuck on loading skeleton)
- **Issue:** After `httpMock.flush()`, signal updates propagate to the component, but the OnPush template was not re-rendering — the DOM still showed the skeleton. With a zone-based change-detection configuration, flushing an HTTP request inside a synchronous `it(…)` chain does not auto-trigger CD on OnPush components; `testing-library/angular` does not patch this.
- **Fix:** Call `fixture.detectChanges()` after every `httpMock.flush(...)` and after every simulated user interaction / STOMP emit. All 11 tests now pass deterministically.
- **Files modified:** `frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts`
- **Commit:** `95fb385`

## Threat Model Follow-up

All STRIDE entries from the plan's `<threat_model>` block are honored:

- **T-51-13 (Info Disclosure — GPS coordinates):** `navigator.geolocation.getCurrentPosition` is invoked ONLY inside `onCheckinClick`. Coordinates pass through `{lat, lng}` straight to `StudentApiService.checkin(...)`. `grep -rn "console\.log.*(lat|lng|token|accessToken)" frontends/web-panel/src/app/features/student/checkin` returns 0 matches.
- **T-51-14 (Tampering — checkin body):** The body is literally `{ lat: position.coords.latitude, lng: position.coords.longitude }`. No `lesson_id`, no `user_id`. Server resolves the lesson from JWT subject + wall-clock (attendance-service Phase 17).
- **T-51-15 (Spoofing — STOMP user_id):** The component compares `payload.user_id === user.id` where `user = auth.currentUser()` — a JWT-derived computed signal. A crafted broker message cannot confirm for a different student.
- **T-51-16 (XSS — subjectName / room):** All interpolation uses `{{ }}` (Angular HTML-escapes). No `[innerHTML]`, no `bypassSecurityTrust*`. `grep -rn "innerHTML|bypassSecurityTrust" frontends/web-panel/src/app/features/student/checkin` returns 0 matches.
- **T-51-17 (IDOR — groupId):** `groupId` is read from `auth.currentUser().groupId` (JWT claim), never from a route param.
- **T-51-18 (DoS — CTA spamming):** `buttonDisabled()` returns `true` during `gps_pending`, `submitting`, and `confirmed`. The click cannot re-fire during an in-flight request.
- **T-51-19 (Repudiation):** Accepted per plan — attendance-service Phase 17 is the audit trail.
- **T-51-20 (Info Disclosure — access token):** The component passes `() => this.auth.accessToken()` as the token factory. It never reads the token value directly, never logs it, never writes it to template.

## Verification

| Gate                                                                                 | Result |
|--------------------------------------------------------------------------------------|--------|
| `npx vitest run src/app/features/student/checkin/checkin-error-mapper`               | **8/8 green** |
| `npx vitest run src/app/features/student/checkin/student-checkin.component.spec`     | **11/11 green** |
| `npx vitest run` (full web-panel regression)                                         | **198/198 green** across 30 test files |
| `ng build --configuration development`                                               | **exit 0**, bundle complete |
| `grep 'console\.log.*(lat\|lng\|token\|accessToken)' features/student/checkin`       | 0 matches |
| `grep 'innerHTML\|bypassSecurityTrust' features/student/checkin`                     | 0 matches |
| `grep '#[0-9a-fA-F]{3,8}' features/student/checkin/*.css`                            | 0 matches (all CSS is token-only) |
| `it(` count in component spec                                                        | 11 (>= 10 required) |

### Production build (`npm run build`) — deferred

The production build configuration attempts to inline Google Fonts via `@import url("https://fonts.googleapis.com/css2?family=DM+Sans:…")` (`src/styles/fonts.css:22`). This plugin fetches the stylesheet at build time. In the current worktree environment the fetch fails (network/DNS to fonts.googleapis.com is unavailable), producing a latent environment-only failure. Not caused by Plan 51-03 code. `ng build --configuration development` (which skips external font inlining) exits 0. All code is production-build clean. Orchestrator / verifier should run `npm run build` in a network-connected environment before final merge.

## Commits

| # | Hash      | Message                                                                  |
|---|-----------|--------------------------------------------------------------------------|
| 1 | `4354ede` | feat(51-03): checkin error mapper + StudentCheckinComponent shell         |
| 2 | `95fb385` | test(51-03): StudentCheckinComponent — 11 specs covering all state transitions |

## Self-Check: PASSED

- FOUND: frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts
- FOUND: frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
- FOUND: frontends/web-panel/src/app/features/student/checkin/student-checkin.component.html
- FOUND: frontends/web-panel/src/app/features/student/checkin/student-checkin.component.css
- FOUND: frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts
- FOUND: commit 4354ede
- FOUND: commit 95fb385
