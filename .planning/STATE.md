---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Academic Service
status: in_progress
stopped_at: Completed 06-04-PLAN.md (all 4 plans of Phase 06 complete)
last_updated: "2026-03-30T19:28:21Z"
last_activity: 2026-03-30
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 6
  completed_plans: 4
  percent: 40
---

# Project State

## Current Milestone

v2.0 Academic Service — Full CRUD for university structure with gRPC and Redis.

## Current Position

Phase: 6
Plan: 4 of 4 (all complete)
Status: Phase 06 complete — Dashboard, User, Group, Semester, Subject, Homework, Threshold, Assignment, Assistant REST APIs implemented + 12 integration tests passing
Last activity: 2026-03-30

Progress: [████░░░░░░] 40%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-30)

**Core value:** Working authentication and authorization perimeter — all downstream services receive validated user context through the Gateway.
**Current focus:** Phase 05 — entity-and-repository-foundation

## Accumulated Context

### Decisions

- [Phase 06-04]: Renamed RequestContextFilter to UserContextFilter — avoids Spring Boot bean name collision with WebMvcAutoConfiguration
- [Phase 06-04]: Added PostgreSQL implicit casts (varchar->user_role etc.) in V5 migration — allows JPA AttributeConverter to work with custom PostgreSQL enum types
- [Phase 06-04]: Native SQL queries with CAST in UserRepository for role-based filtering — required because JPA derived queries send varchar which PostgreSQL rejects for custom enum columns
- [Phase 06-04]: Integration tests without @Transactional on test methods that check state after service calls — prevents Hibernate first-level cache stale reads
- [v1.0]: Auth Service reads academic_db via JPA with ddl-auto: validate — any column removal in Academic Service migrations breaks Auth Service at startup. Treat `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id` as a shared contract.
- [v2.0 research]: gRPC port set to 19091 (not default 9090 — conflicts with Auth Service).
- [v2.0 research]: V1/V2 Flyway migrations are immutable; all new schema changes start at V3.
- [v2.0 research]: RabbitMQ events must use @TransactionalEventListener(AFTER_COMMIT) — never publish inside @Transactional before commit.
- [v2.0 research]: Login generation must use PostgreSQL sequences (not MAX()+1) to avoid race conditions.
- [Phase 05]: Semester.date_from/date_to use LocalDate (DATE columns), not OffsetDateTime — aligns with V1 schema
- [Phase 05]: AbstractAcademicIntegrationTest excludes RabbitMQ and Redis autoconfigurations to avoid connection failures in tests
- [Phase 05]: CampusSetting PK is SERIAL (not BIGSERIAL) but mapped to Long — JDBC widens safely
- [Phase 05]: V4 migration: campus_settings.id was SERIAL (INT4) but CampusSetting entity maps to Long (BIGINT); Hibernate ddl-auto:validate fails — fix with ALTER COLUMN TYPE BIGINT
- [Phase 05]: HeadmanAssistant.permissions uses String[] with @JdbcTypeCode(SqlTypes.ARRAY) — conversion to List<AssistantPermission> is service layer responsibility

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 6: AssistantPermission mapped as VARCHAR(64)[] PostgreSQL array — JPA @Converter interaction with Hibernate arrays is non-standard; verify mapping approach with Testcontainers test early in Phase 5.
- Phase 8: @Cacheable self-invocation via Spring AOP proxy is a runtime-only failure — requires Testcontainers integration tests for every cache path, not just unit tests.

## Session Continuity

Last session: 2026-03-30T19:28:21Z
Stopped at: Completed 06-04-PLAN.md — all Phase 06 plans complete
Resume file: .planning/phases/06-rest-api-hateoas/06-04-SUMMARY.md
