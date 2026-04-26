---
phase: 58-admin-bug-006-fixes
plan: 03
subsystem: academic-service + web-panel
tags: [bug-fix, validation, telegram, user-creation, BUG-006-3]
requirements:
  - BUG-006-3
  - FR-3
depends_on: [02]
requires:
  - academic-service GlobalExceptionHandler (plan 02)
  - ConflictException infrastructure (plan 02)
  - FIELD_MESSAGES map in user-dialog (plan 02)
provides:
  - UserService.validateTelegramForRole guard
  - BadRequestException with optional `field` slot
  - Dynamic Validators.required on telegramId control keyed by role
affects:
  - POST /api/academic/users contract (semantic only — DTO unchanged)
  - Admin user create/edit dialog UX
tech-stack:
  added: []
  patterns:
    - role-conditional required validator via `role.valueChanges`
    - structured BadRequestException carrying DTO field name for Problem-Detail
key-files:
  created:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/BadRequestException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
decisions:
  - "D-08..D-11 honored: telegramId required only for STUDENT; TEACHER/ADMIN keep it optional"
  - "Per D-09: service-level guard (not @AssertTrue on DTO) — easier to unit-test and keeps contract module free of cross-field logic"
  - "Treat telegramId=0 as missing — some clients submit 0 instead of null"
  - "BadRequestException extended with optional `field` slot (mirrors ConflictException pattern from plan 02)"
metrics:
  duration: ~25min
  completed: 2026-04-14
  tests_added: 11 (5 Java + 6 Angular)
  tests_total_green: 332 (18 Java conflict+telegram + 314 web-panel)
---

# Phase 58 Plan 03: Telegram Required For Students Summary

One-liner: Enforce telegramId for STUDENT user creation on both backend (service-level guard → 400 with `field="telegramId"` in RFC 7807 body) and admin frontend (dynamic `Validators.required` on the dialog field keyed by role), leaving TEACHER/ADMIN optional.

## What Was Delivered

### Backend (`academic-service`)
- New private method `UserService.validateTelegramForRole(request)` invoked as the **first** statement of `createUser` — before login generation or any `existsBy*` pre-check. Throws a structured `BadRequestException("telegramId", "Telegram ID обязателен для студента")` when `role==STUDENT` and `telegramId` is `null` or `0L`.
- `BadRequestException` extended with a second constructor `(String field, String message)` and a `getField()` accessor. The single-arg constructor is preserved for backward compatibility with existing call sites (`UserService.patchUser`, `UserService.transferStudent`).
- `GlobalExceptionHandler.handleBadRequest` now propagates `ex.getField()` into the 8-arg `ErrorResponse` constructor, so the Problem-Detail payload contains `field: "telegramId"` for this class of errors (mirrors the ConflictException path added by plan 02).
- New unit test `UserServiceTelegramRequiredTest` (5 cases): student-null, student-zero, teacher-null, admin-null, student-valid.
- Existing `UserServiceConflictTest.studentRequest()` fixture updated to supply `telegramId=123456789L` so its happy path still lands on `save()`.

### Frontend (`web-panel`)
- `UserDialogComponent` gains a `telegramId: FormControl<number | null>` initialised as optional.
- `role.valueChanges` (via `takeUntilDestroyed`) toggles `Validators.required` on the telegramId control — plus a one-shot apply in the constructor to cover edit mode and `presetRole`.
- Template adds a dedicated `<mat-form-field>` for Telegram ID (visible for all roles, `[required]` binds to `role === 'student'`), with a role-aware hint «Без Telegram ID студент не сможет получать уведомления и подтверждать через бота» and an error «Telegram ID обязателен для студента».
- `save()` now includes `telegramId` in the `CreateUserRequest` body when present; patch branch emits it when changed.
- 6 new vitest cases: required when student, optional when teacher/admin, student→teacher clears required, teacher→student adds required, 400-with-field path. Existing «save in create mode» and `fillCreateForm` helper updated to supply telegramId so role=student tests don't collide with the new validator.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Missing telegramId FormControl in user-dialog**
- **Found during:** Task 2 (inspecting component before adding validator)
- **Issue:** Plan assumed a `telegramId` control already existed; the form only had lastName/firstName/middleName/role/groupId/employeeNumber/isHeadman. Adding a validator to a non-existent control would silently no-op.
- **Fix:** Added `telegramId: FormControl<number | null>(null)` and wired it into the `save()` create/edit flows and template.
- **Files modified:** `user-dialog.component.ts`, `user-dialog.component.html`
- **Commit:** `c996f95`

**2. [Rule 1 — Bug] Existing tests broken by new required validator**
- **Found during:** Task 2 (running vitest)
- **Issue:** Three pre-existing specs (`save in create mode…`, `fillCreateForm` helper used by 409 tests) built forms with `role: 'student'` but no telegramId, which would now be invalid.
- **Fix:** Added `telegramId: 123456789` to these fixtures. Updated the expected POST body accordingly.
- **Files modified:** `user-dialog.component.spec.ts`
- **Commit:** `c996f95`

**3. [Rule 3 — Blocking] `UserServiceConflictTest.studentRequest()` fixture invalid**
- **Found during:** Task 1 (foreseeable before test run)
- **Issue:** Fixture had `telegramId=null` for STUDENT, which would now throw BadRequestException before reaching the conflict-check code path it was meant to exercise.
- **Fix:** Updated fixture to `telegramId=123456789L`.
- **Files modified:** `UserServiceConflictTest.java`
- **Commit:** `fd39642`

## Test Evidence

```
# Backend
./gradlew.bat :services:academic-service:academic-app:test \
  --tests "*UserServiceTelegramRequiredTest*" --tests "*UserServiceConflictTest*"
> BUILD SUCCESSFUL (8 tests green)

# Frontend
cd frontends/web-panel && npm test -- --run
> Test Files  45 passed (45)
> Tests       314 passed (314)
```

## Threat Flags

None — surface unchanged (existing POST /users endpoint), and the threat model (T-58-03-01..03) is mitigated by the server-side guard running before any DB work (Tampering mitigation) and by never serialising the rejected value (Information Disclosure accepted).

## Commits

| Hash    | Type  | Description |
|---------|-------|-------------|
| fd39642 | feat  | require telegramId for STUDENT role (backend) |
| c996f95 | feat  | dynamic telegramId validator in user-dialog (frontend) |

## Self-Check: PASSED

- File `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java` — FOUND (contains `validateTelegramForRole`)
- File `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/BadRequestException.java` — FOUND (2-arg ctor + getField)
- File `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java` — FOUND (propagates ex.getField)
- File `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java` — FOUND
- File `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts` — FOUND (telegramId control + applyTelegramRequiredForRole)
- File `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html` — FOUND (Telegram ID field + role-aware hint)
- File `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts` — FOUND (new cases)
- Commit fd39642 — FOUND in `git log`
- Commit c996f95 — FOUND in `git log`
- All referenced tests green (see "Test Evidence")
