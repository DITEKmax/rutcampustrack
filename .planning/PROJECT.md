# RutCampusTrack

## Vision
Microservice attendance tracking system for RUT MIIT university. Production system + portfolio case with full microservice architecture.

## Core Problem
Replace three separate backends (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) with a unified microservice architecture for tracking student attendance.

## Core Value
Full-stack attendance tracking system: 5 backend microservices + 4 frontend clients (React PWA, Telegram Mini App, Angular Web Panel, Landing page). Complete student-to-admin workflow — from geo-checkin in Telegram to admin dashboard in the browser.

## Current Milestone: v9.0 Frontend Unification — Single Login & Role-Based Web Clients

**Goal:** Fix critical post-v8.0 production frontend bugs and deliver full web cabinets for STUDENT and HEADMAN roles, unified under a single `/login` entry point.

**Target features:**
- **Block A — Infra & routing fixes:** nginx root `/` → `/login` (not PWA), landing to `/presentation/`, PWA to `/app/`, dead `https://t.me/` links removed, web-panel `baseHref` migration `/admin/` → `/`, unified `/login` for all roles
- **Block B — Student web cabinet:** `/student/{dashboard, schedule, checkin, homework, stats, notifications, excuses, late-checkin, profile}`, PWA install prompt (non-intrusive, no forced redirect)
- **Block C — Headman web cabinet:** `/headman/{dashboard, group, subjects, journal, excuses, late-checkin, stats}`, closes WPAN-13 blocker
- **Block D — Headman PWA mode:** HEADMAN role branch in React PWA (closes `docs/design-decisions.md §3` promise)
- **Block E — Landing presentation mode + docs:** landing → `/presentation/` with GSAP "how the system works" animation, docs sync

**Key context:**
- Full context brief: `.planning/v9.0-BRIEF.md` (problems, architecture, constraints, phase breakdown, draft requirements)
- Fresh codebase map: `.planning/codebase/` (7 docs, 3070 lines, generated 2026-04-08)
- Closes documented but unimplemented job stories: JS-TEACHER-08, JS-HEADMAN-01/04/20, JS-STUDENT-09, JS-SYSTEM-04
- HEADMAN = `is_headman` boolean (already in JWT claims per `JwtService.java:96`); `UserRole` enum stays `{ADMIN, TEACHER, STUDENT}`
- OTP login UI deferred to v10.0 (backend ready, only UI missing)

## Current State

v8.0 shipped (2026-04-08). Full production-ready system: 5 backend services, 4 frontend clients, CI/CD pipeline, Docker deployment, SSL, unified API docs, comprehensive README.

**Backend:** Auth (JWT/OTP/TMA) + Academic (CRUD/gRPC/Redis/RabbitMQ) + Schedule (auto-generation/status transitions/cron) + Attendance (geo-checkin/manual marking/reports/MongoDB) + Notification (STOMP WebSocket + Telegram bot + Web Push). All inter-service communication working: gRPC, RabbitMQ fanout, STOMP, Web Push API.

**Frontend PWA:** React + Vite + TanStack Query + Tailwind + Framer Motion. Login with httpOnly cookie JWT, schedule, geo check-in, attendance stats, homework, Web Push, A2HS, offline SW shell. 63 vitest tests.

**Telegram Mini App:** React + Vite + TanStack Query + Telegram SDK. initData HMAC-SHA256 auth, schedule view, geo check-in with MainButton + haptic feedback, attendance stats with red zone, homework list, Telegram theme support, BackButton navigation. 35 vitest tests.

**Web Panel:** Angular 19 + Material 3 + Tailwind. Teacher features: attendance journal grid (CdkTable + virtual scroll), stats charts (ng2-charts). Admin features: dashboard with 4 stat cards, user CRUD with search/filter/pagination, group management with headman assign/revoke, semester management with typed-confirmation delete. 129 vitest tests.

**Landing:** Static HTML/CSS with Tailwind CDN, GSAP scroll animations, responsive 360-1440px, dark mode.

**Infrastructure:** API Gateway with CORS + JWT filter, nginx reverse proxy with Let's Encrypt SSL, multi-stage Dockerfiles for all 11 services, docker-compose.prod.yml (17 containers), GitHub Actions CI + GHCR deploy pipeline, unified Swagger UI at Gateway.

**Latest milestone:** v8.0 CI/CD, Deployment & Documentation shipped 2026-04-08 (8 phases, 11 plans, 26/26 requirements).

**v9.0 progress:** Phase 49 complete (2026-04-08) — nginx root redirects to `/login`, landing moved to `/presentation/`, PWA moved to `/app/`, dead `t.me/` links replaced with `/login`. Critical-path routing fix landed; Phase 50 (`baseHref` migration + unified `/login`) unblocked. 8/33 v9.0 requirements satisfied offline; 8 HUMAN-UAT items pending live-host verification.

## Shipped Milestones

<details>
<summary>v7.0 Frontends — Mini App, Web Panel, Landing — SHIPPED 2026-04-07</summary>

**Goal:** Build 3 remaining frontend applications — Telegram Mini App, Angular Web Panel, Landing page.
**Phases:** 33-40 (8 phases, 16 plans)
**Requirements:** 32/33 satisfied (WPAN-13 blocked by backend role constraint)

Key deliverables:
- Gateway CORS expansion + nginx containers for Mini App, Web Panel, Landing
- Telegram initData HMAC-SHA256 auth + body-based refresh endpoint
- Static landing page with GSAP animations, responsive layout, dark mode
- Telegram Mini App: schedule, geo check-in, stats, homework, theme support
- Angular Web Panel scaffold: login, sidebar, role guards, auth interceptor
- Teacher journal grid (CdkTable + virtual scroll) + stats charts (ng2-charts)
- Admin dashboard + user/group/semester CRUD with headman management

</details>

<details>
<summary>v6.0 PWA + Web Push — SHIPPED 2026-04-06</summary>

**Goal:** Student mobile client «RutTrack» (React PWA) with native Web Push notifications.
**Phases:** 27-32 (6 phases, 14 plans)
**Requirements:** 32/32 satisfied

Key deliverables:
- Web Push backend: VAPID keys, subscription CRUD, async push delivery (lesson/cancel/homework)
- API Gateway CORS + nginx container for PWA serving
- React PWA: JWT auth (httpOnly cookies), A2HS, offline shell, 5-tab BottomNav
- Schedule + geo check-in UI with STOMP real-time updates
- Service Worker push handlers with deep link navigation, soft-ask permission pattern
- Attendance stats with red zone indicators + homework list with optimistic completion toggle

</details>

<details>
<summary>v5.0 Notification Service (Web + Bot) — SHIPPED 2026-04-05</summary>

**Goal:** Real-time push notifications via WebSocket (web panel) and Telegram bot.
**Phases:** 20-26 (7 phases, 16 plans)
**Requirements:** 19/25 satisfied (6 partial — 4 await future event publishers, 2 need live testing)

Key deliverables:
- STOMP WebSocket with JWT handshake auth and group-based event routing (5 event types)
- Telegram bot: /start account linking, /login OTP, /status attendance check
- Bot infrastructure: aio-pika watchdog, async gRPC client, Redis client, throttled send queue (30 msg/s)
- Event notifications: inline check-in button, cancellation, homework, headman alerts
- Reminder lifecycle: midpoint + near-end reminders, cleanup on close/checkin
- Deployment hardening: JWT volume mount, docker-compose env vars, defensive guards

</details>

<details>
<summary>v4.0 Attendance Service MVP — SHIPPED 2026-04-04</summary>

**Goal:** Core attendance tracking — geo-checkin, manual marking, auto-absent, basic reports.
**Phases:** 15-19 (5 phases, 12 plans)
**Requirements:** 23/23 satisfied (INFRA-01..06, CHKN-01..07, MARK-01..05, RPRT-01..05)

Key deliverables:
- Geo-checkin with Haversine geofence, Redis dedup/rate-limit, 5-min time window
- Headman manual marking with group authorization and upsert semantics
- Auto-absent on lesson.closed via RabbitMQ ($setOnInsert race-safe)
- Journal grid, student stats, lesson attendance, attendance records
- @RequireRole AOP security on all 9 controller methods
- Domain isolation: report/ accesses checkin/ data only through AttendanceReadPort

</details>

## Target Users
- **Students** (500-5000): geo-checkin, excuse tickets, homework tracker
- **Headmen** (student + is_headman): attendance marking, schedule, subjects, assistants
- **Teachers**: read-only journal, statistics, export
- **Admins**: user/group/semester management, dashboard

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.4, Gradle Kotlin DSL (monorepo)
- **Databases**: PostgreSQL (academic_db, schedule_db), MongoDB (attendance_db), Redis (cache + auth)
- **Messaging**: RabbitMQ (fanout exchange), gRPC (inter-service)
- **Frontend**: React (Telegram Mini App), Angular (web panel), HTML/CSS (landing)
- **Bot**: Python Aiogram 3

## Architecture
5 services + API Gateway + 2 notification containers. Contract-first (api-contract + app modules). HATEOAS Level 3, RFC 7807, Flyway, enums as lowercase strings.

## Developer
Solo developer (Persik), lead developer and sysadmin. IntelliJ IDEA on Windows, deploy to VPS with Docker.

## Requirements

### Validated
- ✓ FR-1 through FR-10: User login, token refresh, logout, public key, OTP flow, change password, Gateway JWT filter, routing, public routes, seed data — v1.0
- ✓ NFR-1: RSA key generation, BCrypt hashing — v1.0
- ✓ NFR-4: RFC 7807 error responses, Java records for DTOs — v1.0
- ✓ CRUD users with auto-generated logins/passwords (ADMIN) — v2.0
- ✓ CRUD groups, assign/revoke headman (ADMIN) — v2.0
- ✓ CRUD semesters with confirmation phrase for delete (ADMIN) — v2.0
- ✓ Student transfers between groups with history (ADMIN) — v2.0
- ✓ Admin dashboard with summary statistics — v2.0
- ✓ CRUD subjects with type (lecture/practice/lab) (HEADMAN) — v2.0
- ✓ Teacher-subject-group assignments (HEADMAN) — v2.0
- ✓ Headman assistants management with granular permissions (HEADMAN) — v2.0
- ✓ CRUD homeworks with homework_completions tracker (HEADMAN/STUDENT) — v2.0
- ✓ Red zone threshold configuration (global/group/subject) — v2.0
- ✓ Student profile and group composition view (STUDENT) — v2.0
- ✓ Teacher own subjects and groups view (TEACHER) — v2.0
- ✓ gRPC server implementing academic.proto (7 RPCs) — v2.0
- ✓ Redis caching with invalidation for read-heavy methods — v2.0
- ✓ RabbitMQ event publishing (group.updated, semester.archived, homework.*) — v2.0

- ✓ TMPL-01..05: Schedule template CRUD with headman authorization — v3.0
- ✓ LSSN-01..07: Lesson auto-generation, cancel/restore/mass-cancel/geo-block — v3.0
- ✓ VIEW-01..02: Schedule view for date range — v3.0
- ✓ CRON-01..04: Cron-based lesson status transitions with Moscow TZ — v3.0
- ✓ EVNT-01..04: RabbitMQ events (lesson.started/closed/cancelled) — v3.0
- ✓ GRPC-01..03: gRPC server (GetActiveLesson, GetLessonById, GetLessonsByGroup) — v3.0

### Recently Validated (v5.0)
- ✓ INFRA-01..03: Two RabbitMQ queues with DLQ, docker-compose containers, Redis key namespace — v5.0
- ✓ WS-01..04: STOMP WebSocket with JWT auth, lesson/cancel/homework push to group topics — v5.0
- ✓ BINFRA-01..03: gRPC group members client, throttled send queue, aio-pika watchdog — v5.0
- ✓ BOT-01..03: /start account linking, /login OTP flow, /status attendance check — v5.0
- ✓ NOTIF-01, NOTIF-04..07: Inline check-in button, cleanup on close/checkin, cancellation, homework notifications — v5.0
- ✓ NOTIF-08..09: Headman excuse/late-checkin alert handlers (wired, awaiting publisher) — v5.0 (partial)
- ✓ WS-05..06: Headman WebSocket push handlers (wired, awaiting publisher) — v5.0 (partial)

### Recently Validated (v6.0)
- ✓ PWA-01..07: Login, token refresh, logout, manifest, Service Worker, A2HS, iOS onboarding — v6.0
- ✓ SCHED-01..03: Schedule view (today/week), offline cache with stale-while-revalidate — v6.0
- ✓ CHKIN-01..03: Geo check-in with GPS, success/failure feedback, real-time STOMP updates — v6.0
- ✓ PUSH-01..07: VAPID keys, subscription CRUD, push delivery (lesson/cancel/homework), 410 cleanup — v6.0
- ✓ PUSHUI-01..04: SW push handler, notificationclick deep links, soft-ask permission, foreground dedup — v6.0
- ✓ ATT-01..03: Stats per subject, red zone indicators, attendance records with status badges — v6.0
- ✓ HW-01..02: Homework list, optimistic completion toggle — v6.0
- ✓ INFRA-01..03: Gateway CORS, push route, nginx PWA serving — v6.0

### Deferred (from previous milestones)
- [ ] Excuse tickets: create/submit/review flow with event publishing (excuse.requested)
- [ ] Late check-in ("forgot to mark") flow with event publishing (late_checkin.requested)
- [ ] NOTIF-02, NOTIF-03: Live timer testing for midpoint/near-end reminders (TZ fix applied)
- [ ] WS-07: Live broker-level group isolation verification
- [ ] WPAN-13: Headman assistant management (blocked — backend @RequireRole(STUDENT) on assistant endpoints)

### Recently Validated (v8.0)
- ✓ MON-01..02: Spring Boot Actuator health/info on all 4 Java services, production-safe config — v8.0
- ✓ DOCK-01..04: Multi-stage Dockerfiles for all 11 services (5 Java + 1 Python + 4 frontend + notification-web) — v8.0
- ✓ DOCK-05..07, MON-03: docker-compose.prod.yml with 17 services, healthchecks, .env.prod secrets, no exposed DB ports — v8.0
- ✓ NET-01..05: Nginx SSL termination, path-based routing, Let's Encrypt certbot, HTTP→HTTPS redirect — v8.0
- ✓ CI-01..04: GitHub Actions CI (Java build+test, Python ruff lint, frontend builds, Gradle cache) — v8.0
- ✓ CI-05..07: GitHub Actions Deploy (11 GHCR images, SSH deploy, GitHub Secrets) — v8.0
- ✓ DOC-01..03: Unified Swagger UI at Gateway (springdoc 2.8.6, aggregated specs, webflux-ui) — v8.0
- ✓ DOC-04: Complete 372-line README with architecture, setup, API docs, deploy guide — v8.0

### Recently Validated (v4.0)
- ✓ INFRA-01..06: MongoDB indexes, enum converters, gRPC clients, RabbitMQ queue, event publishing — v4.0
- ✓ CHKN-01..07: Geo-checkin with geofence, time window, geo-block, dedup, rate limit — v4.0
- ✓ MARK-01..05: Manual marking, auto-absent ($setOnInsert), cancellation propagation — v4.0
- ✓ RPRT-01..05: Lesson attendance, journal grid, student stats, records, domain isolation — v4.0
- ✓ @RequireRole on all 9 controller methods, URL path alignment — v4.0 Phase 19

### Out of Scope
- Mobile native apps — web-first (Telegram Mini App + Angular panel)
- Key Management Service — RSA keys on filesystem for now
- Bulk CSV user import — manual creation sufficient for now
- Excuse tickets (create/submit/review) — deferred to v4.1+
- File attachments + Telegram forwarding — deferred to v4.1+
- Late checkin ("forgot to mark") flow — deferred to v4.1+
- Advanced analytics (trends, top-skippers, red zone alerts) — deferred to v4.1+
- PDF/Excel export — deferred to v4.1+

## Milestones

### v9.0: Frontend Unification — Single Login & Role-Based Web Clients — 🛠 IN PROGRESS
Post-v8.0 production revealed that root `/` serves PWA to all roles, `/admin/` login is broken for non-ADMIN users (infinite redirect loop), landing has dead Telegram links, and web cabinets for STUDENT/HEADMAN don't exist despite being fixed in `docs/design-decisions.md §3` and `docs/job-stories.md`. v9.0 unifies all web clients under a single `/login` with role-based routing, delivers full student and headman web cabinets, extends PWA to HEADMAN, and moves landing to `/presentation/`. See `.planning/v9.0-BRIEF.md` for full context.

### v8.0: CI/CD, Deployment & Documentation — ✅ SHIPPED 2026-04-08
Production-ready deployment pipeline. Actuator health endpoints, multi-stage Dockerfiles (11 images), docker-compose.prod.yml (17 services), nginx SSL with certbot, GitHub Actions CI + GHCR deploy, unified Swagger UI, complete README. 8 phases, 11 plans, 26 requirements. See `.planning/milestones/v8.0-ROADMAP.md`.

### v7.0: Frontends — Mini App, Web Panel, Landing — ✅ SHIPPED 2026-04-07
Telegram Mini App + Angular Web Panel + Landing page. 8 phases, 16 plans, 32/33 requirements. See `.planning/milestones/v7.0-ROADMAP.md`.

### v6.0: PWA + Web Push — ✅ SHIPPED 2026-04-06
React PWA «RutTrack» with Web Push notifications. 6 phases, 14 plans, 32 requirements. See `.planning/milestones/v6.0-ROADMAP.md`.

### v5.0: Notification Service (Web + Bot) — ✅ SHIPPED 2026-04-05
WebSocket push + Telegram bot with 3 commands, 8 event handlers, reminder lifecycle. 7 phases, 16 plans, 25 requirements (19 satisfied, 6 partial). See `.planning/milestones/v5.0-ROADMAP.md`.

### v4.0: Attendance Service MVP — ✅ SHIPPED 2026-04-04
Geo-checkin, manual marking, auto-absent, 4 report endpoints. 5 phases, 12 plans, 23 requirements. See `.planning/milestones/v4.0-ROADMAP.md`.

### v3.0: Schedule Service — ✅ SHIPPED 2026-04-04
Schedule template CRUD, lesson auto-generation, status transitions, RabbitMQ events, gRPC server. 5 phases, 10 plans.

### v2.0: Academic Service — ✅ SHIPPED 2026-03-31
5 phases, 12 plans, 37 requirements, 50 tests. Full CRUD + gRPC + Redis caching + RabbitMQ events. See `.planning/MILESTONES.md`.

### v1.0: Auth Service + API Gateway — ✅ SHIPPED 2026-03-30
4 phases, 4 plans, 26 tests. Full auth flow: login → JWT → Gateway validation → header injection → downstream routing. See `docs/phase-1-report.md`.

### Previous: Phase 0 (completed)
Scaffold, contracts, infrastructure. See `docs/phase-0-report.md`.

## Key Decisions

| Decision | Outcome | Milestone |
|----------|---------|-----------|
| Auth reads academic_db via JPA (Flyway disabled) | ✓ Works well, no schema drift | v1.0 |
| RSA keys on filesystem | ✓ Simple, sufficient for VPS | v1.0 |
| Refresh token rotation (delete on use) | ✓ Prevents replay attacks | v1.0 |
| OTP Telegram delivery deferred | — Pending, notification-bot phase | v1.0 |
| Null-safe header injection | ✓ TEACHER/ADMIN don't get "null" headers | v1.0 |
| Testcontainers over H2 | ✓ Real PostgreSQL ENUMs, no false positives | v1.0 |
| No JPA associations (@ManyToOne etc.) | ✓ FK columns as Long IDs, prevents N+1 and cascade issues | v2.0 |
| campus_settings.id SERIAL→BIGINT | ✓ V4 migration fixes V1 inconsistency with BIGSERIAL convention | v2.0 |
| Contract-first REST (api-contract interfaces) | ✓ Controllers implement interfaces, Swagger in contract | v2.0 |
| @RequireRole AOP over Spring Security | ✓ Simpler, Gateway already validates JWT — service checks role only | v2.0 |
| MongoDB with Spring Data (not JPA) | ✓ Flexible document model for attendance records, bulkOps for auto-absent | v4.0 |
| AttendanceReadPort for domain isolation | ✓ report/ never imports checkin/ — ArchUnit enforced | v4.0 |
| $setOnInsert for auto-absent race safety | ✓ Existing checkins never overwritten by auto-absent | v4.0 |
| Volatile geofence cache with 30min TTL | ✓ Simple, avoids Redis overhead for infrequent geofence changes | v4.0 |
| GenericContainer for Redis Testcontainer | ✓ testcontainers:redis BOM module doesn't exist — GenericContainer works | v4.0 |
| V5 migration: implicit casts for PostgreSQL enums | ✓ JPA sends varchar, PostgreSQL needs CAST for custom enum columns | v2.0 |
| gRPC queries repositories directly, not REST services | ✓ Avoids RequestContext scope issues in gRPC threads | v2.0 |
| @TransactionalEventListener(AFTER_COMMIT) for domain events | ✓ No events on rollback, services decoupled from AMQP | v2.0 |
| @MockitoBean RabbitTemplate in non-event test bases | ✓ Prevents DomainEventListener from breaking tests without RabbitMQ | v2.0 |
| Eager lesson generation (UNIQUE constraint) | ⚠️ saveAll without ON CONFLICT DO NOTHING — retry throws 409, not silent dedup | v3.0 |
| @Profile("!test") on SchedulingConfig | ✓ Cron jobs disabled in test profile, matches @ActiveProfiles("test") | v3.0 |
| grpc.server.port: 19092 | ✓ gRPC server starter added, port configured | v3.0 |
| TZ=Europe/Moscow + hibernate.jdbc.time_zone | ✓ All TIME columns interpreted in Moscow timezone (CRON-04) | v3.0 |
| STOMP in-memory broker (no external broker) | ✓ Sufficient for single-instance VPS deployment | v5.0 |
| JWT claims at handshake only (no re-validation) | ✓ Simplifies WebSocket lifecycle; expired JWT clients keep receiving | v5.0 |
| Separate /headman topic over ChannelInterceptor ACL | ✓ Simpler architecture; client-side subscription honor system acceptable for MVP | v5.0 |
| aio-pika watchdog with 5s retry loop | ✓ Handles RabbitMQ restart gracefully; consumer auto-restarts | v5.0 |
| Token bucket 30 msg/s for Telegram send queue | ✓ No 429 errors; retry backoff [1,2,4]s with duck-typed retry_after | v5.0 |
| Redis RPUSH list for reminder message_ids | ✓ LRANGE retrieves all IDs in order for bulk delete on lesson.closed | v5.0 |
| TZ=Europe/Moscow env var on notification-bot | ✓ datetime.now() aligns with Moscow lesson times from Schedule Service | v5.0 |
| OTP code returned in HTTP response body | ✓ Bot delivers OTP to student via Telegram instead of separate channel | v5.0 |
| grpcio 1.73.0 + protobuf 6.31.0 | ✓ Compatible pair; 1.80.x requires protobuf 6.x breaking change | v5.0 |
| VAPID keys persist in Redis (no TTL) | ✓ Never regenerated on restart — avoids invalidating subscriptions | v6.0 |
| injectManifest for vite-plugin-pwa | ✓ Required for custom push event handler in Service Worker | v6.0 |
| httpOnly cookie for refresh token | ✓ Prevents XSS token theft; access token in memory only | v6.0 |
| OPTIONS bypass before isPublicRoute in Gateway | ✓ CORS preflight works without JWT | v6.0 |
| BeforeInstallPromptEvent in declare global block | ✓ TypeScript module visibility for A2HS event | v6.0 |
| useThreshold returns null on 404 | ✓ No red zone indicators shown when no threshold configured (D-06) | v6.0 |
| Optimistic mutations with TanStack Query | ✓ Homework checkbox toggle feels instant; reverts on error | v6.0 |
| MessageDigest.isEqual for TMA HMAC comparison | ✓ Constant-time prevents timing oracle on initData hash | v7.0 |
| TMA re-auth via initData on 401 (not refresh-body) | ✓ Simpler than body-based refresh; initData always available in WebView | v7.0 |
| Memory-only tokens in Mini App (not localStorage) | ✓ Follows D-05; initData re-auth replaces persistence need | v7.0 |
| Tailwind CDN + GSAP CDN for landing page | ✓ Zero-build static page; CSS-only hamburger; darkMode media | v7.0 |
| Angular signals for Web Panel token storage | ✓ Both tokens in signals (memory-only); D-06 overrides httpOnly cookie | v7.0 |
| CdkTable + CdkVirtualScrollViewport for journal | ✓ Handles 500+ rows with 40px fixed row height; sticky student column | v7.0 |
| AdminApiService single Injectable for all CRUD | ✓ 16 methods covering users/groups/semesters/dashboard | v7.0 |
| http.request('DELETE') for semester delete | ✓ HttpClient.delete() drops request body; request() preserves it | v7.0 |
| PATCH status=archived for user archive (not DELETE) | ✓ Explicit control over archive/restore; no confirmation dialog for restore | v7.0 |
| Actuator: health+info only, no env/heapdump in prod | ✓ Minimal attack surface; SecurityFilterChain permits /actuator/** | v8.0 |
| Multi-stage Dockerfiles with layered JARs | ✓ ~300MB images; layer caching for dependencies | v8.0 |
| python:3.12-slim for notification-bot (not Alpine) | ✓ grpcio musl wheels unavailable on Alpine; slim works out of box | v8.0 |
| Sequential GHCR build steps (not matrix) | ✓ Simpler, avoids runner quota issues for 11 images | v8.0 |
| Nginx reverse proxy + certbot sidecar | ✓ SSL termination at edge; only nginx binds host ports 80/443 | v8.0 |
| Two-phase ACME bootstrap (staging then prod) | ✓ Validates DNS/config before requesting real cert | v8.0 |
| springdoc-openapi-starter-webflux-ui for Gateway | ✓ WebFlux variant required; RewritePath proxy for /openapi/{service} | v8.0 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-09 after Phase 49 completion (nginx routing + landing dead-link fix)*
