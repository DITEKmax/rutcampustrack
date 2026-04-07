---
phase: 38-web-panel-scaffold-auth
plan: "03"
subsystem: frontend/web-panel
tags: [angular, layout, sidebar, animations, theme, routing, signals, vitest]
dependency_graph:
  requires: [38-01, 38-02]
  provides: [web-panel-shell, web-panel-sidebar, web-panel-theme, web-panel-routes]
  affects: [38-04]
tech_stack:
  added: []
  patterns:
    - Angular Animations trigger with state/transition for 240px/64px sidebar collapse (200ms ease-in-out)
    - Signal-based collapsed state persisted to localStorage
    - ThemeService with localStorage persistence + prefers-color-scheme OS fallback
    - Phosphor Icons v2 via CSS @import in styles.css (not JS import — esbuild-incompatible)
    - Angular 17+ built-in control flow (@for/@if) instead of NgFor/NgIf directives
    - Lazy loadComponent for all routes (including shell itself)
    - provideNoopAnimations() in Vitest tests (jsdom lacks Web Animations API)
    - Object.defineProperty for window.matchMedia mock in jsdom
key_files:
  created:
    - frontends/web-panel/src/app/core/theme/theme.service.ts
    - frontends/web-panel/src/app/core/theme/theme.service.spec.ts
    - frontends/web-panel/src/app/layout/shell/shell.component.ts
    - frontends/web-panel/src/app/layout/shell/shell.component.html
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts
    - frontends/web-panel/src/app/features/teacher/dashboard/teacher-dashboard.component.ts
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts
    - frontends/web-panel/src/app/shared/empty/empty.component.ts
  modified:
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/styles.css
    - frontends/web-panel/src/main.ts
decisions:
  - "Phosphor icons loaded via CSS @import '@phosphor-icons/web/regular' in styles.css — package exports map has no root '.' entry so JS import fails in esbuild; CSS import resolves correctly"
  - "provideNoopAnimations() in specs — jsdom does not implement element.animate() (Web Animations API); provideAnimations() triggers real WebAnimationsPlayer which throws TypeError"
  - "Object.defineProperty for window.matchMedia in jsdom — vi.spyOn fails with 'matchMedia does not exist' because jsdom never defines the property; Object.defineProperty creates it"
  - "NgFor/NgIf removed from SidebarComponent imports — template uses Angular 17+ @for/@if control flow directives which require no explicit imports"
metrics:
  duration_minutes: 18
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 10
  files_modified: 3
---

# Phase 38 Plan 03: Shell Layout, Sidebar, Theme, Routes Summary

**One-liner:** Angular Animations sidebar (240px/64px, 200ms) with role-filtered nav, localStorage-persisted dark/light ThemeService, fully guarded lazy route config, and stub dashboards — 43 Vitest tests all green, clean build.

## What Was Built

Complete application shell with sidebar navigation, dark mode toggle, and route configuration for the Web Panel admin interface.

### Task 1: ThemeService, ShellComponent, SidebarComponent

**ThemeService** (`theme.service.ts`):
- `signal<boolean>(false)` for `_isDark` with `asReadonly()` public API
- Constructor reads `localStorage.getItem('web-panel.theme')` first; falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches`
- `toggle()` flips signal, writes to localStorage, calls `document.documentElement.classList.toggle('dark', ...)`
- 6 unit tests covering: stored dark, stored light, OS fallback, toggle persistence, class addition, class removal

**ShellComponent** (`shell.component.ts` + `shell.component.html`):
- Standalone component: `flex h-screen overflow-hidden` container
- Left: `<app-sidebar />`, Right: `<main class="flex-1 overflow-y-auto p-8"><router-outlet /></main>`

**SidebarComponent** (`sidebar.component.ts` + `sidebar.component.html`):
- Angular Animations: `trigger('collapse')` with `state('expanded', { width: '240px' })` / `state('collapsed', { width: '64px' })`, `transition('expanded <=> collapsed', animate('200ms ease-in-out'))`
- `trigger('rotateChevron')`: 0deg expanded / 180deg collapsed on the `ph-caret-left` icon
- `collapsed = signal(false)` — restored from `localStorage.getItem('web-panel.sidebar.collapsed')` in `ngOnInit`; auto-collapses when `window.innerWidth < 1024`
- `filteredNavItems = computed()` from `authService.currentUser()` role:
  - TEACHER: Журнал посещаемости (`ph-book-open`), Статистика (`ph-chart-bar`)
  - ADMIN: Пользователи (`ph-users`), Группы (`ph-users-three`), Семестры (`ph-calendar`), Статистика (`ph-chart-bar`)
- Footer: theme toggle (ph-sun/ph-moon), logout (ph-sign-out), collapse chevron
- 4 unit tests: TEACHER nav items, ADMIN nav items, collapse toggle + localStorage, logout call

**Phosphor Icons**: Imported via `@import "@phosphor-icons/web/regular"` in `styles.css`. Icons rendered as `<i class="ph ph-{name}">`.

### Task 2: Route Configuration and Stub Dashboards

**app.routes.ts** — full lazy route tree:
- `/login` → `LoginComponent` (unguarded, public)
- `/` → `ShellComponent` (guarded by `authGuard`)
  - `/teacher` → guarded by `roleGuard(['TEACHER'])`
    - `/teacher/dashboard` → `TeacherDashboardComponent`
    - `/teacher/journal`, `/teacher/stats` → `EmptyComponent`
  - `/admin` → guarded by `roleGuard(['ADMIN'])`
    - `/admin/dashboard` → `AdminDashboardComponent`
    - `/admin/users`, `/admin/groups`, `/admin/semesters`, `/admin/stats` → `EmptyComponent`
  - `''` → redirect to `/login`
- `**` → redirect to `/login`

All components use `loadComponent: () => import(...).then(m => m.ComponentClass)` pattern.

**TeacherDashboardComponent**: heading "Панель преподавателя" + "Выберите раздел в боковом меню."

**AdminDashboardComponent**: heading "Панель администратора" + "Выберите раздел в боковом меню."

**EmptyComponent**: "Раздел в разработке." placeholder for Phase 39/40 feature pages.

## Verification Results

| Check | Result |
|-------|--------|
| `npx vitest run src/app/core/theme/` | PASS — 6/6 tests |
| `npx vitest run src/app/layout/` | PASS — 4/4 tests |
| `npx vitest run` (all) | PASS — 43/43 tests (Plan 02 + Plan 03) |
| `npx ng build --configuration=development` | PASS — Application bundle generation complete (2.1s) |
| theme.service.ts contains `web-panel.theme` | PASS |
| theme.service.ts contains `prefers-color-scheme` | PASS |
| theme.service.ts contains `classList.toggle('dark'` | PASS |
| shell.component.html contains `<app-sidebar` and `<router-outlet` | PASS |
| shell.component.html contains `flex h-screen` | PASS |
| sidebar.component.ts contains `Журнал посещаемости` | PASS |
| sidebar.component.ts contains `Пользователи`, `Группы`, `Семестры` | PASS |
| sidebar.component.ts contains `web-panel.sidebar.collapsed` | PASS |
| sidebar.component.ts contains `240px`, `64px`, `200ms ease-in-out` | PASS |
| sidebar.component.html contains `ph-sign-out`, `ph-caret-left` | PASS |
| app.routes.ts contains `authGuard`, `roleGuard(['TEACHER'])`, `roleGuard(['ADMIN'])` | PASS |
| app.routes.ts contains all required paths | PASS |
| app.routes.ts uses `loadComponent: () => import(` | PASS |
| teacher-dashboard contains `Панель преподавателя`, `Выберите раздел` | PASS |
| admin-dashboard contains `Панель администратора`, `Выберите раздел` | PASS |
| empty.component.ts contains `Раздел в разработке` | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Phosphor icons JS import fails in esbuild bundler**
- **Found during:** Task 2 — Angular build
- **Issue:** `import '@phosphor-icons/web'` in `main.ts` failed with "Application bundle generation failed" because `@phosphor-icons/web@2.1.2` package.json `exports` map has no `.` (root) entry — only `./*` and named weight entries like `./regular`. esbuild resolves via `exports` and throws "No matching export".
- **Fix:** Removed JS import from `main.ts`. Added `@import "@phosphor-icons/web/regular"` to `styles.css`. The `./regular` export resolves to `./src/regular/style.css` which Angular's CSS bundler handles correctly.
- **Files modified:** `frontends/web-panel/src/main.ts`, `frontends/web-panel/src/styles.css`
- **Commit:** 2f0c95b

**2. [Rule 1 - Bug] Web Animations API unavailable in jsdom breaks SidebarComponent tests**
- **Found during:** Task 1 — first Vitest run of sidebar spec
- **Issue:** `provideAnimations()` uses the real `WebAnimationsPlayer` which calls `element.animate()` — a Web Animations API method not implemented in jsdom. Tests threw `TypeError: element.animate is not a function`.
- **Fix:** Replaced `provideAnimations()` with `provideNoopAnimations()` in all sidebar spec test providers.
- **Files modified:** `frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts`
- **Commit:** 03f43f1

**3. [Rule 1 - Bug] `vi.spyOn(window, 'matchMedia')` fails in jsdom**
- **Found during:** Task 1 — theme.service.spec.ts test for OS fallback
- **Issue:** jsdom never defines `window.matchMedia`, so `vi.spyOn` throws "matchMedia does not exist" because it can only spy on existing own properties.
- **Fix:** Replaced `vi.spyOn` with `Object.defineProperty(window, 'matchMedia', { writable: true, configurable: true, value: vi.fn().mockReturnValue({...}) })` to create the property before the test and restore it after.
- **Files modified:** `frontends/web-panel/src/app/core/theme/theme.service.spec.ts`
- **Commit:** 03f43f1

**4. [Rule 1 - Bug] Unused NgFor/NgIf directives in SidebarComponent**
- **Found during:** Task 2 — Angular build (compiler warnings)
- **Issue:** Plan template used `NgFor` and `NgIf` in `imports` array, but the actual template uses Angular 17+ built-in `@for`/`@if` control flow which requires no directive imports.
- **Fix:** Removed `NgFor`, `NgIf` imports from both the TypeScript import statement and the component `imports` array.
- **Files modified:** `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts`
- **Commit:** 2f0c95b

## Known Stubs

- `EmptyComponent` (`shared/empty/empty.component.ts`): "Раздел в разработке." — intentional placeholder for Phase 39 (Teacher journal/stats) and Phase 40 (Admin users/groups/semesters/stats). Routes are wired but feature content not yet implemented.

## Threat Flags

None — this plan introduces no new network endpoints, auth paths, or file access patterns. Route guards are UI-only conveniences; server validates JWT on all API requests.

## Self-Check: PASSED

- `frontends/web-panel/src/app/core/theme/theme.service.ts` — FOUND
- `frontends/web-panel/src/app/core/theme/theme.service.spec.ts` — FOUND
- `frontends/web-panel/src/app/layout/shell/shell.component.ts` — FOUND
- `frontends/web-panel/src/app/layout/shell/shell.component.html` — FOUND
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — FOUND
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.html` — FOUND
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts` — FOUND
- `frontends/web-panel/src/app/features/teacher/dashboard/teacher-dashboard.component.ts` — FOUND
- `frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts` — FOUND
- `frontends/web-panel/src/app/shared/empty/empty.component.ts` — FOUND
- `frontends/web-panel/src/app/app.routes.ts` — FOUND (updated)
- Commit 03f43f1 (Task 1) — FOUND
- Commit 2f0c95b (Task 2) — FOUND
