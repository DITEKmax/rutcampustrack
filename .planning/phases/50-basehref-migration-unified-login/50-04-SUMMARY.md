---
phase: 50-basehref-migration-unified-login
plan: 04
subsystem: auth
tags: [angular, signals, web-panel, vitest, role-redirect, auth-guard]

# Dependency graph
requires:
  - phase: 50-basehref-migration-unified-login
    provides: Plan 01 — AuthService.resolveDashboardFor + expanded AuthUser (isHeadman, groupId, STUDENT union); Plan 02 — guest.guard.ts (indirect via app.routes.ts import chain during full-suite verification)
provides:
  - login.component.ts routes post-login redirects through AuthService.resolveDashboardFor + router.navigateByUrl (AUTH-v9-02 final wire-up)
  - role.guard.ts fallback uses AuthService.resolveDashboardFor (no more hardcoded /teacher/dashboard fallback that mis-routed STUDENT)
  - auth.guard.spec.ts +3 it-blocks covering STUDENT and headman roleGuard fallback paths
  - login.component.spec.ts +2 new it-blocks for STUDENT/headman redirects, 2 existing updated to navigateByUrl
  - Full vitest suite green at 147 passed (baseline 142 + 5 new)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuthService.resolveDashboardFor = single source of truth, consumed from login.component.ts + role.guard.ts (Plan 04) + guest.guard.ts (Plan 02)"
    - "Post-login navigation via router.navigateByUrl(string) instead of router.navigate([array]) — simpler API when target is already a resolved path"
    - "roleGuard fallback delegates to resolveDashboardFor(user) instead of hardcoded role→path ternary — covers 4-role matrix without branching"

key-files:
  created: []
  modified:
    - frontends/web-panel/src/app/features/login/login.component.ts
    - frontends/web-panel/src/app/features/login/login.component.spec.ts
    - frontends/web-panel/src/app/core/auth/role.guard.ts
    - frontends/web-panel/src/app/core/auth/auth.guard.spec.ts

key-decisions:
  - "router.navigateByUrl(target) preferred over router.navigate([target]) for post-login flow — target is already a fully-resolved absolute path string, not a segment array; navigateByUrl is the right shape"
  - "Kept mockRouter.navigate spy alongside new navigateByUrl spy in login.component.spec.ts — existing 8 non-redirect tests (401, network error, disabled button, etc.) do not interact with navigation and remain green either way, but tooling future-proofs against refactors that may re-introduce navigate calls"
  - "Extended TEACHER_TOKEN and ADMIN_TOKEN fixtures in both spec files to full JWT shape (is_headman: false, group_id: null) for consistency with Plan 01 AuthUser interface — even though role.guard only reads .role, future tests may rely on the full claim set"

requirements-completed: [AUTH-v9-01, AUTH-v9-02, AUTH-v9-03, AUTH-v9-06]

# Metrics
duration: ~20min
completed: 2026-04-09
---

# Phase 50 Plan 04: login.component + role.guard → resolveDashboardFor Summary

**Replaced the hardcoded ADMIN/TEACHER ternary in `login.component.onSubmit` and `role.guard.ts` fallback with a single call to `AuthService.resolveDashboardFor`, added vitest coverage for all 4 post-login redirects (TEACHER/ADMIN updated, STUDENT/headman new) and STUDENT+headman roleGuard fallback, bringing the unified `/login` flow across 4 roles under unit-test lock.**

## Performance

- **Duration:** ~20 min (including baseline run, 2 RED commits, 2 GREEN commits, full suite verification)
- **Started:** 2026-04-09T02:25:00Z
- **Completed:** 2026-04-09T02:37:00Z
- **Tasks:** 2
- **Files modified:** 4
- **Tests:** 142 baseline → 147 after Plan 04 (+3 in auth.guard.spec, +2 in login.component.spec)

## Accomplishments

- **Task 1 — role.guard.ts + auth.guard.spec.ts:** Fallback in `roleGuard` now calls `auth.resolveDashboardFor(user)` instead of the hardcoded `user.role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard'` ternary. 3 new it-blocks cover the STUDENT and headman cases that the old fallback got wrong (plain STUDENT → `/student/dashboard`, headman → `/headman/dashboard`, `roleGuard([TEACHER])` for STUDENT → `/student/dashboard`). Pitfall 2 from the Phase 50 research is closed.
- **Task 2 — login.component.ts + login.component.spec.ts:** `onSubmit` next-callback now resolves the target URL through `AuthService.resolveDashboardFor(currentUser())` and navigates via `router.navigateByUrl(target)`. 2 existing redirect tests updated (TEACHER + ADMIN, `navigate` → `navigateByUrl` + full JWT shape in `currentUser` mock), 2 new tests added (plain STUDENT, headman). `mockAuthService` gained a `resolveDashboardFor` spy implementing the same 5-branch logic as the real service.
- **4 post-login redirects** (TEACHER/ADMIN/STUDENT/headman) now explicitly asserted through `router.navigateByUrl` — AUTH-v9-02 locked down in unit tests.
- **AUTH-v9-03** closed: the `login.component.spec.ts` TEACHER/ADMIN fixtures now use full JWT shape (`is_headman`, `group_id`) and `currentUser` returns the widened `AuthUser` with `isHeadman`/`groupId`, exercising the Plan 01 JWT parser end-to-end.
- **AUTH-v9-06** (logout) verified non-regressed: `auth.service.spec.ts` `logout()` test stays green in the full suite (147/147).

## Task Commits

Per-task atomic commits in TDD RED→GREEN order:

1. **Base sync:** chore(50-04): base-sync Plan 01 artifacts into worktree — `2de0ae2`
2. **Task 1 (RED):** test(50-04): add failing tests for roleGuard STUDENT/headman fallback — `369aa04`
3. **Task 1 (GREEN):** fix(50-04): replace roleGuard hardcoded fallback with resolveDashboardFor — `27ce577`
4. **Task 2 (RED):** test(50-04): add failing tests for login post-redirect to all 4 roles — `d2d5671`
5. **Task 2 (GREEN):** feat(50-04): route login success through AuthService.resolveDashboardFor — `6aca2f6`

## Files Created/Modified

- `frontends/web-panel/src/app/core/auth/role.guard.ts` — fallback ternary replaced with `router.createUrlTree([auth.resolveDashboardFor(user)])`. `/teacher/dashboard` and `/admin/dashboard` string literals entirely removed from this file.
- `frontends/web-panel/src/app/core/auth/auth.guard.spec.ts` — TEACHER_TOKEN + ADMIN_TOKEN widened to full JWT shape, new STUDENT_TOKEN + HEADMAN_TOKEN fixtures added, `roleGuard` describe block gained 3 new it-blocks validating STUDENT and headman fallback routing.
- `frontends/web-panel/src/app/features/login/login.component.ts` — `next` callback in `onSubmit` now resolves via `this.authService.resolveDashboardFor(this.authService.currentUser())` and navigates via `this.router.navigateByUrl(target)`. Hardcoded ternary removed, `const role = ...` removed.
- `frontends/web-panel/src/app/features/login/login.component.spec.ts` — `mockAuthService` gained `resolveDashboardFor` spy with full 5-branch logic, `mockRouter` gained `navigateByUrl` spy, TEACHER/ADMIN `currentUser` mocks widened, 2 existing redirect assertions updated from `navigate([...])` to `navigateByUrl(...)`, 2 new it-blocks added for STUDENT/headman.

## Decisions Made

- **`router.navigateByUrl(target)` over `router.navigate([target])`.** The plan explicitly mandates `navigateByUrl` because `resolveDashboardFor` returns a fully-resolved absolute path string (`/admin/dashboard`, `/student/dashboard`, etc.). `navigate([...])` expects a segment array and would re-parse the string — `navigateByUrl` is the direct API for this shape. This also matches the intended `guestGuard` pattern from Plan 02 and avoids mixing navigation styles.
- **Kept `navigate` spy alongside `navigateByUrl` in `mockRouter`.** Even though Plan 04's redirect tests only assert `navigateByUrl`, the mockRouter still provides `navigate: vi.fn()` because `AuthService.logout()` (tested indirectly in the full suite) still calls `router.navigate(['/login'])`. Dropping `navigate` would break the logout regression coverage (AUTH-v9-06).
- **Extended TEACHER_TOKEN and ADMIN_TOKEN to full JWT shape in both spec files.** The JWT body now carries `is_headman: false, group_id: null` for TEACHER/ADMIN, matching the real backend payload from `JwtService.java:94-96`. Plan 01 already did this in `auth.service.spec.ts`; Plan 04 extends the same shape to `auth.guard.spec.ts` and `login.component.spec.ts` so all 4 redirect matrices share the same fixtures.
- **`mockAuthService.resolveDashboardFor` mock duplicates the real implementation.** Plan 04 could have passed just a `vi.fn().mockReturnValue('/admin/dashboard')` and relied on assertion-per-test. Instead, the mock implements the full 5-branch logic (null → /login, ADMIN → /admin, TEACHER → /teacher, headman → /headman, STUDENT → /student). Rationale: tests remain readable (no per-test mock override), and if Plan 01's resolveDashboardFor contract changes in the future, mock logic drift is caught by a single assertion pattern.

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria for Task 1 and Task 2 passed:

- Task 1: `auth.resolveDashboardFor(user)` appears 1× in role.guard.ts; 0× occurrences of `/teacher/dashboard` or `/admin/dashboard` string literals in role.guard.ts; `STUDENT_TOKEN` and `HEADMAN_TOKEN` each appear ≥2× in auth.guard.spec.ts; full 9/9 tests in auth.guard.spec.ts pass.
- Task 2: `this.authService.resolveDashboardFor(this.authService.currentUser())` appears 1× in login.component.ts; `this.router.navigateByUrl` appears 1×; 0× occurrences of `this.router.navigate([` in login.component.ts; `navigateByUrl` appears 5× in login.component.spec.ts; 12 it-blocks (10 existing + 2 new); full 12/12 tests in login.component.spec.ts pass; full vitest suite 147/147 pass.

## Issues Encountered

- **Worktree base at pre-Plan-01 commit.** The worktree was created on `d6df3c24` (deployment commit) which predates Plan 01 work. Fixed via a `chore(50-04): base-sync Plan 01 artifacts into worktree` commit that copies `auth.service.ts`, `auth.service.spec.ts`, `sidebar.component.ts`, `sidebar.component.spec.ts` from the main repo. The orchestrator's cherry-pick will no-op this commit (file contents already match main HEAD).
- **No `node_modules` in worktree.** Created a relative junction (`mklink /J`) pointing to `../../../../../frontends/web-panel/node_modules` so `node node_modules/vitest/dist/cli.js run` works from the worktree cwd without a full `npm install`.
- **git-bash `ls` does not display windows-junction contents.** Quirk of MSYS2 — `cmd /c dir` works correctly and confirms modules are present. Does not affect vitest execution which uses native Node filesystem.

## User Setup Required

None — all changes are Angular TypeScript and spec files in the web-panel subtree. No environment variables, no infrastructure config, no backend changes. JWT payload shape is already in place from `services/auth-service/auth-app/.../JwtService.java:94-96` (backend unchanged in Phase 50).

## Known Stubs

None — Plan 04 fully wires the `resolveDashboardFor` contract into both consumer sites (login.component + role.guard). The dashboards themselves (`/student/dashboard`, `/headman/dashboard`) are placeholder components (Plan 03 scope, D-06), not stubs in this plan.

## Next Phase Readiness

- **Wave 3 Plan 03 (placeholder routes + app.routes.ts wiring)** is a sibling in parallel — no dependency from Plan 04, and the landing surface (`app.routes.ts`) is untouched by Plan 04. Orchestrator can merge both plans independently via cherry-pick.
- **Phase 50 integration gate:** with Plan 01 (AuthService foundation), Plan 02 (new guards — studentGuard, headmanGuard, guestGuard), Plan 03 (placeholder routes), and Plan 04 (login + role.guard wire-up), the 4-role unified `/login` → dashboard flow is fully covered by unit tests. Remaining Phase 50 work: baseHref migration in `angular.json` + prod nginx catch-all + landing `href="/admin/"` → `/login` replacement (separate plans, not blocked by Plan 04).
- **AUTH-v9-07** (129 existing tests stay green) holds — suite now at 147 passed, 0 failed.

## Threat Flags

None — the only new surface touched is client-side routing. No new API endpoints, no new file/DB access, no new auth/session state. T-50-16 (roleGuard hardcoded fallback elevation bug) from the plan's threat register is now **mitigated** (was `mitigate` disposition; Task 1 implements the fix with test coverage).

## Self-Check: PASSED

- Files created/modified verified:
  - FOUND: frontends/web-panel/src/app/features/login/login.component.ts
  - FOUND: frontends/web-panel/src/app/features/login/login.component.spec.ts
  - FOUND: frontends/web-panel/src/app/core/auth/role.guard.ts
  - FOUND: frontends/web-panel/src/app/core/auth/auth.guard.spec.ts
- Commits verified:
  - FOUND: 2de0ae2 (base-sync)
  - FOUND: 369aa04 (Task 1 RED)
  - FOUND: 27ce577 (Task 1 GREEN)
  - FOUND: d2d5671 (Task 2 RED)
  - FOUND: 6aca2f6 (Task 2 GREEN)
- Acceptance criteria Task 1: all 9 grep checks pass; `vitest run auth.guard.spec.ts` → 9/9 green
- Acceptance criteria Task 2: all 11 grep checks pass; `vitest run login.component.spec.ts` → 12/12 green; full suite → 147/147 green
- Threat register T-50-16: mitigation implemented and tested

---
*Phase: 50-basehref-migration-unified-login*
*Completed: 2026-04-09*
