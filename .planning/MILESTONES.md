# Milestones

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
