# Milestones

## v9.0 Frontend Unification — Single Login & Role-Based Web Clients (Shipped: 2026-04-27)

**Phases completed:** 13 phases (49-61), 41 plans
**Timeline:** 2026-04-08 → 2026-04-27

**Key accomplishments:**

1. Nginx routing + landing dead link fix (Phase 49) — `/` → `/login` redirect, dead Telegram links cleaned
2. baseHref migration to unified `/login` (Phase 50) — Angular web-panel single-app for all roles, lazy feature routes per role (admin/teacher/student/headman)
3. Student web cabinet (Phases 51-53) — schedule + check-in + homework + stats + notifications + profile + excuses + late check-in + PWA install banner
4. Headman web cabinet (Phases 54-55) — group management + subjects (WPAN-13 backend fix to @RequireRole) + attendance management + stats
5. PWA Headman mode (Phase 56) — `features/headman/` directory in React PWA, segmented control + journal + stats
6. Landing presentation mode + project documentation (Phase 57) — GSAP "how it works" animation, role descriptions, URL layout doc
7. Admin BUG-006 fixes (Phase 58) — search, group rename/archive/promotion, semester validation, telegram-required for students, conflict-handler init password
8. Excuses backend (Phase 59) — excuse.requested publisher + headman approval flow
9. Headman schedule management (Phase 60) — template CRUD + one-off lessons, native SQL для PG enum, AFTER_COMMIT events, attendance cascade
10. Headman homework management UI (Phase 61) — `/headman/homework` weekly list, three student view modes (Day/Week/Month), homework events extended with lesson_date+lesson_number, push on update

**Archives:**

- `.planning/milestones/v9.0-ROADMAP.md`
- `.planning/milestones/v9.0-REQUIREMENTS.md`
- `.planning/milestones/v9.0-BRIEF.md`
- `.planning/milestones/v9.0-SUMMARY.md`
- `.planning/milestones/v9.0-phases/` (49-61, 13 directories)

**Known gap:**

- Phase 61 plan 7 SUMMARY/VERIFICATION not created (closed by user without formal verification)

---

## v8.0 CI/CD, Deployment & Documentation (Shipped: 2026-04-08)

**Phases completed:** 8 phases, 11 plans
**Timeline:** 2 days (2026-04-07 → 2026-04-08)
**Requirements:** 26/26 satisfied
**Git:** 30 commits, 253 files, +41,428 lines

**Key accomplishments:**

1. Spring Boot Actuator health/info endpoints on all 4 Java services with production-safe config (MON-01, MON-02)
2. Multi-stage Dockerfiles for all 11 services: 5 Java (layered JARs), 1 Python (grpcio-compatible), 4 frontends (nginx), 1 notification-web
3. Production docker-compose.prod.yml with 17 services, Actuator healthchecks, .env.prod secret interpolation, no exposed DB ports
4. Nginx reverse proxy with Let's Encrypt SSL: path-based routing to all services and frontends, certbot bootstrap script
5. GitHub Actions CI (Java build+test, Python ruff lint, frontend builds) + Deploy (11 GHCR images, SSH-based VPS deploy)
6. Unified Swagger UI at Gateway aggregating OpenAPI specs from auth, academic, schedule, attendance services (springdoc 2.8.6)
7. Complete 372-line README with architecture diagram, local dev setup, Swagger UI links, and production deploy guide

**Archives:**

- `.planning/milestones/v8.0-ROADMAP.md`
- `.planning/milestones/v8.0-REQUIREMENTS.md`

---

## v7.0 Frontends — Mini App, Web Panel, Landing (Shipped: 2026-04-07)

**Phases completed:** 8 phases, 16 plans, 32 tasks

**Key accomplishments:**

- HMAC-SHA256 Telegram initData validation in auth-service: POST /auth/tma exchanges initData for JWT pair, POST /auth/refresh-body provides WebView-compatible cookie-less token refresh
- Complete RutCampusTrack marketing landing page — single static HTML with Tailwind CDN, GSAP scroll animations, Phosphor Icons, responsive 360-1440px, dark mode via prefers-color-scheme
- All 4 Mini App feature pages built with full UX states, App.tsx wired with routes and transitions, 21 new tests passing (35 total)
- One-liner:
- AdminApiService with 16 CRUD methods, dashboard with 4 stat cards, RoleChip/StatusChip components, and route wiring for all admin pages
- Group CRUD with headman assign/revoke via user PATCH, semester CRUD with mat-datepicker and typed-confirmation delete

---

## v6.0 PWA + Web Push (Shipped: 2026-04-06)

**Phases completed:** 6 phases, 14 plans
**Timeline:** 2 days (2026-04-05 → 2026-04-06)
**Requirements:** 32/32 satisfied
**Tests:** 63 vitest tests, 4,733 LOC TypeScript

**Key accomplishments:**

1. Web Push backend: VAPID key generation (Redis-persisted), push subscription CRUD in MongoDB, async push delivery for lesson.started/cancelled/homework events, HTTP 410 auto-cleanup
2. API Gateway CORS expansion for PWA origin + OPTIONS bypass in JwtAuthenticationFilter, nginx container for PWA static serving
3. React PWA scaffold: Vite + TanStack Query + Tailwind + Framer Motion, JWT auth with httpOnly cookie refresh, manifest + Service Worker, A2HS install prompt, iOS onboarding
4. Schedule view with week navigation + geo check-in with GPS capture + STOMP WebSocket real-time updates
5. Service Worker push handlers with notificationclick deep links (check-in for lesson.started, schedule for cancelled), soft-ask permission pattern, foreground push dedup
6. Attendance stats per subject with red zone indicators + homework list with optimistic completion toggle

**Archives:**

- `.planning/milestones/v6.0-ROADMAP.md`
- `.planning/milestones/v6.0-REQUIREMENTS.md`

---

## v5.0 Notification Service (Web + Bot) (Shipped: 2026-04-05)

**Phases completed:** 7 phases, 16 plans
**Timeline:** 2 days (2026-04-04 → 2026-04-05)
**Git:** 101 commits, 463 files, ~84K insertions
**Requirements:** 19/25 satisfied (6 partial — 4 await future event publishers, 2 need live testing)

**Key accomplishments:**

1. STOMP WebSocket with JWT handshake auth and group-based event routing for all 5 event types (20 tests)
2. Python Telegram bot with /start account linking, /login OTP flow, /status attendance check
3. Bot infrastructure: aio-pika watchdog reconnect, async gRPC client with 5-min cache, Redis async client, throttled send queue (30 msg/s token bucket)
4. Event notifications: inline check-in button (Mini App WebAppInfo), lesson cancellation, homework published/updated, headman excuse/late-checkin alerts
5. Full reminder lifecycle: midpoint + near-end reminders via asyncio timers, cleanup on lesson.closed and attendance.marked
6. Deployment hardening: JWT key volume mount, docker-compose env vars, defensive None guards

**Known gaps:**

- WS-05, WS-06, NOTIF-08, NOTIF-09: Handlers wired for excuse.requested/late_checkin.requested but no publisher exists yet (future scope)
- WS-07: Group isolation needs live broker-level verification
- NOTIF-02, NOTIF-03: TZ fix applied; live timer testing still needed

---

## v4.0 Attendance Service MVP (Shipped: 2026-04-04)

**Phases completed:** 5 phases, 12 plans, 21 tasks

**Key accomplishments:**

- attendance-app compiles with protobuf+gRPC+Testcontainers dependencies; MongoDB AttendanceDocument entity with 4 programmatic indexes and lowercase enum converters registered via MongoCustomConversions
- gRPC client wrappers for Schedule/Academic services, RabbitMQ DLQ consumer infrastructure, AOP role enforcement, and RFC 7807 error handling — 27 tests all green
- RabbitMQ event consumers fully wired: bulk upsert $setOnInsert auto-absent on lesson.closed and updateMulti cancellation on lesson.cancelled, with safe Integer-to-Long extraction
- 12 new tests prove MARK-03/MARK-04/MARK-05: integration tests via real RabbitMQ + MongoDB Testcontainers and unit tests isolating LessonEventService business logic
- One-liner:
- Geo-checkin write path with 7-step orchestration (rate-limit -> lesson -> time-window -> geo-block -> geofence -> Redis dedup -> MongoDB save -> RabbitMQ publish), CheckinController returning 201 with HATEOAS EntityModel, 7 unit tests, and 8 integration tests covering all CHKN-01..07 + INFRA-06.
- Headman manual attendance marking via MongoTemplate $set/$setOnInsert upsert with group membership authorization and RabbitMQ event publishing
- One-liner:
- ReportService with 4-endpoint read-path logic (left-join roster, journal grid, CANCELLED-excluded stats with gRPC subject name resolution, filterable records) plus thin ReportController implementing ReportApi with HATEOAS wrapping
- 8 unit tests for ReportService stats math + ArchUnit domain isolation rule + 6 MockMvc integration tests covering all 4 report endpoints with correct auth and data shapes
- One-liner:

---

## v3.0 Schedule Service (Shipped: 2026-04-04)

**Phases completed:** 5 phases (10–14), 11 plans
**Requirements:** 25/25 complete (TMPL-01..05, LSSN-01..07, VIEW-01..02, CRON-01..04, EVNT-01..04, GRPC-01..03)

**Key accomplishments:**

1. Schedule template CRUD with headman authorization and gRPC Academic Service validation
2. Lesson auto-generation with week-parity algorithm — POST creates template and generates all semester lessons; PUT detects schedule-affecting changes and re-generates future planned lessons
3. Cron-based status transitions: planned→active→closed with Moscow TZ, RabbitMQ events (lesson.started, lesson.closed, lesson.cancelled)
4. gRPC server: GetActiveLesson, GetLessonById, GetLessonsByGroup for Attendance Service consumption
5. Integration tests with Testcontainers PostgreSQL

**Known tech debt:**

- IllegalArgumentException → HTTP 500 in REST layer (missing handler)
- LSSN-03 idempotency: saveAll throws 409 on retry (no ON CONFLICT DO NOTHING)
- GRPC-03 GetLessonsByGroup includes cancelled lessons (no filter control)

---

## v2.0 Academic Service (Shipped: 2026-03-31)

**Phases completed:** 5 phases, 12 plans, 18 tasks
**Timeline:** ~14 hours (2026-03-30 → 2026-03-31)
**Tests:** 50 (integration tests with Testcontainers)
**LOC:** ~24,355 Java
**Requirements:** 37/37 complete

**Key accomplishments:**

1. JPA Entity + Repository Foundation — 7 entities, 7 repositories, soft delete, login sequences, Testcontainers validation
2. Contract-first REST API with HATEOAS — Full CRUD for all roles (admin, headman, student, teacher), RFC 7807 errors, @RequireRole AOP, 24 integration tests
3. gRPC Server — 7 RPCs for inter-service communication (GetGroup, GetGroupMembers, GetTeacherSubjects, IsHeadman, GetActiveSemester, GetCampusGeofence, GetUserById)
4. Redis Caching — 5 @Cacheable gRPC read paths, configurable TTLs, cascading @CacheEvict, 10 integration tests
5. RabbitMQ Events — 4 domain events via @TransactionalEventListener(AFTER_COMMIT), fanout exchange, 6 integration tests

**Archives:**

- `.planning/milestones/v2.0-ROADMAP.md`
- `.planning/milestones/v2.0-REQUIREMENTS.md`

---

## v1.0 Auth Service + API Gateway (Shipped: 2026-03-30)

**Phases completed:** 4 phases, 4 plans, 17 tasks
**Timeline:** 3 days (2026-03-28 → 2026-03-30)
**Tests:** 26 (15 integration + 11 unit)
**LOC:** ~2,254

**Key accomplishments:**

1. Auth Service with JWT RSA (2048-bit), login/refresh/logout, BCrypt, Spring Security
2. OTP flow with Redis-backed rate limiting (3 attempts/5min, 60s cooldown, 120s TTL)
3. API Gateway JWT filter (GlobalFilter, order -100) with null-safe header injection and RFC 7807 errors
4. Full Testcontainers integration test suite covering all auth endpoints against real PostgreSQL + Redis
5. Gateway E2E verification script (`scripts/verify-gateway-e2e.sh`)

**Archives:**

- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.0-REQUIREMENTS.md`
- `docs/phase-1-report.md`

---
