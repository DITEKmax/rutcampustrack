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

- [x] Phase 27: Web Push Backend (3/3 plans) — completed 2026-04-05
- [x] Phase 28: API Gateway CORS + nginx (2/2 plans) — completed 2026-04-06
- [x] Phase 29: PWA Scaffold + Auth (3/3 plans) — completed 2026-04-06
- [x] Phase 30: Schedule + Check-in UI (2/2 plans) — completed 2026-04-06
- [x] Phase 31: Push Frontend + End-to-End Integration (2/2 plans) — completed 2026-04-06
- [x] Phase 32: Stats + Homework (2/2 plans) — completed 2026-04-06

Full details: `.planning/milestones/v6.0-ROADMAP.md`

</details>

### v7.0 Frontends — Mini App, Web Panel, Landing (In Progress)

**Milestone Goal:** Build three remaining frontend clients: Telegram Mini App (React) for student attendance inside Telegram, Angular Web Panel for teacher/admin management, and a static Landing page.

- [x] **Phase 33: Infrastructure** — URL layout, Gateway CORS expansion, nginx configs, docker-compose (completed 2026-04-06)
- [ ] **Phase 34: Auth Service TMA** — `POST /api/auth/tma` initData endpoint + `POST /api/auth/refresh-body`
- [ ] **Phase 35: Landing Page** — Static HTML/CSS marketing page with nginx container
- [ ] **Phase 36: Mini App Scaffold + Auth** — Vite scaffold, Telegram SDK init, initData auth flow, dev mock env
- [ ] **Phase 37: Mini App Features** — Schedule, geo check-in, attendance stats, homework, Telegram UX
- [ ] **Phase 38: Web Panel Scaffold + Auth** — Angular 21 standalone, Tailwind, interceptors, role guards, login/logout
- [ ] **Phase 39: Web Panel Teacher** — Attendance journal grid (CdkTable), stats charts (ng2-charts)
- [ ] **Phase 40: Web Panel Admin** — User/group/semester CRUD, headman assign/revoke, dashboard summary

## Phase Details

### Phase 33: Infrastructure
**Goal**: URL layout decision, Gateway CORS expansion for all frontend origins, nginx configs for Mini App / Web Panel / Landing containers, docker-compose updates
**Depends on**: Phase 32 (v6.0 shipped)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04
**Success Criteria** (what must be TRUE):
  1. URL layout document exists with clear port/path mapping for all 4 frontends (PWA, Mini App, Web Panel, Landing)
  2. Gateway CORS config includes dev origins for Mini App and Web Panel
  3. docker-compose.yml has nginx service entries for mini-app, web-panel, and landing containers
  4. Each nginx config has correct `try_files` for SPA routing
**Plans:** 2/2 plans complete
Plans:
- [x] 33-01-PLAN.md — URL layout doc + nginx configs + docker-compose services
- [x] 33-02-PLAN.md — Gateway CORS + PUBLIC_PATHS expansion
**UI hint**: no

### Phase 34: Auth Service TMA
**Goal**: Auth Service can exchange Telegram initData for a JWT (HMAC-SHA256 validation), and provides a body-based refresh endpoint for Mini App (WebView drops httpOnly cookies)
**Depends on**: Phase 33 (Gateway CORS for Mini App origin)
**Requirements**: AUTH-01, AUTH-02
**Success Criteria** (what must be TRUE):
  1. `POST /api/auth/tma` with valid Telegram initData returns a JWT access token + refresh token in response body
  2. `POST /api/auth/tma` with tampered initData returns 401 with clear error
  3. `POST /api/auth/refresh-body` with valid refresh token returns new access + refresh tokens in response body
  4. User lookup by `telegram_id` (stored from v5.0 bot /start linking) works correctly
**Plans:** 1 plan
Plans:
- [ ] 34-01-PLAN.md — TmaService HMAC validation + controller endpoints + integration tests
**UI hint**: no

### Phase 35: Landing Page
**Goal**: Static HTML/CSS marketing page served by its own nginx container — hero, features, role overview, screenshots
**Depends on**: Phase 33 (nginx container and URL layout)
**Requirements**: LAND-01, LAND-02, LAND-03
**Success Criteria** (what must be TRUE):
  1. `http://localhost:8880` serves a responsive landing page with hero section, feature highlights, and role overview
  2. Page is fully static (no JS framework, no API calls)
  3. Mobile-responsive layout works on 360px-1440px viewports
**Plans:** TBD
**UI hint**: yes

### Phase 36: Mini App Scaffold + Auth
**Goal**: Vite React scaffold with Telegram SDK, viewport setup, initData auth flow, localStorage refresh pattern, dev mock environment
**Depends on**: Phase 33 (Gateway CORS), Phase 34 (Auth TMA endpoint)
**Requirements**: TMA-01, TMA-02, TMA-03, TMA-04, TMA-05
**Success Criteria** (what must be TRUE):
  1. Mini App opens inside Telegram WebView and renders without blank screen
  2. initData is extracted and exchanged for JWT via `POST /api/auth/tma`
  3. Access token stored in React state, refresh token in localStorage
  4. Token refresh works via body-based endpoint (not httpOnly cookie)
  5. Dev mock environment allows local development outside Telegram
**Plans:** TBD
**UI hint**: yes

### Phase 37: Mini App Features
**Goal**: Schedule view, geo check-in with MainButton + haptic feedback, attendance stats with red zone, homework list, Telegram native UX (BackButton, theme colors)
**Depends on**: Phase 36 (scaffold and auth established)
**Requirements**: TMA-06, TMA-07, TMA-08, TMA-09, TMA-10, TMA-11
**Success Criteria** (what must be TRUE):
  1. Student sees today's schedule with lessons, times, rooms, and status badges
  2. Student can check in via MainButton with GPS capture and haptic feedback on success
  3. Attendance stats show per-subject percentages with red zone indicators
  4. Homework list with completion toggle works
  5. UI respects Telegram theme (dark/light mode) and uses BackButton for navigation
**Plans:** TBD
**UI hint**: yes

### Phase 38: Web Panel Scaffold + Auth
**Goal**: Angular 21 standalone scaffold with Tailwind, HTTP interceptors, role-based route guards, login/logout for TEACHER and ADMIN roles
**Depends on**: Phase 33 (Gateway CORS for Web Panel origin)
**Requirements**: WPAN-01, WPAN-02, WPAN-03, WPAN-04, WPAN-05
**Success Criteria** (what must be TRUE):
  1. Teacher/admin can log in with username/password and see role-appropriate dashboard
  2. JWT access token in Angular signal (memory), refresh token in httpOnly cookie
  3. Unauthorized route access redirects to login
  4. Token auto-refresh works via HTTP interceptor
  5. Logout clears tokens and redirects to login
**Plans:** TBD
**UI hint**: yes

### Phase 39: Web Panel Teacher
**Goal**: Attendance journal grid (CdkTable with virtual scroll for 500+ rows), attendance stats charts (ng2-charts/Chart.js)
**Depends on**: Phase 38 (scaffold and auth)
**Requirements**: WPAN-06, WPAN-07, WPAN-08
**Success Criteria** (what must be TRUE):
  1. Teacher sees attendance journal as a students-x-lessons grid with status cells
  2. Grid handles 500+ students without performance degradation (virtual scroll)
  3. Teacher can view attendance stats chart per subject/group
**Plans:** TBD
**UI hint**: yes

### Phase 40: Web Panel Admin
**Goal**: Admin CRUD for users/groups/semesters, headman assign/revoke, dashboard with summary statistics
**Depends on**: Phase 38 (scaffold and auth)
**Requirements**: WPAN-09, WPAN-10, WPAN-11, WPAN-12, WPAN-13
**Success Criteria** (what must be TRUE):
  1. Admin can create/edit/archive users with auto-generated logins
  2. Admin can manage groups and assign/revoke headmen
  3. Admin can manage semesters with confirmation phrase for delete
  4. Dashboard shows summary stats (total students, groups, attendance rates)
**Plans:** TBD
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
| 31. Push Frontend + E2E Integration | v6.0 | 2/2 | Complete | 2026-04-06 |
| 32. Stats + Homework | v6.0 | 2/2 | Complete | 2026-04-06 |
| 33. Infrastructure | v7.0 | 2/2 | Complete | 2026-04-06 |
| 34. Auth Service TMA | v7.0 | 0/1 | Planned | - |
| 35. Landing Page | v7.0 | 0/? | Planned | - |
| 36. Mini App Scaffold + Auth | v7.0 | 0/? | Planned | - |
| 37. Mini App Features | v7.0 | 0/? | Planned | - |
| 38. Web Panel Scaffold + Auth | v7.0 | 0/? | Planned | - |
| 39. Web Panel Teacher | v7.0 | 0/? | Planned | - |
| 40. Web Panel Admin | v7.0 | 0/? | Planned | - |
