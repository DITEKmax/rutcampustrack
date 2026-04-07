---
phase: 38-web-panel-scaffold-auth
plan: "02"
subsystem: frontend/web-panel
tags: [angular, auth, signals, interceptor, reactive-forms, vitest, tdd]
dependency_graph:
  requires: [38-01]
  provides: [web-panel-auth-service, web-panel-auth-interceptor, web-panel-login-page]
  affects: [38-03]
tech_stack:
  added: []
  patterns:
    - Angular signal-based token storage (memory-only, no localStorage)
    - HttpInterceptorFn with 401 retry queue (adapted from PWA axios.ts pattern)
    - Functional CanActivateFn route guards (authGuard + roleGuard factory)
    - Reactive forms with FormBuilder for login form
    - Angular TestBed with zone.js + getTestBed().initTestEnvironment()
    - @testing-library/angular render() + userEvent for component tests
key_files:
  created:
    - frontends/web-panel/src/app/core/auth/auth.service.ts
    - frontends/web-panel/src/app/core/auth/auth.api.ts
    - frontends/web-panel/src/app/core/auth/auth.interceptor.ts
    - frontends/web-panel/src/app/core/auth/auth.guard.ts
    - frontends/web-panel/src/app/core/auth/role.guard.ts
    - frontends/web-panel/src/app/features/login/login.component.ts
    - frontends/web-panel/src/app/features/login/login.component.html
    - frontends/web-panel/src/app/core/auth/auth.service.spec.ts
    - frontends/web-panel/src/app/core/auth/auth.guard.spec.ts
    - frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts
    - frontends/web-panel/src/app/features/login/login.component.spec.ts
  modified:
    - frontends/web-panel/src/app/app.config.ts
    - frontends/web-panel/src/test-setup.ts
decisions:
  - "zone.js imported in test-setup.ts — Angular 19.2.20 BrowserTestingModule requires Zone.js at test time (provideExperimentalZonelessChangeDetection not available as separate function in this version)"
  - "resetInterceptorState() exported from auth.interceptor.ts — module-level isRefreshing/pendingRequests must be reset in beforeEach to prevent cross-test contamination"
  - "Both tokens stored in Angular signals (memory-only) per orchestrator override of D-06 — NOT localStorage, NOT httpOnly cookie"
  - "Refresh token sent in body ({ refreshToken }) per backend contract — NOT cookie (Web Panel cannot rely on httpOnly cookies unlike PWA)"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 11
  files_modified: 2
---

# Phase 38 Plan 02: Auth Layer — AuthService, Interceptor, Guards, LoginComponent Summary

**One-liner:** Signal-based auth layer with JWT decode, 401 retry queue interceptor (body-based refresh), functional route guards, and reactive login form with role-based routing — 33 Vitest tests all green.

## What Was Built

Complete Angular 19 authentication infrastructure for the Web Panel. Both tokens stored in Angular signals (memory-only, lost on page refresh — acceptable for admin panel per threat model T-38-02). All unit tests written TDD-style (RED first, then GREEN).

### Task 1: AuthService, AuthApi, AuthInterceptor, Guards

**AuthService** (`auth.service.ts`):
- Two private `signal<string | null>(null)` — `_accessToken` and `_refreshToken`
- `isAuthenticated` = `computed(() => _accessToken() !== null)`
- `currentUser` = `computed()` decoding JWT payload via `atob(token.split('.')[1])`, calling `role.toUpperCase()` for case-safe comparison (handles both `TEACHER` and `teacher` from backend)
- `logout()` calls `AuthApi.logout(refreshToken)` then `clearTokens()` + `router.navigate(['/login'])`

**AuthApi** (`auth.api.ts`):
- `login()` → POST `/api/auth/login` with `{ login, password }`
- `refresh()` → POST `/api/auth/refresh` with `{ refreshToken }` in body (NOT cookie)
- `logout()` → POST `/api/auth/logout` with `{ refreshToken }` in body

**AuthInterceptor** (`auth.interceptor.ts`):
- Module-level `isRefreshing` flag + `pendingRequests` queue (same pattern as PWA `axios.ts`)
- Attaches `Authorization: Bearer` to all requests except `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
- On 401: queues pending requests → calls refresh → replays all queued requests with new token
- On refresh failure: flushes queue with errors, clears tokens, navigates to `/login`
- `resetInterceptorState()` exported for test isolation

**authGuard** (`auth.guard.ts`): `CanActivateFn` — returns `true` or `UrlTree(['/login'])`

**roleGuard** (`role.guard.ts`): Factory `(allowedRoles: string[]) => CanActivateFn` — redirects to `/admin/dashboard` or `/teacher/dashboard` based on JWT role claim

**app.config.ts**: Updated `withInterceptors([authInterceptor])`

### Task 2: LoginComponent

**LoginComponent** (`login.component.ts` + `login.component.html`):
- Centered `mat-card` (max-w-[400px]) with "Добро пожаловать" heading (28px/regular per typography scale)
- Reactive form: login + password fields (required-only validation)
- Submit disabled when `form.invalid || loading`; shows "Вход..." + spinner during in-flight request
- On success: `setTokens()` → navigate to `/teacher/dashboard` (TEACHER) or `/admin/dashboard` (ADMIN)
- On 401: shows "Неверный логин или пароль. Проверьте данные и попробуйте снова.", re-enables form, clears password field
- On network error (non-401): shows "Не удалось подключиться к серверу. Проверьте соединение."

## Verification Results

| Check | Result |
|-------|--------|
| `npx vitest run src/app/core/auth/` | PASS — 23/23 tests |
| `npx vitest run src/app/features/login/` | PASS — 10/10 tests |
| `npx vitest run src/app/core/auth/ src/app/features/login/` | PASS — 33/33 total |
| auth.service.ts contains `signal<string \| null>(null)` | PASS |
| auth.service.ts contains `isAuthenticated = computed(` | PASS |
| auth.service.ts contains `currentUser = computed(` | PASS |
| auth.service.ts contains `role.toUpperCase()` | PASS |
| auth.api.ts contains `/api/auth/refresh` with `{ refreshToken }` body | PASS |
| auth.interceptor.ts contains `isRefreshing` and `pendingRequests` | PASS |
| auth.guard.ts contains `router.createUrlTree(['/login'])` | PASS |
| role.guard.ts contains `/admin/dashboard` and `/teacher/dashboard` | PASS |
| app.config.ts contains `withInterceptors([authInterceptor])` | PASS |
| login.component.html contains "Добро пожаловать", "Логин", "Пароль", "Войти", "Вход..." | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Angular 19.2.20 BrowserTestingModule requires Zone.js in test environment**
- **Found during:** Task 1 — first TestBed test run
- **Issue:** `getTestBed().initTestEnvironment(BrowserTestingModule, ...)` threw `NG0908: In this configuration Angular requires Zone.js`. The `@analogjs/vitest-angular/setup-testbed` helper uses `ɵgetCleanupHook` which does not exist in Angular 19.2.20. `provideExperimentalZonelessChangeDetection` could not be passed through `TestEnvironmentOptions.providers` (that field does not exist).
- **Fix:** Added `import 'zone.js'` as the first line of `test-setup.ts`. Uses standard `getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting(), { teardown: { destroyAfterEach: true } })`.
- **Files modified:** `frontends/web-panel/src/test-setup.ts`
- **Commit:** cc87c4c

**2. [Rule 2 - Security] Exported resetInterceptorState() for test isolation**
- **Found during:** Task 1 — interceptor spec design
- **Issue:** Module-level `isRefreshing` and `pendingRequests` in `auth.interceptor.ts` would leak state between tests, causing false positives/negatives.
- **Fix:** Exported `resetInterceptorState()` function from the interceptor module. Called in `beforeEach` in `auth.interceptor.spec.ts`.
- **Files modified:** `frontends/web-panel/src/app/core/auth/auth.interceptor.ts`
- **Commit:** cc87c4c

## Known Stubs

None — all auth functionality is fully wired. `app.routes.ts` still has empty routes array (stub from Plan 01, will be populated in Plan 03 with shell/guard routes).

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: token-storage | auth.service.ts | Access + refresh tokens in Angular signals (memory-only). Mitigated per T-38-02: not in localStorage, not in sessionStorage. Lost on page refresh — acceptable for admin panel. |

## Self-Check: PASSED

- `frontends/web-panel/src/app/core/auth/auth.service.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.api.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.interceptor.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.guard.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/role.guard.ts` — FOUND
- `frontends/web-panel/src/app/features/login/login.component.ts` — FOUND
- `frontends/web-panel/src/app/features/login/login.component.html` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.guard.spec.ts` — FOUND
- `frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts` — FOUND
- `frontends/web-panel/src/app/features/login/login.component.spec.ts` — FOUND
- Commit 61a2811 (test RED Task 1) — FOUND
- Commit cc87c4c (feat GREEN Task 1) — FOUND
- Commit a9b43c9 (test RED Task 2) — FOUND
- Commit fc98099 (feat GREEN Task 2) — FOUND
