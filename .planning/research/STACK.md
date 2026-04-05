# Stack Research

**Domain:** React PWA mobile client (RutTrack) + Web Push backend extension for university attendance tracking
**Researched:** 2026-04-05
**Confidence:** MEDIUM-HIGH overall. Frontend versions HIGH (npm verified). Java Web Push library MEDIUM (Maven Central verified, BouncyCastle integration has known complexity — see Pitfall note).

---

## Context: What Is Already in Place (Do Not Re-research)

| Component | Details |
|-----------|---------|
| Java 21 + Spring Boot 3.4 + Gradle Kotlin DSL | All 5 services + notification-web |
| notification-web (port 9094) | STOMP WebSocket on `/ws` with SockJS, JWT handshake interceptor (`?token=`), RabbitMQ fanout consumer, in-memory broker on `/topic/group/{groupId}` |
| API Gateway routing | `/api/ws/**` → notification-web (StripPrefix=1). No gateway changes needed for new endpoints. |
| Auth Service | JWT RSA. `/api/auth/login`, `/api/auth/refresh`. Access token carries `sub` (user_id), `role`, `group_id`, `is_headman`. |
| MongoDB | `attendance_db` on existing docker-compose MongoDB container. Push subscriptions can reuse this. |
| RabbitMQ | `rut-uit.events` fanout exchange. `notification-web.events` queue already bound. |
| Design decisions | Phosphor Icons + Motion (framer-motion) mandated for all React frontends. Standalone display. Name: RutTrack. |

**New additions below are ONLY for:**
1. React PWA frontend at `frontends/pwa/`
2. Web Push backend extension inside the existing `notification-web` service

---

## Recommended Stack

### Core Technologies — Frontend (frontends/pwa/)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| React | 19.0.x | UI framework | Latest stable (19.0.4, Jan 2026). Consistent with Telegram Mini App (shared component extraction possible). Concurrent rendering for snappy mobile UX. |
| TypeScript | 5.x | Type safety | Required by TanStack Query v5 and Zustand 5. Standard in all monorepo frontends. |
| Vite | 8.x | Build tool, dev server | Current stable (8.0.3). Rolldown-based, 10-30x faster than Vite 6. One caveat: vite-plugin-pwa 1.2.0 peer deps do not formally declare Vite 8 — see Version Compatibility section. |
| Tailwind CSS | 4.1.x | Utility-first CSS | Stable since Jan 2025. First-party `@tailwindcss/vite` plugin — no PostCSS config. Incremental builds 100x faster than v3. Mobile-first defaults match the PWA use case. |

### PWA Layer — Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| vite-plugin-pwa | 1.2.0 | Service Worker generation (via Workbox 7), manifest injection, A2HS support | Always. Automates SW registration, asset precaching, cache strategies, and SW update lifecycle. Zero custom Workbox code for standard use cases. |
| workbox | 7.x (bundled inside vite-plugin-pwa) | Caching: stale-while-revalidate for API, cache-first for static assets, networkOnly for check-in | Do NOT install workbox separately. Configure via the `workbox:` key in VitePWAOptions. |
| virtual:pwa-register/react | (provided by vite-plugin-pwa) | `useRegisterSW` React hook for "New version available" prompt | Use on app mount. Stores update callback; trigger reload when user confirms. |

### State Management & Data Fetching — Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| TanStack Query (React Query) | 5.96.x | Server state: all REST API calls (schedule, attendance stats, homework, user profile) | All data fetched from API Gateway. Handles caching, background refetch, stale-while-revalidate for offline reads. Explicitly chosen in design-decisions.md for sharing with Mini App. |
| Zustand | 5.0.12 | Client state: auth tokens, user role/claims, push subscription status, install prompt state | Auth state (JWT access token, refresh token, decoded claims), UI flags (push permission granted, A2HS prompt shown, iOS onboarding shown). Do NOT put server data here — that is TanStack Query. |
| axios | 1.x | HTTP client | JWT Bearer injection + transparent token refresh on 401 using axios interceptors. Cleaner than native fetch for centralized auth across 20+ API endpoint calls. |

### Real-Time — Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @stomp/stompjs | 7.x | STOMP protocol client: receive real-time lesson/homework events from notification-web | Connect to existing notification-web `/ws` endpoint. Subscribe to `/topic/group/{groupId}` and optionally `/topic/group/{groupId}/headman` for headmen. |
| sockjs-client | 1.6.x | SockJS transport | REQUIRED because notification-web registers the STOMP endpoint with `.withSockJS()`. The server expects the SockJS handshake; raw WebSocket will fail. |

### UI Components — Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @phosphor-icons/react | 2.x | Icons | Mandated by design-decisions.md for all React frontends. Use `bold`/`fill` weight for mobile touch targets. 24px navigation, 20px inline. |
| motion (framer-motion) | 11.x | Screen transitions, gesture feedback, list animations | Mandated by design-decisions.md for React frontends. `AnimatePresence` for route changes. `layout` prop for attendance list reorders. |

### Testing — Frontend

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Vitest | 4.1.x | Unit test runner (Jest-compatible) | Shares vite.config.ts — no separate Jest config. Runs in-process with Vite for fast feedback. |
| @testing-library/react | 16.x | Component interaction testing | Test user-visible behavior (click check-in button, see confirmation), not implementation details. |
| @testing-library/jest-dom | 6.x | Custom DOM matchers | `toBeInDocument`, `toHaveClass`, etc. Import in vitest setup file. |
| jsdom | 25.x | Browser environment simulation | Set `environment: 'jsdom'` in vitest.config.ts. |
| msw (Mock Service Worker) | 2.x | Intercept API calls in tests | Mock API Gateway REST endpoints without real network. Critical for testing offline behavior and error states. |

---

### Backend Additions — notification-web (Java / Spring Boot 3.4)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| nl.martijndwars:web-push | 5.1.2 | VAPID key management, Web Push payload encryption, HTTP push delivery | Canonical Java Web Push library from web-push-libs org. Last release Feb 2025. Java 8+ compatible (Java 21 confirmed). Provides both `PushService` (sync) and `PushAsyncService` (async) — use async for non-blocking delivery. |
| org.bouncycastle:bcprov-jdk18on | 1.78.x | JCE provider required by web-push for EC key operations | web-push depends on BouncyCastle. Use `jdk18on` variant (correct for Java 8+ environments — `jdk15on` is the deprecated artifact). Register in a `@Configuration` class via `Security.addProvider(new BouncyCastleProvider())`. |
| spring-boot-starter-data-mongodb | (managed by Spring Boot 3.4 BOM) | Store push subscriptions (`endpoint`, `p256dh`, `auth`, `user_id`) | Already available in the BOM. Reuse the `attendance_db` MongoDB instance from docker-compose. Add collection `push_subscriptions`. |
| spring-boot-starter-web | (managed by Spring Boot 3.4 BOM) | Expose REST endpoints: GET /vapid-public-key, POST /push/subscribe, DELETE /push/subscribe | notification-web already has spring-boot-starter-websocket which pulls this transitively, but declare it explicitly for clarity. |

---

## Installation

### Frontend (frontends/pwa/)

```bash
# Bootstrap with Vite + React + TypeScript template
npm create vite@latest pwa -- --template react-ts
cd pwa

# PWA
npm install vite-plugin-pwa

# Tailwind CSS v4 (first-party Vite plugin, replaces PostCSS)
npm install tailwindcss @tailwindcss/vite

# Icons + Animation (design-decisions.md mandated)
npm install @phosphor-icons/react motion

# Server state + client state + HTTP
npm install @tanstack/react-query zustand axios

# WebSocket / STOMP (for notification-web integration)
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client

# Dev dependencies (test)
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
```

### Backend additions — notification-web/build.gradle.kts

Add to the existing `dependencies {}` block:

```kotlin
// Web Push
implementation("nl.martijndwars:web-push:5.1.2")
implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

// Push subscription storage
implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
```

---

## Integration Points with Existing notification-web

### New REST endpoints (served at /api/ws/* via Gateway)

| Endpoint | Purpose | Auth |
|----------|---------|------|
| `GET /vapid-public-key` | Return base64url-encoded VAPID public key for browser `PushManager.subscribe()` | Public |
| `POST /push/subscribe` | Save PushSubscription JSON (endpoint + p256dh + auth keys) for authenticated user | JWT required |
| `DELETE /push/subscribe` | Remove push subscription for authenticated user | JWT required |

API Gateway already routes `/api/ws/**` → notification-web with `StripPrefix=1`. No gateway changes needed. Endpoints are reachable at `/api/ws/vapid-public-key`, `/api/ws/push/subscribe`.

### Web Push in EventConsumer.java

The existing `EventConsumer.onEvent()` already handles STOMP delivery. Add a parallel web push delivery call in the same method: when an event arrives via RabbitMQ, fire both STOMP (existing) AND enqueue Web Push to all subscriptions for the group's members. This keeps event fan-out centralized in one consumer.

### VAPID key storage

Store VAPID public/private keys and subject (mailto: or URL) in `application.yml` environment variables:
- `VAPID_PUBLIC_KEY` (base64url)
- `VAPID_PRIVATE_KEY` (base64url)
- `VAPID_SUBJECT` (e.g., `mailto:admin@rutmiit.ru`)

Generate once using the webpush-java CLI or a `@PostConstruct` bean that generates and persists on first run. Do NOT regenerate on every restart — browsers cache subscriptions against the VAPID public key.

### Push subscription MongoDB document

```json
{
  "user_id": 12345,
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "p256dh": "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlTieli...",
  "auth": "tBHItJI5svbpez7KI4CCXg",
  "created_at": "2026-04-05T12:00:00Z",
  "user_agent": "Mozilla/5.0..."
}
```

On HTTP 410 Gone response from the push service, delete the subscription from MongoDB (browser has unsubscribed).

### Service Worker push handler (client side)

The PWA service worker receives push events from the browser's push service and calls `self.registration.showNotification()`. Configure via vite-plugin-pwa's `strategies: 'injectManifest'` mode to inject custom push handler code. The payload from the server is JSON with `title`, `body`, `action_url`, `event_type`.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Build tool | Vite 8 | Next.js 15 | Next.js adds SSR/SSG complexity not needed for a PWA SPA. downstream_consumer explicitly ruled out "full SSR framework." |
| Build tool | Vite 8 | Create React App | Unmaintained since 2023. Webpack-based, dramatically slower than Vite. |
| CSS | Tailwind CSS v4 | styled-components / CSS Modules | Tailwind is faster to iterate for mobile-first. Mini App also uses Tailwind — shared class vocabulary. v4 eliminates PostCSS configuration step. |
| State management | Zustand 5 | Redux Toolkit | Redux is over-engineered for this scale (500-5000 users, solo developer). Zustand has identical API surface to `useState`, zero boilerplate. |
| HTTP client | axios | native fetch | Axios interceptors cleanly handle JWT injection + token refresh retry in one place. Native fetch requires more boilerplate for the same result at 20+ endpoint calls. |
| HTTP client | axios | ky | ky is valid and smaller, but axios has larger ecosystem, more examples, and better TypeScript types for interceptors. |
| Web Push (Java) | nl.martijndwars:web-push | Firebase Cloud Messaging | FCM requires Google dependency, server-to-server key, and separate service setup. VAPID-based Web Push is self-contained. downstream_consumer says "no separate push service — extend notification-web." |
| Web Push (Java) | nl.martijndwars:web-push | com.interaso:webpush (Kotlin) | interaso/webpush is zero-dependency and cleaner API, but it is Kotlin. This project is pure Java. webpush-java 5.1.2 is the established Java standard with more community examples. |
| SW approach | vite-plugin-pwa (Workbox) | Manual service worker | Custom SW requires hand-coding cache invalidation, update lifecycle, precache manifest generation. vite-plugin-pwa handles this correctly and integrates with Vite's content-hashed asset manifest. |
| Real-time | @stomp/stompjs + sockjs-client | Native WebSocket | Server uses `.withSockJS()` — cannot use raw WebSocket. Must use SockJS transport. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Next.js | Full SSR framework. PWA is a SPA. downstream_consumer explicitly excluded it. | Vite + React |
| Create React App | Unmaintained since 2023. Webpack slow. | Vite |
| workbox (standalone npm install) | vite-plugin-pwa bundles Workbox 7. Separate install causes version drift. | Configure via `workbox:` key in VitePWAOptions |
| react-stomp / react-stomp-hooks | Thin wrappers adding a dependency layer. Not needed for a single STOMP connection. | @stomp/stompjs directly |
| Firebase Cloud Messaging | External Google dependency, vendor lock-in, separate service. | nl.martijndwars:web-push + VAPID |
| org.bouncycastle:bcprov-jdk15on | Deprecated artifact since BouncyCastle 1.71. Causes ClassNotFoundException on Java 11+. | org.bouncycastle:bcprov-jdk18on |
| Tailwind CSS v3 | Starting with v3 when v4 is stable is technical debt on day one. v4 eliminates tailwind.config.js and PostCSS. | Tailwind CSS v4 + @tailwindcss/vite |
| Redux / React Context for server state | TanStack Query already handles server state with caching and offline stale data. Doubling up creates inconsistency. | TanStack Query v5 for server data, Zustand for client-only state |

---

## Stack Patterns by Variant

**Offline shell caching strategy:**
- `cache-first` for JS/CSS/image bundles (via vite-plugin-pwa precache manifest — content-hashed, never stale)
- `stale-while-revalidate` for API responses (`/api/schedule/**`, `/api/academic/**`, `/api/attendance/**`) — stale data shows instantly while fresh data loads in background
- `networkOnly` for check-in POST (`POST /api/attendance/checkin`) — fail immediately offline with "No connection" message per design-decisions.md

**STOMP connection lifecycle:**
1. Connect after JWT is available in Zustand store (post-login)
2. Subscribe to `/topic/group/{groupId}` (from JWT claims)
3. If `is_headman=true`, also subscribe to `/topic/group/{groupId}/headman`
4. On JWT refresh, reconnect STOMP client with new token in query string
5. Disconnect on logout

**Web Push subscription flow (client):**
1. After login, check `Notification.permission`
2. If 'default', prompt user (show modal, not immediate browser prompt)
3. On user consent: `Notification.requestPermission()` → `PushManager.subscribe({ userVisibleOnly: true, applicationServerKey: vapidPublicKey })`
4. Send subscription JSON to `POST /api/ws/push/subscribe` with JWT header
5. Service worker push handler: parse JSON payload, call `self.registration.showNotification(title, { body, data: { action_url } })`
6. `notificationclick` handler: `clients.openWindow(event.notification.data.action_url)`

**iOS onboarding (no native push on iOS PWA):**
- Detect iOS Safari without standalone mode: `navigator.userAgent.includes('iPhone') && !window.navigator.standalone`
- Show one-time instruction modal: "Safari → Share → Add to Home Screen"
- Store `ios_onboarding_shown` in `localStorage` to show only once

---

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| vite-plugin-pwa@1.2.0 | Vite 7.x (official), Vite 8.x (works, peer dep warning) | GitHub issue #918 confirmed Vite 8 migration works in practice. Use `--legacy-peer-deps` or `overrides` in package.json if npm blocks install. Verify at project initialization. LOW confidence on official support. |
| nl.martijndwars:web-push@5.1.2 | Java 8+, Java 21 | Requires BouncyCastle on classpath. Use `bcprov-jdk18on` (not `jdk15on`). Register in Spring `@Configuration`: `Security.addProvider(new BouncyCastleProvider())`. Known issue: fat-JAR packaging may strip signed BouncyCastle JAR; Spring Boot's executable JAR handles this correctly. |
| @stomp/stompjs@7.x | sockjs-client@1.6.x | Required pair for notification-web's SockJS endpoint. |
| TanStack Query@5.96.x | React@18.x, React@19.x | v5 uses `useSyncExternalStore` internally. React 19 fully supported. |
| Tailwind CSS@4.1.x | Vite@8.x | `@tailwindcss/vite` is the recommended integration. No `tailwind.config.js` or PostCSS config needed. |
| Zustand@5.x | React@19.x | Zustand 5 dropped React 16 support. React 19 fully supported. |
| motion (framer-motion)@11.x | React@19.x | framer-motion 11 supports React 18+. React 19 compatibility confirmed via community reports. |

---

## Sources

- [vite-plugin-pwa GitHub](https://github.com/vite-pwa/vite-plugin-pwa) — versions, Vite 8 issue #918 (MEDIUM confidence on Vite 8 peer dep)
- [vite-plugin-pwa npm](https://www.npmjs.com/package/vite-plugin-pwa) — v1.2.0 confirmed (HIGH)
- [Zustand npm](https://www.npmjs.com/package/zustand) — v5.0.12 confirmed (HIGH)
- [TanStack Query v5 docs](https://tanstack.com/query/v5/docs/framework/react/overview) — v5.96.x confirmed (HIGH)
- [Tailwind CSS v4.0 announcement](https://tailwindcss.com/blog/tailwindcss-v4) — stable Jan 2025, @tailwindcss/vite plugin (HIGH)
- [nl.martijndwars/web-push Maven Central](https://central.sonatype.com/artifact/nl.martijndwars/web-push) — v5.1.2 Feb 2025 (MEDIUM)
- [webpush-java GitHub](https://github.com/web-push-libs/webpush-java) — BouncyCastle dependency, PushAsyncService API (HIGH)
- [Vaadin: Sending web push from Spring Boot](https://vaadin.com/blog/send-web-push-notifications-java) — Spring Boot + webpush-java integration pattern (MEDIUM)
- [stomp-js: Using STOMP with SockJS](https://stomp-js.github.io/guide/stompjs/rx-stomp/using-stomp-with-sockjs.html) — @stomp/stompjs + sockjs-client pairing (HIGH)
- [Vite releases](https://vite.dev/releases) — Vite 8.0.3 current (HIGH)
- [React versions](https://react.dev/versions) — React 19.0.4 current (HIGH)
- [Vitest npm](https://www.npmjs.com/package/vitest) — v4.1.2 current (HIGH)
- existing `services/notification-web/build.gradle.kts` — confirmed existing dependencies (jjwt 0.12.6, Spring Boot websocket/amqp/actuator)
- existing `services/api-gateway/src/main/resources/application.yml` — confirmed `/api/ws/**` routing, no gateway changes needed
- `docs/design-decisions.md` — Phosphor Icons + Motion mandated for React frontends

---
*Stack research for: RutTrack PWA (React + Vite + Tailwind) + Web Push extension in notification-web*
*Researched: 2026-04-05*
