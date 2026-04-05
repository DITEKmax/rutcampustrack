# Project Research Summary

**Project:** RutCampusTrack v6.0 — PWA Mobile Client «RutTrack» + Web Push
**Domain:** React PWA (installable mobile client) + Web Push VAPID extension to existing Spring Boot microservice
**Researched:** 2026-04-05
**Confidence:** HIGH

## Executive Summary

RutTrack v6.0 is a mobile-first PWA for university students built on top of a fully operational microservice backend (5 Spring Boot services + API Gateway). The new work is exclusively in two areas: (1) a React + Vite + Tailwind PWA frontend at `frontends/pwa/` for student schedule viewing, geo check-in, attendance stats, and homework tracking; and (2) a lightweight Web Push (VAPID) extension inside the existing `notification-web` Spring Boot container. No new backend services are introduced. The entire stack matches the project's existing conventions — Java 21 + Spring Boot 3.4 on the backend, React + TypeScript on the frontend — and shares Phosphor Icons and Motion with the existing Telegram Mini App.

The recommended approach follows a strict layered build order: backend push infrastructure first (testable with curl), then Gateway CORS configuration, then PWA auth and schedule scaffold, then Service Worker and push subscription wiring, then EventConsumer fanout wiring for end-to-end push, and finally the remaining student UI features (check-in, stats, homework, offline). This sequence ensures each layer is independently testable before the next depends on it. The PWA uses the `injectManifest` strategy with vite-plugin-pwa to support custom push event handlers alongside Workbox caching — the default `generateSW` strategy cannot accommodate `push` event listener hooks.

The primary risks are iOS-specific: Web Push requires Add-to-Home-Screen installation before push subscriptions work on iOS, and the Geolocation Permissions API reports `denied` as `prompt` on iOS Safari. A critical threading risk exists in notification-web: Web Push HTTP delivery must be dispatched asynchronously (dedicated thread pool) to avoid blocking the RabbitMQ consumer during lesson-start fan-out to 30+ students. JWT storage must use React memory + httpOnly cookie (never localStorage) both for security and because the Service Worker has no localStorage access. All risks have clear prevention strategies tied to specific implementation phases.

---

## Key Findings

### Recommended Stack

The frontend is React 19 + TypeScript 5 + Vite 8 + Tailwind CSS v4, consistent with the existing Telegram Mini App (shared component extraction possible). vite-plugin-pwa 1.2.0 (Workbox 7 bundled) handles Service Worker generation, precaching, and manifest injection. TanStack Query v5 manages server state; Zustand 5 manages client-only state (auth tokens, push subscription status, install prompt). STOMP via `@stomp/stompjs` + `sockjs-client` connects to the existing notification-web WebSocket endpoint — `sockjs-client` is mandatory because the server uses `.withSockJS()`. The backend addition is `nl.martijndwars:web-push:5.1.2` + `org.bouncycastle:bcprov-jdk18on:1.78.1` added to notification-web's Gradle build, plus `spring-boot-starter-data-mongodb` for push subscription storage in the existing MongoDB instance.

One version compatibility caveat: vite-plugin-pwa 1.2.0 does not formally declare Vite 8 peer dependency support. Community reports confirm it works (GitHub issue #918), but `--legacy-peer-deps` may be needed at install time. VAPID keys must be persisted in Redis (not regenerated on restart) to avoid invalidating all existing push subscriptions — this is a non-negotiable constraint.

**Core technologies:**
- React 19 + TypeScript 5 + Vite 8: SPA PWA framework — consistent with Mini App, concurrent rendering for mobile UX
- Tailwind CSS v4 + `@tailwindcss/vite`: utility CSS — zero PostCSS config, no tailwind.config.js, shared class vocabulary with Mini App
- vite-plugin-pwa 1.2.0 (`injectManifest` strategy): Service Worker + precache — required for custom push event handler hooks
- TanStack Query v5: server state — caching, background refetch, stale-while-revalidate for offline reads
- Zustand 5: client state — auth tokens, push subscription status, A2HS install prompt state
- `@stomp/stompjs` + `sockjs-client`: real-time events — existing notification-web uses SockJS transport (raw WebSocket would fail)
- `nl.martijndwars:web-push:5.1.2` + `bcprov-jdk18on:1.78.1`: Java VAPID push — canonical web-push-libs library, Java 21 confirmed
- `spring-boot-starter-data-mongodb`: push subscription persistence — reuses existing MongoDB container

### Expected Features

All backend API endpoints are operational. The PWA consumes them directly via API Gateway. The only new backend code is VAPID subscription management (2 REST endpoints + MongoDB `push_subscriptions` collection) inside notification-web.

**Must have (v6.0 launch — table stakes):**
- Login screen + JWT auto-refresh (15-min access token; httpOnly cookie for refresh token)
- Today's schedule view — primary daily-use screen, gates all other features
- Geo check-in button on active lesson card — core product function, visible only in check-in window
- Check-in result feedback (success / not-in-zone / already-marked)
- Weekly schedule navigation
- Offline app shell (app loads without network via Service Worker precache)
- Add-to-Home-Screen prompt with iOS Safari instruction screen (iOS does not fire `beforeinstallprompt`)
- VAPID key pair setup in notification-web + push subscription endpoint
- `lesson.started` Web Push notification with "Отметиться" action button — headline feature of the milestone
- `lesson.cancelled` Web Push notification

**Should have (v6.1 iteration — differentiators):**
- Attendance stats screen with red zone warning indicator
- Attendance records list with status color coding (б/н/у/сп)
- Homework list + personal completion tracker
- `homework.published` Web Push notification
- Real-time check-in state update via STOMP WebSocket (`attendance.marked` event)
- Headman manual marking screen (complex group marking grid UI)

**Defer (v6.2+, blocked on backend or out-of-scope):**
- Excuse ticket creation flow (backend deferred project-wide)
- Late check-in request flow (backend deferred)
- Web Push for TEACHER role (defer to v8.0 web panel milestone)
- Push notification preferences per type (needs preferences storage backend)
- Background sync for check-in (not viable — 5-min time window makes offline queue useless)

### Architecture Approach

The architecture extends the existing system minimally. notification-web gains a new `push/` Java package (PushController, PushSubscriptionService, PushSubscriptionRepository, VapidConfig) and a MongoDB dependency. The API Gateway gains a new `/api/push/**` route (StripPrefix=1) alongside the existing `/api/ws/**` STOMP route, plus `globalcors` configuration for the PWA origin. The PWA is served from a new nginx Docker container (multi-stage Vite build). VAPID keys are generated once on first startup, persisted in Redis without TTL, and never regenerated. Push delivery in EventConsumer is dispatched via `@Async` with a dedicated ThreadPoolTaskExecutor — the consumer method returns immediately; actual HTTPS delivery to FCM/APNs/Mozilla happens off the consumer thread.

**Major components:**
1. `notification-web` (extended) — new `push/` package for VAPID subscription management and outbound push delivery; `EventConsumer` extended to fan-out Web Push alongside existing STOMP delivery using async dispatch
2. `api-gateway` (config-only change) — `globalcors` with `allowedOriginPatterns` for PWA origins; new `/api/push/**` route (StripPrefix=1); existing `/api/ws/**` STOMP route preserved unchanged
3. `frontends/pwa/` (new container) — React PWA with custom Service Worker (`injectManifest`), nginx serving with correct `Cache-Control` headers per resource type, multi-stage Dockerfile
4. `push_subscriptions` MongoDB collection (new in existing container) — stores `endpoint + p256dh + auth + user_id + group_id`; indexed by `endpoint` (unique, for 410 cleanup) and `group_id` (fan-out query)

### Critical Pitfalls

1. **Service Worker `skipWaiting()` called unconditionally** — causes ChunkLoadError on open tabs during deployment (old tab tries to load chunks from new cache); use `registerType: 'prompt'` in vite-plugin-pwa and only call `skipWaiting()` after explicit user confirmation.

2. **iOS push subscription outside installed PWA** — `PushManager.subscribe()` silently fails in Safari browser tab (iOS requires Add-to-Home-Screen); detect with `window.matchMedia('(display-mode: standalone)').matches` and show install instructions before attempting any push subscription; EU iOS 17.4+ may not support PWA standalone at all.

3. **Duplicate CORS headers from Gateway + downstream service** — browser rejects responses with two `Access-Control-Allow-Origin` values; configure CORS only at Gateway level, never add `@CrossOrigin` in downstream services; add `DedupeResponseHeader` Gateway filter as safety net.

4. **JWT stored in localStorage** — exposed to XSS; Service Worker cannot access localStorage; use React memory for access token + httpOnly Secure cookie for refresh token.

5. **Synchronous Web Push delivery blocking RabbitMQ consumer** — lesson.started fan-out to 30 students × 200-2000ms per HTTP call = consumer stall, STOMP messages delayed; dispatch push sends via `@Async` ThreadPoolTaskExecutor before writing any push code.

6. **Web Push endpoint expiry (HTTP 410 Gone) not handled** — dead subscriptions accumulate silently, delivery degrades over weeks; delete subscription from MongoDB immediately on 410 or 404 response from push service.

7. **Notification permission requested on app load or first visit** — one-shot browser prompt wasted before user sees value; use soft-ask modal ("Get notified when class starts") before calling `Notification.requestPermission()` from an explicit user gesture.

---

## Implications for Roadmap

Based on combined research, the architecture's build order dependency graph directly maps to a 6-phase delivery sequence. Each phase is independently testable before the next one depends on it.

### Phase 1: Web Push Backend (notification-web extension)
**Rationale:** All subsequent phases depend on push endpoints existing. Backend is testable with curl before any frontend exists. Zero dependency on PWA progress — can run in parallel with Phase 2.
**Delivers:** `push/` package (PushController, PushSubscriptionService, PushSubscriptionRepository, VapidConfig); `GET /push/vapid-public-key`, `POST /push/subscribe`, `DELETE /push/subscribe` endpoints; MongoDB `push_subscriptions` collection with indexes; VAPID key generation persisted in Redis; async `ThreadPoolTaskExecutor` for push dispatch; 410 Gone cleanup logic.
**Addresses:** VAPID setup (FEATURES.md P1); push subscription storage (ARCHITECTURE.md Layer 1)
**Avoids:** Pitfall 5 (synchronous consumer blocking — async dispatch established here); Pitfall 6 (410 cleanup from day one); Anti-Pattern 3 (VAPID keys in Redis, not env-only)

### Phase 2: API Gateway CORS + Push Route
**Rationale:** Config-only change. Must be done before any PWA development can call the API from `localhost:5173`. Fast phase; can proceed in parallel with Phase 1.
**Delivers:** `globalcors` with `allowedOriginPatterns` for `localhost:5173` and production PWA domain; `/api/push/**` route with StripPrefix=1; existing `/api/ws/**` STOMP route preserved; `DedupeResponseHeader` filter.
**Addresses:** CORS for PWA origin (ARCHITECTURE.md Pattern 4)
**Avoids:** Pitfall 3 (CORS configured only at Gateway, never in downstream services); StripPrefix conflict between STOMP and push routes (ARCHITECTURE.md Open Question 3)

### Phase 3: PWA Scaffold — Auth + Schedule View
**Rationale:** Proves API integration works end-to-end with real auth. Establishes core PWA identity (manifest, A2HS, nginx serving) and the JWT storage pattern before any subsequent feature is built on it. This phase blocks all subsequent UI phases.
**Delivers:** Vite + React + TypeScript project at `frontends/pwa/`; axios client with JWT interceptor + 401 refresh; `authStore` (Zustand); `LoginPage`; `SchedulePage` (today + weekly view); `manifest.webmanifest` (name: RutTrack, display: standalone); A2HS prompt handling via `beforeinstallprompt`; iOS Safari onboarding screen; nginx container (no-cache for `sw.js`/`index.html`, 1-year for `/assets/*`); multi-stage Dockerfile; httpOnly cookie for refresh token.
**Uses:** React 19, Vite 8, Tailwind v4, TanStack Query v5, Zustand 5, Phosphor Icons, Motion (STACK.md)
**Avoids:** Pitfall 4 (JWT in React memory + httpOnly cookie, never localStorage); iOS A2HS detection (prerequisite for Pitfall 2 prevention)

### Phase 4: Service Worker + Push Subscription Opt-in
**Rationale:** Depends on Phase 1 (push endpoints) and Phase 3 (app shell must exist for SW registration). Once SW is in place, push subscription flow can be tested end-to-end (subscribe → POST lands in MongoDB → manual curl push to endpoint) before real RabbitMQ events are wired.
**Delivers:** `src/sw.ts` with `precacheAndRoute` + `push` event handler + `notificationclick` handler; `api/push.ts` + `useWebPush` hook; `SettingsPage` with "Enable Notifications" toggle; soft-ask permission modal; offline schedule cache (stale-while-revalidate via Workbox); `pushsubscriptionchange` SW event handler; iOS standalone guard before push subscription attempt.
**Uses:** vite-plugin-pwa `injectManifest` strategy, Workbox 7 (STACK.md)
**Avoids:** Pitfall 1 (`registerType: 'prompt'`, no unconditional `skipWaiting()`); Pitfall 7 (soft-ask before native prompt); Pitfall 2 (iOS standalone guard)

### Phase 5: EventConsumer Web Push Wiring (End-to-End Push)
**Rationale:** Depends on Phase 4 (subscriptions must exist in DB). Extends existing `EventConsumer.onEvent()` to call `PushSubscriptionService.sendToGroup()` alongside the existing STOMP delivery. This is the v6.0 headline feature delivered end-to-end.
**Delivers:** `lesson.started` → Web Push to group students with "Отметиться" action button and `action_url` pointing to check-in page; `lesson.cancelled` → Web Push to group; full end-to-end smoke test (RabbitMQ event → phone notification → tap → PWA opens at check-in screen).
**Implements:** ARCHITECTURE.md Layer 5 and full Web Push data flow diagram
**Avoids:** Pitfall 5 (async dispatch already established in Phase 1); Pitfall 6 (410 cleanup already implemented)

### Phase 6: Student UI Features (Check-in, Stats, Homework, Offline)
**Rationale:** Auth, schedule, and push are proven in Phases 3-5. This phase completes the v6.0 student feature set using the established patterns. Geo check-in iOS error handling requires platform-specific code established here.
**Delivers:** `CheckInPage` with geo check-in button (navigator.geolocation), 15-second timeout, iOS geolocation error handling (all three error codes), offline "Нет подключения" message; `AttendancePage` (stats + records with status color coding); `HomeworkPage` + personal completion tracker; real-time check-in state update via `useWebSocket` hook (STOMP `attendance.marked`); `homework.published` push handler in EventConsumer.
**Addresses:** All P1 table stakes and selected P2 differentiators from FEATURES.md MVP definition
**Avoids:** Pitfall (Geolocation) — never rely on `navigator.permissions.query` on iOS, always handle error codes explicitly; Performance Trap — `getCurrentPosition()` once per check-in, never `watchPosition`

### Phase Ordering Rationale

- Backend before frontend: Push endpoints must exist before SW subscription flow can be end-to-end tested. Parallel development risks integration bugs that are hard to isolate without working endpoints.
- CORS gateway config is fast and independent — it runs in parallel with Phase 1 and unblocks all PWA development.
- PWA scaffold (auth + schedule) before push subscription wiring: The Service Worker requires a deployed app shell. The JWT storage pattern (httpOnly cookie) established in Phase 3 prevents the localStorage pitfall from propagating into later code.
- Push subscription before EventConsumer wiring: The MongoDB collection must have real subscription documents before the fan-out logic can be validated end-to-end.
- Student UI last: All platform plumbing (auth, SW, push, STOMP) is proven before building feature screens. The geo check-in iOS error handling is complex enough to warrant its own focused phase.

### Research Flags

Phases likely needing deeper research or verification during planning:
- **Phase 1 (notification-web extension):** Verify BouncyCastle `bcprov-jdk18on` loads correctly in Spring Boot's executable JAR — known edge case with signed JAR packaging. Read existing `notification-web/build.gradle.kts` before adding dependencies to avoid duplicates.
- **Phase 4 (Service Worker + vite-plugin-pwa):** vite-plugin-pwa 1.2.0 + Vite 8 peer dependency — may require `--legacy-peer-deps` flag; verify at `npm create vite@latest` step, not mid-phase. If blocked, Vite 7 is fully supported with no architectural change.
- **Phase 6 (Geo check-in):** Physical iOS device QA required. iOS Geolocation Permissions API bug (reports `denied` as `prompt`) is invisible in simulator. Must test on real hardware before shipping.

Phases with standard patterns (skip additional research):
- **Phase 2 (Gateway CORS):** Exact YAML specified in ARCHITECTURE.md Pattern 4. Standard Spring Cloud Gateway config. No research needed.
- **Phase 3 (PWA scaffold):** Well-documented Vite + React patterns. STACK.md provides exact dependency list and version compatibility table.
- **Phase 5 (EventConsumer wiring):** Extends existing EventConsumer with method calls to an in-JVM service. ARCHITECTURE.md data flow section specifies the code structure.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | npm versions verified via npm registry; Maven Central version confirmed for webpush-java; existing codebase inspected as authoritative source for notification-web baseline dependencies |
| Features | HIGH | Sourced directly from PROJECT.md, job-stories.md, and design-decisions.md — project's own canonical requirements, not inferred |
| Architecture | HIGH | Based on full source inspection of existing codebase + official Spring Cloud Gateway docs + official MDN Push API docs; build order derived from dependency analysis |
| Pitfalls | HIGH | iOS limitations verified against Apple Developer Forums and MDN; CORS duplicate headers verified against Spring Cloud Gateway docs; SW lifecycle and JWT storage pitfalls verified against multiple independent authoritative sources |

**Overall confidence: HIGH**

### Gaps to Address

- **vite-plugin-pwa 1.2.0 + Vite 8 compatibility:** No official peer dep declaration. Community confirms it works (GitHub issue #918). Verify at project init in Phase 3 with `npm install vite-plugin-pwa`. If blocked, use Vite 7 — no architectural impact.
- **iOS Web Push on iOS 17.4+ EU (Digital Markets Act):** iOS 17.4+ in EU regions may disable PWA standalone mode entirely, making Web Push permanently unavailable for EU students on iOS. Treat as known platform limitation; ensure graceful fallback to STOMP WebSocket for in-app alerts when push is unavailable.
- **Gateway CORS + cookie domain alignment:** If PWA is served from a subdomain (e.g., `app.ruttrack.ru`) and Gateway is on `api.ruttrack.ru`, the `Set-Cookie` for the refresh token requires `SameSite=None; Secure`. Verify deployment domain topology before Phase 2 to configure correctly from the start.
- **notification-web MongoDB URI in docker-compose:** The existing notification-web service entry in docker-compose does not currently declare a `MONGODB_URI` environment variable. Add `MONGODB_URI=mongodb://mongo-attendance:27017/attendance_db` in Phase 1 docker-compose update.
- **Push payload size:** Browser push services impose a 4KB payload limit. Homework descriptions can be user-generated text. Truncate `body` text at 200 characters before sending push payload in EventConsumer wiring (Phase 5).

---

## Sources

### Primary (HIGH confidence)
- `docs/job-stories.md` — all student/headman job stories (JS-STUDENT-01..11, JS-HEADMAN-20)
- `docs/design-decisions.md` — PWA design decisions, offline strategy, iOS onboarding, Phosphor Icons + Motion mandate
- `docs/phases-plan.md` — v6.0 milestone definition and deferred items
- `CLAUDE.md` — existing architecture, business rules, check-in window, notification rules
- `services/notification-web/` — existing codebase (authoritative for what must be extended)
- `services/api-gateway/src/main/resources/application.yml` — confirmed `/api/ws/**` routing; StripPrefix=1 behavior
- [Spring Cloud Gateway CORS docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html)
- [vite-plugin-pwa official docs](https://vite-pwa-org.netlify.app/guide/)
- [MDN PushSubscription API](https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription)
- [MDN Service Workers Caching](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Caching)
- [stomp-js: Using STOMP with SockJS](https://stomp-js.github.io/guide/stompjs/rx-stomp/using-stomp-with-sockjs.html)

### Secondary (MEDIUM confidence)
- [nl.martijndwars/web-push Maven Central](https://central.sonatype.com/artifact/nl.martijndwars/web-push) — v5.1.2 confirmed Feb 2025
- [webpush-java GitHub](https://github.com/web-push-libs/webpush-java) — BouncyCastle dependency, PushAsyncService API
- [Vaadin: Web Push with Spring Boot](https://vaadin.com/blog/send-web-push-notifications-java) — Spring Boot + webpush-java integration pattern
- [vite-plugin-pwa GitHub issue #918](https://github.com/vite-pwa/vite-plugin-pwa) — Vite 8 compatibility community reports
- [Handling Geolocation for PWA Safari](https://blog.poespas.me/posts/2025/03/01/handling-geolocation-for-pwa-safari-challenges/)
- [Web Push errors HTTP status codes — Pushpad](https://pushpad.xyz/blog/web-push-errors-explained-with-http-status-codes)
- [Service Worker Lifecycle — Felix Gerschau](https://felixgerschau.com/service-worker-lifecycle-update/)
- [Handling Service Worker updates — Chrome Developers](https://developer.chrome.com/docs/workbox/handling-service-worker-updates)
- [Permission UX — web.dev](https://web.dev/articles/push-notifications-permissions-ux)

### Tertiary (MEDIUM-LOW confidence — verify at implementation time)
- [PWA iOS Limitations 2026 — MagicBell](https://www.magicbell.com/blog/pwa-ios-limitations-safari-support-complete-guide) — iOS 17.4+ EU restriction
- [PWA Push Notifications on iOS 2026](https://webscraft.org/blog/pwa-pushspovischennya-na-ios-u-2026-scho-realno-pratsyuye?lang=en) — iOS standalone requirement confirmation

---
*Research completed: 2026-04-05*
*Ready for roadmap: yes*
