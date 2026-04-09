---
phase: 50
slug: basehref-migration-unified-login
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-09
---

# Phase 50 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 3.x |
| **Config file** | `frontends/web-panel/vitest.config.ts` |
| **Quick run command** | `cd frontends/web-panel && npx vitest run --reporter=dot` |
| **Full suite command** | `cd frontends/web-panel && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/web-panel && npx vitest run --reporter=dot`
- **After every plan wave:** Run `cd frontends/web-panel && npx vitest run`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-T1   | 01   | 1    | AUTH-v9-02, AUTH-v9-03 | T-50-01, T-50-03 | AuthUser расширен + resolveDashboardFor + JWT is_headman safe default | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot src/app/core/auth/auth.service.spec.ts | yes | green |
| 01-T2   | 01   | 1    | AUTH-v9-03 | — | Sidebar типы расширены, D-06 no-entries-for-STUDENT проверен | unit | cd frontends/web-panel && npx vitest run --reporter=dot | yes | green |
| 02-T1   | 02   | 2    | AUTH-v9-05 | T-50-06 | studentGuard пропускает STUDENT/headman, блокирует TEACHER/ADMIN/unauth | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot src/app/core/auth/student.guard.spec.ts | yes | green |
| 02-T2   | 02   | 2    | AUTH-v9-04 | T-50-05 | headmanGuard пропускает только STUDENT+isHeadman, plain STUDENT -> /student/dashboard | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot src/app/core/auth/headman.guard.spec.ts | yes | green |
| 02-T3   | 02   | 2    | AUTH-v9-04, AUTH-v9-05 | T-50-07 | guestGuard редиректит authenticated, пропускает unauth; full regression | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot | yes | green |
| 03-T1   | 03   | 3    | INFRA-v9-04 | T-50-14 | StudentPlaceholderComponent + HeadmanPlaceholderComponent standalone | unit | cd frontends/web-panel && npx vitest run --reporter=dot | yes | green |
| 03-T2   | 03   | 3    | INFRA-v9-04, AUTH-v9-04, AUTH-v9-05 | T-50-10, T-50-11, T-50-12 | 3 новых маршрута под ShellComponent + guestGuard на /login + schedule route для criterion 4 | unit | cd frontends/web-panel && npx vitest run --reporter=dot | yes | green |
| 04-T1   | 04   | 3    | AUTH-v9-02 | T-50-16 | role.guard.ts fallback использует resolveDashboardFor, STUDENT/headman покрыты | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot src/app/core/auth/auth.guard.spec.ts | yes | green |
| 04-T2   | 04   | 3    | AUTH-v9-01, AUTH-v9-02, AUTH-v9-03 | T-50-15 | login.component.ts использует resolveDashboardFor + navigateByUrl, 4 role редиректа | unit TDD | cd frontends/web-panel && npx vitest run --reporter=dot | yes | green |
| 05-T1   | 05   | 4    | INFRA-v9-04 | T-50-21 | angular.json baseHref: / в обоих местах + landing footer replaces /admin/ с /login | build + grep | cd frontends/web-panel && npx ng build --configuration production && grep -c 'base href="/"' dist/browser/index.html | yes | green |
| 05-T2   | 05   | 4    | INFRA-v9-04 | T-50-19, T-50-20, T-50-22 | Prod nginx: /admin/ удалён, catch-all / добавлен в конец, /login 301 сохранён, prefix-match порядок соблюдён | grep + manual curl после deploy | Manual: curl -I https://ruttrack.site/ -> 301 /login; curl -I https://ruttrack.site/login -> 200 | manual | pending-UAT |
| 06-T1   | 06   | 5    | AUTH-v9-07 | — | Full suite regression + prod build | unit regression | cd frontends/web-panel && npx vitest run | yes | green |
| 06-T2   | 06   | 5    | AUTH-v9-01..07, INFRA-v9-04 | — | Human UAT — 6 ROADMAP criteria | manual UAT | Human | manual | pending-UAT |

*Status: pending | green | red | flaky | pending-UAT (manual, awaiting human checkpoint)*

### Plan 06 Task 1 Execution Evidence (2026-04-09)

- **Vitest full suite** `node node_modules/vitest/dist/cli.js run` executed in main repo `C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/` (worktree had no installed `node_modules`; main repo code is at commit `2887a71` containing all Plans 01-05 changes).
  - Result: **162 passed / 162 total across 25 test files**. Exit code 0.
  - Duration 34.11s (transform 16.57s, setup 156.69s, collect 64.08s, tests 35.30s, environment 101.39s, prepare 130.24s)
  - Notable suites that exercise phase 50 surface:
    - `auth.service.spec.ts` — 21 tests
    - `auth.guard.spec.ts` — 9 tests (roleGuard fallback covered for all 4 roles)
    - `student.guard.spec.ts` — 5 tests
    - `headman.guard.spec.ts` — 5 tests
    - `guest.guard.spec.ts` — 5 tests
    - `login.component.spec.ts` — 12 tests (includes new STUDENT + headman redirects)
    - `sidebar.component.spec.ts` — 5 tests (includes STUDENT no-nav-items test)
- **Production build** `node node_modules/@angular/cli/bin/ng.js build --configuration production` executed in same directory.
  - Result: **Application bundle generation complete in 6.867 seconds**. Exit code 0.
  - Two non-blocking warnings (neither is a regression from phase 50):
    1. `TS-998113: All imports are unused` for `src/app/features/teacher/journal/journal-cell/journal-cell.component.ts:8` — pre-existing (noted in Plan 05 Issues Encountered)
    2. `bundle initial exceeded maximum budget` — 652.04 kB vs 500.00 kB budget — pre-existing from Phase 39/40
- **BaseHref verification in emitted bundle**:
  - `dist/browser/index.html:6` = `<base href="/">` (1 match)
  - `<base href="/admin/">` = 0 matches
- **Baseline vs final regression count**: The 129 ROADMAP floor (AUTH-v9-07) is handily exceeded. Intermediate plan summaries reported varying counts (131, 142, 146, 147, 157) because each plan ran in a separate worktree with different base-sync states. The **phase 50 end-state at main HEAD is 162 passed**, meaning +33 tests above the 129 baseline (new tests from Plans 01, 02, 04).

---

## Wave 0 Requirements

*Baseline (129 existing vitest tests) must pass before any new work.*

- [x] `cd frontends/web-panel && npx vitest run` — 129+ tests green (AUTH-v9-07 baseline — verified at 162 at end of phase)
- [x] No new spec files needed in Wave 0 — infrastructure covers all phase requirements
- [x] New specs added during execution:
  - `src/app/core/auth/student.guard.spec.ts` (Plan 02)
  - `src/app/core/auth/headman.guard.spec.ts` (Plan 02)
  - `src/app/core/auth/guest.guard.spec.ts` (Plan 02)
  - `src/app/core/auth/auth.service.spec.ts` (extended in Plan 01)
  - `src/app/core/auth/auth.guard.spec.ts` (extended in Plan 04)
  - `src/app/features/login/login.component.spec.ts` (extended in Plan 04)
  - `src/app/layout/sidebar/sidebar.component.spec.ts` (extended in Plan 01)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Prod nginx routes `/` to web-panel after baseHref migration | INFRA-v9-04 | Requires live reverse-proxy container rebuild + restart; cannot be asserted in vitest | 1) Rebuild `rct-web-panel-nginx` image 2) Restart nginx reverse-proxy 3) `curl -I https://ruttrack.site/` -> 301 `/login` 4) `curl -I https://ruttrack.site/login` -> 200 + `Content-Type: text/html` 5) `curl -I https://ruttrack.site/admin/dashboard` -> 200 + SPA fallback |
| Landing HTML CTA button points to `/login` | INFRA-v9-04 | Static HTML edit, no unit test framework for landing | 1) `grep -n 'ruttrack.site/admin' frontends/landing/dist/index.html` -> 0 matches (verified 2026-04-09) 2) `grep -n 'href="/login"' frontends/landing/dist/index.html` -> 4 matches (verified 2026-04-09) |
| All 4 roles reach correct dashboard from `/login` E2E | AUTH-v9-01, AUTH-v9-02, AUTH-v9-03, AUTH-v9-04 | Requires running backend + real JWT; vitest covers unit paths but not full round-trip | 1) Login as `admin` -> `/admin/dashboard` 2) Login as `teacher` -> `/teacher/dashboard` 3) Login as `student` -> `/student/dashboard` 4) Login as headman student -> `/headman/dashboard` |
| Logout clears tokens and revokes on server | AUTH-v9-06 | Requires live backend token revoke endpoint | 1) Login 2) Click logout 3) Verify `AuthService.currentUser()` returns null 4) Verify backend `POST /api/auth/logout` called with valid token |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (Plan 01 Task 1 baseline step)
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending — ждёт human UAT checkpoint (Task 2 of Plan 06)
