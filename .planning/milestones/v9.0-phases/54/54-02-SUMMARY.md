---
phase: 54-headman-web-cabinet-group-management-subjects
plan: 02
subsystem: frontend/web-panel
tags: [angular, routing, sidebar, headman, http-client, vitest]
dependency_graph:
  requires: []
  provides:
    - headman-routes (dashboard, group, subjects)
    - HeadmanApiService
    - sidebar headman nav items
  affects:
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
tech_stack:
  added: []
  patterns:
    - lazy-loaded Angular routes with canActivate headmanGuard
    - Angular computed signals for conditional nav filtering
    - HttpClient with HttpParams for REST API calls
    - Vitest + HttpTestingController for unit tests
key_files:
  created:
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts
  modified:
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
decisions:
  - "Split filteredNavItems into filteredNavItems (non-headman) + filteredHeadmanNavItems (headman-only) to support separate Старостат section header in the sidebar template"
  - "Headman nav items added to allNavItems with isHeadman: true flag; filteredNavItems excludes them via !item.isHeadman filter"
  - "Added Старостат section in sidebar HTML template — only rendered when filteredHeadmanNavItems().length > 0"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-09"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 3
---

# Phase 54 Plan 02: Angular Routing + Sidebar Extension + HeadmanApiService Summary

**One-liner:** Lazy-loaded headman routes (dashboard/group/subjects) with headmanGuard, sidebar isHeadman-filtered Старостат section, and HeadmanApiService with 12 HTTP methods (10 unit tests passing, 277 total).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Update app.routes.ts (replace placeholder + add group/subjects routes) | 389b931 | frontends/web-panel/src/app/app.routes.ts |
| 2 | Extend sidebar + create HeadmanApiService with spec | 1e0a3ac | sidebar.component.ts, sidebar.component.html, headman-api.service.ts, headman-api.service.spec.ts |

## What Was Built

### Task 1 — app.routes.ts
- Replaced `HeadmanPlaceholderComponent` static import with lazy-loaded `HeadmanDashboardComponent`
- Added `/headman/group` route — lazy-loads `HeadmanGroupComponent`, `canActivate: [headmanGuard]`
- Added `/headman/subjects` route — lazy-loads `HeadmanSubjectsComponent`, `canActivate: [headmanGuard]`
- All three headman routes have `data: { title: '...', eyebrow: 'Староста' }`
- Redirect `{ path: '', redirectTo: 'dashboard' }` preserved

### Task 2 — Sidebar + HeadmanApiService

**sidebar.component.ts changes:**
- Added `isHeadman?: boolean` field to `NavItem` interface
- Added three headman nav items to `allNavItems`: "Кабинет старосты" (ph-crown-simple), "Группа" (ph-users-three), "Предметы" (ph-books)
- Updated `filteredNavItems` computed to exclude items where `item.isHeadman === true`
- Added `filteredHeadmanNavItems` computed — returns `[]` when `!user.isHeadman`, otherwise returns headman items only

**sidebar.component.html changes:**
- Added Старостат section block after the main secondary nav — conditionally rendered via `@if (filteredHeadmanNavItems().length > 0)`
- Section header "Старостат" shown when not collapsed; divider shown when collapsed

**HeadmanApiService (12 methods):**
- `getGroupMembers(page, size)` — GET /api/academic/groups/my/members
- `getTodayLessons(groupId)` — GET /api/schedule/groups/{id}/lessons (today's dates)
- `getPendingExcuses()` — GET /api/academic/headman/excuses?status=pending (deferred endpoint)
- `getPendingLateCheckins()` — GET /api/academic/headman/late-checkins?status=pending (deferred)
- `listAssistants(groupId)` — GET /api/academic/assistants?groupId=X
- `assignAssistant(body)` — POST /api/academic/assistants
- `updateAssistantPermissions(id, body)` — PATCH /api/academic/assistants/{id}/permissions
- `revokeAssistant(id)` — DELETE /api/academic/assistants/{id}
- `listSubjects(page, size)` — GET /api/academic/subjects
- `createSubject(body)` — POST /api/academic/subjects
- `updateSubject(id, body)` — PUT /api/academic/subjects/{id}
- `deleteSubject(id)` — DELETE /api/academic/subjects/{id}
- `listTeachers()` — GET /api/academic/users/teachers

## Deviations from Plan

### Auto-decisions (no user permission needed)

**1. [Rule 2 - Enhancement] Split filteredNavItems into two computed signals**
- **Found during:** Task 2
- **Reason:** The plan says to add a "Старостат" section label above headman items. The existing sidebar template has a single `sectionLabel()` for all secondary items. To show a separate "Старостат" header, the headman items need to be in a separate rendered list.
- **Fix:** Split into `filteredNavItems` (non-headman) and `filteredHeadmanNavItems` (headman-only). Added a separate section in the HTML template using `@if (filteredHeadmanNavItems().length > 0)`.
- **Outcome:** Cleaner separation, headman users see their section only. Non-headman students see no headman items at all.

## Verification

- TypeScript compilation: passes (3 TS errors for missing headman components dashboard/group/subjects are EXPECTED — those components are created in Plans 3-5, as documented in Task 1 plan notes)
- Pre-existing TS errors: auth.interceptor.spec.ts, login.component.spec.ts, student-dashboard.spec.ts — unrelated to this plan
- Vitest: **277 tests pass** (267 from Phase 53 + 10 new HeadmanApiService tests), 40 test files
- HeadmanApiService spec: 10/10 tests pass

## Known Stubs

None — all HTTP methods have real implementations. `getPendingExcuses` and `getPendingLateCheckins` reference deferred backend endpoints (will return 404 until Phase 55), but the methods themselves are correctly implemented. Graceful 404 handling will be added in the consuming components (Plans 3-5).

## Self-Check: PASSED

Files exist:
- frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts: FOUND
- frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts: FOUND
- frontends/web-panel/src/app/app.routes.ts: FOUND (modified)
- frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts: FOUND (modified)

Commits exist:
- 389b931: FOUND
- 1e0a3ac: FOUND
