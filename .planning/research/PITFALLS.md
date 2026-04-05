# Pitfalls Research

**Domain:** PWA + Web Push — adding React PWA mobile client and Web Push (VAPID) to an existing Spring Boot microservice attendance system (RutCampusTrack v6.0).
**Researched:** 2026-04-05
**Confidence:** HIGH (iOS PWA limitations verified against official Apple Developer Forums and MDN; CORS duplicate headers verified against Spring Cloud Gateway docs; Service Worker lifecycle pitfalls verified against Chrome Developers and MDN; JWT race condition patterns verified against multiple independent sources; Web Push 410 handling verified against push service HTTP status code documentation)

---

## Critical Pitfalls

### Pitfall 1: Service Worker skipWaiting() Causes Asset Version Mismatch Mid-Session

**What goes wrong:**
When `skipWaiting()` is called unconditionally in the install handler, the new Service Worker activates immediately — but tabs already open were loaded with the old SW's cached assets. If the app uses code-splitting (React lazy imports), the old tab may try to load a chunk URL that no longer exists in the new cache, causing blank screens or runtime errors. Students mid-checkin lose their session.

**Why it happens:**
Developers see `skipWaiting()` as "always deploy updates immediately" and add it without coordinating with the client. The race condition is invisible in development (single tab, no lazy loading), but manifests in production with multiple open tabs and Vite/CRA chunk hashing.

**How to avoid:**
Do NOT call `skipWaiting()` unconditionally in the install handler. Instead, use a user-driven update flow:
1. In the SW, listen for a `postMessage` signal from the client before calling `skipWaiting()`.
2. In the React app, detect `waiting` SW via `navigator.serviceWorker.addEventListener('controllerchange', ...)` and show a "New version available — tap to update" banner.
3. Only call `skipWaiting()` after the user taps the banner (between user actions, not during checkin flow).

For Vite + Workbox: use `vite-plugin-pwa` with `registerType: 'prompt'` (not `'autoUpdate'`), which implements this pattern.

**Warning signs:**
- ChunkLoadError in the console after a deployment while a tab is open
- Blank white screen after the page is left open overnight and refreshes
- Users reporting "the checkin button disappeared" after a background update

**Phase to address:**
Service Worker scaffold phase — establish the update strategy before writing any caching logic.

---

### Pitfall 2: iOS PWA Web Push Only Works When Installed to Home Screen — No Fallback

**What goes wrong:**
The `PushManager.subscribe()` call silently fails or the permission prompt never appears when the student opens the PWA in a Safari browser tab (not installed). On iOS 16.4–17, Web Push is only available to installed PWAs (Add to Home Screen). Additionally, iOS 17.4+ in EU countries has no PWA standalone mode at all due to the Digital Markets Act — PWAs open in Safari tabs with no push support, breaking the feature for EU students.

**Why it happens:**
Developers test on Android Chrome where push works in the browser tab, then assume iOS behaves the same. The `'serviceWorker' in navigator` check passes in Safari, but `PushManager` subscription throws `NotAllowedError` outside a standalone context.

**How to avoid:**
- Before attempting any push subscription, check `window.matchMedia('(display-mode: standalone)').matches` on iOS.
- If not standalone on iOS: show an "Install to home screen to enable push notifications" instruction screen with screenshots specific to Safari's share menu.
- Use `navigator.userAgent` + `navigator.standalone` (Safari-specific property) to detect iOS Safari browser tab vs. installed PWA.
- Never call `Notification.requestPermission()` outside a user gesture on iOS — it will be silently blocked.
- Treat EU iOS as "push unsupported" and fall back gracefully to STOMP WebSocket for real-time alerts when the app is open.

**Warning signs:**
- `PushManager.subscribe()` throws `DOMException: NotAllowedError` on iOS without any permission dialog appearing
- `navigator.standalone` is `undefined` or `false` — user is in browser tab
- Zero iOS subscribers in your push subscription table despite iOS users existing

**Phase to address:**
Web Push subscription flow phase — implement platform detection and A2HS onboarding before wiring VAPID subscription.

---

### Pitfall 3: Duplicate CORS Headers — Gateway + Downstream Service Both Set Access-Control Headers

**What goes wrong:**
Spring Cloud Gateway adds `Access-Control-Allow-Origin` headers to responses. If any downstream service (auth-service, attendance-service) also has `@CrossOrigin` or a `WebMvcConfigurer` CORS bean, the response contains the header twice. Browsers reject responses with duplicate `Access-Control-Allow-Origin` headers with "The 'Access-Control-Allow-Origin' header contains multiple values" — the PWA gets CORS errors despite the backend being "configured correctly."

**Why it happens:**
Backend services were developed before the PWA frontend existed. A developer adds `@CrossOrigin("*")` to a controller to fix a local dev test, or each service has its own CORS config from an earlier phase. The Gateway then adds its own headers on top.

**How to avoid:**
- Configure CORS **only** at the Gateway level (`spring.cloud.gateway.globalcors`). Never add `@CrossOrigin` or `CorsConfigurationSource` beans in downstream services.
- Add `DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin` filter at the gateway to remove duplicates as a safety net.
- Set `spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping: true` so OPTIONS preflight requests are handled even for routes without explicit route predicates.
- For the PWA dev server (e.g., `http://localhost:5173`), add it to `allowedOrigins` in gateway config — **not** via wildcard in production.

**Warning signs:**
- Browser console: "The 'Access-Control-Allow-Origin' header contains multiple values '*, *'"
- Preflight OPTIONS returns 404 (gateway not handling OPTIONS for that route)
- Works in Postman but fails in browser

**Phase to address:**
API Gateway CORS configuration phase — done before any PWA API call is wired.

---

### Pitfall 4: JWT Stored in localStorage is Vulnerable to XSS — Service Worker Intercept Required

**What goes wrong:**
Storing the JWT access token in `localStorage` exposes it to any injected script, including from compromised npm packages (supply chain attacks). React apps with many dependencies are high-risk. The attendance system JWT carries role + group info — stolen tokens allow impersonating students and faking checkins.

Additionally, the Service Worker does not have access to `localStorage`. If a push notification arrives while the app is closed and the SW tries to make an authenticated API call (e.g., to display rich push content), it cannot read the token.

**Why it happens:**
`localStorage` is the simplest approach and is widely taught in tutorials. The Service Worker access gap is discovered only when implementing background push handlers that need auth.

**How to avoid:**
Use a hybrid storage strategy:
- **Access token**: store in React memory only (React Context / Zustand). Never persist to localStorage. On page refresh, use the refresh token to get a new access token silently.
- **Refresh token**: store in `httpOnly; Secure; SameSite=Strict` cookie. Set from the Spring Boot auth endpoint (`Set-Cookie` response header). The Gateway routes `/api/auth/refresh` without stripping cookies.
- **For Service Worker background auth**: store only a non-sensitive user identifier in `IndexedDB` for display purposes (e.g., username for notification body). Never store the JWT in SW-accessible storage.

The current system already rotates refresh tokens on use (delete on use pattern from v1.0 Key Decisions) — this is compatible with the cookie approach.

**Warning signs:**
- `localStorage.getItem('token')` call in React code
- Service Worker trying to call API endpoints with Authorization headers using tokens from `localStorage` (throws because SW has no localStorage access)
- Auth token visible in browser DevTools Application → Local Storage

**Phase to address:**
PWA authentication layer phase — establish token storage strategy before any API integration.

---

### Pitfall 5: Web Push Subscription Endpoint Expires — Silent Delivery Failures Accumulate

**What goes wrong:**
Web Push subscriptions have endpoints that expire or become invalid when:
- The user clears browser data
- The browser reinstalls or updates
- The user revokes notification permission from OS settings (not the browser UI)
- iOS unsubscribes the user if the PWA is not used for 7+ days (Apple's aggressive cache eviction)

When the backend sends to an expired endpoint, the push service returns `410 Gone` or `404 Not Found`. If the backend ignores these status codes, the subscription table accumulates dead endpoints. Over time, push attempts to thousands of dead endpoints waste resources and hide real delivery failures.

**Why it happens:**
Developers implement "happy path" push sending without error handling. The `nl.martijndwars:web-push` Java library throws `WebPushException` on non-2xx responses, but if the exception is caught and logged without action, dead subscriptions persist forever.

**How to avoid:**
In `notification-web` (Spring Boot), implement explicit status-code handling after every push attempt:
- `410 Gone` or `404 Not Found`: delete the subscription from the database immediately. These are permanent failures.
- `429 Too Many Requests`: back off with exponential retry (min 1 min). Do not delete.
- `5xx`: retry with exponential backoff up to 3 times, then mark subscription as `suspect`.

On the PWA side, implement `pushsubscriptionchange` event handler in the Service Worker to detect automatic endpoint renewal and POST the new subscription to the backend immediately.

```javascript
// service-worker.js
self.addEventListener('pushsubscriptionchange', async (event) => {
  const newSubscription = event.newSubscription ||
    await self.registration.pushManager.subscribe(event.oldSubscription.options);
  await fetch('/api/notifications/subscriptions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(newSubscription.toJSON())
  });
});
```

**Warning signs:**
- Push delivery rate dropping over weeks without user growth declining
- Database subscription count growing while active user count stays flat
- 410/404 errors in notification-web logs that are caught but not actioned

**Phase to address:**
Web Push backend (notification-web extension) phase — implement error handling before the first real push is sent.

---

### Pitfall 6: Geolocation Permission Denied on iOS Does Not Return `denied` State

**What goes wrong:**
On iOS/Safari, `navigator.permissions.query({ name: 'geolocation' })` returns `'prompt'` even when the user has previously denied geolocation in Settings. When the app then calls `getCurrentPosition()`, it gets `PERMISSION_DENIED` error (code 1) — a mismatch that makes it impossible to know in advance if permission will succeed. The "Request permission" button flow breaks: the app shows the button, user taps it, nothing appears (permission was already denied at OS level), and the checkin button stays disabled silently.

**Why it happens:**
Developers rely on the Permissions API to gate the checkin flow (show button only if state is `'prompt'` or `'granted'`). On Chrome/Android, this works perfectly. The iOS bug where `denied` is reported as `prompt` is undocumented behavior that surfaces only in QA.

**How to avoid:**
- Never gate checkin UI solely on `navigator.permissions.query`. Always attempt `getCurrentPosition()` and handle the error callback explicitly.
- On `PERMISSION_DENIED` (code 1): show an actionable error with platform-specific instructions ("Go to Settings → Privacy → Location Services → Safari → Allow").
- On `POSITION_UNAVAILABLE` (code 2): show "Cannot get location — are you indoors?" with retry button.
- On `TIMEOUT` (code 3): retry once with higher timeout (15s instead of 5s), then show error.
- Add a 15-second timeout to `getCurrentPosition()` — mobile browsers can hang indefinitely without one.
- Test geolocation flows specifically on a physical iOS device, not just iPhone simulator (simulator GPS is unreliable).

**Warning signs:**
- iOS users reporting the checkin button "does nothing" — likely permission denied silently
- `PERMISSION_DENIED` errors in client-side error tracking from iOS devices
- Location accuracy reported as >100m on indoor requests (not an error, but geofence may need widening)

**Phase to address:**
Geolocation checkin UI phase — implement robust error handling and platform detection before wiring to backend.

---

### Pitfall 7: Notification Permission Requested Too Early — One-Shot Prompt Wasted

**What goes wrong:**
Calling `Notification.requestPermission()` on app load or first visit results in the browser's native permission dialog appearing before the user understands the app. Students dismiss or block it. Once blocked, the browser will never show the prompt again — there is no programmatic way to re-trigger a browser permission dialog. The push notification feature is permanently dead for that user unless they manually go into browser settings.

**Why it happens:**
It's the simplest implementation: subscribe on load. Developers underestimate how jarring a permission prompt feels when a user has just opened an app for the first time.

**How to avoid:**
Use the "double permission" (soft ask first) pattern:
1. Show an in-app card/modal explaining WHY push is useful ("Get notified 5 minutes before each class starts — tap Allow to enable").
2. Only if the user taps "Allow" on your custom UI, call `Notification.requestPermission()` to trigger the browser dialog.
3. If the user taps "Not now" on your UI, store the decision and re-ask after 3 days or after the first missed checkin (contextual timing).

Never call `Notification.requestPermission()` outside a user gesture on iOS — it will be silently rejected (no dialog, no error).

**Warning signs:**
- Permission prompt appearing before the login screen
- Push opt-in rate below 15% (industry benchmark for late-ask is 40–60%)
- Users have `Notification.permission === 'denied'` on first session

**Phase to address:**
Push permission UX phase — design and implement the soft-ask flow before VAPID subscription wiring.

---

### Pitfall 8: Extending notification-web With Web Push Breaks STOMP Threading Model

**What goes wrong:**
`notification-web` currently runs as a Spring Boot app with an in-memory STOMP broker. Adding Web Push involves making outbound HTTPS calls to push service endpoints (FCM, APNs, etc.) per-user — potentially many concurrent requests. If these outbound HTTP calls are made synchronously on the STOMP message handling thread (the thread that consumes RabbitMQ messages), the consumer blocks. Under lesson start events (30+ students), the RabbitMQ consumer stalls, message queue grows, and other events (cancellation, homework) are delayed.

**Why it happens:**
The natural place to add push delivery is in the existing RabbitMQ message handler method — "handle event → send WebSocket → also send push." The outbound push HTTP call (to Google/Apple servers) can take 200–2000ms. 30 students × 1000ms = 30s of blocked consumer time.

**How to avoid:**
- Add push delivery as a separate `@Async` task submitted from the event handler, using a dedicated thread pool (`ThreadPoolTaskExecutor`) with queue capacity for bursts.
- The event handler stays non-blocking: `messagingTemplate.convertAndSend(...)` (STOMP, fast) + `pushDispatcher.sendAsync(subscriptions, payload)` (async, does not block consumer).
- Configure the thread pool: corePoolSize=5, maxPoolSize=20, queueCapacity=200. Size for peak lesson start (N_groups × N_students_per_group).
- Use `PushAsyncService` from `nl.martijndwars:web-push` (or OkHttp async calls) — not the blocking `PushService`.

**Warning signs:**
- STOMP WebSocket messages arriving late during lesson start peak periods
- RabbitMQ consumer lag growing in RabbitMQ management UI during business hours
- `notification-web` thread count spiking to container max under load

**Phase to address:**
notification-web Web Push backend phase — design the async dispatch architecture before writing any push send code.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| `localStorage` for JWT | Simple, works everywhere | XSS vulnerability, no SW access | Never — use memory + httpOnly cookie |
| `skipWaiting()` unconditionally | Instant updates | Blank screens on active sessions | Never in production |
| No subscription cleanup on 410 | Simpler backend code | Dead endpoint accumulation, degraded delivery | Never |
| `@CrossOrigin("*")` on downstream services | Fixes local dev | Duplicate headers in production, security risk | Dev only, never commit |
| Ask permission on app load | Fewer lines of code | One-shot prompt wasted, low opt-in rate | Never |
| Synchronous push send in event handler | Simpler code | Blocks RabbitMQ consumer under load | Never with fan-out |
| Precache all API responses | Offline looks good | Stale attendance/schedule data served | Never for dynamic data |
| Cache-first for schedule API | Fast loads | Student sees yesterday's schedule offline | Never — use network-first for schedule |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| PWA → API Gateway | Using relative URLs (e.g., `/api/auth`) — works on same origin, breaks on CDN | Use env-var `VITE_API_BASE_URL=https://api.rutcampustrack.ru` at build time |
| PWA → API Gateway | Not including `credentials: 'include'` in fetch for cookie-based refresh token | Add `credentials: 'include'` to all fetch calls; Gateway must have `allowCredentials: true` |
| Service Worker → API Gateway | SW fetch bypass (SW caches a redirect, browser never follows it) | Return actual resources, not redirects, from API behind Gateway |
| VAPID key pair | Regenerating VAPID keys after subscriptions exist | All existing subscriptions become invalid; treat VAPID keys as immutable once generated |
| notification-web → Web Push | Sending VAPID `sub` claim as plain email (e.g., `noreply@example.com`) | Must be `mailto:noreply@example.com` — Apple APNs returns 403 Forbidden without `mailto:` prefix |
| Spring Boot cookie | `Set-Cookie` for refresh token not reaching PWA on different domain | `SameSite=None; Secure` required for cross-origin cookie; Gateway must be same domain as PWA or use subdomain |
| Push subscription → DB | Storing raw `PushSubscription` JSON as text | Store `endpoint`, `keys.p256dh`, `keys.auth` as separate columns — enables cleanup queries by endpoint |
| iOS PWA install | Relying on `beforeinstallprompt` event to show A2HS prompt | iOS Safari does not fire `beforeinstallprompt` — must show manual instructions specific to Safari share button |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Precaching entire schedule data in SW | App takes 10+ seconds to install, users abort | Cache only app shell (HTML/CSS/JS), not API data | First install with >100 schedule entries |
| Per-user push send in tight loop | notification-web CPU spikes, message queue backs up | Async thread pool + PushAsyncService | >10 concurrent students in same group |
| IndexedDB for offline schedule with no eviction | Storage grows unbounded, iOS 50MB limit hit, data evicted | Store only current + next 7 days; evict past lessons on SW activate | After 2-3 weeks of daily use on iOS |
| Real-time geolocation watch (`watchPosition`) left active | Battery drain on student's phone during entire class | Use `getCurrentPosition()` once for checkin, not continuous watch | Immediate (all mobile platforms) |
| React re-renders on WebSocket message | UI jank while WebSocket messages stream in rapidly | Debounce or batch state updates from STOMP messages | During lesson start (multiple event types at once) |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| VAPID private key stored in code / git | Entire push service compromised — attacker sends push as your server | Store in environment variable or secrets manager; never in codebase |
| Push subscription endpoint stored without user binding | Attacker who gains DB read can send push to arbitrary users | Bind subscription to `user_id` in DB; validate ownership on every push |
| Service Worker intercepts auth token refresh without HTTPS check | Token refresh over plain HTTP if HTTPS misconfigured | SW registration requires HTTPS (except localhost) — verify redirect chain is HTTPS-only |
| Trusting notification click payload data as authenticated | Notification payload is client-visible — do not use it for authorization decisions | Push payload = display data only; re-authenticate via API on notification click action |
| `SameSite=Lax` on refresh token cookie | Cross-site POST can trigger token refresh (CSRF) | Use `SameSite=Strict` for refresh cookie; never `Lax` or `None` without CSRF token |
| Caching API responses containing PII in SW cache | Other users on shared device can access cached attendance data | Never cache authenticated API responses in SW; scope cache to app shell only |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No offline indicator | Students think checkin failed, retry, get duplicate request | Show "You're offline — checkin will be sent when connection restored" banner |
| Permission blocked with no recovery path shown | Students who blocked push never get reminders | On every settings page visit, check `Notification.permission === 'denied'` and show "Enable in browser settings" with deep-link instructions |
| Push notification with no action URL | Tapping notification opens PWA root, not the relevant lesson | Always set `data.url` in push payload and handle `notificationclick` in SW to navigate to the correct route |
| Install prompt shown before value is demonstrated | User installs, then uninstalls immediately | Show A2HS prompt after first successful checkin — user has seen the value |
| Geolocation spinner with no timeout | Student stands outside with poor GPS, spinner runs forever | 15-second hard timeout on `getCurrentPosition()`; show "Location taking too long — try moving outside" |
| "Allow notifications" button in app while permission is `denied` | Button appears active but tapping it silently does nothing | Check `Notification.permission` before showing the button; if `denied`, show "Enable in Settings" link instead |

---

## "Looks Done But Isn't" Checklist

- [ ] **VAPID subscription:** Keys generated and stored — verify they are NOT in git history and NOT regenerated on app restart
- [ ] **Push delivery:** Notification sends on happy path — verify 410/404 responses delete the subscription from DB
- [ ] **Service Worker update:** SW installs — verify update flow uses user-driven activation, not unconditional `skipWaiting()`
- [ ] **iOS push:** Works on Android Chrome — verify on physical iOS 16.4+ device after Add to Home Screen
- [ ] **CORS:** PWA fetches work in dev — verify no duplicate `Access-Control-Allow-Origin` in production with `curl -I`
- [ ] **JWT in cookies:** Token appears to work — verify `httpOnly` flag is set by inspecting `Set-Cookie` response header
- [ ] **Offline checkin:** App loads offline — verify that cached attendance data is not served stale for >15 minutes
- [ ] **Permission UX:** Notification opt-in implemented — verify soft-ask appears before native browser dialog
- [ ] **Geolocation error handling:** Location succeeds in ideal conditions — verify all three error codes handled (1, 2, 3) with actionable messages
- [ ] **pushsubscriptionchange handler:** SW registered — verify handler posts new subscription to backend (easy to forget, hard to test)

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| VAPID keys regenerated (all subscriptions invalid) | HIGH | Generate new key pair, update backend config, force re-subscription on next app load via flag in API response |
| Dead subscriptions accumulated (no 410 cleanup) | MEDIUM | Write migration script to send test push to all subscriptions, delete all that return 410/404 |
| localStorage JWT (XSS discovered) | HIGH | Rotate all JWT signing keys, invalidate all refresh tokens, migrate to cookie storage, notify users to re-login |
| Duplicate CORS headers in production | LOW | Add `DedupeResponseHeader` filter in Gateway config, redeploy Gateway only |
| iOS push permission blocked by all early-ask users | MEDIUM | Cannot re-trigger programmatically; add in-app "Enable in Safari Settings" guide; accept lower iOS opt-in rate |
| SW stuck in waiting (users see stale app) | LOW | Add user-facing "New version available" banner with reload action; existing users unblocked on next explicit reload |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| skipWaiting race condition | Service Worker scaffold (Phase 1 of v6.0) | Test with 2 tabs open: deploy update, verify no chunk load error |
| iOS push only in standalone | A2HS onboarding + push subscription phase | Test on physical iOS device in Safari tab — must show install instructions |
| Duplicate CORS headers | API Gateway CORS config phase (before PWA wiring) | `curl -I` response for preflight; check header count = 1 |
| JWT in localStorage | PWA auth layer phase | DevTools → Application → Local Storage must be empty of tokens |
| Expired push endpoints | notification-web Web Push backend phase | Simulate 410 response, verify subscription deleted from DB |
| iOS geolocation permission mismatch | Geolocation checkin UI phase | QA on physical iOS with location denied in Settings |
| Permission prompt too early | Push permission UX phase | Verify permission dialog does NOT appear on first page load |
| Blocking push send in STOMP handler | notification-web async architecture phase | Load test with 30 concurrent users; verify STOMP messages not delayed |

---

## Sources

- [PWA Push Notifications on iOS in 2026: What Really Works](https://webscraft.org/blog/pwa-pushspovischennya-na-ios-u-2026-scho-realno-pratsyuye?lang=en)
- [PWA iOS Limitations and Safari Support 2026 — MagicBell](https://www.magicbell.com/blog/pwa-ios-limitations-safari-support-complete-guide)
- [Permission UX — web.dev](https://web.dev/articles/push-notifications-permissions-ux)
- [Web Push Error 410: subscription expired — Pushpad](https://pushpad.xyz/blog/web-push-error-410-the-push-subscription-has-expired-or-the-user-has-unsubscribed)
- [Web Push errors HTTP status codes — Pushpad](https://pushpad.xyz/blog/web-push-errors-explained-with-http-status-codes)
- [Service Worker Lifecycle — Felix Gerschau](https://felixgerschau.com/service-worker-lifecycle-update/)
- [Handling Service Worker updates — Chrome for Developers (Workbox)](https://developer.chrome.com/docs/workbox/handling-service-worker-updates)
- [CORS Configuration — Spring Cloud Gateway official docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html)
- [LocalStorage vs Cookies JWT storage — Cyber Chief](https://www.cyberchief.ai/2023/05/secure-jwt-token-storage.html)
- [Race Conditions in JWT Refresh Token Rotation — DEV Community](https://dev.to/silentwatcher_95/race-conditions-in-jwt-refresh-token-rotation-3j5k)
- [Handling Geolocation for PWA Safari — Poespas Blog](https://blog.poespas.me/posts/2025/03/01/handling-geolocation-for-pwa-safari-challenges/)
- [webpush-java library — web-push-libs GitHub](https://github.com/web-push-libs/webpush-java)
- [Sending web push notifications from Spring Boot — Vaadin](https://vaadin.com/blog/send-web-push-notifications-java)
- [Service Workers Caching — MDN](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Caching)
- [STOMP WebSocket Spring Boot — Spring Framework docs](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)

---
*Pitfalls research for: PWA + Web Push added to existing Spring Boot microservice attendance system*
*Researched: 2026-04-05*
