# RutCampusTrack

## Vision
Microservice attendance tracking system for RUT MIIT university. Production system + portfolio case with full microservice architecture.

## Core Problem
Replace three separate backends (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) with a unified microservice architecture for tracking student attendance.

## Core Value
Working authentication and authorization perimeter — all downstream services receive validated user context (X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman) through the Gateway.

## Current Milestone: v2.0 Academic Service

**Goal:** Full CRUD for university structure with gRPC server for internal calls and Redis caching.

**Target features:**
- REST API через контракт (UserApi, GroupApi, SemesterApi, SubjectApi, AssignmentApi, HomeworkApi, ThresholdApi) с HATEOAS + пагинацией
- CRUD endpoints по ролям: ADMIN (users, groups, semesters, headman, transfers, dashboard), HEADMAN (subjects, teacher assignments, assistants, homeworks, thresholds), STUDENT (profile, group, homework completions), TEACHER (own subjects/groups)
- gRPC-сервер (academic.proto): GetGroup, GetGroupMembers, GetTeacherSubjects, IsHeadman, GetActiveSemester, GetCampusGeofence, GetUserById
- Redis-кэширование read-heavy методов с инвалидацией
- RabbitMQ events: group.updated, semester.archived, homework.published/updated
- Автогенерация логинов (student00001, teacher00001) с initial_password

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
- ✓ CRUD users with auto-generated logins/passwords (ADMIN) — Phase 6
- ✓ CRUD groups, assign/revoke headman (ADMIN) — Phase 6
- ✓ CRUD semesters with confirmation phrase for delete (ADMIN) — Phase 6
- ✓ Student transfers between groups with history (ADMIN) — Phase 6
- ✓ Admin dashboard with summary statistics — Phase 6
- ✓ CRUD subjects with type (lecture/practice/lab) (HEADMAN) — Phase 6
- ✓ Teacher-subject-group assignments (HEADMAN) — Phase 6
- ✓ Headman assistants management with granular permissions (HEADMAN) — Phase 6
- ✓ CRUD homeworks with homework_completions tracker (HEADMAN/STUDENT) — Phase 6
- ✓ Red zone threshold configuration (global/group/subject) — Phase 6
- ✓ Student profile and group composition view (STUDENT) — Phase 6
- ✓ Teacher own subjects and groups view (TEACHER) — Phase 6

### Active
- ✓ gRPC server implementing academic.proto (7 RPCs) — Phase 7
- [ ] Redis caching with invalidation for read-heavy methods
- [ ] RabbitMQ event publishing (group.updated, semester.archived, homework.*)

### Out of Scope
- Mobile native apps — web-first (Telegram Mini App + Angular panel)
- Key Management Service — RSA keys on filesystem for now

## Milestones

### v1.0: Auth Service + API Gateway — ✅ SHIPPED 2026-03-30
4 phases, 4 plans, 26 tests. Full auth flow: login → JWT → Gateway validation → header injection → downstream routing. See `docs/phase-1-report.md`.

### Previous: Phase 0 (completed)
Scaffold, contracts, infrastructure. See `docs/phase-0-report.md`.

## Current State
Milestone 1 shipped. Phase 8 complete — Redis caching added to Academic Service with 5 @Cacheable gRPC read paths (groups, group_members, active_semester, campus_geofence, users), configurable per-cache TTLs, @CacheEvict on all mutation methods, 10 Testcontainers integration tests. Next: Phase 9 RabbitMQ Events.

## Key Decisions

| Decision | Outcome | Milestone |
|----------|---------|-----------|
| Auth reads academic_db via JPA (Flyway disabled) | ✓ Works well, no schema drift | v1.0 |
| RSA keys on filesystem | ✓ Simple, sufficient for VPS | v1.0 |
| Refresh token rotation (delete on use) | ✓ Prevents replay attacks | v1.0 |
| OTP Telegram delivery deferred | — Pending, notification-bot phase | v1.0 |
| Null-safe header injection | ✓ TEACHER/ADMIN don't get "null" headers | v1.0 |
| Testcontainers over H2 | ✓ Real PostgreSQL ENUMs, no false positives | v1.0 |
| gRPC queries repositories directly, not REST services | ✓ Avoids RequestContext scope issues in gRPC threads | v2.0 Phase 7 |
| No JPA associations (@ManyToOne etc.) | ✓ FK columns as Long IDs, prevents N+1 and cascade issues | v2.0 |
| campus_settings.id SERIAL→BIGINT | ✓ V4 migration fixes V1 inconsistency with BIGSERIAL convention | v2.0 |
| Contract-first REST (api-contract interfaces) | ✓ Controllers implement interfaces, Swagger in contract | v2.0 Phase 6 |
| @RequireRole AOP over Spring Security | ✓ Simpler, Gateway already validates JWT — service checks role only | v2.0 Phase 6 |
| V5 migration: implicit casts for PostgreSQL enums | ✓ JPA sends varchar, PostgreSQL needs CAST for custom enum columns | v2.0 Phase 6 |

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
*Last updated: 2026-03-31 after Phase 7 completion*
