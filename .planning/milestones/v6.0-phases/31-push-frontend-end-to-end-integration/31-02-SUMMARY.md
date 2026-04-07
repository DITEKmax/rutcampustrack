---
phase: 31-push-frontend-end-to-end-integration
plan: 02
subsystem: pwa-push
tags: [push, pwa, react, notifications, profile]
dependency_graph:
  requires: [notification-web-push-api, api-gateway]
  provides: [push-subscription-ui, profile-page]
  affects: [pwa-routing, user-experience]
tech_stack:
  added: []
  patterns: [soft-ask-push-pattern, ios-standalone-guard]
key_files:
  created:
    - frontends/pwa/src/features/push/api.ts
    - frontends/pwa/src/features/push/usePushSubscription.ts
    - frontends/pwa/src/features/push/PushPermissionCard.tsx
    - frontends/pwa/src/features/push/__tests__/PushPermissionCard.test.tsx
    - frontends/pwa/src/features/profile/ProfilePage.tsx
  modified:
    - frontends/pwa/src/main.tsx
decisions:
  - urlBase64ToUint8Array inlined in usePushSubscription to avoid cross-plan file dependency
  - ProfilePlaceholder kept as dead code (not deleted) per plan instruction
metrics:
  duration: 4m
  completed: 2026-04-06
  tasks: 2/2
  tests_added: 8
  tests_total: 39
---

# Phase 31 Plan 02: Push Subscription UI Summary

Push subscription soft-ask UI with API client, React hook, PushPermissionCard component, and ProfilePage replacing ProfilePlaceholder with push settings and logout flow.

## What Was Done

### Task 1: Push API client and subscription hook
- Created `api.ts` with three functions mapping to backend contract: `fetchVapidPublicKey` (GET /api/push/vapid-public-key), `subscribePush` (POST /api/push/subscribe), `unsubscribePush` (DELETE /api/push/subscribe)
- Created `usePushSubscription` hook with full lifecycle: iOS standalone guard, Notification.requestPermission on explicit gesture only (PUSHUI-03), VAPID key fetch, pushManager.subscribe, backend registration
- `urlBase64ToUint8Array` inlined to avoid cross-plan Wave 1 file dependency
- Commit: 74bdcbc

### Task 2: PushPermissionCard, ProfilePage, routing
- Created `PushPermissionCard` with state-based rendering: enable button (default), disable button (granted), unsupported message, denied warning, iOS standalone warning
- Uses Phosphor icons (Bell, BellSlash, Warning) with weight="bold" per project convention
- Created `ProfilePage` replacing `ProfilePlaceholder` with PushPermissionCard + preserved logout confirmation flow
- Updated `main.tsx` lazy import and route from ProfilePlaceholder to ProfilePage
- 8 tests covering all PushPermissionCard states (all 39 PWA tests pass)
- Commit: 4189d7c

## Verification Results

1. `npx tsc --noEmit` -- passes (no TypeScript errors)
2. `npx vitest run --reporter=verbose` -- 39 tests pass (7 test files), 8 new PushPermissionCard tests
3. PushPermissionCard shows "Включить уведомления" button (never auto-requests permission)
4. ProfilePage contains both PushPermissionCard and logout functionality
5. main.tsx routes /profile to ProfilePage (no ProfilePlaceholder references remain)

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None - all data flows are wired to real backend endpoints via apiClient.

## Threat Mitigations Applied

- T-31-04 (Tampering): All push API calls use apiClient which attaches JWT Bearer token; backend validates @RequireRole(STUDENT)
- T-31-05 (Spoofing): Accepted - push subscription tied to browser+device, backend validates userId from JWT
- T-31-06 (Repudiation): Accepted - permission state tracked by browser natively

## Self-Check: PASSED

All 5 created files verified on disk. Both commits (74bdcbc, 4189d7c) verified in git log.
