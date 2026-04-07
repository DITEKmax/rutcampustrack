# Requirements: RutCampusTrack

**Defined:** 2026-04-06
**Core Value:** Three frontend clients — Telegram Mini App, Angular Web Panel, Landing Page — for existing microservice backend

## v7.0 Requirements

Requirements for Frontends milestone. Each maps to roadmap phases 33-40.

### Infrastructure

- [x] **INFRA-01**: URL layout document with port/path mapping for all frontends
- [x] **INFRA-02**: Gateway CORS config includes Mini App and Web Panel dev/prod origins
- [x] **INFRA-03**: docker-compose has nginx services for mini-app, web-panel, landing
- [x] **INFRA-04**: Each nginx config has correct `try_files` for SPA routing

### Auth Service TMA

- [x] **AUTH-01**: `POST /api/auth/tma` validates Telegram initData (HMAC-SHA256) and returns JWT
- [x] **AUTH-02**: `POST /api/auth/refresh-body` accepts refresh token in request body and returns new token pair

### Landing Page

- [x] **LAND-01**: Static HTML/CSS landing page with hero, features, role overview
- [x] **LAND-02**: Mobile-responsive layout (360px-1440px)
- [x] **LAND-03**: Served by dedicated nginx container

### Telegram Mini App — Auth

- [x] **TMA-01**: Mini App opens in Telegram WebView without blank screen
- [x] **TMA-02**: initData extracted and exchanged for JWT via `POST /api/auth/tma`
- [x] **TMA-03**: Access token in React state (memory-only per D-05)
- [x] **TMA-04**: Token refresh via initData re-auth (per D-06, not refresh-body)
- [x] **TMA-05**: Dev mock environment for local development outside Telegram

### Telegram Mini App — Features

- [x] **TMA-06**: Today's schedule view with lessons, times, rooms, status badges
- [x] **TMA-07**: Geo check-in via MainButton with GPS capture and haptic feedback
- [x] **TMA-08**: Attendance stats per subject with red zone indicators
- [x] **TMA-09**: Homework list with completion toggle
- [x] **TMA-10**: Telegram theme support (dark/light mode)
- [x] **TMA-11**: BackButton navigation integration

### Web Panel — Auth

- [x] **WPAN-01**: Teacher/admin login with username/password
- [x] **WPAN-02**: JWT access token in Angular signal (memory-only per D-06)
- [x] **WPAN-03**: Role-based route guards (TEACHER, ADMIN)
- [x] **WPAN-04**: Token auto-refresh via HTTP interceptor
- [x] **WPAN-05**: Logout clears tokens and redirects to login

### Web Panel — Teacher

- [x] **WPAN-06**: Attendance journal grid (students x lessons matrix)
- [x] **WPAN-07**: Virtual scroll for 500+ students in journal grid (CdkTable)
- [x] **WPAN-08**: Attendance stats charts per subject/group (ng2-charts)

### Web Panel — Admin

- [x] **WPAN-09**: User CRUD (create/edit/archive) with auto-generated logins
- [x] **WPAN-10**: Group management with headman assign/revoke
- [x] **WPAN-11**: Semester management with confirmation phrase for delete
- [x] **WPAN-12**: Dashboard with summary statistics
- [ ] **WPAN-13**: Headman assistant management (blocked — backend @RequireRole(STUDENT))

## Previously Satisfied (v6.0)

- ✅ PWA-01..07: Login, token refresh, logout, manifest, Service Worker, A2HS, iOS onboarding
- ✅ SCHED-01..03: Schedule view (today/week), offline cache
- ✅ CHKIN-01..03: Geo check-in with GPS, feedback, real-time STOMP updates
- ✅ PUSH-01..07: VAPID keys, subscription CRUD, push delivery, 410 cleanup
- ✅ PUSHUI-01..04: SW push handler, deep links, soft-ask permission, foreground dedup
- ✅ ATT-01..03: Stats per subject, red zone indicators, attendance records
- ✅ HW-01..02: Homework list, optimistic completion toggle
- ✅ INFRA-01..03 (v6.0): Gateway CORS, push route, nginx PWA serving

## Deferred (from previous milestones)

- [ ] Excuse tickets: create/submit/review flow with event publishing (excuse.requested)
- [ ] Late check-in ("forgot to mark") flow with event publishing (late_checkin.requested)
- [ ] NOTIF-02, NOTIF-03: Live timer testing for midpoint/near-end reminders
- [ ] WS-07: Live broker-level group isolation verification

## Out of Scope

| Feature | Reason |
|---------|--------|
| Excuse ticket creation | Backend flow deferred (PROJECT.md) |
| Late check-in request | Backend flow deferred (PROJECT.md) |
| PDF/Excel export | Backend generation service needed |
| STOMP real-time in Mini App | TMA-12 deferred; no STOMP work in v7.0 |
| Web Push for TEACHER/ADMIN | Defer to future milestone |
| Native mobile app | Web-first strategy |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 33 | Complete |
| INFRA-02 | Phase 33 | Complete |
| INFRA-03 | Phase 33 | Complete |
| INFRA-04 | Phase 33 | Complete |
| AUTH-01 | Phase 34 | Complete |
| AUTH-02 | Phase 34 | Complete |
| LAND-01 | Phase 35 | Complete |
| LAND-02 | Phase 35 | Complete |
| LAND-03 | Phase 35 | Complete |
| TMA-01 | Phase 36 | Complete |
| TMA-02 | Phase 36 | Complete |
| TMA-03 | Phase 36 | Complete |
| TMA-04 | Phase 36 | Complete |
| TMA-05 | Phase 36 | Complete |
| TMA-06 | Phase 37 | Complete |
| TMA-07 | Phase 37 | Complete |
| TMA-08 | Phase 37 | Complete |
| TMA-09 | Phase 37 | Complete |
| TMA-10 | Phase 37 | Complete |
| TMA-11 | Phase 37 | Complete |
| WPAN-01 | Phase 38 | Complete |
| WPAN-02 | Phase 38 | Complete |
| WPAN-03 | Phase 38 | Complete |
| WPAN-04 | Phase 38 | Complete |
| WPAN-05 | Phase 38 | Complete |
| WPAN-06 | Phase 39 | Complete |
| WPAN-07 | Phase 39 | Complete |
| WPAN-08 | Phase 39 | Complete |
| WPAN-09 | Phase 40 | Complete |
| WPAN-10 | Phase 40 | Complete |
| WPAN-11 | Phase 40 | Complete |
| WPAN-12 | Phase 40 | Complete |
| WPAN-13 | Phase 40 | Blocked |

**Coverage:**
- v7.0 requirements: 33 total (4 INFRA + 2 AUTH + 3 LAND + 11 TMA + 13 WPAN)
- Satisfied: 32/33 (WPAN-13 blocked by backend role constraint)
- Mapped to phases: 33
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-06*
*Last updated: 2026-04-07 after gap closure inline fixes*
