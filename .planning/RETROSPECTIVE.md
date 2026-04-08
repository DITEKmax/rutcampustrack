# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v8.0 — CI/CD, Deployment & Documentation

**Shipped:** 2026-04-08
**Phases:** 8 | **Plans:** 11

### What Was Built
- Spring Boot Actuator health/info endpoints on all 4 Java services with production-safe config
- Multi-stage Dockerfiles for all 11 services: 5 Java (layered JARs), 1 Python (grpcio-compatible), 4 frontends (nginx), 1 notification-web
- Production docker-compose.prod.yml with 17 services, Actuator healthchecks, .env.prod secret interpolation, no exposed DB ports
- Nginx reverse proxy with Let's Encrypt SSL: path-based routing, HTTP→HTTPS redirect, certbot sidecar with auto-renewal
- SSL bootstrap script (init-letsencrypt.sh) with two-phase ACME approach (staging validation then production cert)
- GitHub Actions CI: Java build+test, Python ruff lint+test, frontend builds, Gradle caching
- GitHub Actions Deploy: 11 GHCR image push (sequential), SSH-based VPS docker compose pull+up
- Unified Swagger UI at Gateway aggregating OpenAPI specs from all 4 REST services (springdoc 2.8.6)
- Complete 372-line README with architecture diagram, local dev setup, API docs, and production deploy guide

### What Worked
- Sequential GHCR build steps over matrix strategy — simpler CI config, avoids GitHub runner quota issues for 11 images
- Two-phase ACME bootstrap — validates DNS/config with staging cert before requesting production cert
- springdoc-openapi-starter-webflux-ui for Gateway — correct variant for Spring Cloud Gateway (WebFlux)
- python:3.12-slim over Alpine — grpcio musl wheels unavailable, slim works out of box
- Phase 48 README as final milestone phase — captures all prior decisions and infrastructure in one document
- Actuator restricted to health+info only — minimal attack surface while enabling container healthchecks

### What Was Inefficient
- REQUIREMENTS.md checkboxes for phases 42-45 remained unchecked despite phase completion — traceability table status also stale ("Pending")
- SUMMARY.md one_liner extraction still poor — milestone CLI extracted gibberish for most plans (filenames, partial sentences)
- No milestone audit run before completion — relied on individual phase verifications

### Patterns Established
- Multi-stage Dockerfile: Gradle build stage → eclipse-temurin:21-jre runtime stage with layered JARs
- Frontend Dockerfile: node build stage → nginx:alpine runtime with custom nginx.conf
- Production compose: .env.prod interpolation, no host DB ports, Actuator healthcheck wait conditions
- SSL: nginx + certbot sidecar with volume-shared certs, init-letsencrypt.sh for first deploy
- CI/CD: ci.yml on push/PR, deploy.yml on main merge, GHCR for container registry

### Key Lessons
1. Requirements checkboxes should be updated at phase completion, not deferred to milestone — stale checkboxes create false "incomplete" signals during milestone readiness checks
2. SUMMARY.md one_liner field is consistently unreliable across milestones — either enforce structured extraction or deprecate the field
3. Infra-only milestones (no new business logic) execute faster than feature milestones — 8 phases in 2 days with no rework
4. springdoc WebFlux variant is required for Spring Cloud Gateway — standard servlet variant fails silently

### Cost Observations
- Model mix: ~15% opus (orchestration), ~85% sonnet (execution/verification)
- Sessions: ~3 sessions across 2 days
- Notable: Documentation-heavy milestone (Dockerfiles, compose, CI YAML, README) — no test writing, faster execution than feature milestones

---

## Milestone: v7.0 — Frontends — Mini App, Web Panel, Landing

**Shipped:** 2026-04-07
**Phases:** 8 | **Plans:** 16

### What Was Built
- Gateway CORS expansion + nginx containers for Mini App, Web Panel, Landing
- Telegram initData HMAC-SHA256 auth + body-based refresh endpoint
- Static landing page with GSAP animations, responsive layout, dark mode
- Telegram Mini App: schedule, geo check-in, stats, homework, theme support
- Angular Web Panel: login, teacher journal+stats, admin dashboard+user/group/semester CRUD

### What Worked
- MessageDigest.isEqual for TMA HMAC comparison — constant-time prevents timing oracle
- Memory-only tokens in Mini App (not localStorage) — initData re-auth replaces persistence need
- CdkTable + CdkVirtualScrollViewport for journal — handles 500+ rows efficiently
- AdminApiService single Injectable for all CRUD — 16 methods, clean API surface

### What Was Inefficient
- WPAN-13 (headman assistant management) blocked by backend @RequireRole(STUDENT) constraint — deferred
- v7.0 milestone archives not created during v6.0 completion

### Patterns Established
- TMA auth: initData HMAC-SHA256 validation, re-auth on 401 via initData (no refresh token)
- Angular signals for token storage (memory-only)
- Landing: Tailwind CDN + GSAP CDN, zero-build static page

### Key Lessons
1. httpOnly cookies don't work in Telegram WebView — Mini App needs body-based refresh
2. Angular Material 3 requires standalone components — no NgModule declarations

### Cost Observations
- Model mix: ~20% opus, ~80% sonnet
- Sessions: ~4 sessions across 2 days

---

## Milestone: v6.0 — PWA + Web Push

**Shipped:** 2026-04-06
**Phases:** 6 | **Plans:** 14

### What Was Built
- Web Push backend in notification-web: VAPID key generation (Redis-persisted), push subscription CRUD in MongoDB, async push delivery for lesson.started/cancelled/homework events, HTTP 410 auto-cleanup
- API Gateway CORS expansion for PWA origin + OPTIONS bypass in JwtAuthenticationFilter, nginx container for PWA static serving
- React PWA scaffold: Vite + TanStack Query + Tailwind + Framer Motion, JWT auth with httpOnly cookie refresh, manifest + Service Worker, A2HS install prompt, iOS onboarding
- Schedule view with week navigation + geo check-in with GPS capture + STOMP WebSocket real-time updates
- Service Worker push handlers with notificationclick deep links (check-in for lesson.started, schedule for cancelled), soft-ask permission pattern, foreground push dedup
- Attendance stats per subject with red zone indicators + homework list with optimistic completion toggle

### What Worked
- httpOnly cookie for refresh token — secure pattern, prevents XSS token theft
- injectManifest for vite-plugin-pwa — required for custom push event handler in Service Worker
- OPTIONS bypass before isPublicRoute in Gateway — CORS preflight works without JWT
- Optimistic mutations with TanStack Query — homework checkbox toggle feels instant; reverts on error
- useThreshold returns null on 404 — no red zone indicators shown when no threshold configured
- soft-ask permission pattern — no intrusive permission prompt on first load
- STOMP WebSocket for real-time check-in count updates — live feedback during active lesson

### What Was Inefficient
- Agent confusion during milestone transition — agents overwrote v7.0 planning files with stale v6.0 content, requiring manual repair
- Phase 32 was initially shown as Planned in committed ROADMAP.md even after completion — milestone closing procedure missed updating it
- v6.0 milestone archives (v6.0-ROADMAP.md, v6.0-REQUIREMENTS.md) were not created during milestone completion

### Patterns Established
- PWA: Vite + React + TanStack Query + Tailwind + Framer Motion stack
- Auth: httpOnly cookie for refresh token, access token in React memory (useState)
- Push: soft-ask permission → PushSubscription → SW push handler → notificationclick deep link
- Offline: stale-while-revalidate with TanStack Query gcTime/staleTime
- A2HS: BeforeInstallPromptEvent in declare global block for TypeScript
- STOMP integration from PWA: SockJS + @stomp/stompjs with JWT handshake

### Key Lessons
1. Milestone completion procedure MUST create archive files (v{X}.0-ROADMAP.md, v{X}.0-REQUIREMENTS.md) and update all planning files atomically — agents doing partial updates cause inconsistency
2. httpOnly cookies don't work in Telegram WebView — Mini App (v7.0) must use localStorage + body-based refresh instead
3. OPTIONS bypass must come before public route check in Gateway JWT filter — otherwise CORS preflight gets 401
4. VAPID keys must persist across service restarts (Redis, no TTL) — regeneration invalidates all existing subscriptions

### Cost Observations
- Model mix: ~20% opus (orchestration), ~80% sonnet (execution/verification)
- Sessions: ~4 sessions across 2 days
- Notable: First frontend milestone — TypeScript/React phases executed differently from Java backend phases; UI-heavy work needed more iterative adjustments

---

## Milestone: v5.0 — Notification Service (Web + Bot)

**Shipped:** 2026-04-05
**Phases:** 7 | **Plans:** 16

### What Was Built
- Notification Web (Java): STOMP WebSocket with JWT handshake auth, SimpMessagingTemplate routing to group topics, headman-only topic for excuse/late-checkin events, RabbitMQ consumer
- Notification Bot (Python/Aiogram 3): /start account linking, /login OTP flow with FSM, /status attendance check with multi-service data aggregation
- Bot infrastructure: aio-pika consumer with watchdog reconnect, async gRPC client with 5-min cache, Redis async client for reminder message_ids, throttled send queue (30 msg/s token bucket)
- Event notifications: lesson.started with inline Mini App check-in button, lesson.cancelled, homework published/updated, headman alerts for excuse/late-checkin requests
- Reminder lifecycle: midpoint + near-end reminders via asyncio timers, cleanup on lesson.closed (bulk delete), immediate cleanup on attendance.marked (per-student delete)
- Deployment hardening: JWT key volume mount, 6 docker-compose env vars, None guard in lesson_closed handler, TZ=Europe/Moscow

### What Worked
- Contract-first gRPC approach carried over smoothly to Python — proto stubs generated and imported cleanly
- EventDispatcher pattern with lambda handler registry — clean separation of routing from business logic
- TDD in Python (RED-GREEN per task) — caught late-binding closure bug in lesson_started early
- Default-arg lambda binding (s=student) — documented Python pattern for async closure correctness
- Token bucket rate limiter with duck-typed retry_after — handles Telegram 429 elegantly
- Phase 26 gap closure pattern (from v4.0) — single hardening phase fixed 3 deployment issues cleanly
- Integration checker agent caught TZ env var gap and missing event publishers — high-value cross-phase verification

### What Was Inefficient
- SUMMARY.md `one_liner` fields mostly empty — milestone extraction returned placeholders for 14/16 plans
- `requirements_completed` frontmatter field unused in most SUMMARYs — 3-source cross-reference fell back to VERIFICATION + REQUIREMENTS only
- excuse.requested and late_checkin.requested handlers built but no publisher exists — 4 requirements (WS-05, WS-06, NOTIF-08, NOTIF-09) are technically partial until future phases add the workflow
- Phase 24 VERIFICATION.md was named `VERIFICATION.md` instead of `24-VERIFICATION.md` — inconsistent with other phases, caused initial "missing" detection in audit

### Patterns Established
- Python bot: Pydantic BaseSettings for env-var-driven config with explicit defaults
- aiogram 3 DI via dp[] dictionary injection — all handlers receive clients as keyword args
- asyncio.create_task for background timers with in-memory dict registry for cancellation
- Redis RPUSH/LRANGE list pattern for ordered message_id tracking with TTL
- `try/except TelegramBadRequest: pass` for safe delete_message (message may already be deleted)
- Health endpoint pattern: check consumer task + connection liveness, return JSON status

### Key Lessons
1. Always add TZ env var to docker-compose when container code uses naive datetime — timezone mismatch causes systematic timer errors
2. When building event handlers for future event types, document that the publisher doesn't exist yet — prevents confusion during milestone audit
3. SUMMARY.md one_liner field should be filled at plan completion — empty fields cascade to poor milestone documentation
4. Python async closures in loops need default-arg binding — `lambda s=student:` not `lambda: student`
5. Watchdog pattern (while True + sleep + restart) is essential for aio-pika consumers — RabbitMQ restart silently kills consumers

### Cost Observations
- Model mix: ~20% opus (orchestration/audit), ~80% sonnet (execution/verification)
- Sessions: ~6 sessions across 2 days
- Notable: 7 phases in 2 days — fastest milestone by phase count; Python phases executed faster than Java due to less boilerplate

---

## Milestone: v4.0 — Attendance Service MVP

**Shipped:** 2026-04-04
**Phases:** 5 | **Plans:** 12

### What Was Built
- MongoDB infrastructure: programmatic indexes, lowercase enum converters, gRPC clients to Schedule + Academic services
- RabbitMQ event consumers: auto-absent on lesson.closed ($setOnInsert race-safe), cancellation propagation, DLQ
- Geo-checkin write path: 7-step orchestration (rate-limit, active lesson, time window, geo-block, Haversine geofence, Redis dedup, MongoDB save) + event publishing
- Headman manual marking: group authorization, upsert semantics, attendance.marked event
- Report read path: lesson attendance (left-join roster), journal grid, student stats (CANCELLED-excluded), attendance records
- Domain isolation: report/ accesses checkin/ data only through AttendanceReadPort (ArchUnit enforced)
- Security: @RequireRole on all 9 controller methods, URL path alignment

### What Worked
- AttendanceReadPort for domain isolation — clean boundary between report/ and checkin/ packages
- $setOnInsert for auto-absent — race-safe, never overwrites existing checkins
- Volatile geofence cache with 30min TTL — simple and effective for infrequent geofence changes
- BulkMode.UNORDERED for auto-absent — one student error doesn't block the rest
- Phase 19 gap closure pattern — audit found INT-01/INT-02, single phase fixed both cleanly
- Integration checker agent caught all cross-phase wiring — 22/22 exports verified

### What Was Inefficient
- Some SUMMARY.md files had empty `one_liner` fields — extraction during milestone completion returned "One-liner:" placeholder
- Phase 18 VERIFICATION noted Docker-dependent integration tests couldn't run during static analysis — recurring pattern from v2.0
- semester.archived integration test has pre-existing NPE — carried through 5 phases without diagnosis
- ReportController initially shipped without @RequireRole (Phase 18) — caught by milestone audit, fixed in Phase 19

### Patterns Established
- mongoTemplate.remove(new Query()) over dropCollection() — preserves MongoDB indexes between tests
- lenient() stubs in @BeforeEach — avoids UnnecessaryStubbingException with Mockito strict mode
- GenericContainer for Redis Testcontainer (testcontainers:redis BOM doesn't exist)
- @MockitoSpyBean for event publisher verification — wraps real bean for verify() without mocking behavior
- Domain isolation via port interface + ArchUnit rule

### Key Lessons
1. Security annotations (@RequireRole) should be added during initial controller implementation, not as a separate gap-closure phase — inconsistency is easy to miss
2. MongoDB $setOnInsert is the correct pattern for "insert if absent, leave if exists" — simpler than conditional logic
3. Domain isolation should be enforced from the start (port interface + ArchUnit) — retrofitting is harder
4. Pre-existing test failures should be diagnosed when first noticed, not carried forward across phases

### Cost Observations
- Model mix: ~20% opus (orchestration), ~80% sonnet (execution/verification)
- Notable: Single-plan Phase 19 executed efficiently with worktree isolation in ~10 minutes

---

## Milestone: v3.0 — Schedule Service

**Shipped:** 2026-04-04
**Phases:** 5 | **Plans:** 11

### What Was Built
- Schedule template CRUD with headman authorization, gRPC client to Academic Service for validation
- Lesson auto-generation with week-parity algorithm (even/odd weeks)
- Cron-based lesson status transitions (planned→active→closed) with Moscow TZ
- RabbitMQ event publishing (lesson.started, lesson.closed, lesson.cancelled)
- gRPC server (GetActiveLesson, GetLessonById, GetLessonsByGroup) for Attendance Service

### What Worked
- gRPC client reuse pattern from v2.0 — consistent inter-service communication
- @Profile("!test") on SchedulingConfig — cleanly disables cron in tests
- TZ=Europe/Moscow + hibernate.jdbc.time_zone — all TIME columns in Moscow timezone
- Week-parity lesson generation algorithm — correct and efficient

### What Was Inefficient
- LSSN-03 eager generation without ON CONFLICT DO NOTHING — saveAll throws 409 on retry instead of silent dedup
- IllegalArgumentException handler missing in REST GlobalExceptionHandler (works in gRPC but not REST)
- GetLessonsByGroup hardcoded to include all statuses including cancelled — no caller filter control

### Patterns Established
- @Profile("!test") for cron configuration
- TZ handling: explicit Moscow timezone in JVM + Hibernate + cron expressions
- gRPC server implementation pattern with Spring Boot starter

### Key Lessons
1. When generating bulk data (lessons), consider idempotency from the start — ON CONFLICT DO NOTHING is cheaper to add upfront than to retrofit
2. Exception handlers should be consistent across REST and gRPC layers — share the same exception types
3. gRPC server queries should support filtering parameters rather than hardcoding included statuses

---

## Milestone: v2.0 — Academic Service

**Shipped:** 2026-03-31
**Phases:** 5 | **Plans:** 12

### What Was Built
- Complete JPA data layer (7 entities, 7 repositories, soft delete, login sequences)
- Contract-first REST API with HATEOAS for 4 roles (admin, headman, student, teacher)
- gRPC server with 7 RPCs for inter-service communication
- Redis caching on 5 read-heavy gRPC paths with cascading eviction
- RabbitMQ domain event publishing (4 event types) via @TransactionalEventListener(AFTER_COMMIT)

### What Worked
- Contract-first approach (api-contract + app modules) — clean separation, Swagger stays in interfaces
- @RequireRole AOP over Spring Security — simpler, Gateway handles JWT, service just checks role
- Testcontainers over H2 — caught real PostgreSQL ENUM issues that H2 would miss
- No JPA associations — FK columns as Long IDs eliminated N+1 and cascade problems
- gRPC querying repositories directly instead of through REST services — avoided RequestContext scope issues

### What Was Inefficient
- Some SUMMARY.md files didn't populate the `one_liner` field properly, making milestone extraction fragile
- Progress table in ROADMAP.md got stale (phases showed "In Progress" after completion) — need better auto-update
- Phase 9 DomainEventListener broke 44 existing tests — the regression fix (mock RabbitTemplate in test base classes) should have been anticipated during planning
- 2 CacheIntegrationTest failures (activateSemester) pre-existed but weren't caught before Phase 9

### Patterns Established
- `@MockitoBean RabbitTemplate` in test base classes that exclude RabbitAutoConfiguration
- DomainEvent with nested Payload record pattern for typed events
- Phase verification with spot-check before marking complete
- Separate test base classes per infrastructure combo (Postgres-only, Postgres+Redis, Postgres+RabbitMQ)

### Key Lessons
1. When adding infrastructure beans (like DomainEventListener) that depend on optional services (RabbitMQ), always check how existing test configurations handle the dependency — test base classes that exclude auto-configuration will break
2. Flyway V3 migration for sequences — always test login generation under concurrent scenarios
3. @TransactionalEventListener(AFTER_COMMIT) + non-transacted RabbitTemplate is the correct combination — transacted template causes message loss with AFTER_COMMIT

### Cost Observations
- Model mix: ~30% opus (orchestration), ~70% sonnet (execution/verification)
- Notable: Parallel executor agents (worktree isolation) worked well for Wave 1 plans

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 4 | 4 | Established contract-first, Testcontainers, RFC 7807 |
| v2.0 | 5 | 12 | Added gRPC, Redis, RabbitMQ; parallel agent execution |
| v3.0 | 5 | 11 | Cron scheduling, Moscow TZ, week-parity generation, gRPC server |
| v4.0 | 5 | 12 | MongoDB + Testcontainers, domain isolation, gap-closure pattern |
| v5.0 | 7 | 16 | Python Aiogram bot, WebSocket, asyncio timers, cross-language gRPC |
| v6.0 | 6 | 14 | First frontend: React PWA, Web Push, Service Worker, STOMP from browser |
| v7.0 | 8 | 16 | Telegram Mini App, Angular Web Panel, Landing; TMA HMAC auth |
| v8.0 | 8 | 11 | CI/CD pipeline, Docker, SSL, Swagger aggregation, README |

### Cumulative Quality

| Milestone | Tests | Key Pattern |
|-----------|-------|-------------|
| v1.0 | 26 | Integration tests with Testcontainers PostgreSQL + Redis |
| v2.0 | 50 | Added gRPC in-process tests, Redis cache verification, RabbitMQ event tests |
| v3.0 | — | Cron job tests with @Profile("!test"), lesson generation verification |
| v4.0 | ~80 | MongoDB Testcontainers, ArchUnit domain isolation, Redis dedup/rate-limit tests |
| v5.0 | ~128 | Java: 20 WebSocket/RabbitMQ tests. Python: 108 pytest-asyncio tests (fakeredis, mock gRPC/Telegram) |
| v6.0 | 63 | Vitest for React components + hooks, TanStack Query test utils, SW push handler mocks |
| v7.0 | 164 | Angular Vitest (129 Web Panel), Mini App vitest (35), ArchUnit |
| v8.0 | — | No new tests (infra/docs milestone); Actuator integration tests in Phase 41 |

### Top Lessons (Verified Across Milestones)

1. Testcontainers > mocks/H2 — catches real database behavior every time (v1.0-v4.0)
2. Contract-first with separate modules prevents coupling drift (v2.0-v4.0)
3. Always verify how new infrastructure beans affect existing test configurations (v2.0-v4.0)
4. Security annotations should be applied during initial controller creation, not retrofitted (v4.0)
5. Domain isolation boundaries should be designed upfront with port interfaces (v4.0)
6. Always set TZ env var in docker-compose when code uses naive datetime — systematic timer errors otherwise (v5.0)
7. Gap-closure hardening phase after milestone audit is a reliable pattern — catches deployment blockers before ship (v4.0-v5.0)
8. Milestone completion must atomically update ALL planning files and create archives — partial updates by agents cause state drift requiring manual repair (v6.0)
9. Requirements checkboxes should be updated at phase completion — stale checkboxes create false incomplete signals at milestone readiness (v8.0)
