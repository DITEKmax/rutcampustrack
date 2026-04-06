# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- ✅ **v3.0 Schedule Service** — Phases 10-14 (shipped 2026-04-04)
- ✅ **v4.0 Attendance Service MVP** — Phases 15-19 (shipped 2026-04-04)
- ✅ **v5.0 Notification Service (Web + Bot)** — Phases 20-26 (shipped 2026-04-05)
- ✅ **v6.0 PWA + Web Push** — Phases 27-32 (shipped 2026-04-06)
- 🚧 **v7.0 Frontends — Mini App, Web Panel, Landing** — Phases 33-40 (in progress)

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

<details>
<summary>✅ v6.0 PWA + Web Push (Phases 27-32) — SHIPPED 2026-04-06</summary>

**Milestone Goal:** Student mobile client «RutTrack» (React PWA) with native Web Push notifications — installable on Android/iOS, offline-capable app shell, geo check-in, schedule view, attendance stats, homework tracker.

- [x] **Phase 27: Web Push Backend** — VAPID infrastructure and push subscription endpoints in notification-web (completed 2026-04-05)
- [x] **Phase 28: API Gateway CORS + nginx** — Gateway CORS config for PWA origin, push route, nginx serving container (completed 2026-04-06)
- [x] **Phase 29: PWA Scaffold + Auth** — React PWA project, login, JWT auth, manifest, A2HS, Service Worker shell (completed 2026-04-06)
- [x] **Phase 30: Schedule + Check-in UI** — Today/week schedule view with offline cache, geo check-in button and feedback (completed 2026-04-06)
- [x] **Phase 31: Push Frontend + End-to-End Integration** — Service Worker push handler, subscription opt-in, end-to-end smoke test (completed 2026-04-06)
- [x] **Phase 32: Stats + Homework** — Attendance stats/records with red zone indicator, homework list with completion tracker (completed 2026-04-06)

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
**Plans:** 2/2 plans complete
Plans:
- [x] 28-01-PLAN.md — Gateway CORS config + JwtAuthenticationFilter OPTIONS bypass + tests
- [x] 28-02-PLAN.md — nginx container + placeholder PWA files + docker-compose
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
**Plans:** 3/3 plans complete
Plans:
- [x] 29-01-PLAN.md — Auth-service httpOnly cookie refactor (backend)
- [x] 29-02-PLAN.md — React PWA scaffold + Vite + Tailwind + shadcn + SW + manifest
- [x] 29-03-PLAN.md — Auth flow UI + app shell + bottom nav + iOS onboarding + A2HS
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
**Plans:** 2/2 plans complete
Plans:
- [x] 30-01-PLAN.md — Types, API hooks, schedule UI (SchedulePage, WeekDayTabs, LessonCard, StatusBadge, OfflineStaleNotice)
- [x] 30-02-PLAN.md — Check-in flow (CheckInButton, CheckInToast, useStompCheckin, CheckInScreen) + STOMP real-time integration
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
**Plans:** 2/2 plans complete
Plans:
- [x] 31-01-PLAN.md — SW push + notificationclick handlers, push utilities + tests
- [x] 31-02-PLAN.md — Push subscription hook, PushPermissionCard, ProfilePage upgrade
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
**Plans:** 2/2 plans complete
Plans:
- [x] 32-01-PLAN.md — Attendance stats + records pages, BottomNav restructure, routing
- [x] 32-02-PLAN.md — Homework list page with server-side completion toggle
**UI hint**: yes

Full details: `.planning/milestones/v6.0-ROADMAP.md`

</details>

### 🚧 v7.0 Frontends — Mini App, Web Panel, Landing (In Progress)

**Milestone Goal:** Build three remaining frontend clients: Telegram Mini App (React) for student attendance inside Telegram, Angular Web Panel for teacher/admin management, and a static Landing page.

- [ ] **Phase 33: Infrastructure** — URL layout, Gateway CORS expansion, nginx configs, docker-compose
- [ ] **Phase 34: Auth Service TMA** — `POST /api/auth/tma` initData endpoint + `POST /api/auth/refresh-body`
- [ ] **Phase 35: Landing Page** — Static HTML/CSS marketing page with nginx container
- [ ] **Phase 36: Mini App Scaffold + Auth** — Vite scaffold, Telegram SDK init, initData auth flow, dev mock env
- [ ] **Phase 37: Mini App Features** — Schedule, geo check-in, attendance stats, homework, Telegram UX
- [ ] **Phase 38: Web Panel Scaffold + Auth** — Angular 21 standalone, Tailwind, interceptors, role guards, login/logout
- [ ] **Phase 39: Web Panel Teacher** — Attendance journal grid (CdkTable), stats charts (ng2-charts)
- [ ] **Phase 40: Web Panel Admin** — User/group/semester CRUD, headman assign/revoke, dashboard summary

## Phase Details

### Phase 33: Infrastructure
**Goal**: URL layout is decided, Gateway CORS accepts Mini App and Web Panel origins, nginx configs exist for all three new frontends, and docker-compose brings up all three containers
**Depends on**: Phase 32 (v6.0 complete)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05
**Success Criteria** (what must be TRUE):
  1. A decision document captures the production URL layout for all four frontends (PWA, Mini App, Web Panel, Landing) with no routing conflicts
  2. A preflight OPTIONS request from the Mini App dev origin and the Web Panel dev origin each return the correct `Access-Control-Allow-Origin` header from the Gateway
  3. `GET /api/auth/tma` and `GET /api/auth/refresh-body` are listed in Gateway PUBLIC_PATHS — unauthenticated requests to these paths are not rejected by the JWT filter
  4. `docker compose up` starts three new nginx containers (mini-app-nginx, web-panel-nginx, landing-nginx); each serves a placeholder HTML page on its configured port
**Plans:** 2 plans
Plans:
- [ ] 33-01-PLAN.md — URL layout, nginx configs, placeholder HTML, docker-compose containers
- [ ] 33-02-PLAN.md — Gateway CORS expansion + PUBLIC_PATHS update
**UI hint**: no

### Phase 34: Auth Service TMA
**Goal**: Auth Service validates Telegram initData HMAC-SHA256 and issues a JWT pair, and accepts a body-based refresh token for Mini App environments where httpOnly cookies are unavailable
**Depends on**: Phase 32 (v6.0 complete; Auth Service baseline working)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04
**Success Criteria** (what must be TRUE):
  1. `POST /api/auth/tma` with valid Telegram initData (current auth_date) returns a 200 with access token and refresh token
  2. `POST /api/auth/tma` with stale initData (auth_date > 5 minutes old) returns a 401 error response
  3. `POST /api/auth/tma` with a tampered initData hash returns a 401 error response
  4. `POST /api/auth/refresh-body` with a valid refresh token in the request body returns a new JWT pair (access + refresh tokens) in the response body
  5. The bot token secret is injected via environment variable and is not hardcoded in source
**Plans**: TBD
**UI hint**: no

### Phase 35: Landing Page
**Goal**: A mobile-responsive static marketing page for RutCampusTrack is live at its configured URL with all planned content sections
**Depends on**: Phase 33 (nginx container and URL layout established)
**Requirements**: LAND-01, LAND-02, LAND-03, LAND-04, LAND-05, LAND-06, LAND-07
**Success Criteria** (what must be TRUE):
  1. Visiting the landing URL shows a hero section with project name, tagline, and working CTA buttons linking to the PWA and Telegram bot
  2. The page contains sections explaining key features, the attendance workflow, and the four user roles (student, headman, teacher, admin)
  3. Screenshots or mockups of the app are visible in a dedicated section
  4. On a 375px-wide mobile screen, no content overflows horizontally and all text remains readable without zooming
  5. The footer displays links and contact information
**Plans**: TBD
**UI hint**: yes

### Phase 36: Mini App Scaffold + Auth
**Goal**: The Telegram Mini App launches inside Telegram, expands to full viewport, authenticates the user via initData, and persists the session using localStorage-based refresh — with a local dev mock environment for development without Telegram
**Depends on**: Phase 33 (Gateway CORS), Phase 34 (TMA auth endpoint)
**Requirements**: TMA-01, TMA-02, TMA-03, TMA-11
**Success Criteria** (what must be TRUE):
  1. Opening the Mini App in Telegram expands to full viewport without white bars or scroll bounce
  2. The app automatically exchanges Telegram initData for a JWT on first open — no manual login form is shown to the student
  3. After the access token expires, the next API call silently refreshes using the refresh token stored in localStorage without showing a login screen
  4. Running `npm run dev` in a browser (without Telegram) shows a mock environment that simulates Telegram initData so all screens can be developed locally
**Plans**: TBD
**UI hint**: yes

### Phase 37: Mini App Features
**Goal**: Students can use the Mini App inside Telegram to view their schedule, submit a geo check-in, review attendance stats, and track homework — all with Telegram-native UX patterns
**Depends on**: Phase 36 (scaffold and auth working)
**Requirements**: TMA-04, TMA-05, TMA-06, TMA-07, TMA-08, TMA-09, TMA-10
**Success Criteria** (what must be TRUE):
  1. Student sees today's lessons in a list with subject name, time, room, and status; navigating to another tab and back preserves state
  2. Student taps the Telegram MainButton to submit a geo check-in on an active lesson; haptic feedback fires on both success and failure; the result is shown inline
  3. Attendance stats screen shows per-subject attendance percentage with a red zone indicator for subjects below threshold
  4. Homework screen shows all group homework items and toggling completion works with optimistic update
  5. Bottom tab navigation switches between schedule, stats, and homework screens; Telegram BackButton navigates within screens (e.g., detail views)
  6. Telegram theme colors (background, text, button colors) are applied consistently throughout — the app looks native to the user's Telegram theme
**Plans**: TBD
**UI hint**: yes

### Phase 38: Web Panel Scaffold + Auth
**Goal**: Teachers and admins can log in to the Angular Web Panel, are routed to the correct view for their role, and can refresh their session and log out securely
**Depends on**: Phase 33 (Gateway CORS accepts Web Panel origin)
**Requirements**: PANEL-01, PANEL-02, PANEL-03, PANEL-10, PANEL-11
**Success Criteria** (what must be TRUE):
  1. Teacher opens the Web Panel, enters credentials, and is redirected to the teacher view; admin credentials redirect to the admin view
  2. A teacher visiting an admin-only route is redirected to their own dashboard (role guard enforced client-side)
  3. After the access token expires, the next API call automatically refreshes using the httpOnly cookie — no login screen interrupts the session
  4. Clicking logout clears the access token signal and redirects to the login page; a subsequent protected API call returns 401
  5. The layout is usable on a 1280px desktop screen and remains functional (no overflow, readable content) on a 768px tablet screen
**Plans**: TBD
**UI hint**: yes

### Phase 39: Web Panel Teacher
**Goal**: Teachers can view the attendance journal grid for their groups and see per-subject attendance charts without needing admin privileges
**Depends on**: Phase 38 (Web Panel scaffold, auth, and role guards established)
**Requirements**: PANEL-04, PANEL-05
**Success Criteria** (what must be TRUE):
  1. Teacher opens the journal view and sees a students × lessons matrix with color-coded status cells (б/н/у/сп/отменена)
  2. With 30+ students and 20+ lessons, the grid scrolls smoothly without UI freezing (CdkTable virtual scroll applied)
  3. Attendance stats view shows a bar chart per subject for the selected group, with each bar labeled with the attendance percentage
**Plans**: TBD
**UI hint**: yes

### Phase 40: Web Panel Admin
**Goal**: Admins can manage the full university data set — users, groups, semesters, headman assignments — and see a dashboard summarizing system state
**Depends on**: Phase 38 (Web Panel scaffold, auth, and role guards established)
**Requirements**: PANEL-06, PANEL-07, PANEL-08, PANEL-09
**Success Criteria** (what must be TRUE):
  1. Admin can create a new user, edit their details, and archive them — the archived user no longer appears in the active user list
  2. Admin can create a group, assign a headman (selecting from existing students), and revoke the headman assignment
  3. Admin can create a semester, activate it, and delete it using the confirmation phrase — a wrong phrase is rejected before deletion
  4. Admin dashboard shows summary counts (users, groups, active semester, total lessons today) that match the backend data
**Plans**: TBD
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
| 27. Web Push Backend | v6.0 | 3/3 | Complete | 2026-04-05 |
| 28. API Gateway CORS + nginx | v6.0 | 2/2 | Complete | 2026-04-06 |
| 29. PWA Scaffold + Auth | v6.0 | 3/3 | Complete | 2026-04-06 |
| 30. Schedule + Check-in UI | v6.0 | 2/2 | Complete | 2026-04-06 |
| 31. Push Frontend + End-to-End Integration | v6.0 | 2/2 | Complete | 2026-04-06 |
| 32. Stats + Homework | v6.0 | 2/2 | Complete | 2026-04-06 |
| 33. Infrastructure | v7.0 | 0/2 | Planned | - |
| 34. Auth Service TMA | v7.0 | 0/TBD | Not started | - |
| 35. Landing Page | v7.0 | 0/TBD | Not started | - |
| 36. Mini App Scaffold + Auth | v7.0 | 0/TBD | Not started | - |
| 37. Mini App Features | v7.0 | 0/TBD | Not started | - |
| 38. Web Panel Scaffold + Auth | v7.0 | 0/TBD | Not started | - |
| 39. Web Panel Teacher | v7.0 | 0/TBD | Not started | - |
| 40. Web Panel Admin | v7.0 | 0/TBD | Not started | - |
