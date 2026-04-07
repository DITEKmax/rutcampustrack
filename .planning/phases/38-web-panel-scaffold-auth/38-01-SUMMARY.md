---
phase: 38-web-panel-scaffold-auth
plan: "01"
subsystem: frontend/web-panel
tags: [angular, tailwind, angular-material, vitest, scaffold]
dependency_graph:
  requires: []
  provides: [web-panel-scaffold, web-panel-build, web-panel-vitest]
  affects: [38-02, 38-03]
tech_stack:
  added:
    - "@angular/core@^19.2.0"
    - "@angular/material@^19.2.19"
    - "@angular/cdk@^19.2.19"
    - "@angular/animations@^19.2.20"
    - "tailwindcss@^4.2.2"
    - "@phosphor-icons/web@^2.1.2"
    - "@fontsource-variable/geist@^5.2.8"
    - "@analogjs/vitest-angular@^2.4.0"
    - "@analogjs/vite-plugin-angular (transitive)"
    - "@testing-library/angular@^17.4.0"
    - "@testing-library/user-event@^14.6.1"
    - "vitest@^3.2.4"
    - "jsdom@^29.0.2"
  patterns:
    - standalone Angular 19 bootstrap with bootstrapApplication()
    - Tailwind v4 CSS @import (no postcss.config)
    - Angular Material M3 CSS variable overrides (no SCSS)
    - Vitest with @analogjs/vite-plugin-angular for TestBed integration
key_files:
  created:
    - frontends/web-panel/angular.json
    - frontends/web-panel/package.json
    - frontends/web-panel/tsconfig.json
    - frontends/web-panel/tsconfig.app.json
    - frontends/web-panel/tsconfig.spec.json
    - frontends/web-panel/vitest.config.ts
    - frontends/web-panel/src/main.ts
    - frontends/web-panel/src/index.html
    - frontends/web-panel/src/styles.css
    - frontends/web-panel/src/test-setup.ts
    - frontends/web-panel/src/app/app.component.ts
    - frontends/web-panel/src/app/app.config.ts
    - frontends/web-panel/src/app/app.routes.ts
  modified:
    - frontends/web-panel/dist/ (replaced placeholder index.html with Angular build output)
decisions:
  - "Used @analogjs/vite-plugin-angular (not /plugin subpath) as Vitest plugin — @analogjs/vitest-angular@2.4.0 has no /plugin export"
  - "outputPath set to dist (not dist/web-panel) to match nginx.conf serving expectations"
  - "Angular Material azure-blue prebuilt theme used as base; brand colors overridden via --mat-sys-* CSS variables"
metrics:
  duration_minutes: 9
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 13
  files_modified: 4
---

# Phase 38 Plan 01: Angular 19 Web Panel Scaffold Summary

**One-liner:** Angular 19 standalone scaffold with Tailwind v4 CSS import, Angular Material M3 CSS variable theme (brand blue #1A56DB), Geist Variable font, and Vitest + @analogjs/vite-plugin-angular test infrastructure.

## What Was Built

Angular 19 project scaffolded from `ng new` in a temp directory, files merged into `frontends/web-panel/` preserving the existing `nginx.conf`. All dependencies installed and configured for Plans 02 and 03 to build auth and layout on top.

### Task 1: Angular 19 Scaffold + Dependencies

- Scaffolded Angular 19 standalone app via `@angular/cli@19.2.23 new web-panel`
- Updated `angular.json` builder from `@angular-devkit/build-angular:application` to `@angular/build:application`, `outputPath` set to `dist`
- `app.config.ts`: `provideRouter`, `provideHttpClient(withInterceptors([]))`, `provideAnimationsAsync`
- `app.component.ts`: minimal standalone component with inline `<router-outlet />` template
- `src/index.html`: `lang="ru"`, title "RutTrack"
- All deps installed: Angular Material 19.2.19, Tailwind 4.2.2, Phosphor Icons, Geist font, Vitest 3.2.4, Testing Library Angular 17.4.0

Build output: `dist/browser/` (Angular build:application behavior), 1.51 MB unoptimized.

### Task 2: Tailwind v4 + Angular Material Theme + Vitest

- `styles.css`: `@import "tailwindcss"` + `@import "@fontsource-variable/geist"` + `@import "@angular/material/prebuilt-themes/azure-blue.css"`
- `@custom-variant dark` for Tailwind class-based dark mode
- `--mat-sys-primary: #1A56DB` (light) / `--mat-sys-primary: #4D8BFF` (dark) and all Geist font overrides
- Status semantic colors: `--color-present`, `--color-absent`, `--color-excused`, `--color-free-attendance`, `--color-pending`
- `vitest.config.ts` with `@analogjs/vite-plugin-angular` plugin, jsdom environment
- `test-setup.ts` with `@angular/compiler` and `@testing-library/jest-dom`
- `tsconfig.spec.json` with `vitest/globals` and `@testing-library/jest-dom` types

## Verification Results

| Check | Result |
|-------|--------|
| `ng build --configuration=development` | PASS (1.5s, no errors) |
| `vitest run --passWithNoTests` | PASS (exit 0) |
| `nginx.conf` preserved | PASS |
| No NgModule files in `src/app/` | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] @analogjs/vitest-angular/plugin subpath does not exist**
- **Found during:** Task 2 — first Vitest run
- **Issue:** Plan specified `import angular from '@analogjs/vitest-angular/plugin'` but `@analogjs/vitest-angular@2.4.0` package.json exports only: `.`, `./package.json`, `./setup-zone`, `./setup-snapshots`, `./setup-serializers`, `./snapshot-serializers`, `./setup-testbed` — no `/plugin` export
- **Fix:** Import from `@analogjs/vite-plugin-angular` (the peer dependency that actually provides the Vite plugin), which exports the `angular()` plugin function from its `src/index.js`
- **Files modified:** `frontends/web-panel/vitest.config.ts`
- **Commit:** 93572f0

## Known Stubs

None — Plan 01 is scaffold/configuration only. All wired components are functional (router, HTTP, animations). Plans 02 and 03 will add auth service, guards, interceptor, login page, and shell layout.

## Threat Flags

None — Plan 01 introduces no network endpoints, auth paths, file access patterns, or schema changes. No user input processed.

## Self-Check: PASSED

- `frontends/web-panel/angular.json` — FOUND
- `frontends/web-panel/package.json` — FOUND
- `frontends/web-panel/src/styles.css` — FOUND
- `frontends/web-panel/vitest.config.ts` — FOUND
- `frontends/web-panel/src/test-setup.ts` — FOUND
- `frontends/web-panel/src/app/app.config.ts` — FOUND
- Commit ec5d054 (Task 1) — FOUND
- Commit 93572f0 (Task 2) — FOUND
- Commit 0620ce1 (cleanup) — FOUND
