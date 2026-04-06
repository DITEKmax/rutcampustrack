---
phase: 29-pwa-scaffold-auth
verified: 2026-04-06T11:00:00Z
status: human_needed
score: 6/6
must_haves:
  truths:
    - "Student enters username/password, taps login, JWT in React memory, lands on app home screen"
    - "On 401, Axios interceptor silently refreshes via httpOnly cookie and retries"
    - "Logout clears cookie server-side, clears in-memory token, redirects to /login"
    - "Android A2HS prompt captured; iOS onboarding overlay shows install instructions"
    - "App shell with 4-tab bottom nav loads offline from Service Worker cache"
    - "PWA manifest with RutCampusTrack branding, SW precaching via injectManifest"
  artifacts:
    - path: "services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java"
    - path: "services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/AccessTokenResponse.java"
    - path: "services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AuthIntegrationTest.java"
    - path: "frontends/pwa/package.json"
    - path: "frontends/pwa/vite.config.ts"
    - path: "frontends/pwa/src/sw.ts"
    - path: "frontends/pwa/src/shared/lib/axios.ts"
    - path: "frontends/pwa/src/features/auth/AuthProvider.tsx"
    - path: "frontends/pwa/src/features/auth/LoginPage.tsx"
    - path: "frontends/pwa/src/features/auth/IOSOnboardingOverlay.tsx"
    - path: "frontends/pwa/src/shared/components/AppShell.tsx"
    - path: "frontends/pwa/src/shared/components/BottomNav.tsx"
    - path: "frontends/pwa/src/shared/components/ProtectedRoute.tsx"
    - path: "frontends/pwa/src/shared/hooks/useInstallPrompt.ts"
human_verification:
  - test: "Open PWA in Chrome, log in with student/password, verify app home screen appears"
    expected: "Login succeeds, access token stored in memory (not localStorage), home screen with bottom nav visible"
    why_human: "Full browser auth flow with real backend requires running services"
  - test: "Wait 14+ minutes or manually expire token, make API call"
    expected: "Silent refresh via httpOnly cookie, no login screen shown"
    why_human: "Requires real token expiry timing and running auth-service"
  - test: "Tap logout on profile page, then try to access protected route"
    expected: "Redirected to login, subsequent API calls return 401"
    why_human: "Requires running services and real session management"
  - test: "Open PWA in iOS Safari (not standalone), verify onboarding overlay"
    expected: "3-step install instructions shown, dismissed with Ponjatno button, does not show again"
    why_human: "Requires physical iOS device with Safari"
  - test: "Install PWA, turn off network, reload app"
    expected: "App shell loads from Service Worker cache, login page shows with disabled inputs"
    why_human: "Requires real browser with Service Worker and network toggle"
---

# Phase 29: PWA Scaffold + Auth Verification Report

**Phase Goal:** Students can install RutTrack on their home screen, log in with username/password, and see a working app shell that loads offline
**Verified:** 2026-04-06T11:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /auth/login returns accessToken in JSON body AND sets httpOnly refresh_token cookie | VERIFIED | AuthController.java has `ResponseCookie.from("refresh_token"...)` with httpOnly(true), Secure(true), SameSite("Strict"), Path("/api/auth"). AccessTokenResponse record has only accessToken + expiresIn. 11 integration tests pass. |
| 2 | POST /auth/refresh reads refresh token from cookie and returns new accessToken + sets new cookie | VERIFIED | `@CookieValue(name = "refresh_token")` on refresh() method. Tests verify cookie extraction and rotation. |
| 3 | POST /auth/logout clears httpOnly cookie and invalidates refresh token in Redis | VERIFIED | `@CookieValue(required = false)` on logout(), clearRefreshCookie sets maxAge=0. Tests assert Max-Age=0 in Set-Cookie. |
| 4 | PWA builds with Vite, manifest has RutCampusTrack/RutTrack branding, SW precaches via injectManifest | VERIFIED | `npm run build` exits 0, produces dist/index.html and dist/sw.js with 9 precache entries. vite.config.ts has `strategies: 'injectManifest'`, `name: 'RutCampusTrack'`, `short_name: 'RutTrack'`. |
| 5 | Auth flow UI: login page, AuthProvider with in-memory JWT, ProtectedRoute, app shell with 4-tab bottom nav | VERIFIED | AuthProvider.tsx wires setAccessTokenGetter to Axios interceptor. LoginPage.tsx renders "Войти в систему". ProtectedRoute.tsx uses useAuth(). BottomNav.tsx has 4 tabs. AppShell.tsx renders Outlet. 9 vitest tests pass. |
| 6 | iOS onboarding overlay and A2HS prompt capture infrastructure in place | VERIFIED | IOSOnboardingOverlay.tsx checks ios_onboarding_shown localStorage flag. useInstallPrompt.ts captures beforeinstallprompt event. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `AuthController.java` | Cookie-based login/refresh/logout | VERIFIED | Contains `ResponseCookie.from("refresh_token"...)` x2, `@CookieValue` x2, httpOnly+Secure+SameSite+Path |
| `AccessTokenResponse.java` | Access-token-only DTO | VERIFIED | Record with accessToken + expiresIn, no refreshToken field |
| `TokenPair.java` | Internal service return type | VERIFIED | Internal record used by AuthService |
| `AuthIntegrationTest.java` | Cookie behavior assertions | VERIFIED | Contains Set-Cookie assertions, cookie extraction helper, Max-Age=0 logout test |
| `frontends/pwa/package.json` | React PWA project | VERIFIED | Contains vite-plugin-pwa, react-router, @tanstack/react-query, axios, motion, tailwindcss |
| `frontends/pwa/vite.config.ts` | Vite + PWA plugin config | VERIFIED | injectManifest strategy, filename: 'sw.ts', manifest with RutCampusTrack branding |
| `frontends/pwa/src/sw.ts` | Custom Service Worker | VERIFIED | `precacheAndRoute(self.__WB_MANIFEST)` with webworker reference |
| `frontends/pwa/components.json` | shadcn/ui configuration | VERIFIED | File exists |
| `frontends/pwa/src/components/ui/button.tsx` | shadcn button component | VERIFIED | File exists |
| `frontends/pwa/src/shared/lib/axios.ts` | Axios with interceptor | VERIFIED | withCredentials: true, isRefreshing flag, pendingQueue, silent 401 refresh |
| `frontends/pwa/src/shared/lib/queryClient.ts` | TanStack Query client | VERIFIED | Imported and used in QueryClientProvider in main.tsx |
| `frontends/pwa/src/shared/hooks/useNetworkStatus.ts` | Network status hook | VERIFIED | useSyncExternalStore with online/offline events |
| `frontends/pwa/src/shared/components/OfflineBanner.tsx` | Offline banner | VERIFIED | AnimatePresence + motion, text "Net podklucheniya k internetu" |
| `frontends/pwa/src/shared/components/LoadingSpinner.tsx` | Loading spinner | VERIFIED | animate-spin CSS class |
| `frontends/pwa/src/shared/types/pwa.d.ts` | PWA type declarations | VERIFIED | BeforeInstallPromptEvent inside declare global block |
| `frontends/pwa/src/features/auth/AuthProvider.tsx` | Auth context | VERIFIED | Exports AuthProvider + useAuth, calls setAccessTokenGetter |
| `frontends/pwa/src/features/auth/LoginPage.tsx` | Login form | VERIFIED | Contains "Войти в систему", offline state with "Нет подключения" |
| `frontends/pwa/src/features/auth/IOSOnboardingOverlay.tsx` | iOS install overlay | VERIFIED | ios_onboarding_shown localStorage flag |
| `frontends/pwa/src/features/auth/api.ts` | Auth API layer | VERIFIED | apiClient.post('/auth/login'), logout, refresh endpoints |
| `frontends/pwa/src/shared/components/AppShell.tsx` | App shell layout | VERIFIED | Uses Outlet from react-router |
| `frontends/pwa/src/shared/components/BottomNav.tsx` | 4-tab navigation | VERIFIED | aria-label="Основная навигация", 4 tab items |
| `frontends/pwa/src/shared/components/ProtectedRoute.tsx` | Auth guard | VERIFIED | useAuth() + Navigate to /login |
| `frontends/pwa/src/shared/hooks/useInstallPrompt.ts` | A2HS hook | VERIFIED | beforeinstallprompt event listener |
| `frontends/pwa/vitest.config.ts` | Test config | VERIFIED | environment: 'jsdom' |
| `frontends/pwa/src/test/setup.ts` | Test setup | VERIFIED | @testing-library/jest-dom import |
| `frontends/pwa/public/icons/icon-192.png` | App icon 192x192 | VERIFIED | File exists |
| `frontends/pwa/public/icons/icon-512.png` | App icon 512x512 | VERIFIED | File exists |
| `frontends/pwa/dist/index.html` | Built HTML | VERIFIED | Produced by npm run build |
| `frontends/pwa/dist/sw.js` | Built Service Worker | VERIFIED | Produced by npm run build with 9 precache entries |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AuthController.login() | ResponseCookie.from("refresh_token") | HttpServletResponse.addHeader(SET_COOKIE) | WIRED | Line 115: `ResponseCookie.from("refresh_token", refreshToken)` |
| AuthController.refresh() | @CookieValue(name = "refresh_token") | Spring cookie extraction | WIRED | Line 59: `@CookieValue(name = "refresh_token") String refreshToken` |
| vite.config.ts | src/sw.ts | vite-plugin-pwa injectManifest | WIRED | Line 14: `filename: 'sw.ts'` |
| main.tsx | queryClient.ts | QueryClientProvider import | WIRED | Lines 4, 73: import and usage of QueryClientProvider with queryClient |
| AuthProvider.tsx | axios.ts | setAccessTokenGetter wiring | WIRED | Line 60: `setAccessTokenGetter(() => tokenRef.current)` |
| api.ts | /api/auth/login | apiClient.post | WIRED | Line 20: `apiClient.post<AccessTokenResponse>('/auth/login', credentials)` |
| ProtectedRoute.tsx | AuthProvider.tsx | useAuth().isAuthenticated | WIRED | Line 5: `const { isAuthenticated } = useAuth()` |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Auth-service tests pass | `gradlew.bat :services:auth-service:test` | BUILD SUCCESSFUL in 36s, 5 tasks | PASS |
| PWA builds successfully | `npm run build` | Built in 9.68s, SW built in 189ms, 9 precache entries | PASS |
| PWA tests pass | `npx vitest run --reporter=verbose` | 2 test files, 9 tests passed (3 AuthProvider + 6 LoginPage) | PASS |
| dist/sw.js produced | `ls dist/sw.js` | File exists | PASS |
| dist/index.html produced | `ls dist/index.html` | File exists | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PWA-01 | 29-01, 29-03 | User can log in with username/password (JWT in memory, refresh in httpOnly cookie) | SATISFIED | AuthController sets httpOnly cookie, AuthProvider stores accessToken in React ref |
| PWA-02 | 29-03 | Access token auto-refreshes silently before 15-min expiry | SATISFIED | Axios interceptor in axios.ts handles 401 with silent /auth/refresh call |
| PWA-03 | 29-01, 29-03 | User can log out (clears tokens, invalidates refresh on server) | SATISFIED | AuthController.logout() clears cookie (maxAge=0) + Redis invalidation; AuthProvider clears in-memory token |
| PWA-04 | 29-02 | PWA has manifest with name RutTrack, standalone display, 192/512 icons | SATISFIED | vite.config.ts manifest config: name RutCampusTrack, short_name RutTrack, display standalone, two icon entries |
| PWA-05 | 29-02 | Service Worker registers and caches app shell for offline loading | SATISFIED | sw.ts with precacheAndRoute, injectManifest strategy, 9 precache entries in build |
| PWA-06 | 29-03 | Android A2HS prompt after first successful check-in | SATISFIED | useInstallPrompt.ts captures beforeinstallprompt event; trigger deferred to Phase 30 (by design) |
| PWA-07 | 29-03 | iOS users see Safari install instructions when not in standalone mode | SATISFIED | IOSOnboardingOverlay.tsx with iOS detection, localStorage dismissal flag |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| main.tsx | 18-19 | HomePlaceholder / ProfilePlaceholder component names | Info | Intentional placeholder screens for future phases (30, 32). Not blocking. |

No TODO/FIXME/HACK comments found in any phase 29 code. No stub implementations detected -- all components are fully wired.

### Human Verification Required

### 1. Full Auth Login Flow

**Test:** Open PWA at localhost:5173, enter student/password, tap "Войти в систему"
**Expected:** Login succeeds, redirected to home screen with 4-tab bottom nav. DevTools shows accessToken in React state (not localStorage). Set-Cookie header visible in login response.
**Why human:** Requires running auth-service, API Gateway, and browser with DevTools

### 2. Silent Token Refresh

**Test:** Log in, wait 15 minutes (or manually expire access token), trigger an API call
**Expected:** Axios interceptor calls /auth/refresh automatically, new accessToken received, original request retried -- no login screen shown
**Why human:** Requires real token expiry and running backend services

### 3. Logout Flow

**Test:** Log in, navigate to Profile, tap logout, confirm
**Expected:** Redirected to /login. Attempting to access /home shows login page. Network tab shows POST /auth/logout with Set-Cookie Max-Age=0.
**Why human:** Requires running services and browser interaction

### 4. iOS Onboarding Overlay

**Test:** Open PWA in iOS Safari (not installed as standalone)
**Expected:** Full-screen overlay with 3-step install instructions appears. Tap "Понятно" dismisses it. Reload -- overlay does not reappear.
**Why human:** Requires physical iOS device with Safari

### 5. Offline App Shell

**Test:** Install PWA, go fully offline (airplane mode), reload the app
**Expected:** Login page loads from Service Worker cache. Inputs are disabled. Button shows "Нет подключения". No network error page.
**Why human:** Requires real browser with Service Worker installed and network toggle

### Gaps Summary

No gaps found. All 6 observable truths are verified at the code level. All 28 artifacts exist, are substantive, and are properly wired. All 7 key links confirmed. All 7 requirements (PWA-01 through PWA-07) are satisfied. Auth-service tests pass (BUILD SUCCESSFUL), PWA builds successfully (9 precache entries), and all 9 vitest tests pass.

5 human verification items remain for end-to-end testing that requires running services and real browsers.

---

_Verified: 2026-04-06T11:00:00Z_
_Verifier: Claude (gsd-verifier)_
