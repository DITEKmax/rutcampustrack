# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- ✅ **v3.0 Schedule Service** — Phases 10-14 (shipped 2026-04-04)
- ✅ **v4.0 Attendance Service MVP** — Phases 15-19 (shipped 2026-04-04)
- ✅ **v5.0 Notification Service (Web + Bot)** — Phases 20-26 (shipped 2026-04-05)
- 🚧 **v6.0 PWA + Web Push** — Phases 27-32 (in progress)

## Phases

<details>
<summary>✅ v1.0 Auth Service + API Gateway (Phases 1.1-1.4) — SHIPPED 2026-03-30</summary>

- [x] Phase 1.1: Auth Service Core — JWT + Login (1/1 plan) — completed 2026-03-28
- [x] Phase 1.2: OTP Flow + Change Password (1/1 plan) — completed 2026-03-29
- [x] Phase 1.3: API Gateway JWT Filter + Routing (1/1 plan) — completed 2026-03-30
- [x] Phase 1.4: Seed Data + Integration Testing (1/1 plan) — completed 2026-03-30

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Academic Service (Phases 5-9) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Entity and Repository Foundation (2/2 plans) — completed 2026-03-30
- [x] Phase 6: REST API + HATEOAS (4/4 plans) — completed 2026-03-30
- [x] Phase 7: gRPC Server (2/2 plans) — completed 2026-03-30
- [x] Phase 8: Redis Caching (2/2 plans) — completed 2026-03-31
- [x] Phase 9: RabbitMQ Events (2/2 plans) — completed 2026-03-31

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

<details>
<summary>✅ v3.0 Schedule Service (Phases 10-14) — SHIPPED 2026-04-04</summary>

- [x] Phase 10: Foundation (2/2 plans) — completed 2026-04-01
- [x] Phase 11: REST API + gRPC Client (3/3 plans) — completed 2026-04-01
- [x] Phase 12: Lesson Auto-Generation (2/2 plans) — completed 2026-04-01
- [x] Phase 13: Status Transitions + RabbitMQ Events (2/2 plans) — completed 2026-04-03
- [x] Phase 14: gRPC Server (2/2 plans) — completed 2026-04-04

Full details: `.planning/milestones/v3.0-ROADMAP.md`

</details>

<details>
<summary>✅ v4.0 Attendance Service MVP (Phases 15-19) — SHIPPED 2026-04-04</summary>

- [x] Phase 15: Infrastructure Foundation (2/2 plans) — completed 2026-04-04
- [x] Phase 16: Event Consumers (2/2 plans) — completed 2026-04-04
- [x] Phase 17: Write Path — Geo-Checkin + Manual Marking (3/3 plans) — completed 2026-04-04
- [x] Phase 18: Read Path — Reports (4/4 plans) — completed 2026-04-04
- [x] Phase 19: Report Security & Routing Fix (1/1 plan) — completed 2026-04-04

Full details: `.planning/milestones/v4.0-ROADMAP.md`

</details>

<details>
<summary>✅ v5.0 Notification Service (Web + Bot) (Phases 20-26) — SHIPPED 2026-04-05</summary>

- [x] Phase 20: Shared Infrastructure (3/3 plans) — completed 2026-04-04
- [x] Phase 21: Notification Web — WebSocket Core (2/2 plans) — completed 2026-04-05
- [x] Phase 22: Bot Infrastructure Layer (3/3 plans) — completed 2026-04-05
- [x] Phase 23: Bot Telegram Commands (3/3 plans) — completed 2026-04-05
- [x] Phase 24: Bot Event Notifications (2/2 plans) — completed 2026-04-05
- [x] Phase 25: Bot Reminder Lifecycle (2/2 plans) — completed 2026-04-05
- [x] Phase 26: Notification Deployment Hardening (1/1 plan) — completed 2026-04-05

Full details: `.planning/milestones/v5.0-ROADMAP.md`

</details>

### 🚧 v6.0 PWA + Web Push (In Progress)

**Milestone Goal:** Student mobile client «RutTrack» (React PWA) with native Web Push notifications — installable on Android/iOS, offline-capable app shell, geo check-in, schedule view, attendance stats, homework tracker.

- [x] **Phase 27: Web Push Backend** — VAPID infrastructure and push subscription endpoints in notification-web (completed 2026-04-05)
- [ ] **Phase 28: API Gateway CORS + nginx** — Gateway CORS config for PWA origin, push route, nginx serving container
- [ ] **Phase 29: PWA Scaffold + Auth** — React PWA project, login, JWT auth, manifest, A2HS, Service Worker shell
- [ ] **Phase 30: Schedule + Check-in UI** — Today/week schedule view with offline cache, geo check-in button and feedback
- [ ] **Phase 31: Push Frontend + End-to-End Integration** — Service Worker push handler, subscription opt-in, end-to-end smoke test
- [ ] **Phase 32: Stats + Homework** — Attendance stats/records with red zone indicator, homework list with completion tracker

## Phase Details

### Phase 27: Web Push Backend
**Goal**: notification-web can generate VAPID keys, store push subscriptions, and deliver Web Push notifications for lesson and homework events
**Depends on**: Phase 26 (notification-web container running)
**Requirements**: PUSH-01, PUSH-02, PUSH-03, PUSH-04, PUSH-05, PUSH-06, PUSH-07, INFRA-02
**Success Criteria** (what must be TRUE):
  1. `GET /api/ws/push/vapid-public-key` returns a Base64-encoded VAPID public key; the same key is returned after service restart (persisted in Redis)
  2. `POST /api/ws/push/subscribe` with a valid PushSubscription JSON stores the subscription in MongoDB `push_subscriptions` collection
  3. `DELETE /api/ws/push/subscribe` removes the stored subscription; subsequent push attempts to that endpoint do not occur
  4. Sending a test push via curl to a subscribed endpoint delivers a browser notification within 5 seconds
  5. A push delivery that receives HTTP 410 from the push service causes the subscription to be deleted from MongoDB automatically
**Plans:** 3/3 plans complete
Plans:
- [x] 27-01-PLAN.md — Module restructure + API contract + VAPID config + Gateway route
- [x] 27-02-PLAN.md — Push subscription CRUD + @RequireRole security
- [x] 27-03-PLAN.md — Async push delivery + EventConsumer hook + 410 cleanup
**UI hint**: no

### Phase 28: API Gateway CORS + nginx
**Goal**: API Gateway accepts cross-origin requests from the PWA origin, and the nginx container serves the PWA static build
**Depends on**: Phase 27 (push route target exists)
**Requirements**: INFRA-01, INFRA-03
**Success Criteria** (what must be TRUE):
  1. A preflight OPTIONS request from `http://localhost:5173` to the Gateway returns `Access-Control-Allow-Origin: http://localhost:5173` without duplicate headers
  2. `GET /api/push/vapid-public-key` is routable through the Gateway (StripPrefix removes `/api/push` leaving `/vapid-public-key` reaching notification-web)
  3. `docker compose up` starts an nginx container serving a static HTML file at `http://localhost:80`; `sw.js` and `index.html` are served with `Cache-Control: no-cache`
**Plans:** 2 plans
Plans:
- [ ] 28-01-PLAN.md — Gateway CORS config + JwtAuthenticationFilter OPTIONS bypass + tests
- [ ] 28-02-PLAN.md — nginx container + placeholder PWA files + docker-compose
**UI hint**: no

### Phase 29: PWA Scaffold + Auth
**Goal**: Students can install RutTrack on their home screen, log in with username/password, and see a working app shell that loads offline
**Depends on**: Phase 28 (Gateway CORS unblocks API calls from localhost:5173)
**Requirements**: PWA-01, PWA-02, PWA-03, PWA-04, PWA-05, PWA-06, PWA-07
**Success Criteria** (what must be TRUE):
  1. Student enters username and password, taps login, and lands on the app home screen; the access token is stored in React memory (never in localStorage)
  2. After 14 minutes of inactivity, the next API call silently refreshes the access token without showing a login screen
  3. Student taps logout and is returned to the login screen; a subsequent attempt to call a protected API endpoint returns 401
  4. Android Chrome displays an A2HS install prompt after the student's first successful check-in
  5. iOS Safari users who open the PWA in a browser (not standalone) see an instruction screen explaining how to add to home screen
  6. After installing and going fully offline, the app shell (login page) loads from the Service Worker cache without a network request
**Plans:** 3 plans
Plans:
- [ ] 29-01-PLAN.md — Auth-service httpOnly cookie refactor (backend)
- [ ] 29-02-PLAN.md — React PWA scaffold + Vite + Tailwind + shadcn + SW + manifest
- [ ] 29-03-PLAN.md — Auth flow UI + app shell + bottom nav + iOS onboarding + A2HS
**UI hint**: yes

### Phase 30: Schedule + Check-in UI
**Goal**: Students can view their daily and weekly schedule and submit a geo check-in from an active lesson card
**Depends on**: Phase 29 (auth and app shell established)
**Requirements**: SCHED-01, SCHED-02, SCHED-03, CHKIN-01, CHKIN-02, CHKIN-03
**Success Criteria** (what must be TRUE):
  1. Student opens the app and sees today's lessons listed with time, subject name, room, and status badge
  2. Student swipes or taps tabs to navigate to any day of the current week without a full page reload
  3. When offline, the schedule screen shows the last-fetched data (up to 1 hour stale) rather than an error
  4. Student taps "Отметиться" on an active lesson card; the app captures GPS coordinates and submits them; a success toast appears within 3 seconds on a good connection
  5. When check-in fails (not in zone, already marked, or no active lesson), the student sees the specific failure reason rather than a generic error
  6. When another student in the same group checks in, the current student's lesson card updates its attendance count in real time via the STOMP WebSocket
**Plans:** 2 plans
Plans:
- [ ] 28-01-PLAN.md — Gateway CORS config + JwtAuthenticationFilter OPTIONS bypass + tests
- [ ] 28-02-PLAN.md — nginx container + placeholder PWA files + docker-compose
**UI hint**: yes

### Phase 31: Push Frontend + End-to-End Integration
**Goal**: Students receive Web Push notifications on their device for lesson start and lesson cancellation events, and tapping a notification opens the correct screen
**Depends on**: Phase 27 (push backend), Phase 29 (Service Worker and app shell), Phase 30 (check-in and schedule screens exist as deep-link targets)
**Requirements**: PUSHUI-01, PUSHUI-02, PUSHUI-03, PUSHUI-04
**Success Criteria** (what must be TRUE):
  1. Student taps "Enable notifications" in settings; the browser shows a permission prompt only after this explicit gesture (not on first app load)
  2. A `lesson.started` RabbitMQ event triggers a Web Push notification on a subscribed device within 10 seconds; tapping it opens the PWA on the check-in screen for that lesson
  3. A `lesson.cancelled` RabbitMQ event triggers a Web Push notification; tapping it opens the schedule screen
  4. When the PWA is open in the foreground, the push notification is suppressed (the STOMP WebSocket already delivered the same event as an in-app update)
**Plans:** 2 plans
Plans:
- [ ] 28-01-PLAN.md — Gateway CORS config + JwtAuthenticationFilter OPTIONS bypass + tests
- [ ] 28-02-PLAN.md — nginx container + placeholder PWA files + docker-compose
**UI hint**: yes

### Phase 32: Stats + Homework
**Goal**: Students can review their attendance statistics per subject with red zone warnings, and view and track homework completion
**Depends on**: Phase 29 (auth), Phase 30 (schedule and check-in patterns established)
**Requirements**: ATT-01, ATT-02, ATT-03, HW-01, HW-02
**Success Criteria** (what must be TRUE):
  1. Student opens the attendance screen and sees a list of subjects with attendance percentage and present/absent/excused counts for each
  2. A subject where the student is below the configured red zone threshold is visually distinguished (red indicator) from subjects above threshold
  3. Student can view a scrollable list of individual attendance records showing date, lesson name, and status with color coding (б/н/у/сп)
  4. Student opens the homework screen and sees all homework items for their group with title, subject, deadline, and completion status
  5. Student taps a checkbox on a homework item; the item toggles to done/undone and the state persists after closing and reopening the app
**Plans:** 2 plans
Plans:
- [ ] 28-01-PLAN.md — Gateway CORS config + JwtAuthenticationFilter OPTIONS bypass + tests
- [ ] 28-02-PLAN.md — nginx container + placeholder PWA files + docker-compose
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1.1 Auth Service Core | v1.0 | 1/1 | Complete | 2026-03-28 |
| 1.2 OTP + Change Password | v1.0 | 1/1 | Complete | 2026-03-29 |
| 1.3 Gateway JWT Filter | v1.0 | 1/1 | Complete | 2026-03-30 |
| 1.4 Seed Data + Integration Tests | v1.0 | 1/1 | Complete | 2026-03-30 |
| 5. Entity and Repository Foundation | v2.0 | 2/2 | Complete | 2026-03-30 |
| 6. REST API + HATEOAS | v2.0 | 4/4 | Complete | 2026-03-30 |
| 7. gRPC Server | v2.0 | 2/2 | Complete | 2026-03-30 |
| 8. Redis Caching | v2.0 | 2/2 | Complete | 2026-03-31 |
| 9. RabbitMQ Events | v2.0 | 2/2 | Complete | 2026-03-31 |
| 10. Foundation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 11. REST API + gRPC Client | v3.0 | 3/3 | Complete | 2026-04-01 |
| 12. Lesson Auto-Generation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 13. Status Transitions + RabbitMQ Events | v3.0 | 2/2 | Complete | 2026-04-03 |
| 14. gRPC Server | v3.0 | 2/2 | Complete | 2026-04-04 |
| 15. Infrastructure Foundation | v4.0 | 2/2 | Complete | 2026-04-04 |
| 16. Event Consumers | v4.0 | 2/2 | Complete | 2026-04-04 |
| 17. Write Path — Geo-Checkin + Manual Marking | v4.0 | 3/3 | Complete | 2026-04-04 |
| 18. Read Path — Reports | v4.0 | 4/4 | Complete | 2026-04-04 |
| 19. Report Security & Routing Fix | v4.0 | 1/1 | Complete | 2026-04-04 |
| 20. Shared Infrastructure | v5.0 | 3/3 | Complete | 2026-04-04 |
| 21. Notification Web — WebSocket Core | v5.0 | 2/2 | Complete | 2026-04-05 |
| 22. Bot Infrastructure Layer | v5.0 | 3/3 | Complete | 2026-04-05 |
| 23. Bot Telegram Commands | v5.0 | 3/3 | Complete | 2026-04-05 |
| 24. Bot Event Notifications | v5.0 | 2/2 | Complete | 2026-04-05 |
| 25. Bot Reminder Lifecycle | v5.0 | 2/2 | Complete | 2026-04-05 |
| 26. Notification Deployment Hardening | v5.0 | 1/1 | Complete | 2026-04-05 |
| 27. Web Push Backend | v6.0 | 3/3 | Complete   | 2026-04-05 |
| 28. API Gateway CORS + nginx | v6.0 | 0/2 | Planned | - |
| 29. PWA Scaffold + Auth | v6.0 | 0/3 | Planned | - |
| 30. Schedule + Check-in UI | v6.0 | 0/? | Not started | - |
| 31. Push Frontend + End-to-End Integration | v6.0 | 0/? | Not started | - |
| 32. Stats + Homework | v6.0 | 0/? | Not started | - |
