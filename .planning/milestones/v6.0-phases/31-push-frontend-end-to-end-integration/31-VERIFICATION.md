---
phase: 31-push-frontend-end-to-end-integration
verified: 2026-04-06T16:00:00Z
status: human_needed
score: 4/4 must-haves verified
human_verification:
  - test: "Tap 'Включить уведомления' in Profile tab on a real device; browser should show the native permission prompt"
    expected: "Permission prompt appears only after button tap, never on page load or navigation"
    why_human: "Notification.requestPermission browser prompt cannot be triggered in automated tests"
  - test: "With notifications enabled, trigger a lesson.started RabbitMQ event; check that a push notification appears on the device within 10 seconds"
    expected: "Notification shows lesson title and body; tapping it opens PWA on /checkin screen"
    why_human: "Requires running backend + RabbitMQ + push service end-to-end; SW push events cannot be simulated in unit tests"
  - test: "With PWA open and focused in foreground, trigger a lesson.started event"
    expected: "No push notification appears (foreground suppression); the STOMP WebSocket delivers the in-app update instead"
    why_human: "Foreground detection via clients.matchAll requires a real browser environment with focused WindowClient"
  - test: "Trigger a lesson.cancelled event with push enabled"
    expected: "Push notification appears; tapping it opens /schedule screen"
    why_human: "End-to-end flow from RabbitMQ to device notification requires live infrastructure"
---

# Phase 31: Push Frontend + End-to-End Integration Verification Report

**Phase Goal:** Students receive Web Push notifications on their device for lesson start and lesson cancellation events, and tapping a notification opens the correct screen
**Verified:** 2026-04-06T16:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Student taps "Enable notifications" in settings; browser shows permission prompt only after explicit gesture | VERIFIED (code) | `Notification.requestPermission()` only called inside `subscribe()` callback in `usePushSubscription.ts:36`; `subscribe` is wired to button `onClick` in `PushPermissionCard.tsx:63`; no `requestPermission` calls in `main.tsx`, `App`, or any `useEffect` |
| 2 | lesson.started triggers push notification; tapping opens /checkin | VERIFIED (code) | `sw.ts:17` handles `push` event with `showNotification`; `getUrlForEventType('lesson.started')` returns `/checkin` (sw.ts:10); `notificationclick` handler navigates to `data.url` via `openWindow` (sw.ts:57-76) |
| 3 | lesson.cancelled triggers push notification; tapping opens /schedule | VERIFIED (code) | `getUrlForEventType('lesson.cancelled')` returns `/schedule` (sw.ts:11); same notificationclick handler applies |
| 4 | Foreground suppression: push notification skipped when PWA window is focused | VERIFIED (code) | `sw.ts:41-43` checks `clients.some((c) => (c as WindowClient).focused)`; if `isFocused` is true, `showNotification` is skipped |

**Score:** 4/4 truths verified (code-level)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontends/pwa/src/sw.ts` | push + notificationclick handlers | VERIFIED | 77 lines; contains both event listeners, foreground suppression, defensive JSON parsing, tag dedup |
| `frontends/pwa/src/features/push/pushUtils.ts` | getUrlForEventType + urlBase64ToUint8Array | VERIFIED | 14 lines; exports both functions |
| `frontends/pwa/src/features/push/__tests__/pushUtils.test.ts` | Unit tests for push utilities | VERIFIED | 7 tests covering all event types + base64 conversion |
| `frontends/pwa/src/features/push/api.ts` | Push API client | VERIFIED | 30 lines; fetchVapidPublicKey, subscribePush, unsubscribePush mapped to backend contract |
| `frontends/pwa/src/features/push/usePushSubscription.ts` | React hook for push lifecycle | VERIFIED | 73 lines; iOS guard, requestPermission on gesture only, VAPID fetch, pushManager.subscribe, backend POST |
| `frontends/pwa/src/features/push/PushPermissionCard.tsx` | Soft-ask UI card | VERIFIED | 71 lines; state-based rendering (enable/disable/unsupported/denied/iOS warning) |
| `frontends/pwa/src/features/push/__tests__/PushPermissionCard.test.tsx` | Component tests | VERIFIED | 8 tests covering all UI states |
| `frontends/pwa/src/features/profile/ProfilePage.tsx` | Profile page with push + logout | VERIFIED | 61 lines; contains PushPermissionCard + full logout confirmation flow |
| `frontends/pwa/src/main.tsx` | Routes to ProfilePage | VERIFIED | Line 21: lazy import ProfilePage; line 66-69: route /profile to ProfilePage; no ProfilePlaceholder references |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| sw.ts | showNotification | push event handler | WIRED | sw.ts:44 calls `self.registration.showNotification` |
| sw.ts | openWindow | notificationclick handler | WIRED | sw.ts:72 calls `self.clients.openWindow(urlToOpen)` |
| usePushSubscription.ts | /api/push/subscribe | subscribePush() from api.ts | WIRED | usePushSubscription.ts:46 calls `subscribePush(sub)` |
| usePushSubscription.ts | /api/push/vapid-public-key | fetchVapidPublicKey() from api.ts | WIRED | usePushSubscription.ts:41 calls `fetchVapidPublicKey()` |
| PushPermissionCard.tsx | usePushSubscription | hook call | WIRED | PushPermissionCard.tsx:6 calls `usePushSubscription()` |
| main.tsx | ProfilePage | lazy import | WIRED | main.tsx:21 lazy imports ProfilePage; route at line 66-69 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PushPermissionCard.tsx | state, loading, error | usePushSubscription hook (browser Notification API + pushManager) | Yes (browser native API) | FLOWING |
| ProfilePage.tsx | user | useAuth hook | Yes (JWT auth flow) | FLOWING |
| api.ts | publicKey | GET /api/push/vapid-public-key (backend) | Yes (real VAPID key from backend) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All tests pass | `npx vitest run --reporter=verbose` | 46/46 tests pass (8 test files) | PASS |
| TypeScript compiles | `npx tsc --noEmit` | No errors (exit 0) | PASS |
| pushUtils tested | vitest pushUtils.test.ts | 7/7 pass (getUrlForEventType + urlBase64ToUint8Array) | PASS |
| PushPermissionCard tested | vitest PushPermissionCard.test.tsx | 8/8 pass (all states) | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PUSHUI-01 | 31-01 | SW handles push event and shows notification | SATISFIED | sw.ts:17-53 push handler with showNotification, title, body, icon, badge, tag, data.url |
| PUSHUI-02 | 31-01 | notificationclick opens PWA on relevant screen | SATISFIED | sw.ts:57-76 notificationclick handler; getUrlForEventType maps lesson.started->/checkin, lesson.cancelled->/schedule |
| PUSHUI-03 | 31-02 | Push permission requested only on explicit button tap | SATISFIED | requestPermission only in subscribe() callback (usePushSubscription.ts:36); wired to onClick in PushPermissionCard.tsx:63; no auto-request anywhere |
| PUSHUI-04 | 31-01 | Foreground suppression when PWA focused | SATISFIED | sw.ts:41-43 checks WindowClient.focused, skips showNotification if true |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | - | - | No anti-patterns found |

No TODO/FIXME/PLACEHOLDER markers. No empty implementations. No hardcoded empty data. No stub handlers.

### Human Verification Required

### 1. Push Permission Soft-Ask Flow

**Test:** Open PWA on a mobile device, navigate to Profile tab, tap "Включить уведомления"
**Expected:** Browser shows native permission prompt; no prompt appears on app load or tab navigation
**Why human:** Browser permission prompts require real user gesture in native browser environment

### 2. lesson.started End-to-End Push

**Test:** With push enabled, publish a lesson.started event to RabbitMQ
**Expected:** Push notification appears within 10s with lesson title/body; tapping opens /checkin screen
**Why human:** Requires running backend, RabbitMQ, push service, and a real device with active SW

### 3. lesson.cancelled End-to-End Push

**Test:** With push enabled, publish a lesson.cancelled event to RabbitMQ
**Expected:** Push notification appears; tapping opens /schedule screen
**Why human:** Same end-to-end infrastructure requirement

### 4. Foreground Suppression

**Test:** Keep PWA open and focused, then trigger a push event
**Expected:** No notification appears (STOMP WebSocket handles in-app update instead)
**Why human:** WindowClient.focused detection requires real browser with active focused tab

### Gaps Summary

No code-level gaps found. All 4 requirements (PUSHUI-01 through PUSHUI-04) are satisfied at the implementation level. All 46 tests pass and TypeScript compiles cleanly.

Human verification is required for 4 end-to-end scenarios that involve browser permission prompts, real push delivery, and foreground detection -- these cannot be validated through static analysis or unit tests alone.

---

_Verified: 2026-04-06T16:00:00Z_
_Verifier: Claude (gsd-verifier)_
