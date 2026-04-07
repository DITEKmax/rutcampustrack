---
phase: 36-mini-app-scaffold-auth
plan: 02
subsystem: ui
tags: [react, telegram, axios, jwt, tanstack-query, react-router, tailwind, shadcn, vitest]

# Dependency graph
requires:
  - phase: 36-01
    provides: Vite React scaffold with Telegram SDK, Tailwind, shadcn, Vitest — all deps installed
  - phase: 34-auth-service-tma
    provides: POST /api/auth/tma endpoint accepting initData, returning accessToken/refreshToken
provides:
  - AuthProvider with initData-based JWT exchange on mount, tokens in React state only
  - Axios apiClient with Bearer token request interceptor and 401 re-auth via initData (not refresh-body)
  - TelegramThemeProvider mapping Telegram themeParams to shadcn CSS variables on <html>
  - LoadingScreen, ErrorScreen UI components per UI-SPEC
  - App.tsx wiring all providers in correct order with react-router BrowserRouter
  - 7 passing AuthProvider unit tests (TMA-02, TMA-03, TMA-04)
  - 9 total passing tests (7 new + 2 from Wave 1)
  - Clean production build (dist/)
affects: [37-mini-app-features]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "bareAxios instance for /api/auth/tma calls — prevents 401 interceptor loop on auth endpoint itself (Pitfall 5)"
    - "initDataRaw cached in useRef for re-auth on 401 — no localStorage, no sessionStorage (D-05)"
    - "setReAuthCallback wired in AuthProvider's useEffect — axios interceptor calls re-auth instead of refresh-body (D-06)"
    - "TelegramThemeProvider uses useSignal(themeParams.state) — returns null in dev mock mode, no-op fallback"
    - "Provider order: QueryClientProvider > TelegramThemeProvider > AuthProvider > BrowserRouter (D-11)"
    - "getInitDataRaw() casts to any to bypass SDK LaunchParams type mismatch — raw string at runtime"

key-files:
  created:
    - frontends/mini-app/src/features/auth/types.ts
    - frontends/mini-app/src/features/auth/api.ts
    - frontends/mini-app/src/features/auth/AuthProvider.tsx
    - frontends/mini-app/src/features/auth/LoadingScreen.tsx
    - frontends/mini-app/src/features/auth/ErrorScreen.tsx
    - frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx
    - frontends/mini-app/src/shared/lib/axios.ts
    - frontends/mini-app/src/shared/lib/queryClient.ts
    - frontends/mini-app/src/shared/providers/TelegramThemeProvider.tsx
  modified:
    - frontends/mini-app/src/App.tsx

key-decisions:
  - "bareAxios used for /api/auth/tma — prevents infinite 401 loop since auth endpoint itself cannot trigger re-auth"
  - "getInitDataRaw() casts retrieveLaunchParams() as any — SDK types LaunchParams.initDataRaw as {} not string, cast needed"
  - "TelegramThemeProvider uses useSignal(themeParams.state) which returns null in dev mock — no-op, falls back to PWA neutral defaults"
  - "apiClient has NO withCredentials — Telegram WebView drops cookies (D-06 enforcement)"

patterns-established:
  - "Memory-only token pattern: useState for accessToken + useRef for sync access in callbacks without closure stale"
  - "Dual-ref auth pattern: tokenRef for interceptor getter, initDataRef for re-auth without triggering effects"

requirements-completed: [TMA-02, TMA-03, TMA-04]

# Metrics
duration: 15min
completed: 2026-04-07
---

# Phase 36 Plan 02: Mini App Auth Flow Summary

**TMA initData-based JWT auth with memory-only tokens, Axios 401 re-auth interceptor, Telegram theme-to-CSS-var mapping, and provider-wrapped App.tsx with react-router routing**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-07T03:09:00Z
- **Completed:** 2026-04-07T03:14:10Z
- **Tasks:** 2 of 2
- **Files modified:** 10 files (9 created, 1 modified)

## Accomplishments

- AuthProvider exchanges initDataRaw for JWT on mount via `POST /api/auth/tma`, tokens stored in React state only (TMA-02, TMA-03, TMA-04)
- Axios apiClient with Bearer token attachment and 401 re-auth via cached initDataRaw (not refresh-body) — D-06 enforced
- TelegramThemeProvider maps Telegram theme colors to shadcn CSS variables on `<html>` element
- LoadingScreen ("Вход через Telegram...") and ErrorScreen ("Не удалось войти") per UI-SPEC
- App.tsx wires QueryClientProvider > TelegramThemeProvider > AuthProvider > BrowserRouter with placeholder HomePage
- 9 passing Vitest tests (7 AuthProvider + 2 DevModeBanner from Wave 1), build succeeds with zero TypeScript errors

## Task Commits

1. **Task 1: Auth types, API call, Axios interceptor, queryClient, AuthProvider** - `e602136` (feat)
2. **Task 2: TelegramThemeProvider, App.tsx wiring** - `e30c4d4` (feat)

## Files Created/Modified

- `frontends/mini-app/src/features/auth/types.ts` — AuthUser and TmaAuthResponse interfaces
- `frontends/mini-app/src/features/auth/api.ts` — tmaAuthApi (bareAxios), getInitDataRaw via SDK
- `frontends/mini-app/src/features/auth/AuthProvider.tsx` — mount-time initData exchange, memory-only tokens, re-auth wiring
- `frontends/mini-app/src/features/auth/LoadingScreen.tsx` — spinner with Framer Motion fade-in
- `frontends/mini-app/src/features/auth/ErrorScreen.tsx` — WarningCircle icon, retry button min-h-[48px]
- `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx` — 7 unit tests for TMA-02/03/04
- `frontends/mini-app/src/shared/lib/axios.ts` — bareAxios + apiClient (no withCredentials), setReAuthCallback
- `frontends/mini-app/src/shared/lib/queryClient.ts` — QueryClient with 5min stale / 10min gc
- `frontends/mini-app/src/shared/providers/TelegramThemeProvider.tsx` — themeParams to CSS var mapping
- `frontends/mini-app/src/App.tsx` — full provider tree + BrowserRouter + HomePage placeholder

## Decisions Made

- Used `bareAxios` (no interceptors) for `/api/auth/tma` calls to prevent 401 infinite loop when auth endpoint itself returns 401
- Cast `retrieveLaunchParams()` as `any` in `getInitDataRaw()` — SDK types the result's `initDataRaw` field as `{}` not `string`, causing TS2322; runtime value is always a string
- `TelegramThemeProvider` uses `useSignal(themeParams.state)` which returns `null` in dev mock mode — effect becomes a no-op, CSS vars stay at PWA neutral defaults from index.css
- `apiClient` has no `withCredentials: true` — Telegram WebView silently drops cookies, enforcing D-06 (re-auth via initData only)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TypeScript TS2322 error in api.ts: initDataRaw type mismatch**
- **Found during:** Task 2 build verification
- **Issue:** `retrieveLaunchParams().initDataRaw ?? ''` — SDK types `initDataRaw` as `{}` (object), not `string | undefined`. TypeScript rejects `{}` being assigned to `string` return type.
- **Fix:** Cast return value of `retrieveLaunchParams()` to `any`, then extract raw string with fallback: `typeof raw === 'string' ? raw : ''`
- **Files modified:** `frontends/mini-app/src/features/auth/api.ts`
- **Verification:** `npm run build` exits 0, zero TypeScript errors
- **Committed in:** `e30c4d4` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 Bug fix)
**Impact on plan:** Essential for TypeScript compilation. Mock in setup.ts returns `{ initDataRaw: 'string' }` which satisfies the mock type — only real SDK type fails at tsc level. No scope creep.

## Issues Encountered

- Worktree had no `node_modules` — ran `npm install` in the worktree's `frontends/mini-app/` directory before testing. Not a deviation, expected setup for parallel agent worktrees.
- Files initially created in main repo path instead of worktree path — corrected by copying to correct path and re-running tests.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 37 (mini-app-features) can import `useAuth()` from `@/features/auth/AuthProvider` for role-based feature rendering
- `apiClient` from `@/shared/lib/axios` ready for TanStack Query hooks
- All routes wired through BrowserRouter — add `<Route>` entries in App.tsx for new pages
- Auth flow works end-to-end when Phase 34 backend is running

## Known Stubs

- `src/App.tsx` — `HomePage` returns placeholder text "Функции появятся в следующей версии". This is intentional per plan: Phase 37 will replace with real feature tabs (schedule, checkin, attendance, homework).

---
*Phase: 36-mini-app-scaffold-auth*
*Completed: 2026-04-07*
