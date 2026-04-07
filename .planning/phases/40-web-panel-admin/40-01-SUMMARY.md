---
phase: 40-web-panel-admin
plan: 01
subsystem: ui
tags: [angular, angular-material, admin, dashboard, crud, hateoas, signals]

requires:
  - phase: 38-web-panel-scaffold-auth
    provides: Shell layout, sidebar nav, authGuard, roleGuard, authInterceptor
  - phase: 39-web-panel-teacher
    provides: HATEOAS extraction pattern, signal-based state pattern, TestBed spec pattern

provides:
  - AdminApiService with 16 CRUD methods for users/groups/semesters/dashboard
  - Admin DTO types (UserResponse, GroupResponse, SemesterResponse, DashboardStatsResponse, etc.)
  - RoleChipComponent and StatusChipComponent for table cell display
  - StatCardComponent reusable stat card with signal inputs
  - AdminDashboardComponent with 4 stat cards from /api/academic/dashboard/stats
  - provideNativeDateAdapter in app.config.ts for mat-datepicker
  - Route wiring for /admin/users, /admin/groups, /admin/semesters to real component paths

affects: [40-02-PLAN, 40-03-PLAN]

tech-stack:
  added: [provideNativeDateAdapter]
  patterns: [AdminApiService CRUD pattern, StatCard signal inputs, deriveSemesterStatus frontend computation]

key-files:
  created:
    - frontends/web-panel/src/app/features/admin/shared/types.ts
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.spec.ts
    - frontends/web-panel/src/app/features/admin/shared/role-chip/role-chip.component.ts
    - frontends/web-panel/src/app/features/admin/shared/status-chip/status-chip.component.ts
    - frontends/web-panel/src/app/features/admin/dashboard/stat-card/stat-card.component.ts
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts
    - frontends/web-panel/src/app/app.config.ts
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/styles.css

key-decisions:
  - "4th stat card shows activeSemesterName instead of avg attendance rate (DashboardStatsResponse has no attendance data; cross-service call out of scope)"
  - "Removed /admin/stats stub route (not in admin spec, was leftover from Phase 38 scaffold)"

patterns-established:
  - "AdminApiService: single Injectable service wrapping all admin CRUD endpoints"
  - "deriveSemesterStatus(): frontend-computed semester status from active flag and date range"
  - "StatCardComponent: reusable card with input.required signal inputs for dashboard metrics"

requirements-completed: [WPAN-12, WPAN-09, WPAN-10, WPAN-11]

duration: 5min
completed: 2026-04-07
---

# Phase 40 Plan 01: Admin Foundation Summary

**AdminApiService with 16 CRUD methods, dashboard with 4 stat cards, RoleChip/StatusChip components, and route wiring for all admin pages**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-07T09:22:25Z
- **Completed:** 2026-04-07T09:27:00Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- AdminApiService covering all user/group/semester/dashboard CRUD endpoints with HATEOAS extraction
- Admin dashboard with 4 stat cards showing real data from /api/academic/dashboard/stats
- RoleChipComponent and StatusChipComponent for colored role/status display in tables
- provideNativeDateAdapter added for mat-datepicker support in future semester dialogs
- All admin routes updated from EmptyComponent stubs to real lazy-loaded component paths

## Task Commits

Each task was committed atomically:

1. **Task 1: Admin types, AdminApiService + spec, RoleChip, StatusChip, chip CSS** - `0201ced` (feat)
2. **Task 2: Admin Dashboard page with stat cards, app.config update, route wiring** - `43c1d9b` (feat)

## Files Created/Modified
- `frontends/web-panel/src/app/features/admin/shared/types.ts` - All admin DTO types and deriveSemesterStatus function
- `frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts` - Injectable HTTP service with 16 CRUD methods
- `frontends/web-panel/src/app/features/admin/shared/admin-api.service.spec.ts` - 13 test cases with HttpTestingController
- `frontends/web-panel/src/app/features/admin/shared/role-chip/role-chip.component.ts` - Colored role chip (admin/teacher/student)
- `frontends/web-panel/src/app/features/admin/shared/status-chip/status-chip.component.ts` - Colored status chip (active/archived)
- `frontends/web-panel/src/app/features/admin/dashboard/stat-card/stat-card.component.ts` - Reusable stat card with signal inputs
- `frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts` - Dashboard page with stats API call
- `frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html` - Dashboard template with 4 stat cards
- `frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts` - 4 dashboard tests
- `frontends/web-panel/src/app/app.config.ts` - Added provideNativeDateAdapter()
- `frontends/web-panel/src/app/app.routes.ts` - Updated admin routes to real component paths, removed stats stub
- `frontends/web-panel/src/styles.css` - Added role chip and status chip CSS (light + dark mode)

## Decisions Made
- 4th stat card shows activeSemesterName instead of avg attendance rate because DashboardStatsResponse has no attendance data and cross-service call is out of scope for this plan
- Removed /admin/stats route that was a leftover stub from Phase 38 scaffold (not in admin spec)
- deleteSemester uses http.request('DELETE', url, { body }) because HttpClient.delete() drops request body

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- AdminApiService ready for Plans 02 (Users CRUD page) and 03 (Groups + Semesters pages) to consume
- Route paths pre-wired to component locations that Plans 02/03 will create
- RoleChip and StatusChip ready for table cell rendering in user/group list pages

## Self-Check: PASSED

All 9 created files verified present on disk. Both commit hashes (0201ced, 43c1d9b) found in git log. 108 tests passing across 17 suites.

---
*Phase: 40-web-panel-admin*
*Completed: 2026-04-07*
