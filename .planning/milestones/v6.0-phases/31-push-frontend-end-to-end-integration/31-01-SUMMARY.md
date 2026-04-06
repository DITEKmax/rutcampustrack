---
phase: 31-push-frontend-end-to-end-integration
plan: 01
subsystem: pwa-push
tags: [service-worker, web-push, notifications, pwa]
dependency_graph:
  requires: []
  provides: [sw-push-handler, sw-notificationclick-handler, getUrlForEventType, urlBase64ToUint8Array]
  affects: [frontends/pwa/src/sw.ts]
tech_stack:
  added: []
  patterns: [foreground-suppression, notification-dedup-via-tag, defensive-json-parsing]
key_files:
  created:
    - frontends/pwa/src/features/push/pushUtils.ts
    - frontends/pwa/src/features/push/__tests__/pushUtils.test.ts
  modified:
    - frontends/pwa/src/sw.ts
    - frontends/pwa/src/test/setup.ts
decisions:
  - Inline getUrlForEventType in sw.ts (cannot import from pushUtils due to injectManifest build isolation)
  - Duplicate function kept in pushUtils.ts for subscription hook use in Plan 02
metrics:
  duration: 191s
  completed: "2026-04-06T12:38:22Z"
  tasks: 2
  files: 4
---

# Phase 31 Plan 01: SW Push Handlers + Push Utilities Summary

SW push/notificationclick event handlers with foreground suppression and deep-link navigation; reusable push utility functions with 7 unit tests.

## What Was Done

### Task 1: Push utility functions with TDD (RED-GREEN)
- Created `pushUtils.ts` with `getUrlForEventType` (maps event types to PWA routes) and `urlBase64ToUint8Array` (converts VAPID key for PushManager.subscribe)
- 7 unit tests: 5 for getUrlForEventType, 2 for urlBase64ToUint8Array
- Added Notification and PushManager mocks to test setup

### Task 2: Service Worker push handlers
- Added `push` event listener with defensive JSON parsing (T-31-01 mitigation)
- Foreground suppression (PUSHUI-04): checks `clients.matchAll` for focused windows, skips showNotification if app is active
- `notificationclick` handler (PUSHUI-02): closes notification, focuses existing tab or opens new window at the correct route
- Notification deduplication via `tag` field (`event_type-lesson_id`)
- `event.waitUntil()` wraps all async logic (T-31-02 mitigation)

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 (RED) | 4782661 | test(31-01): add failing tests for push utility functions |
| 1 (GREEN) | f9fb378 | feat(31-01): implement push utility functions with tests |
| 2 | 65eb205 | feat(31-01): add push and notificationclick handlers to Service Worker |

## Verification Results

- `npx vitest run` — 38/38 tests pass (7 new push utility tests + 31 existing)
- `npx tsc --noEmit` — 0 TypeScript errors
- sw.ts contains both `push` and `notificationclick` event listeners
- pushUtils.ts exports `getUrlForEventType` and `urlBase64ToUint8Array`

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Threat Mitigations Applied

| Threat | Mitigation |
|--------|------------|
| T-31-01 (Tampering) | `event.data?.json()` wrapped in try/catch with fallback defaults |
| T-31-02 (DoS) | `event.waitUntil()` prevents premature SW termination |

## Self-Check: PASSED

All 5 files found. All 3 commits found.
