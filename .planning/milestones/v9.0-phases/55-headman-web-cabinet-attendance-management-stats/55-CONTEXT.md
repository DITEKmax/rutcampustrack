# Phase 55: Headman Web Cabinet — Attendance Management + Stats - Context

**Gathered:** 2026-04-09 (assumptions mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

The headman has a working dashboard, group management, and subject CRUD (from Phase 54). This phase
adds the four remaining headman routes:

- `/headman/journal` — mass-mark attendance in a students × lessons matrix grid
- `/headman/excuses` — approve/reject pending excuse tickets (graceful degradation if backend absent)
- `/headman/late-checkin` — approve/reject late check-in requests (graceful degradation if backend absent)
- `/headman/stats` — group attendance statistics with per-subject red-zone threshold inline editing

**Out of scope:**
- PWA headman mode (Phase 56)
- Landing HEADMAN section (Phase 57)
- Backend excuse/late-checkin approval endpoints (not implemented — graceful degradation only)
</domain>

<decisions>
## Implementation Decisions

### Journal Grid — lessonId Gap Fix (Backend)

- **D-01:** Add `lessonId` field to `JournalCell` backend DTO
  (`services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalCell.java`).
  The `AttendanceRecord` port object already carries `lessonId` — it just isn't exposed in the
  response. Extend `JournalCell` constructor and `ReportService.getJournal()` to pass
  `r.lessonId()` through. The Angular `JournalCell` type in `teacher/journal/types.ts` must also
  gain an optional `lessonId?: number` field so the teacher grid remains unaffected (no breaking
  change — the teacher grid never uses `lessonId`, just renders the symbol).

- **D-02:** The `JournalCell` DTO lives in the **contract** module — no Lombok. Add field via
  standard constructor extension (add `Long lessonId` as first parameter to preserve reading order;
  or add as last parameter — confirm with the builder pattern in `ReportService`).

### Journal Grid — Write Capability (Angular)

- **D-03:** Create `HeadmanJournalGridComponent` (separate from teacher's `JournalGridComponent`)
  in `frontends/web-panel/src/app/features/headman/journal/`. Reuse the CdkTable +
  CdkVirtualScrollViewport structure from `journal-grid.component.ts` as a template, but replace
  the read-only `JournalCellComponent` with a clickable cell that cycles through statuses on click:
  `absent → present → excused → free_attendance → cancelled → absent`. Each click immediately
  calls `markAttendance(lessonId, userId, newStatus)` — no "Save all" button. Optimistic UI:
  update the cell signal locally, then revert on HTTP error with snackbar.

- **D-04:** The headman journal page (`HeadmanJournalPageComponent`) uses a subject dropdown only
  (no group dropdown — headman always sees their own group). Load flow:
  1. On init: call `getGroupMembers()` to get group members + `groupId`
  2. On subject select: call `getJournal(groupId, subjectId, dateFrom, dateTo)` — default date
     range: first day of current month to today (same as teacher journal default)
  3. Grid renders — each cell is clickable

- **D-05:** Add `getJournal(groupId, subjectId, dateFrom, dateTo)` and
  `markAttendance(lessonId, userId, status)` methods to existing `HeadmanApiService`:
  - `getJournal` → `GET /api/attendance/reports/journal` (same endpoint as teacher)
  - `markAttendance` → `PUT /api/attendance/lessons/{lessonId}/students/{userId}` with
    `{ status: AttendanceStatus }` body

### Excuse & Late Check-in — Graceful Degradation

- **D-06:** Both `/headman/excuses` and `/headman/late-checkin` pages render an informational empty
  state when the backend endpoint is absent (404 / connection error). The pattern is already
  established in `HeadmanDashboardComponent` for `getPendingExcuses()` and `getPendingLateCheckins()`
  — use `catchError(() => of(null))` pipe, then check for null in the template.

- **D-07:** Empty state message: "Функция находится в разработке. Заявки появятся здесь автоматически."
  Use `.page-empty` CSS class (established pattern). No approval/rejection action buttons are
  rendered — the components are shells that degrade gracefully without throwing console errors.

- **D-08:** Both components are thin wrappers: `HeadmanExcusesComponent` and
  `HeadmanLateCheckinComponent` in `frontends/web-panel/src/app/features/headman/excuses/`
  and `.../late-checkin/` respectively. No dialog components needed for this phase.

### Stats + Red-Zone Threshold

- **D-09:** `HeadmanStatsComponent` in `frontends/web-panel/src/app/features/headman/stats/`
  loads the journal for ALL subjects the headman's group has (load subjects list, then for each
  subject call `getJournal` over the current semester window, derive per-student attendance rates
  from the cell statuses). No separate stats endpoint exists — derive from journal data.
  Use `forkJoin` for parallel per-subject journal loads.

- **D-10:** Red-zone threshold display: for each subject row, show the effective threshold
  percentage (call `GET /api/academic/thresholds/resolve?groupId={id}&subjectId={id}` via
  `resolveThreshold(groupId, subjectId)` method added to `HeadmanApiService`). Show as
  inline editable number input (MatInput, type=number, min=0, max=100). On blur/enter:
  call `PUT /api/academic/thresholds/subject?subjectId={id}` with `{ minPercentage }` body
  via `setSubjectThreshold(subjectId, minPercentage)` — no full-page reload, just update the
  signal for that subject's threshold.

- **D-11:** Add `resolveThreshold(groupId, subjectId)` and `setSubjectThreshold(subjectId, minPercentage)`
  methods to `HeadmanApiService`:
  - `resolveThreshold` → `GET /api/academic/thresholds/resolve?groupId=X&subjectId=Y`
  - `setSubjectThreshold` → `PUT /api/academic/thresholds/subject?subjectId=Y`
    with body `{ minPercentage: number }`

### Sidebar + Route Registration

- **D-12:** Add four nav items to `allNavItems` array in
  `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` (after the existing
  `/headman/subjects` entry, before the closing bracket):
  ```
  { label: 'Журнал', icon: 'ph-table', route: '/headman/journal', roles: ['STUDENT'], isHeadman: true }
  { label: 'Пропуски', icon: 'ph-file-text', route: '/headman/excuses', roles: ['STUDENT'], isHeadman: true }
  { label: 'Запросы отметки', icon: 'ph-clock-countdown', route: '/headman/late-checkin', roles: ['STUDENT'], isHeadman: true }
  { label: 'Статистика', icon: 'ph-chart-bar', route: '/headman/stats', roles: ['STUDENT'], isHeadman: true }
  ```
  The `filteredHeadmanNavItems` computed signal already picks these up automatically — no structural
  change needed.

- **D-13:** Add four lazy-loaded child routes to the headman block in `app.routes.ts` before the
  `{ path: '', redirectTo: 'dashboard', pathMatch: 'full' }` fallback:
  ```
  { path: 'journal', loadComponent: () => import('.../headman-journal-page.component')..., data: { title: 'Журнал', eyebrow: 'Староста' } }
  { path: 'excuses', loadComponent: () => import('.../headman-excuses.component')..., data: { title: 'Пропуски', eyebrow: 'Староста' } }
  { path: 'late-checkin', loadComponent: () => import('.../headman-late-checkin.component')..., data: { title: 'Запросы отметки', eyebrow: 'Староста' } }
  { path: 'stats', loadComponent: () => import('.../headman-stats.component')..., data: { title: 'Статистика', eyebrow: 'Староста' } }
  ```
  All routes inherit `canActivate: [headmanGuard]` from the parent headman block.

### Testing

- **D-14:** Backend: extend existing `ReportService` tests (or integration tests) to assert that
  `JournalCell` now serializes `lessonId` in the JSON response. Existing teacher journal tests must
  still pass (the `lessonId` field addition is additive).

- **D-15:** Angular: add spec file for `HeadmanJournalPageComponent` and `HeadmanStatsComponent`
  with mocked `HeadmanApiService`. The `HeadmanExcusesComponent` and `HeadmanLateCheckinComponent`
  specs verify the graceful-degradation empty state renders on 404 (mock returns `throwError`).
  All existing web-panel vitest tests (129+) must pass unchanged.

### Claude's Discretion

- Exact cycling order for attendance status in the headman journal cell (proposed: `absent →
  present → excused → free_attendance → cancelled → absent`, but the product owner may prefer
  a different order — implement with a `NEXT_STATUS` map that's easy to change).
- Date range selector on the journal page — default is first-of-month to today (same as teacher);
  MatDatepicker range or two separate date inputs (use two separate inputs as in teacher journal).
- Whether to show a loading progress bar or skeleton rows in the headman journal grid during subject
  switch (use MatProgressBar at the top of the grid card — consistent with teacher journal).
- Exact icon names for the new sidebar items (use Phosphor Icons matching the eyebrow style).
- Semester window for stats load — default to current semester or last 30 days if semester endpoint
  is not readily available from `HeadmanApiService`.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Headman Web Cabinet (HEAD-WEB-05..08) — four requirements for this phase

### Backend — Attendance Marking
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/MarkingApi.java`
  — `PUT /attendance/lessons/{lessonId}/students/{userId}` endpoint contract
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/marking/MarkRequest.java`
  — request DTO: `{ status: AttendanceStatus }`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalCell.java`
  — **MUST BE EXTENDED** to add `lessonId` field (currently missing, critical for D-01)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`
  — `getJournal()` method (line 114) — extend to pass `r.lessonId()` to `JournalCell` constructor
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java`
  — headman authorization logic for marking

### Backend — Threshold
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java`
  — `GET /academic/thresholds/resolve` and `PUT /academic/thresholds/subject` endpoints
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/SetThresholdRequest.java`
  — request DTO: `{ minPercentage: Integer }`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/ResolvedThresholdResponse.java`
  — response DTO: `{ minPercentage, level, sourceId }`

### Angular — Teacher Journal (reuse pattern)
- `frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts`
  — CdkTable + CdkVirtualScrollViewport pattern — **USE AS TEMPLATE** for headman grid
- `frontends/web-panel/src/app/features/teacher/journal/journal-page.component.ts`
  — filter/load pattern for journal page
- `frontends/web-panel/src/app/features/teacher/journal/types.ts`
  — `JournalCell` Angular type — **MUST ADD `lessonId?: number`** (D-01)

### Angular — Headman existing code
- `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts`
  — existing service to extend with 4 new methods (D-05, D-11)
- `frontends/web-panel/src/app/app.routes.ts`
  — headman routes block to extend (add 4 routes before `redirectTo: 'dashboard'`)
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts`
  — `allNavItems` array to extend (add 4 headman nav items)
- `frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts`
  — graceful degradation pattern for `catchError(() => of(null))` (D-06 reference)
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JournalGridComponent` (teacher, `journal-grid.component.ts`) — CdkTable + signals grid with
  sticky header and virtual scroll — use structure as template, create new headman-specific version
- `JournalCellComponent` (teacher, `journal-cell.component.ts`) — read-only cell — headman needs
  a clickable version; do not modify the teacher component
- `JournalApiService` (teacher, `journal-api.service.ts`) — `getJournal()` call pattern via
  `GET /api/attendance/reports/journal` with `groupId, subjectId, dateFrom, dateTo` params
- `HeadmanApiService` — 13 methods already exist; add 4 more for Phase 55
- `catchError(() => of(null))` degradation pattern — established in `HeadmanDashboardComponent`
- `.page-header` / `.page-card` / `.page-empty` / `.page-error` CSS primitives — all components use

### Established Patterns
- CdkTable grid: columns derived via `computed()` signal, `displayedColumns` as string array,
  `dataSource` as computed signal, `buildCellMap()` for O(1) cell lookups
- Dialog pattern: `MatDialog.open(Component, { width: '520px', maxWidth: '95vw', data: {...} })`
- Snackbar on error: `MatSnackBar.open(message, undefined, { duration: 4000 })`
- Optimistic UI: update signal locally, revert in `catchError`, show snackbar with error message
- Route fade animation: `trigger('routeFade', [...])` on host element (all existing headman pages)

### Integration Points
- `JournalCell` backend DTO — single change point for `lessonId` exposure; affects
  `ReportService.getJournal()` only (teacher journal read path is the same endpoint — the added
  field is additive and non-breaking for the teacher grid which ignores `lessonId`)
- `MarkingController` — `@RequireRole(STUDENT)` + headman check in `MarkingService` — already
  passes for headmen (same AOP fix from Phase 54)
- `ThresholdController.setSubjectThreshold()` — `@RequireRole(STUDENT)` — already passes for
  headmen post Phase 54 fix
- Sidebar `filteredHeadmanNavItems` computed — automatically picks up new `isHeadman: true` items
- `app.routes.ts` headman children block — open-ended, insert 4 routes before `redirectTo`

### Key Gap Confirmed
- `JournalCell` backend DTO (`dto/report/JournalCell.java`) has NO `lessonId` field — only `date`,
  `lessonNumber`, `status`, `symbol`. The marking API requires `lessonId`. **D-01 must be
  implemented first** before the journal grid write capability works.
- `AttendanceRecord` port object DOES have `lessonId` — the data is available in `ReportService`,
  just not mapped to the response DTO. This is a one-line fix in `ReportService.getJournal()` once
  `JournalCell` constructor is extended.
</code_context>

<specifics>
## Specific Ideas

- ROADMAP Note: "Journal grid: CdkTable + CdkVirtualScrollViewport pattern already exists in
  teacher journal (Phase 39) — reuse component architecture, change data source to headman
  group × lessons"
- Optimistic UI on cell click: update the cell immediately in the UI, then send PUT. If the PUT
  fails, revert to previous status and show a snackbar error. Do not queue requests or debounce —
  each cell click is a discrete server action.
- Phase 55 success criterion 4: "the headman can edit the threshold per subject inline and save —
  the chart updates without full reload" — inline edit is required (not a dialog).
- Excuse/late-checkin: ROADMAP says "if not available, show confirmation and queue (graceful
  degradation as in Phase 53)" — Phase 53 established the empty-state/degradation pattern.
</specifics>

<deferred>
## Deferred Ideas

- Actual excuse ticket approval/rejection (requires backend endpoint implementation) — future phase
- Actual late check-in approval/rejection (requires backend endpoint implementation) — future phase
- Bulk mark-all-absent / mark-all-present action on journal grid — backlog
- PWA headman mode — Phase 56

None — analysis stayed within phase scope.
</deferred>

---

*Phase: 55-headman-web-cabinet-attendance-management-stats*
*Context gathered: 2026-04-09*
