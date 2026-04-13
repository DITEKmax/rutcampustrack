---
phase: 52-student-web-cabinet-homework-stats-notifications-profile
plan: "02"
subsystem: web-panel/student-homework
tags: [angular, signals, OnPush, MatCheckbox, optimistic-update, HATEOAS]
dependency_graph:
  requires:
    - phase: 52-01
      provides: StudentApiService.getHomeworks/markHomeworkComplete/unmarkHomeworkComplete/getActiveSemesterId, HomeworkItem type, lazy route for /student/homework
  provides:
    - HomeworkItemComponent (standalone OnPush card with MatCheckbox, optimistic toggle)
    - StudentHomeworkComponent (full homework list page with skeleton/empty/error states)
  affects:
    - frontends/web-panel /student/homework route (placeholder replaced with full implementation)
tech_stack:
  added: []
  patterns:
    - Angular signal-based optimistic update with per-item pending/error state
    - switchMap chain (getActiveSemesterId → getHomeworks) for dependent API calls
    - ChangeDetectionStrategy.OnPush on all new components
key_files:
  created:
    - frontends/web-panel/src/app/features/student/homework/homework-item/homework-item.component.ts
    - frontends/web-panel/src/app/features/student/homework/student-homework.component.html
    - frontends/web-panel/src/app/features/student/homework/student-homework.component.css
  modified:
    - frontends/web-panel/src/app/features/student/homework/student-homework.component.ts
key_decisions:
  - "HomeworkItemComponent uses inline template/styles (no external files) — self-contained, easier to review alongside the component logic"
  - "Optimistic toggle stores original completed state in closure before the API call; on error reverts using the captured value rather than a !item.completed flip"
  - "Subject displayed as subjectId numeric value — name resolution via SubjectCacheService is out of scope for this plan per plan spec"
patterns-established:
  - "Optimistic mutation pattern: capture current, set pending, flip local, call API, on error revert + set itemError"
  - "Homework section split: incompleteItems computed signal (completed=false, createdAt desc) rendered above completeItems (completed=true, createdAt desc)"
requirements-completed:
  - STU-WEB-04
duration: ~20min
completed: 2026-04-09
---

# Phase 52 Plan 02: Homework List Page Summary

**Angular homework list page with OnPush card component, signal-based optimistic checkbox toggle, loading skeleton, and empty/error states wired to academic-service**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-09T14:45:00Z
- **Completed:** 2026-04-09T15:05:00Z
- **Tasks:** 2
- **Files modified:** 4 (1 replaced placeholder, 3 created new)

## Accomplishments
- `HomeworkItemComponent` — standalone `ChangeDetectionStrategy.OnPush` card with `MatCheckbox`, disabled-while-pending state, `is-completed` CSS class (opacity 0.55 + line-through), external link with `rel="noopener"`, subject chip, accessibility attributes (`role="listitem"`, `aria-busy`, `aria-label`)
- `StudentHomeworkComponent` — full homework page replacing Plan 01 placeholder: loads via `getActiveSemesterId()` → `switchMap` → `getHomeworks(groupId, semesterId)`, shows 4-row skeleton while loading, separate incomplete/complete sections, optimistic toggle with revert on error and inline per-item error toast
- Build verified against main repo (both worktree and main repo share the same Angular sources that the Angular CLI uses)

## Task Commits

Each task was committed atomically:

1. **Task 1: HomeworkItemComponent — card with optimistic toggle** - `c735673` (feat)
2. **Task 2: StudentHomeworkComponent — page container with loading/empty/error states** - `6c8d8c8` (feat)

## Files Created/Modified
- `frontends/web-panel/src/app/features/student/homework/homework-item/homework-item.component.ts` — New standalone OnPush component with inline template + styles
- `frontends/web-panel/src/app/features/student/homework/student-homework.component.ts` — Full page component replacing Plan 01 placeholder
- `frontends/web-panel/src/app/features/student/homework/student-homework.component.html` — Page template with skeleton, error, empty, and list states
- `frontends/web-panel/src/app/features/student/homework/student-homework.component.css` — Skeleton shimmer animation, section labels, item error styles

## Decisions Made
- `HomeworkItemComponent` uses inline template and styles (no separate `.html`/`.css` files) — the component is self-contained and card-sized; keeping template/styles co-located with the class improves reviewability.
- Subject displayed as numeric `subjectId` — name resolution via `SubjectCacheService` is explicitly called out as out of scope in the plan spec.
- Captured `current.completed` in a closure before the optimistic flip so the revert always restores the exact pre-call state rather than a double-negation.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
- Worktree did not have `node_modules/` — resolved by creating a symlink to the main repo's `node_modules` directory (`ln -s .../rutcampustrack/frontends/web-panel/node_modules`). This is standard for git worktrees sharing a monorepo.

## Known Stubs

None — `HomeworkItemComponent` shows `item.subjectId` (a number) rather than a resolved subject name, but this is explicitly documented in the plan as out of scope, not a stub preventing the page from functioning.

## Threat Surface Scan

No new network endpoints or auth paths introduced. The `rel="noopener"` mitigation for T-52-HW-02 (external URLs) is present in `HomeworkItemComponent`. T-52-HW-01 (toggle only fires on checkbox interaction, itemId comes from signal not route params) is satisfied. T-52-HW-03 ([disabled]="pending" on MatCheckbox) is satisfied.

## Self-Check

Files exist on disk:
- `homework-item/homework-item.component.ts` — FOUND
- `student-homework.component.ts` — FOUND
- `student-homework.component.html` — FOUND
- `student-homework.component.css` — FOUND

Commits exist in git log:
- `c735673` — FOUND
- `6c8d8c8` — FOUND

## Self-Check: PASSED

## Next Phase Readiness
- Plan 02 complete: `/student/homework` route is fully functional with optimistic completion toggle
- Plan 03 can proceed: `/student/notifications` page (StudentNotificationsComponent placeholder exists)
- Plan 04 can proceed: `/student/stats` and `/student/profile` pages (placeholders exist)

---
*Phase: 52-student-web-cabinet-homework-stats-notifications-profile*
*Completed: 2026-04-09*
