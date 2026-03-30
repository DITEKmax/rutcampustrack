---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Academic Service
status: executing
stopped_at: Completed 05-01-PLAN.md (Entity and Repository Foundation)
last_updated: "2026-03-30T11:03:01.991Z"
last_activity: 2026-03-30
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 2
  completed_plans: 1
  percent: 0
---

# Project State

## Current Milestone

v2.0 Academic Service — Full CRUD for university structure with gRPC and Redis.

## Current Position

Phase: 05 (entity-and-repository-foundation) — EXECUTING
Plan: 2 of 2
Status: Ready to execute
Last activity: 2026-03-30

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-30)

**Core value:** Working authentication and authorization perimeter — all downstream services receive validated user context through the Gateway.
**Current focus:** Phase 05 — entity-and-repository-foundation

## Accumulated Context

### Decisions

- [v1.0]: Auth Service reads academic_db via JPA with ddl-auto: validate — any column removal in Academic Service migrations breaks Auth Service at startup. Treat `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id` as a shared contract.
- [v2.0 research]: gRPC port set to 19091 (not default 9090 — conflicts with Auth Service).
- [v2.0 research]: V1/V2 Flyway migrations are immutable; all new schema changes start at V3.
- [v2.0 research]: RabbitMQ events must use @TransactionalEventListener(AFTER_COMMIT) — never publish inside @Transactional before commit.
- [v2.0 research]: Login generation must use PostgreSQL sequences (not MAX()+1) to avoid race conditions.
- [Phase 05]: Semester.date_from/date_to use LocalDate (DATE columns), not OffsetDateTime — aligns with V1 schema
- [Phase 05]: AbstractAcademicIntegrationTest excludes RabbitMQ and Redis autoconfigurations to avoid connection failures in tests
- [Phase 05]: CampusSetting PK is SERIAL (not BIGSERIAL) but mapped to Long — JDBC widens safely

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 6: AssistantPermission mapped as VARCHAR(64)[] PostgreSQL array — JPA @Converter interaction with Hibernate arrays is non-standard; verify mapping approach with Testcontainers test early in Phase 5.
- Phase 8: @Cacheable self-invocation via Spring AOP proxy is a runtime-only failure — requires Testcontainers integration tests for every cache path, not just unit tests.

## Session Continuity

Last session: 2026-03-30T11:03:01.988Z
Stopped at: Completed 05-01-PLAN.md (Entity and Repository Foundation)
Resume file: None
