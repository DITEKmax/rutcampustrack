---
phase: 36-mini-app-scaffold-auth
plan: 01
subsystem: ui
tags: [react, vite, telegram, tailwind, shadcn, vitest, typescript]

# Dependency graph
requires:
  - phase: 33-infrastructure
    provides: nginx container and CORS configured for Mini App origin
provides:
  - Vite React TypeScript project at frontends/mini-app/ with all dependencies installed
  - Telegram SDK init with mock gate (VITE_TMA_DEV env flag)
  - Dev mock environment via mockTelegramEnv when running outside real Telegram
  - DevModeBanner component for dev mode visibility
  - shadcn components: button, card, separator
  - Vitest test infrastructure with Testing Library and jest-dom
  - Passing build (dist/) and passing tests (2 test cases)
affects: [36-02, 37-mini-app-features]

# Tech tracking
tech-stack:
  added:
    - "@telegram-apps/sdk-react@3.3.9 — official React bindings for Telegram Mini App SDK"
    - "@telegram-apps/sdk@3.11.8 — core Telegram SDK"
    - "@tanstack/react-query@5.96.2 — data fetching"
    - "axios@1.14.0 — HTTP client"
    - "react-router@7.14.0 — client-side routing"
    - "tailwindcss@4.1.4 + @tailwindcss/vite — CSS framework"
    - "shadcn@4.1.2 — component library (base-nova style)"
    - "motion@12.38.0 — animations"
    - "@phosphor-icons/react@2.1.10 — icons"
    - "vitest@3.1.3 + @testing-library/react@16.3.0 + jsdom@26.1.0 — test stack"
  patterns:
    - "Telegram SDK init MUST call setupMockEnv() before init() to prevent mock running after real SDK setup"
    - "miniApp.ready() called in both success and error paths of bootstrap to prevent blank Telegram screen"
    - "VITE_TMA_DEV env flag inlined by Vite at build time — mock code path eliminated in production build"
    - "mockTelegramEnv only called when VITE_TMA_DEV=true AND !(await isTMA('complete')) — dual guard"
    - "vi.stubEnv + vi.resetModules pattern for testing env-conditional components in vitest"

key-files:
  created:
    - frontends/mini-app/package.json
    - frontends/mini-app/vite.config.ts
    - frontends/mini-app/vitest.config.ts
    - frontends/mini-app/tsconfig.json
    - frontends/mini-app/tsconfig.app.json
    - frontends/mini-app/tsconfig.node.json
    - frontends/mini-app/components.json
    - frontends/mini-app/index.html
    - frontends/mini-app/.env.development
    - frontends/mini-app/src/env.d.ts
    - frontends/mini-app/src/vite-env.d.ts
    - frontends/mini-app/src/index.css
    - frontends/mini-app/src/lib/utils.ts
    - frontends/mini-app/src/test/setup.ts
    - frontends/mini-app/src/App.tsx
    - frontends/mini-app/src/main.tsx
    - frontends/mini-app/src/shared/lib/mockWebApp.ts
    - frontends/mini-app/src/shared/components/DevModeBanner.tsx
    - frontends/mini-app/src/shared/components/__tests__/DevModeBanner.test.tsx
    - frontends/mini-app/src/components/ui/button.tsx
    - frontends/mini-app/src/components/ui/card.tsx
    - frontends/mini-app/src/components/ui/separator.tsx
  modified: []

key-decisions:
  - "Port 5174 (not 5173) to avoid collision with PWA dev server"
  - "onEvent handler in mockTelegramEnv receives [eventType, params] tuple — not a simple string arg"
  - "HexColor template literal type needed for mockTheme values to satisfy SDK's strict color type"
  - "Font module declaration in env.d.ts for @fontsource-variable/geist (CSS-only, no TypeScript types)"
  - "mockWebApp.ts and main.tsx created in Task 1 run to enable build verification per Task 1 acceptance"

patterns-established:
  - "Independent project — separate package.json, own types, no monorepo workspace (D-01)"
  - "Same UI stack as PWA — Tailwind 4 + shadcn base-nova + Motion (D-02)"
  - "Mock gate pattern: isTMA check before mockTelegramEnv to avoid double-mocking in real Telegram"

requirements-completed: [TMA-01, TMA-05]

# Metrics
duration: 6min
completed: 2026-04-07
---

# Phase 36 Plan 01: Mini App Scaffold + Auth Summary

**Vite React 19 project for Telegram Mini App with SDK init, VITE_TMA_DEV mock gate, miniApp.ready() call, shadcn components, and Vitest test infrastructure**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-04-06T23:57:24Z
- **Completed:** 2026-04-07T00:03:34Z
- **Tasks:** 2 of 2
- **Files modified:** 22 files created

## Accomplishments

- Scaffolded `frontends/mini-app/` as an independent Vite React TypeScript project matching PWA dependency versions
- Telegram SDK initialized with async bootstrap: `setupMockEnv()` → `init()` → `viewport.mount/expand()` → `miniApp.ready()` preventing blank screen in Telegram (TMA-01)
- Dev mock environment: `mockTelegramEnv` with dual guard (VITE_TMA_DEV=true AND not in real TMA) — Vite inlines env at build time so mock code path is eliminated in production (T-36-04 mitigation)
- shadcn components (button, card, separator) installed via base-nova preset with neutral palette
- 2 passing Vitest tests for DevModeBanner using `vi.stubEnv` / `vi.resetModules` pattern

## Task Commits

1. **Task 1: Scaffold Vite project, install dependencies, configure build tooling** - `d01ba9c` (feat)
2. **Task 2: SDK initialization, mock environment, DevModeBanner, and Wave 0 tests** - `0b412a7` (feat)

## Files Created/Modified

- `frontends/mini-app/package.json` — Project manifest with all dependencies including @telegram-apps/sdk-react
- `frontends/mini-app/vite.config.ts` — react() + tailwindcss(), port 5174, /api proxy, no VitePWA
- `frontends/mini-app/vitest.config.ts` — jsdom environment, globals, setup.ts
- `frontends/mini-app/tsconfig.{json,app.json,node.json}` — TypeScript config with @/* path alias
- `frontends/mini-app/components.json` — shadcn base-nova style, neutral palette, cssVariables
- `frontends/mini-app/index.html` — viewport-fit=cover for safe-area-inset support
- `frontends/mini-app/.env.development` — VITE_TMA_DEV=true, VITE_TMA_MOCK_USER=student
- `frontends/mini-app/src/env.d.ts` — ImportMetaEnv types + @fontsource-variable/geist module declaration
- `frontends/mini-app/src/index.css` — Tailwind 4 + shadcn CSS vars + dark mode (copied from PWA)
- `frontends/mini-app/src/lib/utils.ts` — cn() utility with clsx + tailwind-merge
- `frontends/mini-app/src/test/setup.ts` — jest-dom + @telegram-apps/sdk-react mock
- `frontends/mini-app/src/main.tsx` — Async bootstrap: setupMockEnv → init → viewport → miniApp.ready
- `frontends/mini-app/src/shared/lib/mockWebApp.ts` — setupMockEnv() with dual guard and onEvent handler
- `frontends/mini-app/src/shared/components/DevModeBanner.tsx` — Yellow dev banner on VITE_TMA_DEV flag
- `frontends/mini-app/src/shared/components/__tests__/DevModeBanner.test.tsx` — 2 passing tests
- `frontends/mini-app/src/components/ui/{button,card,separator}.tsx` — shadcn components

## Decisions Made

- Port 5174 for dev server to avoid collision with PWA (port 5173)
- `onEvent` handler in `mockTelegramEnv` receives `[eventType, params]` tuple (SDK type discovery)
- Mock theme colors typed as `Record<string, HexColor>` to satisfy SDK's `#${string}` template literal constraint
- `@fontsource-variable/geist` declared as module in `env.d.ts` (CSS-only package, no TS types)
- main.tsx and mockWebApp.ts created in Task 1 run because build verification requires main.tsx to exist

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TypeScript errors in mockWebApp.ts: HexColor type and onEvent tuple destructuring**
- **Found during:** Task 2 (after Task 1 build attempt)
- **Issue:** SDK's `emitEvent` requires `#${string}` template literal type for theme colors; `onEvent` receives `[eventType, params]` tuple not a string
- **Fix:** Added `HexColor = \`#${string}\`` type, destructured `([eventType], _next)` in handler
- **Files modified:** `src/shared/lib/mockWebApp.ts`
- **Verification:** `npm run build` succeeds with zero TypeScript errors
- **Committed in:** `0b412a7` (Task 2 commit)

**2. [Rule 3 - Blocking] Added @fontsource-variable/geist module declaration to env.d.ts**
- **Found during:** Task 1 build verification
- **Issue:** `@fontsource-variable/geist` is CSS-only and has no TypeScript declarations — `tsc -b` failed with TS2307
- **Fix:** Added `declare module '@fontsource-variable/geist'` to `src/env.d.ts`
- **Files modified:** `src/env.d.ts`
- **Verification:** TypeScript compilation succeeds
- **Committed in:** `d01ba9c` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 Bug fix, 1 Blocking fix)
**Impact on plan:** Both auto-fixes necessary for TypeScript compilation. No scope creep.

## Issues Encountered

- `git reset --soft` to the correct base commit left working directory at old HEAD — restored plan files and other affected files via `git checkout HEAD -- <paths>` before staging Task 1 files

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Plan 02 (auth flow + app wiring) can build directly on this scaffold
- All dependencies installed and verified in dist/
- Mock environment works for local development without real Telegram
- Test infrastructure ready (Vitest + Testing Library + jest-dom + SDK mock)

## Known Stubs

- `src/App.tsx` — minimal placeholder (returns static text). Plan 02 will replace with router + AuthProvider + real app wiring.

---
*Phase: 36-mini-app-scaffold-auth*
*Completed: 2026-04-07*

## Self-Check: PASSED

- FOUND: frontends/mini-app/package.json
- FOUND: frontends/mini-app/src/main.tsx
- FOUND: frontends/mini-app/src/shared/lib/mockWebApp.ts
- FOUND: frontends/mini-app/src/shared/components/DevModeBanner.tsx
- FOUND: frontends/mini-app/node_modules/
- FOUND: frontends/mini-app/dist/
- FOUND: frontends/mini-app/src/components/ui/button.tsx
- FOUND commit: d01ba9c (Task 1)
- FOUND commit: 0b412a7 (Task 2)
