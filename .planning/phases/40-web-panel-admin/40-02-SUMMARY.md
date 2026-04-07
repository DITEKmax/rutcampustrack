---
phase: 40-web-panel-admin
plan: 02
subsystem: ui
tags: [angular, angular-material, admin, users, crud, signals, mat-table, mat-dialog, reactive-forms]

requires:
  - phase: 40-web-panel-admin
    plan: 01
    provides: AdminApiService, types (UserResponse, GroupResponse, CreateUserRequest, PatchUserRequest), RoleChipComponent, StatusChipComponent

provides:
  - UsersPageComponent with paginated mat-table, search, role/status filters, edit/archive/restore actions
  - UserDialogComponent for create/edit user with reactive form and role-conditional fields
  - ArchiveUserDialogComponent for destructive archive confirmation

affects: []

tech-stack:
  added: []
  patterns: [debounceTime search with signal-based filters, MatDialog create/edit dual-mode pattern, conditional form fields via role value]

key-files:
  created:
    - frontends/web-panel/src/app/features/admin/users/users-page.component.ts
    - frontends/web-panel/src/app/features/admin/users/users-page.component.html
    - frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
    - frontends/web-panel/src/app/features/admin/users/archive-user-dialog/archive-user-dialog.component.ts
  modified: []

key-decisions:
  - "Archive uses PATCH with status='archived' (not DELETE) for explicit status control"
  - "Restore action has no confirmation dialog — safe non-destructive operation"

requirements-completed: [WPAN-09]

duration: 3min
completed: 2026-04-07
---

# Phase 40 Plan 02: User CRUD Page Summary

**UsersPageComponent with paginated mat-table, debounced search, role/status filters, and UserDialogComponent for create/edit with reactive forms**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-07T09:30:11Z
- **Completed:** 2026-04-07T09:34:00Z
- **Tasks:** 2
- **Files created:** 7

## Accomplishments

- UsersPageComponent with signal-based state, debounced search (300ms + distinctUntilChanged), role and status mat-select filters, and mat-paginator (20/50/100 page sizes)
- mat-table with login, displayName, role (RoleChipComponent), group (lookup by groupId), status (StatusChipComponent), and actions columns
- Edit/archive/restore action buttons with conditional icons (ph-archive vs ph-arrow-counter-clockwise) based on user status
- Archived rows displayed with opacity-60 styling, empty state with helpful message
- UserDialogComponent with dual mode (create/edit): reactive form with role-conditional fields (groupId for student/teacher, employeeNumber for teacher, isHeadman checkbox for student)
- Create mode: builds CreateUserRequest, shows "Логин будет сгенерирован автоматически" hint, closes dialog with result containing login
- Edit mode: pre-fills form from user data, disables role select, patches only changed fields
- ArchiveUserDialogComponent: simple destructive confirmation with warn-colored button
- Snackbar feedback for all CRUD operations (create shows login, update/archive/restore)

## Task Commits

1. **Task 1: UsersPageComponent — mat-table with search, role/status filters, pagination** - `a1a1f18` (feat)
2. **Task 2: UserDialogComponent (create/edit) + ArchiveUserDialogComponent** - `e9e3616` (feat)

## Files Created

- `frontends/web-panel/src/app/features/admin/users/users-page.component.ts` - UsersPageComponent with signal-based state, search, filters, CRUD actions
- `frontends/web-panel/src/app/features/admin/users/users-page.component.html` - Template with mat-table, filters, paginator, empty/loading states
- `frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts` - 4 test cases (create, init, dialog, restore)
- `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts` - Dual-mode dialog with reactive form, role-conditional fields
- `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html` - Dialog template with conditional form sections
- `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts` - 5 test cases (create mode, edit prefill, save, validation, role visibility)
- `frontends/web-panel/src/app/features/admin/users/archive-user-dialog/archive-user-dialog.component.ts` - Simple confirmation dialog

## Decisions Made

- Archive uses PATCH with status='archived' rather than DELETE for explicit status control and consistency with restore (also PATCH)
- Restore action has no confirmation dialog since it's a safe non-destructive operation
- Role field is disabled in edit mode (cannot change role after creation)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None.

## Next Phase Readiness

- UsersPageComponent is fully wired to AdminApiService from Plan 01
- Route `/admin/users` already configured in Plan 01 to load this component
- All 112 tests pass across 18 suites (9 new tests added in this plan)

## Self-Check: PASSED

All 7 created files verified present on disk. Both commit hashes (a1a1f18, e9e3616) found in git log. 112 tests passing across 18 suites.
