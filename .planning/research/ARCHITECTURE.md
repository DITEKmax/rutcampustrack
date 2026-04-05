# Architecture Research

**Domain:** PWA Mobile Client (RutTrack) + Web Push integration with existing Spring Boot microservice architecture
**Researched:** 2026-04-05
**Confidence:** HIGH (based on full source inspection of existing codebase + official docs for all new tech)

---

## Standard Architecture

### System Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          DOCKER PRIVATE NETWORK                             │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                     BROWSER / INSTALLED PWA                          │  │
│  │                                                                      │  │
│  │  React App (Vite + TypeScript)        │  Service Worker (sw.ts)     │  │
│  │  - Auth state (JWT, localStorage)     │  - Precache static shell    │  │
│  │  - Schedule, checkin, stats pages     │  - push event → showNotif   │  │
│  │  - STOMP client (foreground real-time)│  - notificationclick →      │  │
│  │  - useWebPush hook (subscribe on      │    clients.openWindow(url)  │  │
│  │    explicit user action)              │                             │  │
│  └──────────────┬──────────────────────────────┬───────────────────────┘  │
│                 │ HTTPS REST / STOMP WS         │ (browser push channel)   │
│                 │ Authorization: Bearer <JWT>   │ (FCM / Mozilla Push)     │
│                 ▼                              ▼                           │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                      API Gateway  :8080                              │  │
│  │  - globalcors: allowedOriginPatterns=[pwa-origin]                    │  │
│  │  - JWT filter: validates RSA signature, injects X-User-Id etc.       │  │
│  │  - /api/auth/**  → auth-service:9090                                 │  │
│  │  - /api/academic/** → academic-service:9091                          │  │
│  │  - /api/schedule/** → schedule-service:9092                          │  │
│  │  - /api/attendance/** → attendance-service:9093                      │  │
│  │  - /api/push/**  → notification-web:9094  (NEW route)                │  │
│  │  - /api/ws/**    → notification-web:9094  (existing, WS proxy)       │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     │                                       │
│        ┌────────────────────────────┤                                       │
│        │          existing services │                                       │
│   Auth(9090)  Acad(9091)  Sched(9092)  Att(9093)                           │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                  Notification Web  :9094  (EXTENDED)                 │   │
│  │                                                                      │   │
│  │  Existing:                           New (push/ package):            │   │
│  │  EventConsumer (@RabbitListener)  →  PushSubscriptionService         │   │
│  │  STOMP WebSocket /ws              →  PushSubscriptionRepository      │   │
│  │  JwtHandshakeInterceptor          →  PushController                  │   │
│  │                                      VapidConfig (PushService bean)  │   │
│  │  EventConsumer also calls pushService.send() for each event type    │   │
│  └─────────────┬───────────────────────────────────────────────────────┘   │
│                │ RabbitMQ AMQP                                              │
│  ┌─────────────┴────────────────────────────────────────────────────────┐  │
│  │              RabbitMQ  rut-uit.events (fanout exchange)               │  │
│  │  ← lesson.started / lesson.closed / lesson.cancelled                 │  │
│  │  ← attendance.marked / homework.published / excuse.requested         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Storage:                                                                   │
│  MongoDB: attendance_db (existing) + push_subscriptions collection (NEW)   │
│  Redis: existing instance + vapid:public_key, vapid:private_key (NEW keys) │
│                                                                             │
│  NEW container:                                                             │
│  nginx (pwa):  builds frontends/pwa/dist → serves on :80/443              │
│                proxy_pass /api/* → gateway:8080                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Status |
|-----------|----------------|--------|
| notification-web (extended) | Add: Web Push send logic, VAPID key management, push subscription REST API, MongoDB push_subscriptions collection | Extend existing service |
| API Gateway | Add globalcors for PWA origin; add `/api/push/**` route to notification-web; keep existing `/api/ws/**` for STOMP | Modify config only |
| PWA Service Worker (sw.ts) | Handle `push` browser events, show native OS notifications, handle `notificationclick` → open PWA at action_url | New file |
| React PWA (nginx container) | Student UI: schedule, checkin, attendance stats, homework; auth flow; push subscription opt-in | New container |
| MongoDB push_subscriptions | Store endpoint + p256dh + auth keys + userId + groupId + role; deduplication | New collection, existing container |
| Redis (existing) | Store VAPID keypair at `vapid:public_key` and `vapid:private_key` (no TTL, generated once) | New keys, existing instance |

---

## Recommended Project Structure

### notification-web Extension

```
services/notification-web/
└── src/main/java/ru/rutcampustrack/notification/
    ├── config/
    │   ├── WebSocketConfig.java              (existing — unchanged)
    │   ├── JwtHandshakeInterceptor.java      (existing — unchanged)
    │   ├── RabbitConfig.java                 (existing — unchanged)
    │   └── VapidConfig.java                  (NEW — PushService bean, VAPID key bootstrap)
    ├── event/
    │   └── EventConsumer.java               (MODIFY — add pushService calls per event type)
    ├── push/                                (NEW package)
    │   ├── PushSubscription.java            (MongoDB @Document)
    │   ├── PushSubscriptionRepository.java  (MongoRepository)
    │   ├── PushSubscriptionService.java     (subscribe/unsubscribe/sendToGroup)
    │   └── PushController.java              (POST /push/subscribe, DELETE, GET /vapid-public-key)
    └── NotificationWebApplication.java      (existing)
```

### React PWA

```
frontends/pwa/
├── public/
│   └── icons/
│       ├── icon-192.png
│       ├── icon-512.png
│       └── icon-maskable-512.png
├── src/
│   ├── api/
│   │   ├── client.ts               (axios instance, JWT interceptor, 401 refresh)
│   │   ├── auth.ts                 (login, refresh, logout)
│   │   ├── schedule.ts             (getLessons by date range)
│   │   ├── attendance.ts           (checkIn with geo, getReport, getStats)
│   │   └── push.ts                 (getVapidKey, subscribe, unsubscribe)
│   ├── components/
│   │   ├── schedule/               (LessonCard, DayView, WeekView)
│   │   ├── checkin/                (CheckInButton, GeoStatusIndicator)
│   │   ├── attendance/             (AttendanceGrid, StatsCard)
│   │   └── ui/                     (shared primitives: Button, Card, Badge)
│   ├── hooks/
│   │   ├── useAuth.ts              (login/logout, token refresh)
│   │   ├── useWebPush.ts           (permission request, subscribe/unsubscribe)
│   │   └── useWebSocket.ts         (STOMP connection, foreground event handling)
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── SchedulePage.tsx
│   │   ├── CheckInPage.tsx
│   │   ├── AttendancePage.tsx
│   │   └── SettingsPage.tsx        (push notification opt-in toggle)
│   ├── store/
│   │   ├── authStore.ts            (Zustand: userId, role, groupId, isHeadman, token)
│   │   └── offlineStore.ts         (Zustand persist: cached schedule, stats)
│   ├── sw.ts                       (Custom Service Worker: push + notificationclick)
│   ├── App.tsx
│   └── main.tsx
├── vite.config.ts                  (vite-plugin-pwa, injectManifest strategy)
├── manifest.webmanifest
├── nginx.conf                      (SPA fallback, SW no-cache, assets cache)
└── Dockerfile                      (multi-stage: node build → nginx serve)
```

### Structure Rationale

- **push/ package in notification-web:** Groups all Web Push logic without polluting existing `event/` and `config/` packages. The EventConsumer (existing) calls PushSubscriptionService by method call — no network hop since they are in the same JVM.
- **sw.ts as custom Service Worker:** vite-plugin-pwa's `injectManifest` strategy injects the Workbox precache manifest into your own SW file. This is the only way to add a `push` event listener alongside Workbox's caching — the default `generateSW` strategy produces a Workbox-only SW with no push hook points.
- **api/ layer in PWA:** Single source of truth for all HTTP calls. JWT Bearer header attached in one axios interceptor. Keeps pages and hooks free of fetch boilerplate.
- **hooks/ layer:** Encapsulates push subscription state and STOMP lifecycle, keeping pages declarative and testable.
- **SettingsPage for push opt-in:** Push permission requested only on explicit user action, not on first app load — required for Chrome to show the permission prompt rather than auto-deny.

---

## Architectural Patterns

### Pattern 1: Extend notification-web, Do Not Add a New Service

**What:** Add Web Push logic into the existing `notification-web` Spring Boot container rather than creating a new `web-push-service`.

**When to use:** When the push trigger source (RabbitMQ `EventConsumer`) and the push sender live in the same process. A fanout event already lands in `notification-web`, so calling `pushSubscriptionService.sendToGroup(groupId, payload)` from `EventConsumer` requires zero network hops and no new inter-service communication patterns.

**Trade-offs:** `notification-web` gains a MongoDB dependency (acceptable — `docs/architecture.md` already assigns `push_subscriptions` collection to this service). The container stays stateless for WebSocket sessions; MongoDB persistence is only for subscriptions.

**VapidConfig bean (key concept):**
```java
@Configuration
public class VapidConfig {

    @Value("${vapid.subject:mailto:admin@rutmiit.ru}")
    private String subject;

    @Bean
    public PushService pushService(StringRedisTemplate redis)
            throws GeneralSecurityException, IOException {
        Security.addProvider(new BouncyCastleProvider());
        String publicKey  = redis.opsForValue().get("vapid:public_key");
        String privateKey = redis.opsForValue().get("vapid:private_key");
        if (publicKey == null) {
            KeyPair keyPair = generateVapidKeyPair();
            publicKey  = encodeKey(keyPair.getPublic());
            privateKey = encodeKey(keyPair.getPrivate());
            redis.opsForValue().set("vapid:public_key", publicKey);
            redis.opsForValue().set("vapid:private_key", privateKey);
        }
        return new PushService(publicKey, privateKey, subject);
    }
}
```

Keys generated once on first startup, persisted in Redis without TTL. VAPID keys must never change while subscriptions exist — if keys rotate, all push subscriptions become invalid and users must re-subscribe.

### Pattern 2: injectManifest Strategy for Service Worker

**What:** Use vite-plugin-pwa's `injectManifest` strategy to write the Service Worker file yourself (`src/sw.ts`). vite-plugin-pwa only injects the Workbox precache manifest list into your file.

**When to use:** Required whenever the SW needs to handle `push` events. The default `generateSW` strategy auto-generates a Workbox-only SW with no hook points for push event handlers.

**Trade-offs:** You own the SW boilerplate (precacheAndRoute call + push handler + notificationclick handler). Slightly more setup at the start, but complete control.

**vite.config.ts key configuration:**
```typescript
VitePWA({
  strategies: 'injectManifest',
  srcDir: 'src',
  filename: 'sw.ts',
  manifest: {
    name: 'RutTrack',
    short_name: 'RutTrack',
    theme_color: '#1a56db',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
    ]
  }
})
```

**src/sw.ts (key structure):**
```typescript
import { precacheAndRoute } from 'workbox-precaching'

// vite-plugin-pwa injects the manifest here at build time
precacheAndRoute(self.__WB_MANIFEST)

self.addEventListener('push', (event: PushEvent) => {
  const data = event.data?.json() ?? {}
  const title = data.title ?? 'RutTrack'
  const options: NotificationOptions = {
    body: data.body,
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    data: { action_url: data.action_url, event_type: data.event_type },
    actions: data.action_url ? [{ action: 'open', title: 'Открыть' }] : []
  }
  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event: NotificationEvent) => {
  event.notification.close()
  const url = event.notification.data?.action_url ?? '/'
  event.waitUntil(clients.openWindow(url))
})
```

### Pattern 3: Push Subscription Lifecycle in React Hook

**What:** Encapsulate the full browser-side push subscription flow in a single `useWebPush` hook — permission request → subscribe → POST to server → update UI state.

**When to use:** Avoids push logic leaking into page components. The awkward async `Notification.permission` check, `urlBase64ToUint8Array` conversion, and error handling all live in one place.

**src/hooks/useWebPush.ts (key structure):**
```typescript
export function useWebPush() {
  const [subscribed, setSubscribed] = useState(false)

  const subscribe = async () => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return

    const permission = await Notification.requestPermission()
    if (permission !== 'granted') return

    const vapidKey = await api.push.getVapidPublicKey()  // GET /api/push/vapid-public-key
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(vapidKey)
    })
    await api.push.subscribe(sub.toJSON())   // POST /api/push/subscribe
    setSubscribed(true)
  }

  const unsubscribe = async () => {
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.getSubscription()
    if (sub) {
      await api.push.unsubscribe(sub.endpoint)  // DELETE /api/push/subscribe
      await sub.unsubscribe()
    }
    setSubscribed(false)
  }

  return { subscribed, subscribe, unsubscribe }
}
```

### Pattern 4: API Gateway CORS for PWA Origin

**What:** Configure `globalcors` in Spring Cloud Gateway's `application.yml` so the PWA (Vite dev server on `:5173` and production domain) can make credentialed requests.

**Why needed:** Browser rejects cross-origin requests without CORS headers. PWA origin differs from API Gateway origin (`:8080`) during development and potentially in production if PWA is served from a subdomain.

**Critical constraint:** `allowCredentials: true` cannot be combined with `allowedOrigins: "*"` — must use `allowedOriginPatterns` with explicit origins (verified against Spring Cloud Gateway docs).

**application.yml addition:**
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true
        cors-configurations:
          '[/**]':
            allowedOriginPatterns:
              - "http://localhost:5173"
              - "https://ruttrack.ru"
            allowedMethods: ["GET","POST","PUT","PATCH","DELETE","OPTIONS"]
            allowedHeaders: ["*"]
            allowCredentials: true
            maxAge: 3600
```

### Pattern 5: nginx Serves PWA Static Files (Not Spring Boot)

**What:** Build the React PWA with `vite build`, copy `dist/` into an nginx Docker container. nginx serves the static bundle and reverse-proxies `/api/*` calls to the API Gateway.

**Why nginx, not Spring Boot static serving:** nginx provides: gzip compression, correct Cache-Control headers, `try_files $uri /index.html` SPA fallback, explicit `no-cache` for `sw.js` and `index.html` (critical for PWA update behavior), and aggressive 1-year caching for content-hashed Vite assets.

**Key nginx.conf directives:**
```nginx
server {
    listen 80;

    # Service Worker: MUST NOT be cached by browser — breaks PWA updates
    location = /sw.js {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        try_files $uri =404;
    }

    # index.html: no-cache so new deployments are picked up
    location = /index.html {
        add_header Cache-Control "no-cache";
        try_files $uri =404;
    }

    # Web App Manifest: correct MIME type required for A2HS
    location = /manifest.webmanifest {
        add_header Content-Type "application/manifest+json";
        try_files $uri =404;
    }

    # Vite assets: content-hashed filenames → safe to cache 1 year
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API proxy to Gateway
    location /api/ {
        proxy_pass http://api-gateway:8080;
    }

    # SPA fallback
    location / {
        try_files $uri /index.html;
    }
}
```

### Pattern 6: STOMP WebSocket for Foreground, Web Push for Background

**What:** Dual notification delivery — when PWA is open (foreground), receive events via existing STOMP WebSocket connection; when PWA is closed/minimized, receive events via Web Push.

**When to use:** This is the correct PWA notification architecture. Web Push requires a Service Worker, which only handles push events when the app is not in the foreground. STOMP provides real-time events that can update UI state directly.

**Implementation:** `useWebSocket` hook connects STOMP on app mount, disconnects on unmount. Service Worker handles `push` events independently. No coordination needed — the server sends both; one of them will reach the user.

---

## Data Flow

### Web Push End-to-End Flow

```
[Cron job: lesson.started transition]
    ↓ RabbitMQ fanout → notification-web.events queue
    ↓
EventConsumer.onEvent(envelope)                    [notification-web]
    ├── messagingTemplate.convertAndSend(          → STOMP → foreground PWA clients
    │     "/topic/group/" + groupId, wsMessage)
    └── pushSubscriptionService
           .sendPushToGroup(groupId, eventType, payload)
               ↓
       PushSubscriptionRepository
           .findByGroupId(groupId)                 [MongoDB: push_subscriptions]
               ↓ list of subscription documents
       for each PushSubscription:
           Notification notif = new Notification(
               sub.getEndpoint(),
               sub.getP256dh(),
               sub.getAuth(),
               jsonPayload                         // {title, body, action_url, event_type}
           )
           pushService.send(notif)                 [webpush-java VAPID signed]
               ↓ HTTPS POST to browser push service
           [FCM / Mozilla Push / Safari APNs]
               ↓ browser wakes Service Worker (PWA closed/backgrounded)
           sw.ts push event handler
               ↓
           self.registration.showNotification()    → native OS notification
               ↓ user taps notification
           notificationclick handler
               ↓
           clients.openWindow(action_url)          → PWA opens to checkin page
```

### Push Subscription Registration Flow

```
[PWA: user taps "Enable Notifications" in SettingsPage]
    ↓
useWebPush.subscribe()
    ↓
GET /api/push/vapid-public-key                     [→ notification-web via Gateway]
    ← "BNs8d...base64url..."
    ↓
navigator.serviceWorker.ready
    ↓
reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapidKey)
})
    ← PushSubscription { endpoint, keys: { p256dh, auth } }
    ↓
POST /api/push/subscribe                           [→ notification-web via Gateway]
    headers: Authorization: Bearer <JWT>
    body: { endpoint, keys: { p256dh, auth } }
    ↓
PushController reads X-User-Id, X-Group-Id, X-User-Role from Gateway headers
    ↓
PushSubscriptionService.upsert(userId, groupId, role, subscriptionDto)
    ↓
MongoDB: findOneAndUpdate(
    { userId: x, endpoint: y },
    { $set: {...} },
    { upsert: true }
)                                                  → stored, deduplication safe
```

### Push Subscription MongoDB Document Schema

```json
{
  "_id": "ObjectId",
  "user_id": "Long",
  "group_id": "Long",
  "role": "STUDENT | TEACHER | HEADMAN",
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "p256dh": "<base64url — client public key for payload encryption>",
  "auth": "<base64url — 16-byte auth secret>",
  "user_agent": "Mozilla/5.0 ...",
  "created_at": "ISODate",
  "last_seen_at": "ISODate"
}
```

**MongoDB indexes for push_subscriptions:**
```javascript
{ endpoint: 1 }             // unique — deduplication, upsert key
{ user_id: 1 }              // DELETE /subscribe by user
{ group_id: 1 }             // fan-out sendToGroup(groupId)
{ user_id: 1, endpoint: 1 } // unique compound (upsert filter)
```

### State Management

```
[React App State (Zustand)]
    authStore: { userId, role, groupId, isHeadman, accessToken }
    pushStore:  { subscribed: boolean, permission: PermissionState }

[Workbox Cache (Service Worker)]
    precache:               /index.html, /assets/*, /icons/*
    stale-while-revalidate: GET /api/schedule/*, GET /api/reports/*
    network-only:           POST /api/attendance/check-in  (must be live)
    network-only:           POST /api/auth/*               (never cache tokens)
```

---

## Integration Points

### New vs Modified Components

| Component | Type | What Changes |
|-----------|------|-------------|
| notification-web | Extend existing Java container | Add push/ package, VapidConfig, MongoDB dependency, extend EventConsumer |
| api-gateway | Modify application.yml only | Add globalcors, add `/api/push/**` route, fix StripPrefix for push endpoints |
| docker-compose | Modify only | Add pwa nginx container; add MongoDB dependency to notification-web |
| frontends/pwa/ | New from scratch | React Vite PWA, nginx container |

### API Gateway Route Fix Required

The current gateway.yml has one route for notification-web:
```yaml
- id: notification-web
  uri: http://notification-web:9094
  predicates:
    - Path=/api/ws/**
  filters:
    - StripPrefix=1       # /api/ws/... → /ws/...
```

With `StripPrefix=1`, `/api/ws/push/subscribe` becomes `/ws/push/subscribe`, which is not the intended push endpoint path. Add a separate route for push REST endpoints with `StripPrefix=2`:

```yaml
- id: notification-web-push
  uri: http://notification-web:9094
  predicates:
    - Path=/api/push/**
  filters:
    - StripPrefix=1       # /api/push/subscribe → /push/subscribe

- id: notification-web-ws
  uri: http://notification-web:9094
  predicates:
    - Path=/api/ws/**
  filters:
    - StripPrefix=1       # /api/ws → /ws (STOMP upgrade, existing)
```

notification-web REST controllers map to `/push/subscribe`, `/push/unsubscribe`, `/vapid-public-key`.

### STOMP WebSocket for PWA

The existing STOMP WebSocket works for the PWA exactly as it does for the Angular web panel:
- PWA connects to `ws://gateway:8080/api/ws?token=<jwt>` (via Gateway WS proxy)
- `JwtHandshakeInterceptor` validates the `?token=` parameter at handshake
- PWA subscribes to `/topic/group/{groupId}` to receive real-time events
- No changes needed in notification-web WebSocket code

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| EventConsumer → PushSubscriptionService | Direct method call (same JVM) | No network; fanout event already in notification-web process |
| PushController → identity | Reads X-User-Id, X-Group-Id, X-User-Role headers injected by Gateway | JWT never touches notification-web REST layer |
| notification-web → MongoDB | Spring Data MongoRepository | New dependency: add spring-boot-starter-data-mongodb to build.gradle.kts |
| PushService → browser push endpoint | Outbound HTTPS from notification-web container | VPS must allow outbound port 443 to FCM/APNS/Mozilla push services |
| PWA → API Gateway | HTTPS REST + STOMP WS | Standard patterns; all existing services already work this way |
| PWA Service Worker ↔ React App | postMessage via ServiceWorkerRegistration | For showing in-app toast when push arrives while app is open |

---

## Build Order (Dependencies)

```
Layer 1 — Backend Web Push (notification-web):
  Goal: push endpoints work, testable with curl before any frontend exists
  - Add spring-boot-starter-data-mongodb to notification-web/build.gradle.kts
  - Add webpush-java dependency (nl.martijndwars:web-push:5.1.2 + BouncyCastle)
  - PushSubscription document + PushSubscriptionRepository
  - VapidConfig bean (VAPID key generation/loading from Redis)
  - PushController (GET /vapid-public-key, POST /push/subscribe, DELETE /push/subscribe)
  - PushSubscriptionService (upsert, sendToGroup, handle 410 Gone cleanup)
  Dependency: none (all new code, MongoDB/Redis already in docker-compose)

Layer 2 — Gateway Config:
  Goal: cross-origin PWA requests allowed; push routes reachable
  - Add globalcors with allowedOriginPatterns for PWA origins
  - Add /api/push/** route (StripPrefix=1)
  Dependency: none (just YAML config changes)

Layer 3 — PWA Scaffold (auth + schedule view, no push):
  Goal: prove API integration works; student can log in and see schedule
  - Vite + React + TypeScript project setup in frontends/pwa/
  - vite-plugin-pwa with injectManifest strategy, manifest.webmanifest
  - api/client.ts (axios + JWT interceptor + refresh interceptor)
  - api/auth.ts, hooks/useAuth.ts, authStore
  - LoginPage, SchedulePage (calls /api/schedule/*)
  - nginx.conf, multi-stage Dockerfile
  Dependency: Layer 2 (CORS must work for Vite dev server :5173 → :8080)

Layer 4 — Service Worker + Push Subscription:
  Goal: SW registered; user can opt-in to push; backend stores subscription
  - src/sw.ts (precacheAndRoute + push event handler + notificationclick)
  - api/push.ts + hooks/useWebPush.ts
  - SettingsPage with "Enable Notifications" toggle
  - End-to-end test: subscribe → POST lands in MongoDB → curl manual push
  Dependency: Layer 1 (push endpoints must exist) + Layer 3 (app shell must exist)

Layer 5 — EventConsumer Web Push Wiring:
  Goal: real events trigger push notifications end-to-end
  - Extend EventConsumer.onEvent() to call pushSubscriptionService per event type
  - lesson.started → push to group students: "Пара началась, отметьтесь"
  - lesson.cancelled → push to group: "Пара отменена"
  - homework.published → push to group: "Новое задание"
  - Handle 410 Gone → delete subscription from MongoDB
  Dependency: Layer 4 (subscriptions must exist to receive push)

Layer 6 — Student UI Features:
  Goal: geo-checkin, attendance stats, homework view
  - CheckInPage (navigator.geolocation → POST /api/attendance/check-in)
  - AttendancePage (GET /api/reports/student/{id})
  - HomeworkPage (GET /api/academic/homeworks)
  - Offline fallback pages (schedule, stats cached via Workbox stale-while-revalidate)
  Dependency: Layer 3 (app shell and auth must work)
```

**Recommended phase sequence for milestone planning:**
1. notification-web push/ package (backend only, testable with curl)
2. API Gateway CORS + push route (config only, fast)
3. PWA scaffold: auth + schedule view
4. PWA Service Worker + subscription opt-in
5. EventConsumer wiring (end-to-end push)
6. Student UI features (checkin, stats, homework, offline)

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-5k users (current VPS) | Single notification-web instance. Synchronous `pushService.send()` per subscription in a loop. MongoDB group_id index makes fan-out queries fast. Acceptable for solo VPS. |
| 5k-50k users | Switch to `PushAsyncService` (webpush-java async variant) + `CompletableFuture.allOf()` to send to all group subscribers in parallel rather than sequentially. |
| 50k+ users | Extract Web Push into its own microservice. Move fan-out to a per-group queue. STOMP in-memory broker also needs replacement (Redis pub/sub bridge for multi-instance). |

**First bottleneck:** `pushService.send()` in a loop blocks EventConsumer thread for large groups. Move to async send with error handling before scaling to 5k+ active subscriptions per group.

---

## Anti-Patterns

### Anti-Pattern 1: Serving PWA Static Files from Spring Boot

**What people do:** Add `src/main/resources/static/` to notification-web or api-gateway with the Vite build output.

**Why it's wrong:** Spring Boot's default static resource handler serves `sw.js` with the same `Cache-Control` as other static files. Browsers cache the Service Worker based on these headers — if SW is cached, users get stale notification code indefinitely. Additionally, mixes frontend build artifacts into a Java service, complicating CI/CD.

**Do this instead:** nginx container with explicit `Cache-Control: no-cache, no-store, must-revalidate` on `sw.js` and `index.html`, aggressive 1-year caching on `/assets/*` (Vite content-hashes these filenames).

### Anti-Pattern 2: Requesting Push Permission on App Mount

**What people do:** Call `Notification.requestPermission()` in a `useEffect` at the top of `App.tsx` so it fires on every first page load.

**Why it's wrong:** Chrome and Firefox block sites that show permission dialogs on first visit and record a "denied" decision. Once denied, the user must manually re-enable in browser settings — many users never do this. Chrome may silently suppress the prompt entirely.

**Do this instead:** Show an in-app UI explaining the value proposition ("Get notified when your class starts — don't miss the check-in window"), then call `Notification.requestPermission()` only on an explicit button click on the Settings page.

### Anti-Pattern 3: Storing VAPID Keys Only in Environment Variables

**What people do:** Generate VAPID keys once, store in `.env` or docker-compose environment, never persist elsewhere.

**Why it's wrong:** If the container restarts and env vars change (or `.env` is lost), the new VAPID keys are different from the keys used when subscriptions were created. All existing subscriptions silently fail — the push service rejects requests signed with different keys. Users must re-subscribe with no warning.

**Do this instead:** Generate keys once and persist in Redis (`vapid:public_key`, `vapid:private_key` with no TTL). On container startup: load from Redis if present, generate+store only if absent. Env var `VAPID_SUBJECT` (email) can be in env — it is not cryptographic material.

### Anti-Pattern 4: Ignoring HTTP 410 Gone from Push Endpoint

**What people do:** Send push to every stored subscription endpoint, log errors and continue.

**Why it's wrong:** Browser push endpoints return `410 Gone` when the user has explicitly unsubscribed or the subscription has expired. Retrying to dead endpoints wastes outbound HTTPS calls, may trigger rate-limiting from FCM, and bloats the `push_subscriptions` collection.

**Do this instead:** In `PushSubscriptionService.sendToGroup()`, catch the exception from `pushService.send()` when the HTTP response status is 410, and immediately call `repository.deleteByEndpoint(endpoint)`. webpush-java's `PushService.send()` throws `PushClientException` for 4xx responses — catch it, inspect status code.

### Anti-Pattern 5: Routing STOMP WebSocket Upgrade Through Gateway for Large Scale

**What people do (at scale):** Keep all WebSocket traffic through Spring Cloud Gateway in production under high load.

**Why it's wrong at scale:** Long-lived WebSocket connections interact poorly with Gateway's connection pool timeouts, load balancer idle timeouts, and its reactive pipeline under high concurrency. For this project's current VPS scale (hundreds of concurrent connections), it is acceptable. At thousands of concurrent connections, WebSocket load becomes a Gateway bottleneck.

**Do this instead (when needed):** Add an nginx reverse proxy rule that forwards WebSocket upgrades directly to notification-web, bypassing the Gateway. `proxy_pass http://notification-web:9094/ws` with `proxy_http_version 1.1`, `proxy_set_header Upgrade $http_upgrade`, `proxy_set_header Connection "upgrade"`. JWT handshake still validates via `JwtHandshakeInterceptor` — the Gateway's role is only missed for the WS path. Not needed for current scale.

---

## Open Questions

1. **Web Push + iOS Safari (PWA install required):** Web Push on iOS requires the app to be installed as a PWA (A2HS). Safari on iOS requires explicit user installation before push subscriptions work. Check if `useWebPush` should gate the subscribe button on `window.matchMedia('(display-mode: standalone)').matches` or show an iOS install prompt first.

2. **notification-web MongoDB connection:** The existing `notification-web` build.gradle.kts does not include `spring-boot-starter-data-mongodb`. Confirm no MongoDB dependency exists before adding — and add the `MONGODB_URI` environment variable to the notification-web docker-compose entry pointing to the existing `mongo-attendance` container.

3. **StripPrefix behavior for WebSocket upgrade:** Current `/api/ws/**` route with `StripPrefix=1` maps to `/ws/**` in notification-web. The STOMP endpoint is `/ws` — verify that the SockJS handshake path (`/ws/info`, `/ws/{session}/{transport}`) is not broken by any path prefix stripping edge case after adding the new `/api/push/**` route.

4. **Push payload size limit:** Browser push services impose a 4KB payload limit. The `{title, body, action_url, event_type}` JSON payload for RutTrack notifications is well within this limit, but if body text is user-generated (e.g., homework description), it must be truncated before sending.

---

## Sources

- webpush-java v5.1.2: [https://github.com/web-push-libs/webpush-java](https://github.com/web-push-libs/webpush-java) — MEDIUM confidence (GitHub README, library stable and widely used)
- Vaadin Web Push with Spring Boot: [https://vaadin.com/blog/send-web-push-notifications-java](https://vaadin.com/blog/send-web-push-notifications-java) — MEDIUM confidence (tutorial article)
- vite-plugin-pwa official docs: [https://vite-pwa-org.netlify.app/guide/](https://vite-pwa-org.netlify.app/guide/) — HIGH confidence (official)
- vite-plugin-pwa nginx deployment: [https://vite-pwa-org.netlify.app/deployment/nginx](https://vite-pwa-org.netlify.app/deployment/nginx) — HIGH confidence (official)
- MDN PushSubscription API: [https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription](https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription) — HIGH confidence (MDN)
- Spring Cloud Gateway CORS: [https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html) — HIGH confidence (official Spring docs)
- Existing codebase: `services/notification-web/` source files — HIGH confidence (authoritative)
- Existing codebase: `services/api-gateway/src/main/resources/application.yml` — HIGH confidence (authoritative)
- Existing design: `docs/architecture.md` sections 3.6, 5.2, 7 — HIGH confidence (authoritative)

---

*Architecture research for: PWA + Web Push integration (v6.0 milestone)*
*Researched: 2026-04-05*
