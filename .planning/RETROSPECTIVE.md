# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

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

### Cumulative Quality

| Milestone | Tests | Key Pattern |
|-----------|-------|-------------|
| v1.0 | 26 | Integration tests with Testcontainers PostgreSQL + Redis |
| v2.0 | 50 | Added gRPC in-process tests, Redis cache verification, RabbitMQ event tests |

### Top Lessons (Verified Across Milestones)

1. Testcontainers > mocks/H2 — catches real database behavior every time
2. Contract-first with separate modules prevents coupling drift
3. Always verify how new infrastructure beans affect existing test configurations
