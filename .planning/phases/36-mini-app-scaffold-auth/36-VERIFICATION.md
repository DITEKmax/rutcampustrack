---
phase: 36-mini-app-scaffold-auth
verified: 2026-04-07T00:19:12Z
status: human_needed
score: 5/5 must-haves verified
human_verification:
  - test: "Open the Mini App in Telegram (real device or Telegram Desktop) and verify it renders without blank screen"
    expected: "App renders immediately with LoadingScreen spinner, then HomePage after auth succeeds; no blank white screen at any point"
    why_human: "miniApp.ready() and viewport.mount() behavior can only be confirmed inside a real Telegram WebView — automated tests mock the SDK"
  - test: "With a real backend running (Phase 34), open the Mini App and observe the auth flow"
    expected: "LoadingScreen ('Вход через Telegram...') displays briefly, then HomePage ('Добро пожаловать в RutTrack') renders — confirming POST /api/auth/tma succeeds end-to-end"
    why_human: "End-to-end auth requires a live backend; unit tests mock the API call"
  - test: "Run the app locally with VITE_TMA_DEV=true (npm run dev) and observe the DevModeBanner"
    expected: "Yellow banner at top: 'DEV MODE — mock user: student'; theme CSS vars applied from mock theme params"
    why_human: "Vite env vars are inlined at build time; visual rendering and theme CSS variable mapping require a browser"
---

# Phase 36: Mini App Scaffold + Auth Verification Report

**Phase Goal:** Vite React scaffold with Telegram SDK, viewport setup, initData auth flow, memory-only token management, dev mock environment
**Verified:** 2026-04-07T00:19:12Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria)

| #  | Truth                                                                       | Status     | Evidence                                                                                   |
|----|-----------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------|
| 1  | Mini App opens inside Telegram WebView and renders without blank screen     | ✓ VERIFIED | `main.tsx`: `miniApp.ready()` called in both success path (line 22) and catch path (line 31) — prevents permanent blank screen |
| 2  | initData is extracted and exchanged for JWT via POST /api/auth/tma          | ✓ VERIFIED | `AuthProvider.tsx`: `getInitDataRaw()` called on mount (line 65), result passed to `tmaAuthApi()` (line 67); `api.ts`: `bareAxios.post('/api/auth/tma', { initData: initDataRaw })` |
| 3  | Access token stored in React state, refresh token in memory (not localStorage per D-05) | ✓ VERIFIED | `AuthProvider.tsx`: tokens in `useState` + `useRef` only; zero `localStorage`/`sessionStorage` references in any source file |
| 4  | On 401, re-authenticate via initData (not refresh-body per D-06)            | ✓ VERIFIED | `axios.ts`: `setReAuthCallback` registered, 401 interceptor calls `onReAuth()` with `_retry` guard; `api.ts`: uses `bareAxios` (no interceptor loop); no `/api/auth/refresh` calls anywhere |
| 5  | Dev mock environment allows local development outside Telegram              | ✓ VERIFIED | `mockWebApp.ts`: `setupMockEnv()` with dual guard (`VITE_TMA_DEV=true` AND `!(await isTMA('complete'))`); `.env.development`: `VITE_TMA_DEV=true`, `VITE_TMA_MOCK_USER=student` |

**Score:** 5/5 truths verified

### Additional Must-Haves (from Plan 01 frontmatter)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| A | npm run build produces dist/ without errors | ✓ VERIFIED | `dist/` contains `index.html` + `assets/` with JS bundles (App-*.js, index-*.js, font files) |
| B | Dev mock injects Telegram SDK when VITE_TMA_DEV=true | ✓ VERIFIED | `setupMockEnv()` calls `mockTelegramEnv()` with full launchParams when condition met |
| C | DevModeBanner renders only when VITE_TMA_DEV=true | ✓ VERIFIED | `DevModeBanner.tsx` line 2: `if (import.meta.env.VITE_TMA_DEV !== 'true') return null` |
| D | miniApp.ready() called in main.tsx | ✓ VERIFIED | `main.tsx` line 22 (success) and line 31 (catch) |

### Additional Must-Haves (from Plan 02 frontmatter)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| E | AuthProvider extracts initDataRaw and posts to /api/auth/tma on mount | ✓ VERIFIED | `AuthProvider.tsx` useEffect (line 64-68): `getInitDataRaw()` → `authenticate(raw)` → `tmaAuthApi(initDataRaw)` → `bareAxios.post('/api/auth/tma', ...)` |
| F | Access token in React state only — never in localStorage or sessionStorage | ✓ VERIFIED | No `localStorage`/`sessionStorage` in any source file (grep confirmed zero matches) |
| G | On 401, axios interceptor re-authenticates via initData, NOT refresh-body | ✓ VERIFIED | `axios.ts`: interceptor calls `onReAuth()` (which is `authenticate(initDataRef.current)` wired by AuthProvider); no `/api/auth/refresh` endpoint called |
| H | Telegram theme colors mapped to shadcn CSS variables on html element | ✓ VERIFIED | `TelegramThemeProvider.tsx`: `useSignal(themeParams.state)` → `document.documentElement.style.setProperty(cssVar, value)` for 7 theme mappings |
| I | LoadingScreen shows spinner with 'Вход через Telegram...' | ✓ VERIFIED | `LoadingScreen.tsx`: `animate-spin` spinner + `<p>Вход через Telegram...</p>` with motion fade-in |
| J | ErrorScreen shows retry button that triggers re-auth | ✓ VERIFIED | `ErrorScreen.tsx`: "Не удалось войти" heading + "Попробовать снова" `Button` with `onClick={onRetry}` |
| K | App.tsx wraps content in QueryClientProvider, TelegramThemeProvider, AuthProvider, BrowserRouter | ✓ VERIFIED | `App.tsx`: provider nesting order confirmed — QueryClientProvider > TelegramThemeProvider > AuthProvider > BrowserRouter |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/mini-app/package.json` | Project manifest with all deps | ✓ VERIFIED | Contains `@telegram-apps/sdk-react: ^3.3.9`, all required dependencies, no `vite-plugin-pwa` |
| `frontends/mini-app/vite.config.ts` | Vite build config with Tailwind and path alias | ✓ VERIFIED | `react()`, `tailwindcss()`, port `5174`, `@` alias, `/api` proxy |
| `frontends/mini-app/vitest.config.ts` | Test framework config | ✓ VERIFIED | `jsdom`, `globals: true`, `setupFiles: ['./src/test/setup.ts']`, `css: false` |
| `frontends/mini-app/src/main.tsx` | Entry point with SDK init and mock gate | ✓ VERIFIED | `setupMockEnv()` before `init()`, `miniApp.ready()` in both paths |
| `frontends/mini-app/src/shared/components/DevModeBanner.tsx` | Dev mode indicator banner | ✓ VERIFIED | Contains `VITE_TMA_DEV` check, `bg-yellow-400` styling |
| `frontends/mini-app/src/shared/lib/mockWebApp.ts` | Mock Telegram environment setup | ✓ VERIFIED | Contains `mockTelegramEnv`, `isTMA`, `VITE_TMA_DEV`, `VITE_TMA_MOCK_USER` |
| `frontends/mini-app/src/features/auth/AuthProvider.tsx` | TMA auth context | ✓ VERIFIED | Contains `retrieveLaunchParams` (via `getInitDataRaw`), `tmaAuthApi`, `setAccessTokenGetter`, `setReAuthCallback`, `tokenRef`, `initDataRef`; no localStorage |
| `frontends/mini-app/src/shared/lib/axios.ts` | Axios instance with 401 re-auth interceptor | ✓ VERIFIED | Contains `setReAuthCallback`, `bareAxios`, `_retry` guard; no `withCredentials`, no `/api/auth/refresh` |
| `frontends/mini-app/src/shared/providers/TelegramThemeProvider.tsx` | Telegram theme to CSS variable mapping | ✓ VERIFIED | Contains `themeParams`, `useSignal`, `--background`, `--primary`, `bg_color` mappings |
| `frontends/mini-app/src/App.tsx` | Root component with all providers and routing | ✓ VERIFIED | Contains `QueryClientProvider`, `TelegramThemeProvider`, `DevModeBanner`, `AuthProvider`, `BrowserRouter` |
| `frontends/mini-app/src/features/auth/LoadingScreen.tsx` | Auth loading UI | ✓ VERIFIED | Contains "Вход через Telegram...", `animate-spin`, `border-t-primary`, motion fade-in |
| `frontends/mini-app/src/features/auth/ErrorScreen.tsx` | Auth error UI with retry | ✓ VERIFIED | Contains "Не удалось войти", "Попробовать снова", `WarningCircle`, `min-h-[48px]`, `onRetry` |
| `frontends/mini-app/src/components/ui/button.tsx` | shadcn button component | ✓ VERIFIED | File exists (shadcn installed) |
| `frontends/mini-app/dist/` | Production build output | ✓ VERIFIED | Contains `index.html`, `assets/App-*.js`, `assets/index-*.js`, font woff2 files |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `main.tsx` | `mockWebApp.ts` | `setupMockEnv()` import | ✓ WIRED | `import { setupMockEnv } from '@/shared/lib/mockWebApp'`, awaited before `init()` |
| `main.tsx` | `@telegram-apps/sdk-react` | `init()` call | ✓ WIRED | `import { init, miniApp, viewport } from '@telegram-apps/sdk-react'`; `init()` called line 11 |
| `AuthProvider.tsx` | `/api/auth/tma` | `tmaAuthApi` call on mount | ✓ WIRED | `tmaAuthApi(initDataRaw)` in `authenticate()`, called in `useEffect` on mount; `api.ts` posts to `/api/auth/tma` via `bareAxios` |
| `axios.ts` | `AuthProvider` | `setReAuthCallback` registration | ✓ WIRED | `AuthProvider.tsx` calls `setReAuthCallback(async () => { await authenticate(initDataRef.current) })` in `useEffect` |
| `AuthProvider.tsx` | `@telegram-apps/sdk-react` | `retrieveLaunchParams().initDataRaw` | ✓ WIRED | `api.ts` imports `retrieveLaunchParams`, `getInitDataRaw()` extracts raw string with cast |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `AuthProvider.tsx` | `accessToken`, `user` | `tmaAuthApi(initDataRaw)` → `bareAxios.post('/api/auth/tma', { initData })` | Yes — real HTTP POST to backend | ✓ FLOWING |
| `TelegramThemeProvider.tsx` | `params` | `useSignal(themeParams.state)` from Telegram SDK | Yes — returns null in mock mode (no-op fallback), real theme in Telegram | ✓ FLOWING (null-safe) |
| `DevModeBanner.tsx` | `mockUser` | `import.meta.env.VITE_TMA_MOCK_USER` | Yes — Vite inlines at build time from `.env.development` | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: Tests cannot be run in this environment (no Node.js runtime access to execute `npm test`), but static analysis confirms:

| Behavior | Evidence | Status |
|----------|----------|--------|
| 9 tests pass (7 AuthProvider + 2 DevModeBanner) | Both test files verified: 7 `it()` blocks in AuthProvider.test.tsx, 2 in DevModeBanner.test.tsx; Summary confirms `npm test` exited 0 | ? SKIP (needs runtime) |
| Build produces dist/ | `dist/index.html` + `dist/assets/` with JS bundles confirmed present | ✓ PASS (static) |
| No localStorage usage in auth | Zero matches for `localStorage`/`sessionStorage` in source files (confirmed via grep) | ✓ PASS (static) |
| No withCredentials in apiClient | `axios.ts` line 7: `axios.create({ baseURL: '/api' })` — no `withCredentials` key | ✓ PASS (static) |
| No window.Telegram direct access | Zero matches for `window.Telegram` in source files | ✓ PASS (static) |

### Requirements Coverage

| Requirement | Source Plan | Description (REQUIREMENTS.md) | ROADMAP SC | Implementation Status |
|-------------|------------|-------------------------------|------------|----------------------|
| TMA-01 | 36-01 | Mini App opens in Telegram WebView without blank screen | SC #1: same | ✓ SATISFIED — `miniApp.ready()` in success + error paths of `main.tsx` bootstrap |
| TMA-02 | 36-02 | initData extracted and exchanged for JWT via POST /api/auth/tma | SC #2: same | ✓ SATISFIED — `getInitDataRaw()` + `tmaAuthApi()` in `AuthProvider` mount effect |
| TMA-03 | 36-02 | "Access token in React state, refresh token in localStorage" (REQUIREMENTS.md text) | SC #3: "not localStorage per D-05" | ✓ SATISFIED per ROADMAP SC — memory-only storage (D-05 overrides REQUIREMENTS.md text). NOTE: REQUIREMENTS.md text is stale — it says "localStorage" but ROADMAP SC #3 explicitly says "not localStorage per D-05". Implementation follows ROADMAP SC. |
| TMA-04 | 36-02 | "Token refresh via body-based endpoint (not httpOnly cookie)" (REQUIREMENTS.md text) | SC #4: "not refresh-body per D-06" | ✓ SATISFIED per ROADMAP SC — re-auth via initData on 401 (D-06 overrides REQUIREMENTS.md text). NOTE: REQUIREMENTS.md text says "refresh-body" but ROADMAP SC #4 says "not refresh-body per D-06". Implementation follows ROADMAP SC. |
| TMA-05 | 36-01 | Dev mock environment for local development outside Telegram | SC #5: same | ✓ SATISFIED — `setupMockEnv()` with dual guard, `.env.development` with mock flags |

**Requirements Coverage Note:** TMA-03 and TMA-04 descriptions in REQUIREMENTS.md are inconsistent with the ROADMAP success criteria and CONTEXT.md decisions (D-05, D-06). The ROADMAP success criteria are the authoritative contract — the implementation correctly follows ROADMAP SC #3 and #4, not the stale REQUIREMENTS.md text. The REQUIREMENTS.md should be updated to reflect the actual design decisions.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `DevModeBanner.tsx` | 2 | `return null` | ℹ️ Info | Intentional conditional render — not a stub. Returns null when `VITE_TMA_DEV !== 'true'`. Correct behavior. |
| `App.tsx` (HomePage) | 17-18 | "Функции появятся в следующей версии" placeholder text | ℹ️ Info | Intentional per plan — Phase 37 fills in feature pages. Auth and provider wiring are real. |

No blockers or warnings found.

### Human Verification Required

#### 1. Real Telegram WebView — Blank Screen Test

**Test:** Install the Mini App bot in Telegram (or use BotFather test mode), open the Mini App link, observe initial load
**Expected:** No blank screen — app renders LoadingScreen spinner immediately, then transitions to HomePage after auth exchange completes
**Why human:** `miniApp.ready()` and `viewport.mount()` effects are Telegram-specific signals that cannot be verified outside a real Telegram WebView. Unit tests mock the SDK.

#### 2. End-to-End Auth Flow — Backend Live

**Test:** Start the backend (Phase 34 Auth Service + API Gateway), then run `npm run dev` in `frontends/mini-app/`, navigate to `http://localhost:5174` (with `VITE_TMA_DEV=true`)
**Expected:** LoadingScreen appears briefly, then "Добро пожаловать в RutTrack" renders — confirming the mock initData was accepted by the backend or the backend returns an error that shows ErrorScreen
**Why human:** Requires a live backend; unit tests mock `tmaAuthApi`

#### 3. Dev Mode Visual — Browser

**Test:** Run `npm run dev` with default `.env.development` settings, open `http://localhost:5174` in a browser
**Expected:** Yellow banner at top of page reading "DEV MODE — mock user: student"; page content visible below banner; theme CSS variables set from mock theme params
**Why human:** Visual rendering of CSS, banner positioning, and theme application require a browser

### Gaps Summary

No gaps found. All 5 ROADMAP success criteria are satisfied by the implementation. The phase goal — "Vite React scaffold with Telegram SDK, viewport setup, initData auth flow, memory-only token management, dev mock environment" — is achieved.

**Note on REQUIREMENTS.md inconsistency:** TMA-03 and TMA-04 descriptions in REQUIREMENTS.md are stale and do not match the actual ROADMAP success criteria or the user's locked decisions (D-05, D-06 from CONTEXT.md). This is a documentation gap, not an implementation gap. REQUIREMENTS.md should be updated to replace "refresh token in localStorage" with "refresh token in memory only (D-05)" and "Token refresh via body-based endpoint" with "On 401, re-authenticate via initData (not refresh-body, D-06)".

---

_Verified: 2026-04-07T00:19:12Z_
_Verifier: Claude (gsd-verifier)_
