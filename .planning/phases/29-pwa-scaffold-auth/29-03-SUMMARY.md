---
phase: 29-pwa-scaffold-auth
plan: 03
subsystem: ui
tags: [react, auth, login, pwa, routing, bottom-nav, ios-onboarding, a2hs]
dependency_graph:
  requires:
    - 29-01 (cookie-based-refresh-token, access-token-only-response)
    - 29-02 (axios-interceptor, shadcn-ui, tanstack-query, vite-pwa-scaffold)
  provides: [auth-provider, login-page, protected-route, app-shell, bottom-nav, ios-onboarding, a2hs-hook]
  affects: [30-pwa-schedule, 31-web-push, 32-geo-checkin]
tech_stack:
  added: [react-router-v7-routing, motion-page-transitions]
  patterns: [in-memory-jwt, jwt-parse-base64, axios-interceptor-wiring, lazy-route-loading, useSyncExternalStore-network]
key_files:
  created:
    - frontends/pwa/src/features/auth/AuthProvider.tsx
    - frontends/pwa/src/features/auth/LoginPage.tsx
    - frontends/pwa/src/features/auth/api.ts
    - frontends/pwa/src/features/auth/IOSOnboardingOverlay.tsx
    - frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx
    - frontends/pwa/src/features/auth/__tests__/LoginPage.test.tsx
    - frontends/pwa/src/features/home/HomePlaceholder.tsx
    - frontends/pwa/src/features/profile/ProfilePlaceholder.tsx
    - frontends/pwa/src/shared/components/AppShell.tsx
    - frontends/pwa/src/shared/components/BottomNav.tsx
    - frontends/pwa/src/shared/components/ProtectedRoute.tsx
    - frontends/pwa/src/shared/hooks/useInstallPrompt.ts
  modified:
    - frontends/pwa/src/main.tsx
    - frontends/pwa/src/App.tsx
    - frontends/pwa/src/vite-env.d.ts
    - frontends/pwa/src/shared/types/pwa.d.ts
decisions:
  - "shadcn components imported from @/components/ui/ (shadcn default path), shared components from @/shared/components/"
  - "BeforeInstallPromptEvent moved inside declare global block for proper TypeScript visibility"
  - "LoginPage uses inline CSS spinner instead of LoadingSpinner component for button loading state"
  - "App.tsx kept as empty stub (not deleted) for backwards compatibility"
metrics:
  duration: ~10 min
  completed: 2026-04-06
  tasks_completed: 2
  tasks_total: 2
  files_changed: 17
  tests: 9
---

# Phase 29 Plan 03: Auth Flow UI + App Shell Summary

**Complete login flow with in-memory JWT, app shell with 4-tab bottom navigation, iOS onboarding overlay, A2HS prompt capture, and page transitions using Motion**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-06T07:43:17Z
- **Completed:** 2026-04-06T07:53:16Z
- **Tasks:** 2/2
- **Files changed:** 17

## Accomplishments

- AuthProvider with in-memory JWT storage wired to Axios interceptor (setAccessTokenGetter, setTokenRefreshCallback, setAuthLogoutCallback)
- LoginPage with Russian copy per UI-SPEC, offline disabled state, error handling (401/network/500)
- ProtectedRoute redirecting unauthenticated users to /login
- AppShell with AnimatePresence page transitions (opacity + y:8px, 150ms easeOut)
- BottomNav with 4 Phosphor icon tabs (House, Calendar, Fingerprint, User) with active indicator dot
- React Router v7 with lazy-loaded routes for /home, /schedule, /checkin, /profile
- IOSOnboardingOverlay with 3-step install instructions, localStorage dismissal flag
- useInstallPrompt hook capturing beforeinstallprompt event for Phase 30 trigger
- ProfilePlaceholder with inline logout confirmation (destructive + ghost buttons)
- Service Worker auto-registration via virtual:pwa-register
- 9 passing tests (3 AuthProvider state, 6 LoginPage rendering + offline)

## Task Commits

1. **Task 1: AuthProvider, auth API, login page, protected route (TDD)** - `140289a` (feat)
2. **Task 2: App shell, bottom nav, routing, iOS onboarding, A2HS hook** - `b210f9e` (feat)

## Files Created/Modified

- `frontends/pwa/src/features/auth/AuthProvider.tsx` - Auth context with in-memory token, JWT parse, Axios wiring
- `frontends/pwa/src/features/auth/LoginPage.tsx` - Login form with offline state, error messages per UI-SPEC
- `frontends/pwa/src/features/auth/api.ts` - Auth API layer (login, logout, refresh)
- `frontends/pwa/src/features/auth/IOSOnboardingOverlay.tsx` - iOS Safari install instruction overlay
- `frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx` - 3 tests for auth state management
- `frontends/pwa/src/features/auth/__tests__/LoginPage.test.tsx` - 6 tests for login page rendering
- `frontends/pwa/src/features/home/HomePlaceholder.tsx` - Empty state placeholder
- `frontends/pwa/src/features/profile/ProfilePlaceholder.tsx` - Profile with logout confirmation
- `frontends/pwa/src/shared/components/AppShell.tsx` - Root layout with bottom nav and page transitions
- `frontends/pwa/src/shared/components/BottomNav.tsx` - 4-tab mobile navigation with Phosphor icons
- `frontends/pwa/src/shared/components/ProtectedRoute.tsx` - Auth guard redirecting to /login
- `frontends/pwa/src/shared/hooks/useInstallPrompt.ts` - A2HS prompt capture hook
- `frontends/pwa/src/main.tsx` - Full routing with createBrowserRouter, SW registration
- `frontends/pwa/src/App.tsx` - Replaced with empty stub (routing in main.tsx)
- `frontends/pwa/src/vite-env.d.ts` - Added vite-plugin-pwa client types
- `frontends/pwa/src/shared/types/pwa.d.ts` - Fixed BeforeInstallPromptEvent global declaration

## Decisions Made

- shadcn components stay at `@/components/ui/` (shadcn default), custom shared components at `@/shared/components/`
- `BeforeInstallPromptEvent` moved inside `declare global` block to fix TypeScript visibility across modules
- LoginPage uses inline CSS spinner in button rather than LoadingSpinner component (keeps button compact)
- `App.tsx` kept as empty export rather than deleted to avoid potential import issues

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] BeforeInstallPromptEvent type not found in useInstallPrompt**
- **Found during:** Task 2 (build verification)
- **Issue:** `pwa.d.ts` declared `BeforeInstallPromptEvent` outside `declare global` block, making it module-scoped and invisible to other files
- **Fix:** Moved interface inside `declare global {}` block
- **Files modified:** `src/shared/types/pwa.d.ts`
- **Commit:** `b210f9e`

**2. [Rule 1 - Bug] Unused LoadingSpinner import in LoginPage**
- **Found during:** Task 2 (build verification, `noUnusedLocals: true`)
- **Issue:** `LoadingSpinner` was imported but the login button uses an inline CSS spinner instead
- **Fix:** Removed the unused import
- **Files modified:** `src/features/auth/LoginPage.tsx`
- **Commit:** `b210f9e`

## Threat Surface Scan

All changes align with the plan's threat model (T-29-09 through T-29-12). No new threat surface introduced.

- T-29-09: JWT payload decoded only for sub/role/groupId; token never stored in localStorage/sessionStorage
- T-29-10: ProtectedRoute checks isAuthenticated client-side (UX guard only; server validates JWT)
- T-29-11: localStorage only stores `ios_onboarding_shown` flag ("1"), no PII
- T-29-12: beforeinstallprompt reference stored in useRef, read-only browser API

## Known Stubs

None -- all components are fully wired to real auth endpoints and Axios interceptor infrastructure.

## Self-Check: PASSED

All 12 key files verified present on disk. Both task commits (140289a, b210f9e) verified in git log.
