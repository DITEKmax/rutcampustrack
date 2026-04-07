---
phase: 39-web-panel-teacher
verified: 2026-04-07T11:45:00Z
status: gaps_found
score: 8/9 must-haves verified
re_verification: false
gaps:
  - truth: "ng2-charts v6.0.1 and chart.js v4.4.7 are installed (not v10)"
    status: failed
    reason: "ng2-charts and chart.js are declared in package.json and package-lock.json but NOT installed in node_modules. `npm install` was not run after the packages were added. This causes the test runner (vitest/vite) to fail with 'Failed to resolve import ng2-charts'."
    artifacts:
      - path: "frontends/web-panel/node_modules/ng2-charts"
        issue: "Directory does not exist — package not installed"
      - path: "frontends/web-panel/node_modules/chart.js"
        issue: "Directory does not exist — package not installed"
    missing:
      - "Run `npm install` in frontends/web-panel to materialize ng2-charts@6.0.1 and chart.js@4.5.1 from package-lock.json"
      - "After install: `npm test` must exit 0 (currently 2 test files fail: stats-page.component.spec.ts and subject-chart.component.spec.ts)"
---

# Phase 39: Web Panel Teacher — Verification Report

**Phase Goal:** Attendance journal grid (CdkTable with virtual scroll for 500+ rows), attendance stats charts (ng2-charts/Chart.js)
**Verified:** 2026-04-07T11:45:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Teacher sees attendance journal as a students-x-lessons grid with status cells | VERIFIED | `journal-grid.component.ts` uses `CdkTable` with `*cdkCellDef`; `JournalCellComponent` renders `status-chip--{status}` spans with 2-char Russian symbols |
| 2 | Grid handles 500+ rows via CdkVirtualScrollViewport with 40px fixed row height | VERIFIED | `journal-grid.component.html` wraps the CdkTable in `<cdk-virtual-scroll-viewport [itemSize]="40">` with `[minBufferPx]="400"` |
| 3 | Sticky student name column stays visible on horizontal scroll | VERIFIED | `journal-grid.component.ts` defines `sticky: true` on the student column; CSS applies `position: sticky; left: 0` |
| 4 | Status cells render correct 2-char symbols with semantic background colors | VERIFIED | `journal-cell.component.ts` outputs `status-chip--{status}` class; `styles.css` defines `status-chip--present`, `--absent`, `--excused`, `--free_attendance` with correct rgba colors |
| 5 | Filter bar (group, subject, date range) loads teacher's groups and filters journal | VERIFIED | `journal-page.component.ts` calls `getMyAssignments()` on init, filters groups, then filters subjects by groupId on group selection; `loadJournal()` calls `getJournal()` |
| 6 | Journal route /teacher/journal loads JournalPageComponent (not EmptyComponent) | VERIFIED | `app.routes.ts` line 31-35: lazy imports `journal-page.component` for path `journal` under `teacher`; no EmptyComponent for teacher routes |
| 7 | Teacher sees attendance stats chart per subject/group as stacked bar chart | VERIFIED | `subject-chart.component.ts` uses `BaseChartDirective` with 4 stacked datasets (green/amber/purple/red); `stats-page.component.html` renders `<app-subject-chart>` inside CSS grid |
| 8 | Chart data is derived client-side from journal API response | VERIFIED | `stats-page.component.ts` calls `JournalApiService.getJournal()` via `forkJoin`, then calls `deriveStudentChartData(response)` from `stats-utils.ts` |
| 9 | ng2-charts v6.0.1 and chart.js installed in node_modules | FAILED | Both packages declared in `package.json` (`"ng2-charts": "^6.0.1"`, `"chart.js": "^4.5.1"`) and `package-lock.json` but **not present in `node_modules/`**. `npm ls ng2-charts` returns `(empty)`. Tests fail with "Failed to resolve import ng2-charts". |

**Score:** 8/9 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/web-panel/src/app/features/teacher/journal/types.ts` | TypeScript interfaces for all journal types | VERIFIED | Exports `JournalResponse`, `JournalStudentRow`, `JournalCell`, `GroupResponse`, `SubjectResponse`, `AssignmentResponse`, `PagedResponse`, `JournalColumn` |
| `frontends/web-panel/src/app/features/teacher/journal/journal-api.service.ts` | HTTP service for journal, groups, subjects, assignments | VERIFIED | `@Injectable({ providedIn: 'root' })`, implements `getJournal()`, `getMyAssignments()`, `getGroups()`, `getSubjects()` with proper HTTP params |
| `frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts` | CdkTable + CdkVirtualScrollViewport grid | VERIFIED | Imports `CdkTableModule` and `ScrollingModule`; uses `computed()` signals for columns/dataSource; O(1) cell lookup via Map |
| `frontends/web-panel/src/app/features/teacher/journal/journal-page.component.ts` | Filter bar + grid host page | VERIFIED | Substantive: group/subject/date filter, `loadJournal()` wired to `journalApi.getJournal()`, signals for all state |
| `frontends/web-panel/src/app/features/teacher/stats/stats-utils.ts` | `deriveStudentChartData()` and `deriveOverallStats()` pure functions | VERIFIED | Both functions exported; `cancelled` exclusion; 16-char name truncation; `StudentChartData` and `OverallStats` interfaces exported |
| `frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts` | Stacked bar chart using BaseChartDirective | VERIFIED (code) / STUB (runtime) | Code is correct; imports `BaseChartDirective` from `ng2-charts`; 4 datasets with correct colors; **but fails at test-time because `ng2-charts` not in node_modules** |
| `frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts` | Stats page with filter bar and chart grid | VERIFIED (code) / FAILING (tests) | Substantive implementation; wired to `JournalApiService` and `deriveStudentChartData`; **test fails due to ng2-charts missing** |
| `frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts` | Summary card with total lessons + attendance % | VERIFIED | Inputs `totalLessons` and `attendanceRate`; renders "Всего занятий" and "Посещаемость"; 4 tests pass |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `journal-page.component.ts` | `journal-api.service.ts` | `inject(JournalApiService)` | WIRED | `private readonly journalApi = inject(JournalApiService)` on line 36; calls `getMyAssignments()`, `getGroups()`, `getSubjects()`, `getJournal()` |
| `journal-grid.component.ts` | `@angular/cdk/table` | `CdkTableModule` import | WIRED | `imports: [CdkTableModule, ScrollingModule, JournalCellComponent]` |
| `journal-grid.component.ts` | `@angular/cdk/scrolling` | `ScrollingModule` import | WIRED | `ScrollingModule` imported; template uses `<cdk-virtual-scroll-viewport [itemSize]="40">` |
| `app.routes.ts` | `journal-page.component.ts` | `loadComponent` lazy import | WIRED | `import('./features/teacher/journal/journal-page.component').then(m => m.JournalPageComponent)` |
| `stats-page.component.ts` | `journal-api.service.ts` | `inject(JournalApiService)` | WIRED | Reuses `JournalApiService`; calls `getMyAssignments()`, `getGroups()`, `getSubjects()`, `getJournal()` |
| `stats-page.component.ts` | `stats-utils.ts` | `deriveStudentChartData()` call | WIRED | Imported and called inside `forkJoin` subscription for each journal response |
| `subject-chart.component.ts` | `ng2-charts` | `BaseChartDirective` import | CODE WIRED / RUNTIME BROKEN | Import exists in source; `BaseChartDirective` in `imports` array; but module missing from `node_modules` |
| `app.config.ts` | `chart.js` | `Chart.register()` | CODE WIRED / RUNTIME BROKEN | `Chart.register(BarController, CategoryScale, LinearScale, BarElement, Legend, Tooltip)` on line 9; module missing from `node_modules` |
| `app.routes.ts` | `stats-page.component.ts` | `loadComponent` lazy import | WIRED | `import('./features/teacher/stats/stats-page.component').then(m => m.StatsPageComponent)` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `journal-page.component.html` | `journalData()` | `JournalApiService.getJournal(groupId, subjectId, dateFrom, dateTo)` — HTTP GET `/api/attendance/reports/journal` | Yes — parameters from teacher filter selection, real HTTP endpoint | FLOWING |
| `journal-grid.component.html` | `dataSource()` computed signal | Receives `JournalResponse` via `@Input({ required: true }) journalData` from journal-page | Yes — passed through from real API response | FLOWING |
| `stats-page.component.html` | `chartEntries()` computed | `forkJoin(requests)` where each request is `JournalApiService.getJournal()`, then `deriveStudentChartData()` transforms | Yes — real HTTP calls per subject | FLOWING |
| `stats-page.component.html` | `overallStats()` | Aggregated from all valid `forkJoin` responses via inline stats calculation | Yes — derives from real data | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All tests pass | `cd frontends/web-panel && npm test` | **2 test files FAIL** (stats-page.component.spec.ts, subject-chart.component.spec.ts): "Failed to resolve import ng2-charts" | FAIL |
| ng2-charts installed | `npm ls ng2-charts` in web-panel | `(empty)` — package not in node_modules | FAIL |
| chart.js installed | `ls node_modules/chart.js` | `NOT FOUND` | FAIL |
| Journal route wired | grep in app.routes.ts | `journal-page.component` found, no EmptyComponent for teacher routes | PASS |
| Stats route wired | grep in app.routes.ts | `stats-page.component` found for teacher/stats path | PASS |
| Chart.register present | grep in app.config.ts | `Chart.register(BarController` on line 9 | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| WPAN-06 | 39-01-PLAN.md | Attendance journal grid (students x lessons matrix) | SATISFIED | `JournalGridComponent` with `CdkTable`, sticky student column, dynamic date/lesson columns, `JournalCellComponent` rendering 2-char symbols |
| WPAN-07 | 39-01-PLAN.md | Virtual scroll for 500+ students in journal grid (CdkTable) | SATISFIED | `CdkVirtualScrollViewport [itemSize]="40"` wrapping `CdkTable`; data source is computed signal from `JournalResponse.students` array |
| WPAN-08 | 39-02-PLAN.md | Attendance stats charts per subject/group (ng2-charts) | PARTIAL | Code implemented correctly but **`ng2-charts` not installed in `node_modules`** — tests fail at resolution; chart cannot render in browser until `npm install` is run |

No orphaned requirements: REQUIREMENTS.md maps WPAN-06, WPAN-07, WPAN-08 to Phase 39 — all three are claimed by the phase plans.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `frontends/web-panel/node_modules/ng2-charts` | — | Package declared in package.json/package-lock.json but not installed | Blocker | `subject-chart.component.spec.ts` and `stats-page.component.spec.ts` fail; `SubjectChartComponent` and `StatsPageComponent` cannot be loaded at runtime |

No stub implementations found in source code. All components have real logic. The only gap is the missing `npm install`.

---

### Human Verification Required

#### 1. Journal Grid Visual Rendering

**Test:** Log in as TEACHER, navigate to `/teacher/journal`, select a group and subject, set date range, click "Показать журнал"
**Expected:** Grid renders with sticky student name column on left; columns are date+lesson pairs; each cell shows the appropriate 2-char symbol (б, н, у, сп, —) on a color-coded chip; scrolling horizontally keeps the student column in place; vertical virtual scroll handles 30+ students smoothly
**Why human:** CdkVirtualScrollViewport layout and sticky positioning require a real browser; jsdom tests don't verify visual layout correctness

#### 2. Stats Charts Visual Rendering

**Test:** After running `npm install`, log in as TEACHER, navigate to `/teacher/stats`, select a group
**Expected:** Loading bar appears, then per-subject stacked bar charts render with 4 color-coded datasets (green/amber/purple/red); OverallStatCard shows total lessons count and attendance percentage; chart legend appears at top
**Why human:** ng2-charts/Chart.js renders to `<canvas>` — pixel rendering cannot be verified programmatically

#### 3. Filter Cascading Behaviour

**Test:** On journal page, select Group A, note the subjects dropdown now shows only Group A's subjects; switch to Group B, verify subjects dropdown updates to Group B's subjects only
**Expected:** Subjects dropdown always shows only subjects assigned to the teacher for the currently selected group
**Why human:** Requires real API data (teacher assignments, groups, subjects) to verify correct filtering

---

### Gaps Summary

**1 blocker gap** prevents full goal achievement:

**ng2-charts / chart.js not installed.** Both packages are declared in `package.json` (`"ng2-charts": "^6.0.1"`, `"chart.js": "^4.5.1"`) and the resolved entries exist in `package-lock.json`, but neither package directory exists in `frontends/web-panel/node_modules/`. This causes `vitest` to fail with "Failed to resolve import ng2-charts" for 2 test files (15 tests in `stats-page.component.spec.ts` and `subject-chart.component.spec.ts`). The charts implementation in source code is complete and correct — this is purely an `npm install` step that was skipped before committing.

**Fix:** Run `npm install` in `frontends/web-panel/`. After install, all 91 tests should pass.

---

_Verified: 2026-04-07T11:45:00Z_
_Verifier: Claude (gsd-verifier)_
