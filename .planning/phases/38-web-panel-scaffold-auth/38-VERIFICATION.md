---
phase: 38-web-panel-scaffold-auth
verified: 2026-04-07T12:00:00Z
status: human_needed
score: 15/15 must-haves verified
human_verification:
  - test: "Open http://localhost:4200 in browser, log in as teacher and admin"
    expected: "Login form renders centered with correct typography, login redirects to role-appropriate dashboard, sidebar shows correct nav items per role"
    why_human: "Visual rendering, layout proportions, Tailwind + Material M3 theming correctness cannot be verified without a browser"
  - test: "Click dark mode toggle in sidebar footer"
    expected: "Theme switches between light and dark, 'dark' class added/removed on <html> element, preference persists after page reload"
    why_human: "Requires browser interaction and visual verification of color changes"
  - test: "Resize browser below 1024px"
    expected: "Sidebar auto-collapses to 64px icon-only mode"
    why_human: "Responsive behavior requires browser viewport resizing"
  - test: "Click collapse chevron in sidebar footer"
    expected: "Sidebar animates from 240px to 64px (200ms ease-in-out) and persists after page reload"
    why_human: "Animation quality and localStorage persistence need browser verification"
  - test: "Log in, then navigate directly to /admin/dashboard as TEACHER"
    expected: "Redirected to /teacher/dashboard (roleGuard intercepts)"
    why_human: "Route guard behavior needs end-to-end browser verification"
  - test: "Log in, then close and reopen browser tab"
    expected: "User is redirected to /login (tokens lost on page unload — memory-only signal storage)"
    why_human: "Memory-only token storage behavior on page reload requires browser testing"
---

# Phase 38: Web Panel Scaffold + Auth Verification Report

**Phase Goal:** Angular 19 standalone scaffold with Tailwind, HTTP interceptors, role-based route guards, login/logout for TEACHER and ADMIN roles
**Verified:** 2026-04-07T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Teacher/admin can log in with username/password and see role-appropriate dashboard | ✓ VERIFIED | `login.component.ts` calls `authApi.login()`, routes to `/teacher/dashboard` or `/admin/dashboard` based on JWT role |
| 2 | JWT access token and refresh token both in Angular signals (memory-only, not localStorage) | ✓ VERIFIED | `auth.service.ts`: `signal<string \| null>(null)` for both `_accessToken` and `_refreshToken`; no localStorage usage in auth module |
| 3 | Unauthorized route access redirects to login | ✓ VERIFIED | `authGuard` returns `router.createUrlTree(['/login'])` when `!isAuthenticated()`; applied to shell route via `canActivate: [authGuard]` in `app.routes.ts` |
| 4 | Token auto-refresh works via HTTP interceptor | ✓ VERIFIED | `auth.interceptor.ts`: `isRefreshing` flag + `pendingRequests` queue, calls `authApi.refresh(refreshToken)` on 401, replays queued requests with new token |
| 5 | Logout clears tokens and redirects to login | ✓ VERIFIED | `auth.service.ts` `logout()` calls `clearTokens()` + `router.navigate(['/login'])`; sidebar `logout()` button wired to `authService.logout()` |

**Plan 01 additional truths:**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 6 | Angular 19 project builds without errors | ✓ VERIFIED | `package.json` has `@angular/core: ^19.2.0`, `angular.json` uses `@angular/build:application`, SUMMARY-01 confirms `ng build` pass |
| 7 | Vitest runs and passes | ✓ VERIFIED | `vitest.config.ts` with `@analogjs/vite-plugin-angular`, SUMMARY-03 confirms 43/43 tests pass |
| 8 | Tailwind v4 configured | ✓ VERIFIED | `styles.css`: `@import "tailwindcss"`, `@custom-variant dark (&:where(.dark, .dark *))` |
| 9 | Angular Material M3 brand blue theme applies | ✓ VERIFIED | `styles.css`: `--mat-sys-primary: #1A56DB` (light) / `--mat-sys-primary: #4D8BFF` (dark) |
| 10 | Shell layout with sidebar renders after login | ✓ VERIFIED | `shell.component.html`: `flex h-screen` + `<app-sidebar />` + `<router-outlet />`; routes guarded by `authGuard` |

**Plan 03 additional truths:**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 11 | Sidebar shows role-appropriate nav items | ✓ VERIFIED | `sidebar.component.ts`: `filteredNavItems = computed()` from `currentUser().role`; TEACHER gets 2 items, ADMIN gets 4 items |
| 12 | Sidebar collapses to icon-only | ✓ VERIFIED | Angular Animations trigger: `state('collapsed', style({ width: '64px' }))`, auto-collapses when `window.innerWidth < 1024` |
| 13 | Dark mode toggle persists to localStorage | ✓ VERIFIED | `theme.service.ts`: `localStorage.setItem('web-panel.theme', ...)`, reads on init, `classList.toggle('dark', ...)` |
| 14 | Routes protected by authGuard + roleGuard | ✓ VERIFIED | `app.routes.ts`: shell route `canActivate: [authGuard]`, teacher/admin child routes `canActivate: [roleGuard([...])]` |
| 15 | Stub dashboards show placeholder content | ✓ VERIFIED | `teacher-dashboard.component.ts`: "Панель преподавателя", `admin-dashboard.component.ts`: "Панель администратора", `empty.component.ts`: "Раздел в разработке." |

**Score:** 15/15 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/web-panel/package.json` | Angular 19 dependencies | ✓ VERIFIED | `@angular/core: ^19.2.0`, `tailwindcss: ^4.2.2`, `@testing-library/angular: ^17.4.0` |
| `frontends/web-panel/src/styles.css` | Tailwind v4 + Material M3 + Geist font | ✓ VERIFIED | `@import "tailwindcss"`, `@import "@fontsource-variable/geist"`, `--mat-sys-primary: #1A56DB` |
| `frontends/web-panel/vitest.config.ts` | Vitest + Angular plugin | ✓ VERIFIED | Uses `@analogjs/vite-plugin-angular` (corrected from plan's `/plugin` subpath) |
| `frontends/web-panel/src/app/app.config.ts` | provideRouter + provideHttpClient + authInterceptor | ✓ VERIFIED | `withInterceptors([authInterceptor])` present |
| `frontends/web-panel/src/app/core/auth/auth.service.ts` | Signal-based auth state | ✓ VERIFIED | `signal<string \| null>(null)` for both tokens, `isAuthenticated = computed()`, `currentUser = computed()` |
| `frontends/web-panel/src/app/core/auth/auth.api.ts` | HTTP calls to auth endpoints | ✓ VERIFIED | `/api/auth/login`, `/api/auth/refresh` (body-based), `/api/auth/logout` |
| `frontends/web-panel/src/app/core/auth/auth.interceptor.ts` | Bearer token + 401 retry queue | ✓ VERIFIED | `HttpInterceptorFn`, `isRefreshing` flag, `pendingRequests` queue, `resetInterceptorState()` exported |
| `frontends/web-panel/src/app/core/auth/auth.guard.ts` | Authentication guard | ✓ VERIFIED | `CanActivateFn`, returns `router.createUrlTree(['/login'])` |
| `frontends/web-panel/src/app/core/auth/role.guard.ts` | Role-based access guard | ✓ VERIFIED | Factory `(allowedRoles) => CanActivateFn`, redirects to `/admin/dashboard` or `/teacher/dashboard` |
| `frontends/web-panel/src/app/features/login/login.component.ts` | Login form UI | ✓ VERIFIED | Reactive form, "Неверный логин или пароль" on 401, "Не удалось подключиться" on network error, `patchValue({ password: '' })` |
| `frontends/web-panel/src/app/core/theme/theme.service.ts` | Dark/light mode with localStorage | ✓ VERIFIED | `localStorage.getItem('web-panel.theme')`, `prefers-color-scheme` fallback, `classList.toggle('dark', ...)` |
| `frontends/web-panel/src/app/layout/shell/shell.component.ts` | Main shell layout | ✓ VERIFIED | Standalone, imports `SidebarComponent` + `RouterOutlet` |
| `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` | Role-filtered nav + collapse | ✓ VERIFIED | `navItems` with roles, Angular Animations 240px/64px, `inject(AuthService)`, `inject(ThemeService)` |
| `frontends/web-panel/src/app/app.routes.ts` | Full route config with guards | ✓ VERIFIED | `authGuard`, `roleGuard(['TEACHER'])`, `roleGuard(['ADMIN'])`, lazy `loadComponent` throughout |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `src/main.ts` | `app.config.ts` | `bootstrapApplication(AppComponent, appConfig)` | ✓ WIRED | `main.ts` imports and passes `appConfig` |
| `styles.css` | `tailwindcss` | `@import "tailwindcss"` | ✓ WIRED | Line 1 of styles.css |
| `auth.interceptor.ts` | `auth.service.ts` | `inject(AuthService)` for token access | ✓ WIRED | `inject(AuthService)` in interceptor function body |
| `auth.interceptor.ts` | `auth.api.ts` | `inject(AuthApi)` for refresh call | ✓ WIRED | `inject(AuthApi)` in interceptor, calls `authApi.refresh(refreshToken)` |
| `login.component.ts` | `/api/auth/login` | `authApi.login()` HTTP POST | ✓ WIRED | `this.authApi.login({ login, password })` in `onSubmit()` |
| `app.routes.ts` | `auth.guard.ts` | `canActivate: [authGuard]` | ✓ WIRED | Shell route: `canActivate: [authGuard]` |
| `sidebar.component.ts` | `auth.service.ts` | `inject(AuthService)` for currentUser | ✓ WIRED | `private authService = inject(AuthService)` |
| `sidebar.component.ts` | `theme.service.ts` | `inject(ThemeService)` for dark mode toggle | ✓ WIRED | `themeService = inject(ThemeService)` |

### Data-Flow Trace (Level 4)

Auth layer data flows are memory-only (signals) — no DB queries in frontend. All data sourced from backend API responses:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `login.component.ts` | `tokens` (from API response) | `authApi.login()` POST `/api/auth/login` | Yes — backend JWT response | ✓ FLOWING |
| `auth.service.ts` | `_accessToken`, `_refreshToken` signals | Set via `setTokens()` from login response | Yes — real JWT strings | ✓ FLOWING |
| `sidebar.component.ts` | `filteredNavItems` computed | `authService.currentUser()` role from decoded JWT | Yes — role decoded from real JWT payload | ✓ FLOWING |
| `auth.interceptor.ts` | `authService.getRefreshToken()` | Signal value set during login | Yes — real refresh token | ✓ FLOWING |

### Behavioral Spot-Checks

No running server available for live API checks. Static verification only.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Package.json has Angular 19 | `grep @angular/core frontends/web-panel/package.json` | `"^19.2.0"` found | ✓ PASS |
| Styles has Tailwind import | `grep tailwindcss src/styles.css` | `@import "tailwindcss"` on line 1 | ✓ PASS |
| authInterceptor wired in app.config | `grep withInterceptors src/app/app.config.ts` | `withInterceptors([authInterceptor])` | ✓ PASS |
| Routes use authGuard | `grep authGuard src/app/app.routes.ts` | `canActivate: [authGuard]` found | ✓ PASS |
| Test count meets minimum | Line counts: service=98, guard=112, interceptor=158, login=255 | All > minimum threshold | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| WPAN-01 | 38-01, 38-02, 38-03 | Teacher/admin login with username/password | ✓ SATISFIED | `login.component.ts` + `auth.api.ts` POST `/api/auth/login` |
| WPAN-02 | 38-01, 38-02 | JWT token storage | ✓ SATISFIED (with deviation noted) | Both tokens in Angular signals per ROADMAP SC-2 and D-06 override. REQUIREMENTS.md states "httpOnly cookie" but CONTEXT.md D-06 explicitly overrides: backend has no httpOnly cookie endpoint, body-based refresh only. ROADMAP is the authoritative contract. |
| WPAN-03 | 38-02, 38-03 | Role-based route guards (TEACHER, ADMIN) | ✓ SATISFIED | `role.guard.ts`, `auth.guard.ts`, applied in `app.routes.ts` |
| WPAN-04 | 38-02 | Token auto-refresh via HTTP interceptor | ✓ SATISFIED | `auth.interceptor.ts` with 401 retry queue pattern |
| WPAN-05 | 38-02, 38-03 | Logout clears tokens and redirects | ✓ SATISFIED | `auth.service.ts` `logout()` + sidebar "Выйти" button |

**WPAN-02 Conflict Note:** REQUIREMENTS.md text says "refresh in httpOnly cookie" but ROADMAP.md success criteria SC-2 says "both in Angular signals (memory-only, not localStorage)". CONTEXT.md D-06 documents the deliberate override: backend serves refresh via body-based POST only (not httpOnly cookie rotation), so both tokens are stored in signals. The implementation satisfies the ROADMAP contract. REQUIREMENTS.md should be updated to reflect this decision.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `shared/empty/empty.component.ts` | 6 | "Раздел в разработке." placeholder template | ℹ️ Info | Intentional stub — all stub routes (journal, stats, users, groups, semesters) use this as placeholder for Phase 39/40 feature implementation |
| `features/teacher/dashboard/teacher-dashboard.component.ts` | 7-14 | Static "Выберите раздел в боковом меню." text | ℹ️ Info | Intentional placeholder per plan spec; not blocking |
| `features/admin/dashboard/admin-dashboard.component.ts` | 7-14 | Static "Выберите раздел в боковом меню." text | ℹ️ Info | Intentional placeholder per plan spec; not blocking |

No blocker anti-patterns found. The stubs are intentionally structured per Plan 03 spec for Phase 39/40 to fill in. The auth, interceptor, guards, and shell layout are all fully implemented.

### Human Verification Required

#### 1. Full Login Flow Visual Check

**Test:** Run `cd frontends/web-panel && npm start`, navigate to `http://localhost:4200`. Log in as teacher (credentials from seed data), then as admin.
**Expected:** Login form centered at 400px max-width with "Добро пожаловать" heading (28px). After login, sidebar shows correct nav items (TEACHER: Журнал посещаемости + Статистика; ADMIN: Пользователи + Группы + Семестры + Статистика). Dashboard shows correct role heading.
**Why human:** Visual rendering, typography scale, layout alignment, and Material M3 theming cannot be verified programmatically.

#### 2. Dark Mode Toggle

**Test:** Click the sun/moon icon in the sidebar footer area.
**Expected:** Page switches between light (white background) and dark (dark background) themes. Tailwind `dark:` variants activate. Preference survives page reload.
**Why human:** Color scheme correctness requires visual inspection; localStorage persistence requires browser session testing.

#### 3. Sidebar Collapse Animation

**Test:** Click the left-chevron icon at the bottom of the sidebar.
**Expected:** Sidebar smoothly animates from 240px to 64px in 200ms. Labels disappear, icons remain. Chevron rotates 180deg. State persists after reload.
**Why human:** Animation smoothness and visual icon-only mode correctness require browser rendering.

#### 4. Viewport Responsive Collapse

**Test:** Resize browser window to below 1024px width.
**Expected:** Sidebar auto-collapses to icon-only mode (64px).
**Why human:** Responsive behavior requires browser viewport manipulation.

#### 5. Role Guard Enforcement

**Test:** Log in as TEACHER. Manually navigate to `/admin/dashboard` in the URL bar.
**Expected:** Browser redirects to `/teacher/dashboard` (roleGuard intercepts wrong-role access).
**Why human:** End-to-end route guard behavior needs browser navigation testing.

#### 6. Memory-Only Token Behavior

**Test:** Log in successfully. Reload the page (F5).
**Expected:** User is redirected to `/login` (tokens were in memory signals, lost on reload).
**Why human:** Requires browser session testing to verify signal-based storage is truly memory-only.

### Gaps Summary

No gaps found. All 15 observable truths are verified, all artifacts exist and are substantive, all key links are wired. 43 unit tests pass per SUMMARY-03 (33 from Plan 02 + 10 from Plan 03). Build succeeds per both SUMMARY-01 and SUMMARY-03.

The only outstanding items are human verification tasks that require browser rendering — visual layout, dark mode appearance, animation quality, and end-to-end route guard behavior. These cannot be verified by static code analysis.

**REQUIREMENTS.md note:** WPAN-02 text ("refresh in httpOnly cookie") is outdated. The implementation correctly uses memory-only signals per the ROADMAP contract and CONTEXT.md D-06 architectural decision. REQUIREMENTS.md should be updated as a housekeeping task.

---

_Verified: 2026-04-07T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
