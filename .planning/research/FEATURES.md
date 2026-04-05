# Feature Research

**Domain:** PWA Mobile Client «RutTrack» — Student attendance app with Web Push notifications
**Researched:** 2026-04-05
**Project:** RutCampusTrack v6.0 PWA + Web Push
**Confidence:** HIGH — based on PROJECT.md, job-stories.md, design-decisions.md, phases-plan.md, Web Push API docs, and comparable PWA attendance system research.

---

## Context: What Already Exists (Must Not Re-Implement)

All backend APIs are operational. The PWA consumes them. No new backend services are created in v6.0 (only VAPID push subscription management is added to notification-web).

### Available Backend Endpoints (via API Gateway :8080)

| Endpoint | Service | What PWA Uses It For |
|----------|---------|---------------------|
| `POST /api/auth/login` | auth-service:9090 | Student login (username + password) |
| `POST /api/auth/refresh` | auth-service:9090 | Token refresh (auto-refresh before expiry) |
| `POST /api/auth/logout` | auth-service:9090 | Logout (invalidates refresh token) |
| `POST /api/auth/otp/request` | auth-service:9090 | OTP request via Telegram |
| `POST /api/auth/otp/verify` | auth-service:9090 | OTP verify → JWT pair |
| `GET /api/academic/students/{id}/profile` | academic-service:9091 | Student profile |
| `GET /api/academic/groups/{id}/members` | academic-service:9091 | Group composition |
| `GET /api/academic/homeworks` | academic-service:9091 | Homework list for group |
| `POST/DELETE /api/academic/homework-completions` | academic-service:9091 | Personal homework tracker |
| `GET /api/schedule/lessons` | schedule-service:9092 | Schedule (by date range + group) |
| `GET /api/schedule/lessons/{id}` | schedule-service:9092 | Single lesson detail |
| `POST /api/attendance/check-in` | attendance-service:9093 | Geo check-in submission |
| `GET /api/reports/student-stats` | attendance-service:9093 | Student attendance stats |
| `GET /api/reports/student-records` | attendance-service:9093 | Attendance records list |
| `POST /api/attendance/excuse-tickets` | attendance-service:9093 | Create excuse ticket (deferred) |
| `POST /api/attendance/late-checkin` | attendance-service:9093 | Late check-in request (deferred) |
| `WS /api/ws` (STOMP) | notification-web:9094 | Real-time push events via WebSocket |

### Existing Notification Infrastructure

- STOMP WebSocket at `/api/ws` with JWT handshake (query param `token`)
- 5 push event types already delivered: `lesson.started`, `lesson.cancelled`, `homework.published`, `excuse.requested`, `late_checkin.requested`
- Telegram bot already covers same events — Web Push duplicates the channel

### New Backend Needed for Web Push

notification-web needs 2 new endpoints (VAPID subscription management):
- `POST /api/ws/push/subscribe` — store PushSubscription object per user
- `DELETE /api/ws/push/subscribe` — unsubscribe

These are lightweight additions to the existing Java WebSocket service, not a new service.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features the PWA cannot ship without. Missing = product feels incomplete or broken.

| Feature | Why Expected | Complexity | Dependency on Backend |
|---------|--------------|------------|----------------------|
| **Login screen** | Entry point to all features. Students need username/password login. | LOW | `POST /api/auth/login`, `POST /api/auth/refresh` |
| **Today's schedule view** | The primary daily-use screen. Students check what pairs they have today before going to campus. Missing = app is useless. | LOW | `GET /api/schedule/lessons?from=today&to=today&groupId=X` |
| **Geo check-in button** | Core product function. Student taps "Отметиться", PWA requests GPS coords, sends to backend. Visible only during active pair window (5 min before start → 5 min after end). | MEDIUM | `POST /api/attendance/check-in`, Geolocation API (browser) |
| **Check-in result feedback** | Immediate visual confirmation: "Отмечено" (green) or "Не в зоне кампуса" (red) after submission. | LOW | Same as check-in |
| **Weekly schedule view** | Students plan their week. Missing = they go back to paper schedule or old Telegram bot. | LOW | `GET /api/schedule/lessons?from=weekStart&to=weekEnd&groupId=X` |
| **Attendance stats screen** | Students track their own attendance percentage per subject. Core student concern (red zone threshold awareness). | MEDIUM | `GET /api/reports/student-stats` |
| **JWT auto-refresh** | Access tokens expire in 15 min. Without silent refresh, student is logged out mid-session. | MEDIUM | `POST /api/auth/refresh` |
| **"Add to Home Screen" prompt** | PWA identity feature. Students expect the app install prompt. Without it, it's just a website. Per design-decisions.md: show iOS Safari instruction for iOS users. | LOW | browser `beforeinstallprompt` API + manifest.json |
| **Offline shell (app loads without network)** | Students check schedule before entering a building with bad signal. At minimum the cached schedule and stats must be readable. | MEDIUM | Service Worker + Cache API (no backend call needed offline) |
| **Logout** | Security expectation. Essential on shared devices. | LOW | `POST /api/auth/logout` |

---

### Differentiators (Competitive Advantage)

Features that make RutTrack meaningfully better than the Telegram bot alone.

| Feature | Value Proposition | Complexity | Dependency on Backend |
|---------|-------------------|------------|----------------------|
| **Web Push notifications (lesson.started with check-in action button)** | Notifications arrive even with PWA closed. Tap "Отметиться" action button → PWA opens directly on check-in screen. This is the v6.0 headline feature — the reason for the milestone. | HIGH | VAPID: `POST /api/ws/push/subscribe`. Event delivery: notification-web pushes via Web Push API. Service Worker handles push event and `notificationclick`. |
| **Web Push: lesson cancelled** | Student gets notified about cancellation without opening Telegram. PWA becomes an independent parallel channel as stated in design-decisions.md. | MEDIUM | notification-web pushes `lesson.cancelled` event via Web Push. Service Worker shows notification with "Посмотреть расписание" action. |
| **Web Push: homework published** | Students get homework assignments outside Telegram. Reduces dependency on bot being linked. | MEDIUM | notification-web pushes `homework.published`. Service Worker shows notification. |
| **Notification permission onboarding flow** | Ask for push permission after value has been demonstrated (not on first visit). Show a clear value proposition: "Получайте уведомления о начале пар прямо на телефон". | LOW | `PushManager.subscribe()` with VAPID public key |
| **iOS onboarding screen** | iOS users need to install via Safari → Share → Add to Home Screen before Web Push works (iOS 16.4+ requirement). Show step-by-step illustrated guide on first visit from iOS Safari. | MEDIUM | None — pure frontend |
| **Homework tracker (personal completions)** | Student marks homework as done. Visual progress. Differentiates from Telegram bot which only shows homework text. | MEDIUM | `POST/DELETE /api/academic/homework-completions` |
| **Attendance records list with status indicators** | See each pair with its status (б/н/у/сп) with color coding. Students understand why their percentage is what it is. | MEDIUM | `GET /api/reports/student-records` |
| **Red zone warning indicator** | Show a visual warning when student's attendance drops below the threshold for a subject. Proactive — student knows before it becomes a problem. | LOW | `GET /api/reports/student-stats` (threshold is in the response) |
| **Real-time check-in state via WebSocket** | When STOMP WebSocket receives `attendance.marked` for the current user, update the UI to "Отмечено" without page refresh or polling. Feels native. | MEDIUM | STOMP WebSocket `/api/ws` — `attendance.marked` event |
| **Headman view: manual marking** | Headman can mark attendance for their group from the PWA. Useful on-the-go — no need to open the desktop web panel. Per JS-HEADMAN-20. | HIGH | `POST /api/attendance/manual` — requires group member list + status grid UI |
| **Offline cached schedule (stale-while-revalidate)** | Schedule is cached in IndexedDB/Cache API. Student can see today's schedule even with no network. Per JS-STUDENT-10 and design-decisions.md: "кэшировать расписание, статистику, ДЗ для чтения". | MEDIUM | Service Worker intercepts `/api/schedule/lessons` and serves from cache on failure |

---

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **In-app Telegram bot commands** | "Replace the bot" — students want one app | The Telegram bot serves students who never install the PWA. Killing the bot removes the primary channel for non-PWA users. Per design-decisions.md: "PWA coexists with Mini App — both channels active". | Keep both. Web Push duplicates Telegram push; students use whichever channel they prefer. |
| **Excuse ticket file upload from PWA** | Students want to submit medical certificates from their phone camera | File storage architecture is deferred (see PROJECT.md Out of Scope). The excuse ticket flow (create/submit/review) itself is deferred. Building file upload in PWA before the ticket flow is designed would be wasted work. | Defer to v6.1+ when excuse ticket backend flow is unblocked. |
| **PWA push for ALL roles (teachers, headmen, admins)** | Teachers want schedule updates too | Web Push subscription management and notification routing by role adds significant complexity to notification-web. Teachers and admins have the Angular web panel (v8.0). Students are the priority audience for PWA. | Ship Web Push for STUDENT role first. Add TEACHER support in v8.0 when the web panel is built. |
| **Background sync for check-in (offline queue)** | "What if the check-in request fails? Queue it and retry when online" | Attendance check-in has a strict 5-min time window. A background sync retry firing 10 minutes later would fail validation on the backend anyway. Storing a pending geo check-in offline creates a false sense of "it'll work". | Show clear "No network connection" message (per JS-STUDENT-10). Do not queue geo check-in. |
| **Real-time attendance dashboard (who's present in my group)** | Headmen want to see live presence | This is the journal grid feature for headmen. Building a live group dashboard in PWA is high complexity (WebSocket updates + student list) and duplicates the web panel functionality. | Headman can mark attendance from PWA (the differentiator above) but the full journal belongs in the desktop web panel (v8.0). |
| **PWA app update notification ("New version available")** | Users expect to know when the app updates | vite-plugin-pwa handles SW updates via `registerSW` with `onNeedRefresh` callback. Showing a modal "Update available" is standard but blocks users. | Use `autoUpdate` strategy for transparent updates on next navigation. No user-blocking modal. |
| **Push notifications when PWA is in foreground** | "Show a popup even when I'm using the app" | Foreground push notifications duplicate the real-time WebSocket events already handled by the STOMP connection. The SW push fires when the app is closed/background. When in foreground, the app already receives the event via WebSocket. | In `push` Service Worker event handler: check `clients.matchAll()` — if any window is focused, skip `showNotification()`. The app handles it in-UI via WebSocket. |
| **OTP login via PWA (password-less)** | Students who forget passwords want Telegram OTP | OTP flow works but requires the student to have the Telegram bot linked already. If they're on PWA first, they may not have done /start. This creates a confusing chicken-and-egg setup flow. | Primary login = username + password. Offer OTP as secondary option only for users who know their telegram_id is linked. |

---

## Feature Dependencies

```
[Login + JWT storage]
    └──required-by──> ALL authenticated features

[manifest.json + HTTPS]
    └──required-by──> [Add to Home Screen prompt]
    └──required-by──> [Service Worker registration]
    └──required-by──> [Web Push subscription]

[Service Worker registration]
    └──required-by──> [Offline shell (app shell caching)]
    └──required-by──> [Offline schedule cache]
    └──required-by──> [Web Push push event handler]
    └──required-by──> [notificationclick handler]

[Web Push subscription (VAPID)]
    └──requires──> [Notification permission granted by user]
    └──requires──> [PWA installed to Home Screen (iOS only)]
    └──requires──> [VAPID backend endpoint in notification-web]
    └──required-by──> [lesson.started push notification]
    └──required-by──> [lesson.cancelled push notification]
    └──required-by──> [homework.published push notification]

[Today's schedule view]
    └──requires──> [Login + JWT]
    └──enhances──> [Geo check-in button] (check-in shown on active lesson card)

[Geo check-in button]
    └──requires──> [Today's schedule view] (lesson_id must be known)
    └──requires──> [Browser Geolocation API permission]
    └──requires──> [Active lesson exists (schedule service state)]

[Attendance stats screen]
    └──requires──> [Login + JWT]
    └──enhances──> [Red zone warning indicator] (same API response)

[Real-time check-in via WebSocket]
    └──requires──> [STOMP WebSocket connection]
    └──enhances──> [Geo check-in button] (updates UI on attendance.marked event)

[Headman manual marking]
    └──requires──> [Login + JWT with is_headman=true]
    └──requires──> [Group member list] (GET /api/academic/groups/{id}/members)
    └──requires──> [Active lesson selection] (GET /api/schedule/lessons)

[iOS onboarding screen]
    └──must-appear-before──> [Web Push subscription request] (iOS requires A2HS install first)

[Homework tracker]
    └──requires──> [Homework list from Academic Service]
    └──independent-of──> [Schedule] (can be navigated to separately)
```

### Dependency Notes

- **Web Push on iOS requires PWA installed to Home Screen first.** iOS 16.4+ supports Web Push only for installed PWAs. The iOS onboarding screen (Safari → Share → Add to Home Screen) must be shown and completed before requesting notification permission. Never call `Notification.requestPermission()` on non-installed iOS Safari — it silently fails.
- **Geo check-in requires an active lesson.** The check-in button should only be shown when a lesson is in `active` status for the student's group. The schedule view must reflect this — poll or use the WebSocket `lesson.started` event to activate the button.
- **VAPID keys must be generated once and stored server-side.** The VAPID public key is embedded in the PWA at build time (or fetched once from a public endpoint). The private key never leaves notification-web. Generate once, store in Docker env var.
- **Offline check-in is not supported.** The check-in path is deliberately network-only. The Service Worker must not cache `POST /api/attendance/check-in` requests.
- **Push subscription must be re-stored on re-registration.** When the Service Worker updates, the push subscription may change. The PWA must re-POST the subscription to the backend after each SW update.

---

## MVP Definition

### Launch With (v6.0 — PWA Core)

Minimum viable mobile client validating that students can use RutTrack as a daily tool.

**Authentication:**
- [ ] Login screen (username + password) with JWT storage in localStorage/sessionStorage
- [ ] Auto-refresh of access token (15-min expiry) using refresh token
- [ ] Logout

**Schedule:**
- [ ] Today's schedule view — list of lessons with time, subject name, room, status
- [ ] Weekly schedule navigation (swipe/tab between days)
- [ ] Offline cached schedule (Service Worker stale-while-revalidate for schedule endpoints)

**Check-in:**
- [ ] Geo check-in button visible on active lesson card
- [ ] GPS coordinate capture via browser Geolocation API
- [ ] Submit to `POST /api/attendance/check-in`
- [ ] Show success/failure feedback with reason (not in zone / no active lesson / already marked)
- [ ] Offline: show "Нет подключения" message instead of attempting

**PWA basics:**
- [ ] manifest.json: name "RutTrack", display: standalone, icons 192x192 + 512x512
- [ ] Service Worker registration via vite-plugin-pwa
- [ ] App shell cached on install (HTML, CSS, JS — loads without network)
- [ ] A2HS prompt handling (`beforeinstallprompt` → deferred prompt shown after first successful check-in)
- [ ] iOS onboarding screen (detect iOS + not installed → show Safari install instructions)

**Web Push:**
- [ ] VAPID key pair generation and storage in notification-web
- [ ] `POST /api/ws/push/subscribe` endpoint in notification-web
- [ ] Service Worker push event handler → `showNotification()`
- [ ] Service Worker `notificationclick` handler → open PWA on check-in screen
- [ ] Permission request flow (triggered by user action, not on first load)
- [ ] `lesson.started` Web Push notification with "Отметиться" action button
- [ ] `lesson.cancelled` Web Push notification

### Add After Validation (v6.1)

- [ ] Attendance stats screen (subject percentage, red zone indicator) — backend ready, high value
- [ ] Attendance records list with status color coding — backend ready
- [ ] Homework list + personal completion tracker — backend ready
- [ ] `homework.published` Web Push notification
- [ ] Real-time check-in state via STOMP WebSocket (`attendance.marked` → update UI)
- [ ] Headman manual marking screen — requires complex group marking grid UI
- [ ] Weekly schedule view improvements (current week highlighting, status badges)

### Future Consideration (v6.2+)

- [ ] Excuse ticket creation flow — blocked on backend (deferred in PROJECT.md)
- [ ] Late check-in request flow — blocked on backend (deferred in PROJECT.md)
- [ ] Push notification preferences (mute by type) — needs preferences storage backend
- [ ] Web Push for TEACHER role — defer to v8.0 web panel milestone
- [ ] PDF/Excel export — deferred project-wide

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Login + JWT storage + auto-refresh | HIGH | LOW | P1 — gates everything |
| Today's schedule view | HIGH | LOW | P1 — primary daily use |
| Geo check-in button + GPS submit | HIGH | MEDIUM | P1 — core product function |
| Check-in feedback (success/fail reason) | HIGH | LOW | P1 — without it, student doesn't know if it worked |
| manifest.json + A2HS prompt | HIGH | LOW | P1 — PWA identity |
| Service Worker + app shell cache | HIGH | MEDIUM | P1 — "offline" requirement in job stories |
| iOS onboarding instructions | HIGH | LOW | P1 — ~50% of students likely on iOS |
| VAPID setup + push/subscribe endpoint | HIGH | MEDIUM | P1 — Web Push headline feature |
| lesson.started push notification | HIGH | MEDIUM | P1 — primary student engagement |
| lesson.cancelled push notification | HIGH | LOW | P1 — students must know about cancellations |
| Weekly schedule navigation | MEDIUM | LOW | P1 — users expect week view |
| Offline schedule cache | MEDIUM | MEDIUM | P1 — stated in design-decisions.md |
| Attendance stats screen | HIGH | MEDIUM | P2 — high value but schedule + checkin first |
| Attendance records list | MEDIUM | LOW | P2 — supports understanding of stats |
| Homework list + completions | MEDIUM | MEDIUM | P2 — valuable but not day-1 critical |
| homework.published push notification | MEDIUM | LOW | P2 — once lesson push works, same pattern |
| Real-time WebSocket check-in state | MEDIUM | MEDIUM | P2 — nice UX but polling works too |
| Headman manual marking | HIGH | HIGH | P2 — high value for headmen, complex UI |
| Red zone warning indicator | MEDIUM | LOW | P2 — trivial add-on to stats screen |
| Excuse ticket flow | HIGH | HIGH | P3 — backend deferred |
| Late check-in request | MEDIUM | MEDIUM | P3 — backend deferred |

**Priority key:**
- P1: Must have for v6.0 launch
- P2: Ship in v6.1 iteration once core is validated
- P3: Future milestone / blocked on backend

---

## Competitor Feature Analysis

| Feature | Telegram Mini App (existing) | Telegram Bot (existing) | RutTrack PWA (target) |
|---------|------------------------------|------------------------|----------------------|
| Check-in | Via bot inline button from lesson.started message | Inline "Отметиться" button | Geo check-in screen in PWA, also via push notification action |
| Schedule view | Limited — bot text replies | Text list via /status | Full schedule UI with weekly view, status badges |
| Push notifications | Telegram messages | Telegram messages | Web Push to phone even with app closed |
| Offline access | No | No | Yes — cached schedule and stats |
| Installable | No | No | Yes — A2HS, standalone mode, home screen icon |
| Attendance stats | No dedicated view | /status shows current lesson | Full stats screen with per-subject percentages |
| Homework tracker | No | Notifications only | List + personal completion checkboxes |
| iOS support | Via Telegram app | Via Telegram app | Safari PWA + Web Push (iOS 16.4+) |
| No Telegram required | No | No | Yes — PWA is independent channel |

---

## Platform-Specific Considerations

### iOS (Safari)

- Web Push requires PWA installed to Home Screen (iOS 16.4+). Chrome/Firefox on iOS use WebKit — same restriction.
- Storage quota: ~50MB cache. Aggressive eviction if app not used for 7 days. Solution: prioritize caching current week schedule (small) over historical records.
- `beforeinstallprompt` does NOT fire on iOS. Detect iOS (`navigator.userAgent`) + not in standalone mode → show manual instruction banner.
- `Notification.requestPermission()` must be called from a user gesture within the installed PWA. Never on page load.

### Android (Chrome)

- Full Web Push support. `beforeinstallprompt` fires after meeting PWA installability criteria.
- A2HS prompt can be shown automatically or deferred and triggered on user action. Prefer deferred — show after first successful check-in for maximum relevance.
- Background sync available but deliberately NOT used for check-in (time window constraint).

### Network Conditions

- University buildings often have poor mobile signal. PWA must handle network timeout on check-in gracefully (show retry button, not silent failure).
- Schedule endpoint should be cached with stale-while-revalidate — serve cached data immediately, update in background. Max stale age: 1 hour (schedule changes are announced in advance via lesson.cancelled events).

---

## Sources

- `.planning/PROJECT.md` — v6.0 active requirements, target features, deferred items (HIGH confidence)
- `docs/job-stories.md` — JS-STUDENT-01 through JS-STUDENT-11, JS-HEADMAN-20, JS-TEACHER-08, JS-SYSTEM-11..12 (HIGH confidence)
- `docs/design-decisions.md` — PWA design decisions section 3, branding, offline caching strategy, iOS onboarding (HIGH confidence)
- `CLAUDE.md` — existing endpoint architecture, business rules (check-in window, roles), notification rules (HIGH confidence)
- [MDN Web Push / Push API docs](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Tutorials/js13kGames/Re-engageable_Notifications_Push) — Web Push implementation guidance (HIGH confidence)
- [vite-plugin-pwa documentation](https://vite-pwa-org.netlify.app/) — Workbox integration, generateSW strategy, service worker registration (HIGH confidence — actively maintained 2025)
- [iOS PWA limitations 2026 guide](https://www.mobiloud.com/blog/progressive-web-apps-ios) — iOS 16.4+ push requirements, storage limits (MEDIUM confidence — verify iOS version at implementation time)
- [Offline-First PWA caching strategies](https://www.magicbell.com/blog/offline-first-pwas-service-worker-caching-strategies) — cache-first for shell, stale-while-revalidate for schedule, network-first for check-in (HIGH confidence — matches MDN guidance)
- [PWA UX tips 2025](https://lollypop.design/blog/2025/september/progressive-web-app-ux-tips-2025/) — A2HS timing, notification permission UX patterns (MEDIUM confidence)

---

*Feature research for: PWA Mobile Client «RutTrack» — student attendance + Web Push*
*Researched: 2026-04-05*
