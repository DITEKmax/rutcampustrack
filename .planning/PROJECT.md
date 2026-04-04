# RutCampusTrack

## Vision
Microservice attendance tracking system for RUT MIIT university. Production system + portfolio case with full microservice architecture.

## Core Problem
Replace three separate backends (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) with a unified microservice architecture for tracking student attendance.

## Core Value
Attendance tracking backbone: Auth (v1.0) + Academic data (v2.0) + Schedule lifecycle (v3.0) are shipped. Now building the core user-facing feature: Attendance Service MVP — geo-checkin, manual marking, auto-absent, basic reports.

## Current State
v4.0 in progress. Phase 16 (Event Consumers) complete — auto-absent on lesson.closed (bulkWrite $setOnInsert), cancellation propagation on lesson.cancelled, semester cache refresh. 39 tests passing. Next: Phase 17 (Write Path — Geo Checkin + Manual Marking).

## Current Milestone: v4.0 Attendance Service MVP

**Goal:** Core attendance tracking — students check in via geo, headman marks manually, system auto-absents on lesson close, basic reports for journal and stats.

**Target features:**
- Geo-checkin — student sends {lat, lng}, validated against campus geofence + active lesson + time window
- Manual marking — headman sets attendance status per student (autosave per click)
- Automatic absent — on lesson.closed event, unmarked students get status=absent
- RabbitMQ consumers — listen to lesson.started/closed/cancelled from Schedule Service
- gRPC clients — Schedule Service (GetActiveLesson, GetLessonById) + Academic Service (GetGroupMembers, GetCampusGeofence)
- Basic reports — journal by group/subject, student attendance stats, lesson breakdown

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
- [ ] Geo-checkin with campus geofence validation (v4.0)
- [ ] Manual attendance marking by headman (v4.0)
- [ ] Automatic absent on lesson.closed event (v4.0)
- [ ] RabbitMQ event consumers for lesson lifecycle (v4.0)
- [ ] gRPC clients to Schedule and Academic services (v4.0)
- [ ] Basic reports: journal, student stats, lesson breakdown (v4.0)

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
*Last updated: 2026-04-04 after v4.0 milestone start*
