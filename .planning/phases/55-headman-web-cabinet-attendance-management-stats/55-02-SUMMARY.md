---
phase: 55
plan: "02"
subsystem: web-panel (Angular)
tags: [angular, routing, sidebar, headman, api-service, journal]
dependency_graph:
  requires: [54-02, 54-05]
  provides: [55-03, 55-04, 55-05]
  affects: [app.routes.ts, sidebar.component.ts, headman-api.service.ts, types.ts]
tech_stack:
  added: []
  patterns: [lazy-loaded-routes, headman-guard, angular-signals, computed-nav-filter]
key_files:
  created: []
  modified:
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/features/teacher/journal/types.ts
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
decisions:
  - "Icon format: ph-table (not ph ph-table) — sidebar HTML adds class=ph separately via [class]=item.icon binding"
  - "JournalCell.lessonId added as first optional field — purely additive, teacher grid ignores it"
  - "4 new methods appended in Attendance & Stats section of HeadmanApiService"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-10"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 4
---

# Phase 55 Plan 02: Angular Infrastructure for Headman Attendance + Stats

**One-liner:** 4 lazy-loaded headman routes with headmanGuard + 4 sidebar nav items + JournalCell.lessonId + 4 HeadmanApiService methods wiring attendance and threshold endpoints.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Register 4 headman routes + 4 sidebar nav items | 4989090 | app.routes.ts, sidebar.component.ts |
| 2 | Extend JournalCell + add 4 HeadmanApiService methods | d1356b1 | types.ts, headman-api.service.ts |

## What Was Built

### Task 1 — Routes + Sidebar

**app.routes.ts:** Added 4 lazy-loaded child routes under the existing `/headman` block, each with `canActivate: [headmanGuard]` (T-55-03 threat mitigation):
- `/headman/journal` → `HeadmanJournalPageComponent`
- `/headman/excuses` → `HeadmanExcusesComponent`
- `/headman/late-checkin` → `HeadmanLateCheckinComponent`
- `/headman/stats` → `HeadmanStatsComponent`

**sidebar.component.ts:** Added 4 entries to `allNavItems` with `isHeadman: true` so `filteredHeadmanNavItems` computed signal picks them up automatically:
- Журнал (`ph-table` icon, `/headman/journal`)
- Пропуски (`ph-file-text` icon, `/headman/excuses`)
- Запросы отметки (`ph-clock-countdown` icon, `/headman/late-checkin`)
- Статистика (`ph-chart-bar` icon, `/headman/stats`)

### Task 2 — Types + Service Methods

**types.ts:** Added `lessonId?: number` as first field of `JournalCell` interface — purely additive, teacher grid code that destructures `JournalCell` continues to work unchanged.

**headman-api.service.ts:** Added 4 methods in new `// Attendance & Stats` section:
- `getJournal(groupId, subjectId, dateFrom, dateTo)` → `GET /api/attendance/reports/journal`
- `markAttendance(lessonId, userId, status)` → `PUT /api/attendance/lessons/{lessonId}/students/{userId}`
- `resolveThreshold(groupId, subjectId)` → `GET /api/academic/thresholds/resolve`
- `setSubjectThreshold(subjectId, minPercentage)` → `PUT /api/academic/thresholds/subject`

## Decisions Made

- **Icon format:** Phosphor icons in sidebar use `ph-tableName` format (no `ph ` prefix) because the HTML template binds `[class]="item.icon"` and adds `class="ph sidebar__icon"` separately. Plan spec showed `ph ph-table` but the correct format matching all existing items is `ph-table`.
- **JournalCell field order:** `lessonId` added as first field per plan spec (D-01 reference) to group the Phase 55 addition visually before existing fields.

## Threat Mitigations Applied

| Threat ID | Mitigation |
|-----------|-----------|
| T-55-03 | All 4 new headman routes include `canActivate: [headmanGuard]` — plain STUDENT role cannot access /headman/journal, /headman/excuses, /headman/late-checkin, /headman/stats |
| T-55-04 | markAttendance sends status as-is; backend MarkingService.ALLOWED_STATUSES enforces valid values server-side |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Phosphor icon format**
- **Found during:** Task 1, after reading sidebar HTML template
- **Issue:** Plan spec used `ph ph-table` format for icon values, but existing sidebar items use `ph-table` format (HTML adds `ph` class separately via `class="ph sidebar__icon"`)
- **Fix:** Used correct `ph-table`, `ph-file-text`, `ph-clock-countdown`, `ph-chart-bar` without `ph ` prefix
- **Files modified:** `sidebar.component.ts`
- **Commit:** 4989090

## Known Stubs

None — this plan only adds infrastructure (routes, nav items, service methods, type extension). No UI components are created here; component stubs are created by Plans 03-05.

## Self-Check: PASSED

- `frontends/web-panel/src/app/app.routes.ts` — modified, committed 4989090
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — modified, committed 4989090
- `frontends/web-panel/src/app/features/teacher/journal/types.ts` — modified, committed d1356b1
- `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts` — modified, committed d1356b1
- Commits 4989090, d1356b1 exist in git log
