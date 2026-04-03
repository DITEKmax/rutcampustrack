# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

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

### Cumulative Quality

| Milestone | Tests | Key Pattern |
|-----------|-------|-------------|
| v1.0 | 26 | Integration tests with Testcontainers PostgreSQL + Redis |
| v2.0 | 50 | Added gRPC in-process tests, Redis cache verification, RabbitMQ event tests |
| v3.0 | — | Cron job tests with @Profile("!test"), lesson generation verification |

### Top Lessons (Verified Across Milestones)

1. Testcontainers > mocks/H2 — catches real database behavior every time
2. Contract-first with separate modules prevents coupling drift
3. Always verify how new infrastructure beans affect existing test configurations
