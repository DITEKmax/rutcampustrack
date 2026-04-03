# Milestones

## v3.0 Schedule Service (Shipped: 2026-04-03)

**Phases completed:** 4 phases, 9 plans, 14 tasks

**Key accomplishments:**

- One-liner:
- ScheduleItemService
- LessonWithItem
- ScheduleItemService fully wired: POST creates template and generates all semester lessons; PUT detects schedule-affecting field changes and re-generates future planned lessons; 7 integration tests prove end-to-end behavior with Testcontainers PostgreSQL.
- LessonRepository.java
- LessonStatusTransitionJobTest
- One-liner:

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
