# RutCampusTrack

## Vision
Microservice attendance tracking system for RUT MIIT university. Production system + portfolio case with full microservice architecture.

## Core Problem
Replace three separate backends (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) with a unified microservice architecture for tracking student attendance.

## Core Value
Full backend microservice backbone shipped: Auth (v1.0) + Academic data (v2.0) + Schedule lifecycle (v3.0) + Attendance MVP (v4.0). All 4 services operational with complete inter-service communication (gRPC + RabbitMQ). Current focus: real-time notification delivery via WebSocket and Telegram bot.

## Current Milestone: v5.0 Notification Service (Web + Bot)

**Goal:** Real-time push notifications via WebSocket (web panel) and Telegram bot — both consuming RabbitMQ events from existing services.

**Target features:**
- Notification Web (Java, port 9094): WebSocket endpoint with JWT auth, RabbitMQ consumer, event→push mapping
- Notification Bot (Python/Aiogram 3): Telegram bot with /start, /login, /status; RabbitMQ consumer; inline check-in buttons; 3-stage reminders with message cleanup
- Bot gRPC client (grpcio): Academic Service calls for group-based message routing
- Infrastructure: Two independent RabbitMQ queues on existing fanout exchange; Redis for reminder message_id storage

## Current State
v5.0 started 2026-04-04. Backend backbone complete (v1.0-v4.0): Auth, Academic, Schedule, Attendance services all operational. Phase 21 complete — notification-web has STOMP WebSocket with JWT auth and RabbitMQ-to-WebSocket routing for all 5 event types (20 tests). Now building Telegram bot infrastructure.

## Shipped Milestones

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

### Active
- [x] Notification Web: WebSocket endpoint with JWT auth and group-based push delivery — v5.0 Phase 21
- [x] Notification Web: RabbitMQ consumer mapping events to WebSocket messages — v5.0 Phase 21
- [ ] Notification Bot: Telegram bot commands (/start, /login, /status)
- [ ] Notification Bot: RabbitMQ consumer with Telegram message delivery
- [ ] Notification Bot: 3-stage lesson reminders with message cleanup
- [ ] Notification Bot: Inline check-in button opening Mini App
- [ ] Bot gRPC client for Academic Service (group members)
- [ ] Infrastructure: Two RabbitMQ queues on fanout exchange, Redis for reminder message_ids

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
*Last updated: 2026-04-04 after v5.0 milestone start*
