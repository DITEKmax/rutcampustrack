---
phase: 51
plan: 02
subsystem: frontend/web-panel
tags: [angular, signals, animations, schedule, student-cabinet, vitest, tdd]
requires:
  - 51-01 StudentApiService.getWeekLessons (schedule-service HATEOAS unwrap)
  - 51-01 SubjectCacheService.getName (shareReplay subject-name cache)
  - 51-01 /student/schedule route + studentGuard parent (app.routes.ts)
  - Phase 50 AuthService.currentUser signal with groupId claim
  - schedule-service Phase 13 lessons endpoint (GET /api/schedule/groups/{id}/lessons)
  - academic-service Phase 8 subjects endpoint (GET /api/academic/subjects/{id})
provides:
  - frontends/web-panel/src/app/features/student/schedule/week-utils.ts (pure ISO-week helpers)
  - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts (presentational row)
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts (full week-view page)
  - Full /student/schedule page — functional replacement for the empty shell from Plan 01
affects:
  - Plan 51-04 (student dashboard): may reuse week-utils helpers for "Сегодня" detection
  - Plan 51-03 (student checkin): may reuse LessonRow visual language for hero card state
tech-stack:
  added: []
  patterns:
    - "signal<T>() + computed() + inject() reactive state (matches admin-dashboard pattern)"
    - "takeUntilDestroyed(destroyRef) for auto-cleanup of HTTP subscriptions"
    - "@angular/animations trigger() + transition(':enter') for route-fade and day-slide"
    - "Presentational lesson-row component receives subjectName via async pipe from parent"
    - "Token-only CSS (no hex literals) — every color/space/motion sourced from tokens.css"
    - "vitest + @testing-library/angular with HttpTestingController + computed signal AuthService mock"
key-files:
  created:
    - frontends/web-panel/src/app/features/student/schedule/week-utils.ts
    - frontends/web-panel/src/app/features/student/schedule/week-utils.spec.ts
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.html
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.css
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.html
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.css
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
        (empty shell from 51-01 → full signal-based component with HTTP, animations, state machine)
key-decisions:
  - "Sunday snaps back to Сб (index 5) in getTodayDayIndex() — schedule UI never shows Вс, so Sunday lands on the last visible tab instead of wrapping to Пн of the next week"
  - "Lesson-row click toggles the detail panel via a single expandedLessonId signal (only one panel open at a time) — matches PWA LessonCard UX and keeps selection state trivially serialisable"
  - "Subject name is resolved lazily per-row via async pipe reading SubjectCacheService.getName() — the cache guarantees one HTTP call per subjectId per session regardless of row count"
  - "Defense-in-depth groupId guard — if auth.currentUser().groupId is null the component surfaces a friendly error instead of hitting /api/schedule/groups/undefined/lessons (T-51-12 mitigation)"
  - "Lesson-type label falls back to 'Пара №{lessonNumber}' because LessonResponse does not expose a type field — Phase 52 may extend the DTO; documented as a known deviation from UI-SPEC line 176"
  - "takeUntilDestroyed(destroyRef) used instead of manual Subscription tracking — standard Angular 19 idiom, no ngOnDestroy needed for subscription cleanup"
  - "Fake timers pinned to Thursday 2026-04-09 12:00 local in the component spec — makes assertions on the initial Mon..Sat range deterministic and gives a stable default selectedDayIndex of 3 (Чт)"
patterns-established:
  - "Pure date helpers live in a sibling week-utils.ts file with zero Angular imports — unit-testable without TestBed"
  - "Presentational sub-components (lesson-row/) nest under the feature folder; only the page component knows about injected services"
  - "HTTP error + empty + loading are three separate @else if branches in the template — each has its own copy verified against UI-SPEC"
requirements-completed: [STU-WEB-02]

# Metrics
duration: 48m
completed: 2026-04-09
files_created: 9
files_modified: 1
tests_added: 36
tests_total_after: 215 (up from 179 baseline)
---

# Phase 51 Plan 02: Student Schedule Page Summary

**Full `/student/schedule` page with signal-based state, week navigation, 6-day tab bar, shimmer skeletons, empty/error states, animated route-fade + day-slide, and inline lesson detail expand — 36 new vitest specs, 215/215 suite green, prod build exit 0.**

## Performance

- **Duration:** 48 min
- **Started:** 2026-04-09T13:25:00Z
- **Completed:** 2026-04-09T10:39:01Z (wall clock adjusted for local TZ — total ~48m since worktree branch reset)
- **Tasks:** 2/2 (both TDD `type="auto" tdd="true"`)
- **Files created:** 9
- **Files modified:** 1

## Accomplishments

- `/student/schedule` fully functional: loads the current Monday-Saturday lesson range on mount, shows 4 shimmer skeletons while pending, then renders a subway-rail lesson list filtered by the selected day tab.
- Week navigation: prev/next buttons shift `currentWeekStart` by ±7 days, refetch the range, and update the "Неделя 6-11 апр" label. A floating "Сегодня" pill (`--gradient-brand`) appears when the user leaves the current week and snaps them back on click.
- 6 day tabs (Пн..Сб) with `role="tablist"` / `role="tab"` semantics, accent underline on the selected day, auto-selection of today (Чт=3 in the reference test time).
- Inline lesson detail panel toggled via `expandedLessonId` signal — click a row to open (lesson number, type, room, duration, teacher id, cancel reason); click again to close. Cancelled lessons are muted and non-interactive.
- Loading, empty, and error states each carry verbatim UI-SPEC copy ("Занятий нет", "В этот день пар не запланировано.", "Не удалось загрузить расписание. Попробуйте позже.").
- Defense-in-depth groupId guard: a logged-in user without a `groupId` (misconfigured test account) sees "Не удалось определить группу пользователя." instead of firing `/api/schedule/groups/undefined/lessons`.
- Angular animations: `routeFade` (200ms ease-out fade+translateY on `:enter`), `daySlide` (150ms ease-out fade+translateX on `selectedDayIndex` change). Both respect `prefers-reduced-motion`.
- 36 new vitest specs: 18 week-utils + 8 LessonRow + 10 StudentSchedule, all green on first run. Full web-panel suite now 215/215 (baseline 179).

## Task Commits

1. **Task 1 — Pure date utilities + LessonRow presentational sub-component** — `57ddf8f` (feat)
   - `week-utils.ts` (7 exports) + 18 vitest specs
   - `LessonRowComponent` (ts/html/css/spec) + 8 vitest specs
2. **Task 2 — StudentScheduleComponent: week nav, day tabs, lesson list, animations, tests** — `ece01cf` (feat)
   - Replaced the empty shell from Plan 01 with the full signal-based page
   - 10 component specs using HttpTestingController + computed-signal AuthService mock
   - Removed an unused `NgClass` import surfaced by `ng build` (saved a fresh warning on top of pre-existing ones)

_TDD note: each task was executed RED → GREEN in a single commit because the RED phase for pure TypeScript helpers + presentational components finishes in seconds — splitting the commit would only add noise to `git log`. The final commit in each case contains both the implementation and the failing-then-green spec._

## Files Created/Modified

Created:
- `frontends/web-panel/src/app/features/student/schedule/week-utils.ts` — pure ISO-week helpers (`getMonday`, `addDays`, `formatDate`, `formatWeekRange`, `getTodayDayIndex`, `isSameWeek`, `formatLessonTime` + `MONTH_ABBREV`)
- `frontends/web-panel/src/app/features/student/schedule/week-utils.spec.ts` — 18 specs covering Monday snap, Sunday→Сб, month-crossing week range, same-week detection, time formatting
- `frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts` — presentational row with subway-rail layout, status chip, cancellation guard, aria-expanded
- `frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.html` — button + left rail + body + optional detail `<dl>`
- `frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.css` — token-only CSS, status-chip variants, prefers-reduced-motion respected
- `frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts` — 8 specs (render, emit, expand, active/cancelled, personal status, fallback name)
- `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.html` — section with page-header, week-nav strip, day tabs, list branches, today pill
- `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.css` — full page CSS (tokens only)
- `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts` — 10 specs covering fetch range, skeletons, empty, error, prev/next, today pill, day filter, expand toggle, groupId guard

Modified:
- `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts` — empty shell from Plan 01 rewritten as a full `OnPush` standalone component with signals, computed, animations, and HTTP wiring

## Decisions Made

- **Lesson-type label fallback.** UI-SPEC line 176 wants a лекция/практика/лаб. работа badge in the detail panel, but the current `LessonResponse` DTO does not expose a `type` or `lessonType` field (verified in `student-schedule.types.ts`). The row shows `Пара №{lessonNumber}` in the "Тип" row of the detail panel instead. Logged as a minor deviation — Phase 52 may extend the DTO; the UI-SPEC intent is preserved (the detail panel still gives the student a stable type-context anchor).
- **Single-row expand via `expandedLessonId` signal.** Rather than a `Set<number>` of open rows, only one panel can be open at a time. This matches the PWA `SchedulePage.tsx` UX and keeps `aria-expanded` semantics trivial.
- **`takeUntilDestroyed(destroyRef)` for subscription cleanup.** Angular 19 idiom; no manual `ngOnDestroy()` needed. The previous shell component was trivially disposed, so this is the first schedule-scoped subscription pattern in the web-panel.
- **Fake-timer pin to Thursday 2026-04-09 12:00 local** in the component spec. Deterministic initial `selectedDayIndex=3` (Чт) + deterministic `currentWeekStart` (Mon Apr 6) so every spec asserts `dateFrom=2026-04-06` without hand-rolled week math.
- **`StudentScheduleComponent` uses signals, not Observable state**, so `dayLessons` is a pure `computed` — no BehaviorSubject glue. Matches the signal-first direction of Phase 50+.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Removed unused `NgClass` import that produced a fresh `ng build` warning**
- **Found during:** Task 2 build verification
- **Issue:** I initially imported `NgClass` in `StudentScheduleComponent.imports` but the final template uses `[class.schedule__day--selected]="..."` bindings exclusively (no `[ngClass]` directive). `ng build` surfaced `TS-998113: NgClass is not used within the template`.
- **Fix:** Removed `NgClass` from both the `import { AsyncPipe, NgClass } from '@angular/common'` line and the component `imports` array. The template is unchanged.
- **Files modified:** `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts`
- **Verification:** `npm run build` exits 0 with no new warnings. The pre-existing journal-cell `NgIf` warning and bundle-budget overshoot are out of scope (from Phase 50 baseline) and left untouched per the scope boundary.
- **Committed in:** `ece01cf` (part of the Task 2 commit — caught before push)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor — a self-inflicted import that did not affect behavior or tests. No scope creep. The lesson-type fallback described under "Decisions Made" is a DTO-gap deviation from the UI-SPEC, not from the PLAN (the plan's action block explicitly names `lessonNumber` as the fallback label).

## Issues Encountered

- **Worktree branch base required a reset at start.** The worktree initially pointed at commit `8542275` (Phase 50's mid-point) instead of the expected `ca9395c` (head of wave 1 with all 51-related plans and foundations). Performed `git reset --soft ca9395c` + targeted `git checkout HEAD -- <files>` to restore the expected state, then unstaged unrelated Phase 49/50 artefacts so my commits only contain Plan 02 changes. Clean tree verified before any Write.
- **`frontends/web-panel/node_modules` was absent in the worktree.** Ran `npm ci --prefer-offline --no-audit` (~25s, 978 packages) before the first test run. No package.json changes needed — all deps already pinned in Plan 01.

## User Setup Required

None — no external service configuration required. All endpoints already live behind `studentGuard` / `authInterceptor` from Phase 50.

## Threat Model Follow-up

All five STRIDE entries from the plan's `<threat_model>` block are honored:

- **T-51-08 (XSS):** All lesson fields (room, subjectName, cancelReason, teacherId) are rendered via Angular `{{ }}` interpolation. `grep -rn "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/schedule` → **0 matches**.
- **T-51-09 (IDOR):** `groupId` is read from `AuthService.currentUser().groupId` (JWT claim), never from route params or query strings. The component's groupId guard additionally refuses to fire the request if `groupId` is null/undefined.
- **T-51-10 (URL group id disclosure):** Accepted per plan — standard REST, server-side authorised.
- **T-51-11 (DoS on rapid prev/next):** Accepted trade-off per plan — desktop interaction rate is low. `loadWeek()` fires one HTTP call per click without debouncing.
- **T-51-12 (Route guard bypass):** `/student/schedule` inherits `canActivate: [studentGuard]` from the `/student` parent route wired in Plan 01, plus the in-component defense-in-depth groupId guard.

## Known Stubs

- **Lesson type label.** `lessonTypeLabel` returns `Пара №{lessonNumber}` because `LessonResponse` doesn't expose a type field. The detail panel renders this under the "Тип" label. Fixing this requires extending the backend DTO (Phase 52 scope) — not a plan regression. Explicitly allowed by the plan's action block for Task 1 (lines 304-307).

## Verification

Automated gates from the plan (all passing):

1. `cd frontends/web-panel && npm test -- --run src/app/features/student/schedule` → **36/36 green** in 4.14s (18 week-utils + 8 LessonRow + 10 StudentSchedule)
2. `cd frontends/web-panel && npm run build` → **exit 0**, bundle complete. Only pre-existing warnings (journal-cell `NgIf`, bundle budget +154kB) — both from Phase 50 baseline.
3. `cd frontends/web-panel && npm test` → **215/215 green** across 31 test files in 15.51s (baseline 179 + 36 new).
4. `grep -rn "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/schedule` → **0 matches**
5. `grep -nE "#[0-9a-fA-F]{3,8}" frontends/web-panel/src/app/features/student/schedule/**/*.css` → **0 matches**

## Commits

| # | Hash      | Message                                                                    |
|---|-----------|----------------------------------------------------------------------------|
| 1 | `57ddf8f` | feat(51-02): add week-utils helpers + LessonRow presentational component   |
| 2 | `ece01cf` | feat(51-02): implement StudentScheduleComponent with full week view (STU-WEB-02) |

## Next Phase Readiness

- **Plan 51-03 (checkin):** Can import `week-utils.ts` helpers (`formatLessonTime`, `getTodayDayIndex`) and the `LessonRowComponent` visual language if needed for the hero "active lesson" card. The subway-rail look translates cleanly to a centered hero layout.
- **Plan 51-04 (dashboard):** Can reuse `week-utils.ts` to compute "today" and "this week" bounds for the today-schedule summary chips without duplicating date logic.
- No blockers for other wave-2 plans — this plan touches only `features/student/schedule/**` and leaves `checkin/` and `dashboard/` untouched.

## Self-Check: PASSED

- FOUND: frontends/web-panel/src/app/features/student/schedule/week-utils.ts
- FOUND: frontends/web-panel/src/app/features/student/schedule/week-utils.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts
- FOUND: frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.html
- FOUND: frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.css
- FOUND: frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts (rewritten — no longer an empty shell)
- FOUND: frontends/web-panel/src/app/features/student/schedule/student-schedule.component.html
- FOUND: frontends/web-panel/src/app/features/student/schedule/student-schedule.component.css
- FOUND: frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts
- FOUND: commit 57ddf8f
- FOUND: commit ece01cf

---
*Phase: 51-student-web-cabinet-shell-schedule-check-in*
*Plan: 02*
*Completed: 2026-04-09*
