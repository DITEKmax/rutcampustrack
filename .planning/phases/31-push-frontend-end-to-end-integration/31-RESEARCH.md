# Phase 31: Push Frontend + End-to-End Integration - Research

**Researched:** 2026-04-06
**Domain:** Web Push API (browser), Service Worker push/notificationclick events, VAPID subscription lifecycle, iOS standalone guard, soft-ask UX pattern
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PUSHUI-01 | Service Worker handles push event and shows notification with action buttons | SW `push` event handler using `self.registration.showNotification()` with `data.url` for deep-link. Foreground suppression via `clients.matchAll()` focused check. |
| PUSHUI-02 | notificationclick opens PWA on relevant screen (check-in for lesson.started, schedule for cancelled) | `notificationclick` handler reads `event.notification.data.url`, calls `clients.openWindow()` or focuses existing tab. Backend payload includes `event_type` so SW can determine URL. |
| PUSHUI-03 | Push permission requested via soft-ask pattern after demonstrated value (not on first load) | `usePushSubscription` hook + `PushPermissionCard` in Settings/Profile tab. Permission requested only on explicit tap. iOS guard: check `navigator.standalone` before attempting. |
| PUSHUI-04 | Foreground push suppressed when PWA window is focused (dedup with WebSocket) | SW `push` handler checks `clients.matchAll({ type:'window', includeUncontrolled:true })` — if any client `focused === true`, skip `showNotification()`. |
</phase_requirements>

---

## Summary

Phase 31 wires the Web Push frontend into the existing PWA scaffold (Phase 29) and check-in/schedule UI (Phase 30). The backend (Phase 27) already sends notifications with a well-defined JSON payload — this phase is purely a frontend and Service Worker concern.

There are three distinct work tracks:

**Track 1 — Service Worker (`sw.ts`):** Add `push` and `notificationclick` event handlers to the existing Service Worker. The `push` handler parses the backend payload `{ title, body, event_type, data }`, performs foreground suppression (check `clients.matchAll` focused), then calls `showNotification()`. The `notificationclick` handler reads `data.url` from the notification and either focuses an existing tab or opens a new window.

**Track 2 — Subscription API client (`features/push/`):** A new feature module exposing `usePushSubscription` hook (React Query mutation) that fetches the VAPID public key, calls `pushManager.subscribe()`, then POSTs the subscription to `POST /api/push/subscribe`. Includes iOS standalone guard and permission-state read before requesting.

**Track 3 — Settings UI in Profile tab:** Replace the current `ProfilePlaceholder` (which already has logout) with a real `ProfilePage` that includes a `PushPermissionCard` — the soft-ask surface where the user explicitly enables/disables notifications. This is the only place where `Notification.requestPermission()` is ever called.

**Primary recommendation:** Keep all push logic in a dedicated `features/push/` module. The Service Worker receives the full push payload JSON from the backend and derives the deep-link URL from `event_type` directly inside `sw.ts` — no additional message-passing to the main thread is needed.

---

## Project Constraints (from CLAUDE.md)

- React + Vite + vite-plugin-pwa (`injectManifest` strategy) — custom `sw.ts` at `frontends/pwa/src/sw.ts`
- `@phosphor-icons/react` for icons (Bold/Fill weight for mobile)
- Motion (framer-motion v12) for animations
- Tailwind CSS v4
- Shadcn/base-ui components
- Vitest + jsdom + @testing-library/react for unit tests
- `react-router` v7 for navigation

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Web Push API (browser) | Native | `pushManager.subscribe()`, push events in SW | Platform API — no library needed [VERIFIED: MDN] |
| workbox-precaching | 7.4.0 | Already installed; `precacheAndRoute` in sw.ts | Already in project [VERIFIED: package.json] |
| @tanstack/react-query | 5.96.2 | Mutations for subscribe/unsubscribe API calls | Already in project [VERIFIED: package.json] |
| axios | 1.14.0 | HTTP client for VAPID key fetch and subscription POST/DELETE | Already in project [VERIFIED: package.json] |
| vite-plugin-pwa | 1.2.0 | `injectManifest` strategy; `virtual:pwa-register` already used | Already in project [VERIFIED: package.json] |

### No new dependencies required

All necessary libraries are already installed. The Web Push API is a browser native API — no JS library is needed on the frontend side.

**Installation:**
```bash
# No new packages — everything is already in package.json
```

---

## Architecture Patterns

### Recommended Project Structure

```
frontends/pwa/src/
├── features/
│   └── push/                      # NEW in Phase 31
│       ├── api.ts                 # fetchVapidKey, subscribePush, unsubscribePush
│       ├── usePushSubscription.ts # React hook wrapping the subscription lifecycle
│       ├── PushPermissionCard.tsx # UI card shown in Profile/Settings
│       └── __tests__/
│           ├── usePushSubscription.test.ts
│           └── PushPermissionCard.test.tsx
├── features/profile/
│   └── ProfilePage.tsx            # Replaces ProfilePlaceholder — adds PushPermissionCard
└── sw.ts                          # MODIFIED: add push + notificationclick handlers
```

### Pattern 1: Push Payload JSON (from backend)

The backend (`WebPushDeliveryService.java`) sends this exact JSON payload [VERIFIED: 27-03-PLAN.md]:

```json
{
  "title": "Пара началась",
  "body": "Математика — отметьтесь!",
  "event_type": "lesson.started",
  "data": {
    "lesson_id": 123,
    "group_id": 5,
    "start_time": "09:00",
    "room": "А-101"
  }
}
```

For `lesson.cancelled`:
```json
{
  "title": "Пара отменена",
  "body": "Математика — пара отменена",
  "event_type": "lesson.cancelled",
  "data": { "lesson_id": 124, "group_id": 5, "date": "2026-04-07" }
}
```

### Pattern 2: Service Worker push + notificationclick

```typescript
// Source: web.dev/articles/push-notifications-common-notification-patterns
// [VERIFIED via WebFetch from official docs]

/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'

declare const self: ServiceWorkerGlobalScope

precacheAndRoute(self.__WB_MANIFEST)

// Deep-link URL from event_type
function getUrlForEventType(eventType: string): string {
  switch (eventType) {
    case 'lesson.started': return '/checkin'
    case 'lesson.cancelled': return '/schedule'
    default: return '/'
  }
}

self.addEventListener('push', (event) => {
  const payload = event.data?.json() ?? {}
  const { title = 'RutTrack', body = '', event_type = '', data = {} } = payload

  const url = getUrlForEventType(event_type)

  // PUSHUI-04: Foreground suppression — skip notification if PWA is focused
  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      const isFocused = clients.some((c) => (c as WindowClient).focused)
      if (isFocused) return // STOMP WebSocket already handled the event
      return self.registration.showNotification(title, {
        body,
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: { url, event_type, lessonId: data.lesson_id },
        tag: `${event_type}-${data.lesson_id ?? ''}`, // deduplicate
      })
    })

  event.waitUntil(promiseChain)
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const urlToOpen = new URL(
    event.notification.data?.url ?? '/',
    self.location.origin
  ).href

  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      // Focus existing tab if URL matches
      for (const client of clients) {
        if (client.url === urlToOpen) {
          return (client as WindowClient).focus()
        }
      }
      return self.clients.openWindow(urlToOpen)
    })

  event.waitUntil(promiseChain)
})
```

### Pattern 3: Subscription API client

```typescript
// Source: progressive-web-app SKILL.md + MDN Push API [CITED: developer.mozilla.org/en-US/docs/Web/API/Push_API]

// features/push/api.ts
export async function fetchVapidPublicKey(): Promise<string> {
  const res = await axiosInstance.get('/api/push/vapid-public-key')
  return res.data.publicKey  // or res.data — check actual backend response shape
}

export async function subscribePush(subscription: PushSubscription): Promise<void> {
  await axiosInstance.post('/api/push/subscribe', subscription.toJSON())
}

export async function unsubscribePush(endpoint: string): Promise<void> {
  await axiosInstance.delete('/api/push/subscribe', { data: { endpoint } })
}
```

### Pattern 4: usePushSubscription hook (soft-ask pattern)

```typescript
// Source: web.dev/articles/push-notifications-permissions-ux [CITED]
// PUSHUI-03: Only called after explicit user gesture, never on page load

export function usePushSubscription() {
  const [permissionState, setPermissionState] = useState<NotificationPermission>(
    () => ('Notification' in window ? Notification.permission : 'denied')
  )

  const subscribe = useCallback(async () => {
    // iOS guard: Web Push only works in standalone mode on iOS [VERIFIED: WebSearch]
    const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent)
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
      || (navigator as Navigator & { standalone?: boolean }).standalone === true
    if (isIOS && !isStandalone) {
      throw new Error('iOS_NOT_STANDALONE')
    }

    // Request permission (must be in response to user gesture — PUSHUI-03)
    const permission = await Notification.requestPermission()
    setPermissionState(permission)
    if (permission !== 'granted') return

    const registration = await navigator.serviceWorker.ready
    const vapidKey = await fetchVapidPublicKey()
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(vapidKey),
    })
    await subscribePush(subscription)
  }, [])

  const unsubscribe = useCallback(async () => {
    const registration = await navigator.serviceWorker.ready
    const subscription = await registration.pushManager.getSubscription()
    if (!subscription) return
    await unsubscribePush(subscription.endpoint)
    await subscription.unsubscribe()
    setPermissionState('default')
  }, [])

  return { permissionState, subscribe, unsubscribe }
}
```

### Pattern 5: VAPID key urlBase64 conversion

The VAPID public key returned by the backend is base64url-encoded. The `pushManager.subscribe()` API requires a `Uint8Array`. This conversion is a known one-liner that must NOT be hand-rolled from scratch:

```typescript
// [CITED: web.dev/articles/push-notifications-web-push-protocol]
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return new Uint8Array([...rawData].map((char) => char.charCodeAt(0)))
}
```

### Pattern 6: PushPermissionCard UI

The Profile tab is the soft-ask surface. The card shows:
- Current state: not requested / granted / denied
- "Включить уведомления" button → triggers `subscribe()`
- iOS not-standalone message when applicable
- "Отключить уведомления" button when permission is granted

This is the ONLY place `Notification.requestPermission()` is called. It is never called on page load.

### Anti-Patterns to Avoid

- **Calling `requestPermission()` on page load:** Browser UX guidelines and Chrome's quiet permission UI both penalise this. Must be behind explicit user gesture. [CITED: web.dev/articles/push-notifications-permissions-ux]
- **Skipping `event.waitUntil()` in push handler:** Without it, the Service Worker may be killed before `showNotification()` completes. Always wrap async logic in `event.waitUntil()`. [VERIFIED: MDN ServiceWorkerGlobalScope push event]
- **Missing `includeUncontrolled: true` in `clients.matchAll()`:** Without this, newly navigated tabs may not be included in the client list, causing false negatives for foreground suppression. [VERIFIED: WebSearch 2024/2025 best practices]
- **Showing notification for every push regardless of focus:** PUSHUI-04 explicitly requires suppressing when PWA window is focused.
- **Attempting push subscription on iOS without A2HS install:** iOS 16.4+ requires standalone mode. Guard must throw a user-visible error if `isIOS && !isStandalone`. [VERIFIED: WebSearch 2025, STATE.md flag]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| VAPID key base64url decode | Custom decoder | Standard `atob()` + Array manipulation (5-line snippet) | It's a one-liner — but use the exact canonical form from web.dev docs |
| Push subscription storage | Custom IndexedDB layer | Browser's `pushManager` handles subscription persistence natively | The browser owns subscription state |
| Push deduplication | Custom message ID tracking | `tag` field in `showNotification()` options | Browser automatically replaces notifications with same tag |
| Notification icon/badge | Custom canvas rendering | PNG icons already in `/public/icons/` from Phase 29 | Icons exist at `icon-192.png` |

---

## Common Pitfalls

### Pitfall 1: TypeScript `WindowClient` cast in Service Worker

**What goes wrong:** `clients.matchAll()` returns `Client[]`, but the `focused` property is on `WindowClient`, not the base `Client`. TypeScript will error on `.focused`.

**Why it happens:** The Web Workers type lib does not automatically narrow `Client` to `WindowClient`.

**How to avoid:** Cast explicitly: `(client as WindowClient).focused`

**Warning signs:** `Property 'focused' does not exist on type 'Client'` TypeScript error.

### Pitfall 2: `Notification.requestPermission()` callback vs Promise

**What goes wrong:** Old Chrome used callback form `requestPermission(callback)`, modern browsers use Promise form. Mixing them causes silent failures.

**How to avoid:** Always use `await Notification.requestPermission()` (Promise form). The callback form is deprecated. [ASSUMED — based on training knowledge; MDN confirms Promise form is current standard]

### Pitfall 3: `applicationServerKey` must be `Uint8Array`, not string

**What goes wrong:** `pushManager.subscribe({ applicationServerKey: vapidKeyString })` silently fails or throws `InvalidAccessError` in some browsers.

**Why it happens:** The Web Push spec requires the key as a `BufferSource` (Uint8Array), not a string.

**How to avoid:** Always pass through `urlBase64ToUint8Array()` before `pushManager.subscribe()`.

**Warning signs:** `DOMException: Failed to execute 'subscribe' on 'PushManager'`

### Pitfall 4: Service Worker not updated after sw.ts changes in dev

**What goes wrong:** Dev mode with `devOptions: { enabled: true, type: 'module' }` (already configured in vite.config.ts) serves the SW, but browser caches old SW. Push handlers appear not to fire.

**Why it happens:** Service Workers have their own lifecycle; the browser only installs a new SW when the byte content changes.

**How to avoid:** Hard-reload / unregister SW in DevTools Application panel when testing push changes. In production, `registerType: 'autoUpdate'` (already set) handles this.

### Pitfall 5: iOS EU restriction

**What goes wrong:** iOS users in EU countries cannot use Web Push from a standalone PWA (Apple removed standalone PWA support under DMA). Push subscription attempts fail.

**Why it happens:** Apple's response to the EU Digital Markets Act removed standalone mode in the EU. [VERIFIED: WebSearch 2025]

**How to avoid:** The iOS standalone guard already handles this gracefully — if `isIOS && !isStandalone`, the user sees the iOS install prompt (already built in Phase 29 via `IOSOnboardingOverlay`). No crash, just a clear instruction.

### Pitfall 6: `tag` field for notification deduplication

**What goes wrong:** If the user receives multiple `lesson.started` pushes for the same lesson (e.g., retry), they stack up in the notification tray.

**How to avoid:** Set `tag: \`${event_type}-${data.lesson_id}\`` in `showNotification()` options. The browser replaces any existing notification with the same tag.

### Pitfall 7: VAPID public key endpoint authentication

**What goes wrong:** `GET /api/push/vapid-public-key` — per Phase 27 D-16, this endpoint requires `@RequireRole(STUDENT)`, meaning the JWT must be present in the axios interceptor when called.

**Why it matters:** The `usePushSubscription` hook calls this endpoint, so it must be called only when the user is authenticated. Since `PushPermissionCard` is inside `ProtectedRoute`, this is already guaranteed by app structure.

**Verification:** The existing `axiosInstance` already handles auth headers via the interceptor pattern from Phase 29.

---

## Code Examples

### Full sw.ts with push handlers

```typescript
/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'

declare const self: ServiceWorkerGlobalScope

precacheAndRoute(self.__WB_MANIFEST)

function getUrlForEventType(eventType: string): string {
  switch (eventType) {
    case 'lesson.started': return '/checkin'
    case 'lesson.cancelled': return '/schedule'
    default: return '/'
  }
}

self.addEventListener('push', (event) => {
  // Source: web.dev/articles/push-notifications-common-notification-patterns [CITED]
  const payload = event.data?.json() ?? {}
  const { title = 'RutTrack', body = '', event_type = '', data = {} } = payload

  const url = getUrlForEventType(event_type)

  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      const isFocused = clients.some((c) => (c as WindowClient).focused)
      if (isFocused) return
      return self.registration.showNotification(title, {
        body,
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: { url, event_type, lessonId: data.lesson_id },
        tag: `${event_type}-${data.lesson_id ?? Date.now()}`,
      })
    })

  event.waitUntil(promiseChain)
})

self.addEventListener('notificationclick', (event) => {
  // Source: web.dev/articles/push-notifications-common-notification-patterns [CITED]
  event.notification.close()
  const urlToOpen = new URL(
    event.notification.data?.url ?? '/',
    self.location.origin
  ).href

  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      for (const client of clients) {
        if (client.url === urlToOpen) {
          return (client as WindowClient).focus()
        }
      }
      return self.clients.openWindow(urlToOpen)
    })

  event.waitUntil(promiseChain)
})
```

### usePushSubscription hook (abbreviated)

```typescript
// features/push/usePushSubscription.ts
import { useState, useCallback } from 'react'
import { fetchVapidPublicKey, subscribePush, unsubscribePush } from './api'

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return new Uint8Array([...rawData].map((c) => c.charCodeAt(0)))
}

export type PushState = 'unsupported' | 'default' | 'granted' | 'denied'

export function usePushSubscription() {
  const [state, setState] = useState<PushState>(() => {
    if (!('Notification' in window) || !('PushManager' in window)) return 'unsupported'
    return Notification.permission as PushState
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const subscribe = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // iOS standalone guard
      const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent)
      const isStandalone = window.matchMedia('(display-mode: standalone)').matches
        || (navigator as Navigator & { standalone?: boolean }).standalone === true
      if (isIOS && !isStandalone) {
        setError('iOS_NOT_STANDALONE')
        return
      }

      const permission = await Notification.requestPermission()
      setState(permission as PushState)
      if (permission !== 'granted') return

      const reg = await navigator.serviceWorker.ready
      const vapidKey = await fetchVapidPublicKey()
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey),
      })
      await subscribePush(sub)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [])

  const unsubscribe = useCallback(async () => {
    setLoading(true)
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        await unsubscribePush(sub.endpoint)
        await sub.unsubscribe()
      }
      setState('default')
    } finally {
      setLoading(false)
    }
  }, [])

  return { state, loading, error, subscribe, unsubscribe }
}
```

---

## Backend Integration Points

| Endpoint | Method | Phase 27 Decision | Used By |
|----------|--------|-------------------|---------|
| `/api/push/vapid-public-key` | GET | D-15/D-16: JWT required, returns `{ publicKey: string }` | `fetchVapidPublicKey()` in `api.ts` |
| `/api/push/subscribe` | POST | D-15/D-16: body is `PushSubscription.toJSON()` | `subscribePush()` in `api.ts` |
| `/api/push/subscribe` | DELETE | D-15/D-16: body `{ endpoint }` | `unsubscribePush()` in `api.ts` |

The actual response shape of `GET /api/push/vapid-public-key` needs to be confirmed from the Phase 27 implementation. [ASSUMED: field is `publicKey` based on standard VAPID conventions — verify against `PushApi.java` before coding `fetchVapidPublicKey()`]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Show permission prompt on page load | Soft-ask: prompt only after user gesture | ~2020 (Chrome quiet UI) | Chrome 80+ silently blocks prompts that score poorly |
| `requestPermission(callback)` | `await requestPermission()` (Promise) | Chrome 46, Firefox 47 | Callback form deprecated |
| Separate FCM SDK | Native Web Push API with VAPID | ~2016 | No third-party dependency for push |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Backend `GET /api/push/vapid-public-key` returns `{ publicKey: string }` | Backend Integration Points | `fetchVapidPublicKey()` reads wrong field — easy fix once verified |
| A2 | `Notification.requestPermission()` Promise form (no callback) | Pattern 4 | Not an issue in modern browsers; callback form would need wrapper |
| A3 | `DELETE /api/push/subscribe` accepts body `{ endpoint: string }` | Backend Integration Points | axios `delete` with `data:` is non-standard — verify against PushApi.java |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed.

---

## Open Questions

1. **Exact VAPID public key response shape**
   - What we know: Phase 27 implemented `GET /api/push/vapid-public-key`, and the key is injected from env var
   - What's unclear: Response JSON field name (`publicKey`? `vapidPublicKey`? plain string?)
   - Recommendation: Read `notification-service/notification-api-contract` PushApi.java before writing `fetchVapidPublicKey()`. Adjust field name accordingly.

2. **DELETE /api/push/subscribe body shape**
   - What we know: Unsubscribe removes by `userId + endpoint` or just `endpoint`
   - What's unclear: Whether the DELETE body is `{ endpoint }` or whether the endpoint is a query param
   - Recommendation: Read `PushController.java` from Phase 27 to confirm. `axios.delete` with `{ data: { endpoint } }` is the likely pattern.

3. **Profile tab replacement scope**
   - What we know: `ProfilePlaceholder` has logout functionality that must be preserved
   - What's unclear: Whether to fully replace `ProfilePlaceholder.tsx` or extend it
   - Recommendation: Rename `ProfilePlaceholder.tsx` → `ProfilePage.tsx`, add `PushPermissionCard` above the logout section.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Frontend build | Yes | v24.14.0 | — |
| vite-plugin-pwa | injectManifest SW build | Yes | 1.2.0 | — |
| workbox-precaching | sw.ts | Yes | 7.4.0 | — |
| Notification API | Browser permission + showNotification | Yes (Chrome/Firefox/Safari 16.4+) | Native | Guard with `'Notification' in window` check |
| PushManager API | pushManager.subscribe | Yes (Chrome/Firefox/Safari 16.4+ standalone) | Native | Guard with `'PushManager' in window` check |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:**
- iOS < 16.4 or iOS non-standalone: Push not available. Fallback: show IOSOnboardingOverlay instruction (already built in Phase 29).
- EU iOS: Push not available in browser tab. Same fallback.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 3.1.3 + @testing-library/react 16.3.0 |
| Config file | `frontends/pwa/vitest.config.ts` |
| Quick run command | `cd frontends/pwa && npm test` |
| Full suite command | `cd frontends/pwa && npm test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PUSHUI-01 | SW push handler shows notification when client not focused | unit | `cd frontends/pwa && npm test -- __tests__/sw` | No — Wave 0 |
| PUSHUI-02 | notificationclick opens correct URL | unit | `cd frontends/pwa && npm test -- usePushSubscription` | No — Wave 0 |
| PUSHUI-03 | subscribe() calls requestPermission only on gesture; iOS guard fires | unit | `cd frontends/pwa && npm test -- usePushSubscription` | No — Wave 0 |
| PUSHUI-04 | SW skips showNotification when focused client exists | unit | `cd frontends/pwa && npm test -- __tests__/sw` | No — Wave 0 |

**Note on SW testing:** Service Worker code (`sw.ts`) cannot be tested with jsdom-based Vitest as-is — it uses `ServiceWorkerGlobalScope` APIs. The pattern used in similar projects is to extract the pure logic functions (`getUrlForEventType`, the focused-client check) into separate testable utilities, and test those units. The SW event wiring itself is smoke-tested manually in a real browser.

### Wave 0 Gaps

- [ ] `src/features/push/__tests__/usePushSubscription.test.ts` — covers PUSHUI-03 (mocks `Notification`, `navigator.serviceWorker`, `pushManager.subscribe`)
- [ ] `src/features/push/__tests__/PushPermissionCard.test.tsx` — covers PUSHUI-03 UI (renders correct state text, calls subscribe on button click)
- [ ] `src/test/setup.ts` — needs `Notification` and `PushManager` mocks added for push tests

### Setup.ts additions needed

```typescript
// Add to src/test/setup.ts for push tests
Object.defineProperty(global, 'Notification', {
  value: { permission: 'default', requestPermission: vi.fn().mockResolvedValue('granted') },
  configurable: true,
  writable: true,
})

Object.defineProperty(global.navigator, 'serviceWorker', {
  value: {
    ready: Promise.resolve({
      pushManager: {
        subscribe: vi.fn(),
        getSubscription: vi.fn().mockResolvedValue(null),
      },
    }),
  },
  configurable: true,
})
```

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Push subscription POST already requires JWT (handled by Gateway + Phase 27 @RequireRole) |
| V3 Session Management | No | SW operates outside session context |
| V4 Access Control | No | Backend enforces STUDENT-only access |
| V5 Input Validation | Yes | Parse push payload defensively with fallback defaults in SW |
| V6 Cryptography | No | VAPID keys are backend concern (Phase 27) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed push payload crashes SW | Tampering/DoS | Wrap `event.data.json()` in try/catch; use defaults `?? {}` |
| Notification spoofing (crafted push from backend) | Spoofing | Irrelevant at frontend — SW only receives what backend sends over authenticated VAPID channel |
| Permission prompt abuse | Tampering | Soft-ask: requestPermission only on explicit user gesture (PUSHUI-03) |

---

## Sources

### Primary (HIGH confidence)

- [MDN Push API](https://developer.mozilla.org/en-US/docs/Web/API/Push_API) — push event, PushManager.subscribe, notification APIs
- [web.dev common notification patterns](https://web.dev/articles/push-notifications-common-notification-patterns) — foreground suppression and notificationclick deep-link patterns (verified via WebFetch)
- [web.dev permissions UX](https://web.dev/articles/push-notifications-permissions-ux) — soft-ask pattern
- Phase 27 Plan 03 (`27-03-PLAN.md`) — exact push payload JSON format `{ title, body, event_type, data }` [VERIFIED: codebase]
- `frontends/pwa/package.json` — library versions [VERIFIED: codebase]
- `frontends/pwa/src/sw.ts` — existing SW baseline [VERIFIED: codebase]
- `frontends/pwa/vite.config.ts` — injectManifest strategy confirmed [VERIFIED: codebase]

### Secondary (MEDIUM confidence)

- [vite-pwa-org injectManifest guide](https://vite-pwa-org.netlify.app/guide/inject-manifest) — custom SW with push event handler pattern
- [MDN WindowClient.focused](https://developer.mozilla.org/en-US/docs/Web/API/WindowClient/focused) — foreground suppression
- [MDN Clients.matchAll](https://developer.mozilla.org/en-US/docs/Web/API/Clients/matchAll) — includeUncontrolled param

### Tertiary (LOW confidence — from WebSearch, validated by MDN)

- iOS 16.4+ standalone mode requirement — [VERIFIED via WebSearch 2025]
- EU iOS PWA standalone removal (DMA) — [VERIFIED via WebSearch 2025]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all packages verified in package.json, no new dependencies needed
- Architecture: HIGH — exact backend payload format verified from Phase 27 plan, SW patterns from official web.dev docs
- Pitfalls: HIGH — TypeScript SW types, `applicationServerKey` type, foreground suppression all verified from MDN/web.dev

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (Web Push API is stable; vite-plugin-pwa moves fast but 1.2.0 is pinned)
