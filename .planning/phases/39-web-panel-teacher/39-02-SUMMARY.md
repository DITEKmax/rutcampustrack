---
phase: 39-web-panel-teacher
plan: 02
subsystem: frontend/web-panel
tags: [angular, ng2-charts, chart-js, teacher-stats, stacked-bar-chart]
dependency_graph:
  requires:
    - Phase 38 auth layer (AuthInterceptor, roleGuard)
    - Plan 39-01: JournalApiService, types.ts (GroupResponse, SubjectResponse, AssignmentResponse, JournalResponse)
    - Backend /api/attendance/reports/journal (TEACHER role, reused for stats derivation)
    - Backend /api/academic/assignments/my, /groups, /subjects
  provides:
    - StatsPageComponent (/teacher/stats route)
    - SubjectChartComponent (stacked bar chart per subject via ng2-charts)
    - OverallStatCardComponent (total lessons + attendance %)
    - stats-utils.ts (deriveStudentChartData, deriveOverallStats pure functions)
  affects:
    - frontends/web-panel/src/app/app.routes.ts (/teacher/stats route wired to StatsPageComponent)
    - frontends/web-panel/src/app/app.config.ts (Chart.register() added)
tech_stack:
  added:
    - "ng2-charts@6.0.1 — BaseChartDirective standalone directive for Angular 19"
    - "chart.js@4.5.1 — stacked bar chart rendering"
  patterns:
    - "Client-side stats derivation from journal API (not /student/stats which returns 403 for TEACHER)"
    - "forkJoin to load all subjects in parallel after group selection"
    - "Pure function stats-utils for testable data transformation (no side effects)"
    - "Signal-based state: groups, subjects, selectedGroupId, loading, error, chartDataMap, overallStats"
    - "Computed signal chartEntries converts Map to iterable array for @for loop"
key_files:
  created:
    - frontends/web-panel/src/app/features/teacher/stats/stats-utils.ts
    - frontends/web-panel/src/app/features/teacher/stats/stats-utils.spec.ts
    - frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts
    - frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts
    - frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts
    - frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts
    - frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts
    - frontends/web-panel/src/app/features/teacher/stats/stats-page.component.html
    - frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts
  modified:
    - frontends/web-panel/src/app/app.config.ts
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/package.json
decisions:
  - "Used client-side stats derivation from journal API — /student/stats returns 403 for TEACHER role; journal endpoint is TEACHER-accessible"
  - "ngOnChanges + barChartData property (not computed signal) for SubjectChartComponent — avoids complexity of wrapping @Input in signal; direct update on input change is simpler and compatible with ng2-charts"
  - "forkJoin for parallel subject fetches — teacher typically has 3-8 subjects per group; no amplification risk; individual errors caught via catchError(of(null))"
  - "semesterStart approximated as Jan 1 of current year — covers full academic year; acceptable for MVP"
metrics:
  duration_seconds: 312
  completed_date: "2026-04-07"
  tasks_completed: 3
  tests_before: 66
  tests_after: 91
  files_created: 9
  files_modified: 3
---

# Phase 39 Plan 02: Teacher Stats Charts — Summary

**One-liner:** Client-side attendance stats derivation from journal API with ng2-charts stacked bar charts per subject and overall stat card at `/teacher/stats`.

## What Was Built

Teacher-facing statistics page for the Angular web panel. Teachers navigate to `/teacher/stats`, select a group from the dropdown (filtered to their assigned groups), and the page fetches journal data for all teacher's subjects in that group using `forkJoin`. Client-side pure functions derive per-student attendance counts (present/excused/free_attendance/absent) and overall stats (total lessons, attendance rate %).

The stats page renders:
- A group filter dropdown (loaded on init from assignments + groups APIs)
- Loading indicator (`mat-progress-bar` indeterminate) during data fetch
- Error state with descriptive message if any request fails
- Empty state ("Нет данных") if group selected but no data returned
- `OverallStatCardComponent` above charts showing total lessons + attendance percentage
- CSS Grid of `SubjectChartComponent` cards: `repeat(auto-fill, minmax(480px, 1fr))`, up to 2 per row on widescreen

Each `SubjectChartComponent` renders an `ng2-charts` stacked bar chart with 4 datasets per student: Присутствовал (green #16A34A), Уваж. причина (amber #D97706), Своб. посещение (purple #9333EA), Отсутствовал (red #DC2626), all at 0.85 opacity matching the UI-SPEC color palette.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Install ng2-charts, Chart.js registration, stats-utils TDD | 5c4e7d9 | stats-utils.ts, stats-utils.spec.ts, app.config.ts, package.json |
| 2 | SubjectChart and OverallStatCard components | e6e3817 | subject-chart/\*, overall-stat-card/\* |
| 3 | StatsPage with filter, chart grid, route wiring | bc02f80 | stats-page.component.ts+html+spec, app.routes.ts |

## Tests

- Before: 66 tests (Plan 39-01 baseline)
- After: 91 tests (+25 new)
- All 15 test files pass

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

The plan specified using either `input()` signal or `@Input` with `ngOnChanges` for `SubjectChartComponent`. I chose `@Input` with `ngOnChanges` + a plain property `barChartData` (not a computed signal). This is the simpler approach and avoids the complexity of wrapping `@Input` in signals while achieving the same reactive update on input change.

## Known Stubs

None — all data flows are wired. Groups are loaded from real assignments API, subjects filtered to teacher's actual assignments, journal data fetched per subject via `JournalApiService`, and chart data derived client-side from the API response. The `/teacher/stats` route loads `StatsPageComponent` (not `EmptyComponent`).

## Threat Surface Scan

No new network endpoints introduced. All API calls reuse the existing `AuthInterceptor` (Bearer token injection from Phase 38) and the same endpoints used by Plan 39-01. The `/teacher/stats` route retains the Phase 38 `roleGuard(['TEACHER'])`. Student names are truncated at 16 chars in chart labels (T-39-06 mitigation). No new trust boundaries.

## Self-Check: PASSED

All 9 key files created on disk. All 3 task commits (5c4e7d9, e6e3817, bc02f80) confirmed in git log. 91 tests pass. ng2-charts@6.0.1 and chart.js@4.5.1 installed. Chart.register(BarController...) present in app.config.ts.
