# Project Research Summary

**Project:** RutCampusTrack v4.0 — Attendance Service MVP
**Domain:** University attendance tracking — geo-checkin, manual marking, auto-absent, basic reports
**Researched:** 2026-04-04
**Confidence:** HIGH

## Executive Summary

The Attendance Service is the fourth and most integration-heavy service in the RutCampusTrack monorepo. Unlike Auth, Academic, and Schedule services which were mostly self-contained, Attendance Service is a pure consumer: it calls two gRPC upstreams (Academic Service for geofence and group members, Schedule Service for lesson state), consumes three RabbitMQ event types from the shared fanout exchange, and writes exclusively to MongoDB. The entire v4.0 scope is well-defined because contracts, schemas, and event envelopes were designed in earlier phases — the implementation risk is integration correctness, not requirements ambiguity.

The recommended build order is infrastructure-first: gRPC client wrappers and RabbitMQ consumer setup before any business logic, because every feature depends on both. The most critical business path is the auto-absent flow triggered by `lesson.closed` events: it must be idempotent (MongoDB unique index), use atomic upserts (`$setOnInsert`), and filter out cancelled lessons from the gRPC response. The geo-checkin path requires Haversine distance calculation (pure Java, no library) against a geofence fetched from Academic Service, with Redis deduplication and rate limiting layered on top.

The primary risks are: (1) MongoDB indexes not created at startup causing silent duplicate records, (2) enum values stored as UPPERCASE strings because `@Enumerated` is JPA-only and ignored by Spring Data MongoDB, (3) a race condition between late geo-checkin and auto-absent that must be resolved by `$setOnInsert` semantics, and (4) the fanout exchange queue binding missing at startup which causes `lesson.closed` events to be dropped forever with no recovery. All four are avoidable if addressed in the infrastructure phase before any service logic is written. Domain isolation between `checkin/` and `report/` packages must be enforced with ArchUnit from the first line of code.

---

## Key Findings

### Recommended Stack

The stack is almost entirely determined by the existing monorepo. All runtime dependencies are managed by Spring Boot 3.4.1 BOM. What must be added to `attendance-app/build.gradle.kts`: Testcontainers BOM `1.20.4` (matches academic-app and schedule-app), `org.testcontainers:mongodb` + `org.testcontainers:rabbitmq`, `grpc-client-spring-boot-starter:3.1.0.RELEASE`, Protobuf plugin `0.9.4`, `javax.annotation-api:1.3.2`, and `spring-boot-starter-aop`. No external geo library is needed — a 10-line Haversine utility replaces it cleanly. No JPA, no Flyway, no relational database.

**Core technologies:**
- `spring-boot-starter-data-mongodb` (Spring Data MongoDB 4.4.1 via BOM) — document persistence for attendance records; schemaless, no Flyway; indexes must be created programmatically
- `spring-boot-starter-amqp` (already present) — first RabbitMQ consumer in the system; must use durable named queue, not AnonymousQueue, or events are lost forever on restart
- `grpc-client-spring-boot-starter:3.1.0.RELEASE` — calls to Academic Service (geofence, group members) and Schedule Service (active lesson, lesson details); client-only, no gRPC server in v4.0
- `testcontainers-bom:1.20.4` with `org.testcontainers:mongodb` + `org.testcontainers:rabbitmq` — integration tests require real MongoDB and real RabbitMQ broker; `@ServiceConnection` on containers avoids manual property wiring
- `spring-boot-starter-aop` — `@RequireRole` aspect for authorization, same pattern as schedule-app
- Pure Java `GeoUtils` (Haversine) — single-point geofence validation against campus center; `GeoUtils.isWithinGeofence()` is 10 lines using `java.lang.Math`; MongoDB `$geoWithin` is the wrong tool here

**Critical version constraints:** `protoc:3.25.3` and `protoc-gen-grpc-java:1.63.0` must match exactly across all services sharing `proto/`. Bumping one independently breaks generated stub compatibility.

### Expected Features

The full feature set for v4.0 is defined with HIGH confidence because all contracts, gRPC interfaces, event schemas, and MongoDB document structure were designed in earlier phases.

**Must have (table stakes) — v4.0 launch blockers:**
- gRPC client infrastructure (Schedule + Academic channels and stubs) — everything else blocks on this
- RabbitMQ consumer infrastructure (durable queue bound to `rut-uit.events` fanout exchange)
- Geo-checkin endpoint: active lesson check, geo-block check, time window (±5 min), Haversine geofence, Redis dedup lock, Redis rate limit, MongoDB upsert, `attendance.marked` event publish
- `lesson.closed` consumer + auto-absent bulk write — journal completeness without headman effort
- `lesson.cancelled` consumer + status update — stats correctness (cancelled docs excluded from stats)
- Manual attendance marking endpoint (headman/assistant only, single-student autosave)
- Lesson attendance view (GET by lesson, merged with group members from gRPC)
- Journal view (GET by group+subject+semester, merged with lesson list from gRPC)
- Student attendance stats (MongoDB aggregation by subject, percentage attended)
- Student attendance list (raw records by student+semester)
- `attendance.marked` event publishing (checkin and manual mark sources only, NOT auto-absent bulk)

**Should have (reliability differentiators) — same phase:**
- Redis deduplication lock (5-sec TTL per `{lesson_id, user_id}`)
- Redis rate limiting (3 checkin attempts per minute per user)
- Denormalized MongoDB document fields (`semester_id`, `group_id`, `subject_id` on every write) — enables read-path report queries without gRPC calls at read time
- Idempotent auto-absent via `$setOnInsert` — handles RabbitMQ at-least-once redelivery correctly
- Geofence response caching (60-min in-memory TTL) — avoids gRPC round-trip per checkin request

**Defer to v4.1+:**
- Excuse ticket creation + headman approval flow (file attachments, Telegram forwarding)
- Late checkin request ("byl no zabyl") + headman confirmation workflow
- Teacher attendance override source (`TEACHER_OVERRIDE` enum exists, write flow deferred)
- Red zone threshold alerts, attendance trend charts, PDF/Excel export

**Anti-features — do not implement in v4.0:**
- WebSocket in Attendance Service itself (stateful; notification-web handles push)
- Eager group membership validation per geo-checkin (gRPC call storm under concurrent load)
- Pagination on journal endpoint (bounded: ~20 lessons × 30 students per query)
- Batch manual marking (single-student autosave is the UX requirement per job stories)

### Architecture Approach

Attendance Service is structured as two isolated domains bridged by a port interface: `checkin/` owns the write path (geo-checkin, manual marking, auto-absent) and `report/` owns the read path (journal, stats). The `report/` domain must not import from `checkin/` directly — it reads only through `shared/port/AttendanceReadPort`. This is enforced by ArchUnit tests. An `event/` package handles both incoming RabbitMQ consumption (`LessonEventConsumer`) and outgoing Spring event forwarding (`DomainEventListener`). A `grpc/` package holds both client wrappers. A `security/` package is a verbatim copy of schedule-service's gateway header handling.

**Major components:**
1. `checkin/CheckInService` — geo validation (Haversine), time window check, Redis dedup/rate limit, MongoDB upsert, Spring event publish
2. `checkin/AutoAbsentService` — bulk absent marking on `lesson.closed`; uses `$setOnInsert` for atomic idempotency; must filter cancelled lessons from gRPC response before writing
3. `event/LessonEventConsumer` — first RabbitMQ consumer in the system; durable queue `rut-uit.attendance-service` bound to `rut-uit.events` fanout exchange; routes by `event_type` field
4. `report/ReportService` — journal and stats read path; reads via `AttendanceReadPort`; merges MongoDB records with group member names from Academic Service gRPC
5. `grpc/ScheduleGrpcClient` + `grpc/AcademicGrpcClient` — blocking stubs with 3-second deadlines; explicit `StatusRuntimeException` handling with status code inspection
6. `config/MongoIndexConfig` — programmatic index creation on `ApplicationReadyEvent`; unique compound index on `{lesson_id, user_id}` is the system's primary idempotency guarantee
7. `shared/port/AttendanceReadPort` — interface only; implemented in `checkin/port/AttendanceReadPortImpl`; the only permitted bridge from report to checkin domain

### Critical Pitfalls

1. **MongoDB unique index not created at startup** — `auto-index-creation` is disabled by default in Spring Data MongoDB; `@CompoundIndex` annotations are silently ignored. Without the unique index on `{lesson_id, user_id}`, concurrent checkins and RabbitMQ retries silently create duplicate records. Prevention: `MongoIndexConfig` using `mongoTemplate.indexOps().ensureIndex()` on `ApplicationReadyEvent`, verified by an integration test that expects `DuplicateKeyException` on duplicate insert.

2. **Enum values stored as UPPERCASE strings in MongoDB** — `@Enumerated(EnumType.STRING)` is JPA-only and is silently ignored by Spring Data MongoDB; enums serialize as `"PRESENT"` instead of `"present"`. All report queries filtering by lowercase status return zero results. Prevention: register `MongoCustomConversions` with explicit read/write converters for each enum. Write a mapping assertion test (insert enum, assert raw BSON document has lowercase string) before any service logic.

3. **Auto-absent race condition with late geo-checkin** — geo-checkin request in-flight when `lesson.closed` consumer runs; auto-absent bulk-inserts `absent` for the student before the checkin write completes; student is marked absent despite being present. Prevention: use MongoDB `$setOnInsert` in auto-absent, never plain insert; `$setOnInsert` is atomic at document level and only writes when no document exists.

4. **RabbitMQ consumer queue not bound — lesson.closed events dropped forever** — fanout exchange discards messages when no queue is bound; events lost during startup are gone with no replay mechanism. Prevention: `RabbitConfig` in Attendance Service must declare a durable queue and `BindingBuilder` binding before any lessons close. Smoke test: publish a synthetic event, assert the listener was invoked.

5. **GetLessonsByGroup returns cancelled lessons — auto-absent pollutes collection** — known tech debt: `ScheduleGrpcServiceImpl.getLessonsByGroup()` passes all four statuses including `cancelled`. Auto-absent must filter: `.filter(l -> "closed".equals(l.getStatus()))` as the first line of the processing loop. Never trust upstream gRPC responses to pre-filter for business-rule-critical operations.

6. **Inject the Spring Boot-managed ObjectMapper in RabbitConsumerConfig** — creating a `new ObjectMapper()` bypasses registered Jackson modules (JavaTimeModule, etc.) and risks `@class` type headers in messages. Prevention: inject `ObjectMapper objectMapper` as a parameter and pass it to `Jackson2JsonMessageConverter`. Same pitfall documented in academic-app's `RabbitConfig.java`.

---

## Implications for Roadmap

The feature dependency graph points clearly to a four-phase build. Infrastructure must precede all domain logic; event consumers must precede the write path so auto-absent semantics can be validated without concurrent geo-checkin interference.

### Phase 1: Infrastructure Foundation
**Rationale:** Every feature depends on working gRPC clients, a properly bound RabbitMQ queue, MongoDB index initialization, and security context. These are the four highest-risk pitfalls (MongoDB indexes, enum serialization, queue binding, ObjectMapper injection) — all must be solved here before any service logic is written. Retrofitting any of these into already-written code is expensive.
**Delivers:** Service starts up and connects to all dependencies; MongoDB `attendances` collection with correct unique and query indexes; durable RabbitMQ queue bound to fanout exchange; `ScheduleGrpcClient` and `AcademicGrpcClient` with proper error handling; `@RequireRole` AOP; enum MongoDB converters; abstract Testcontainers integration test base.
**Addresses:** gRPC client infrastructure (P1), RabbitMQ consumer setup (P1), MongoDB index config, security pattern
**Avoids:** Pitfall 1 (missing unique index), Pitfall 2 (enum UPPERCASE), Pitfall 4 (queue not bound), Pitfall 6 (ObjectMapper injection)
**Stack additions:** Protobuf plugin `0.9.4`, `grpc-client-spring-boot-starter:3.1.0.RELEASE`, `javax.annotation-api:1.3.2`, Testcontainers BOM `1.20.4`, `spring-boot-starter-aop`, `sourceSets`+`protobuf` blocks copied from schedule-app
**Research flag:** Standard patterns — copy from schedule-app and academic-app verbatim with package adjustments

### Phase 2: Event Consumers (Auto-Absent + Cancellation)
**Rationale:** Auto-absent is the highest-risk feature (race condition, idempotency, cancelled lesson filtering). Implementing it before the checkin endpoint means no concurrent writes exist to interfere during testing. The Dead Letter Queue configuration for `lesson.closed` consumer must also be here — a silently dropped auto-absent creates a permanently incomplete journal.
**Delivers:** `lesson.closed` consumer with `AutoAbsentService` using `$setOnInsert`; `lesson.cancelled` consumer updating all docs to `status=cancelled`; DLQ configuration for the queue; `lesson.started` consumer for optional context caching; integration tests verifying no absent records for cancelled lessons; concurrency test confirming `$setOnInsert` wins over checkin race.
**Addresses:** lesson.closed + auto-absent (P1), lesson.cancelled (P1)
**Avoids:** Pitfall 3 (race condition via `$setOnInsert`), Pitfall 5 (cancelled lesson filtering)
**Research flag:** Standard patterns for `@RabbitListener`; `$setOnInsert` semantics are well-documented in MongoDB docs

### Phase 3: Write Path — Geo-Checkin and Manual Marking
**Rationale:** With infrastructure and event consumers working, the write path can be built against a collection that already has realistic auto-absent records. End-to-end checkin → auto-absent → journal is verifiable. Geofence caching must be implemented here to avoid a gRPC round-trip on every checkin request.
**Delivers:** `CheckInService` with Haversine geofence validation, time window check (±5 min), geo-block enforcement, Redis dedup lock (5-sec TTL), Redis rate limit (3/min), MongoDB upsert, `attendance.marked` event publish via `ApplicationEventPublisher`; `ManualMarkController` for headman single-student marking; `DomainEventListener` + `RabbitConfig` for outbound event publishing; geofence in-memory cache with 60-min TTL.
**Addresses:** Geo-checkin (P1), manual marking (P1), Redis dedup (P2), Redis rate limit (P2), `attendance.marked` event (P1)
**Avoids:** GPS fraud (Haversine required), double-tap (Redis dedup lock), `@TransactionalEventListener` vs plain `@EventListener` choice (plain `@EventListener` is correct for outbound — no transaction boundary on checkin path)
**Research flag:** Haversine and Redis patterns are standard; confirm `DomainEventListener` uses plain `@EventListener`, not `@TransactionalEventListener` — MongoDB has no transaction manager configured in v4.0

### Phase 4: Read Path — Reports, Journal, Stats
**Rationale:** Reports depend on correctly populated denormalized fields from Phases 2 and 3. Building reports last means test data is realistic (written by actual write-path logic, not factories). The `report/` domain isolation enforced by ArchUnit is final validation that no direct `checkin/` imports crept in during earlier phases.
**Delivers:** `JournalController` + `ReportService` (group journal grid merging MongoDB docs with `GetLessonsByGroup` gRPC response); `StudentStatsController` (MongoDB aggregation `$group` by `subject_id`, excluding `cancelled` status); `LessonAttendanceController` (lesson snapshot merged with `GetGroupMembers` gRPC); student attendance list endpoint; ArchUnit test `reportDoesNotAccessCheckinInternals`; HATEOAS responses on all read endpoints.
**Addresses:** Journal view (P1), lesson attendance view (P1), student stats (P1), student attendance list (P1)
**Avoids:** N+1 gRPC calls on read path (group members cached per `lesson.closed` event processing); report domain importing checkin internals (ArchUnit blocks it)
**Research flag:** MongoDB aggregation `$group` pipeline is standard; in-memory merge for journal is explicitly acceptable at bounded scale (20 lessons × 30 students per query)

### Phase Ordering Rationale

- Infrastructure before all domain logic: all four critical pitfalls (MongoDB indexes, enum converters, RabbitMQ binding, ObjectMapper) must be resolved before any business logic is written; retrofitting is expensive
- Event consumers before write path: auto-absent must be implemented and tested without concurrent geo-checkin writes to avoid test interference; also ensures the `$setOnInsert` pattern is locked in before the checkin write path is built
- Write path before reports: reports are validated against data written by actual write-path logic; factories producing synthetic data mask denormalization bugs that would be caught by end-to-end flows
- Domain isolation (ArchUnit) enforced from Phase 1: the ArchUnit test must be added in Phase 1 and run in CI on every phase; catching a domain boundary violation in Phase 4 requires refactoring already-working code

### Research Flags

Needs resolution before implementation begins:
- **All phases:** The `semester_id` field is required on every MongoDB document for report queries, but `LessonResponse` proto does not include `semester_id`. Must decide: (a) call `GetActiveSemester` gRPC on each write, or (b) cache on service startup. Resolve before Phase 2 begins.
- **Phase 3:** The 5-minute pre-start checkin window has an edge case: `GetActiveLesson` only returns ACTIVE lessons, so a student checking in 4 minutes early gets a NOT_FOUND response. FEATURES.md documents this as explicitly deferred to v4.1. Must be stated in Phase 3 acceptance criteria to avoid confusion during review.

Phases with standard patterns (no external research needed):
- **Phase 1:** gRPC client setup, durable queue config, AOP security context — verbatim copies from schedule-app
- **Phase 2:** `@RabbitListener` routing by `event_type`, `$setOnInsert` — well-documented MongoDB and Spring AMQP patterns
- **Phase 3:** Haversine formula, Redis SETNX, rate counter with TTL — all standard implementations
- **Phase 4:** MongoDB `$group` aggregation, HATEOAS `EntityModel` — established patterns in this codebase

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All dependencies verified against existing `attendance-app/build.gradle.kts`, `schedule-app/build.gradle.kts`, and Spring Boot 3.4.1 BOM. Version numbers are exact matches from working services in the monorepo. |
| Features | HIGH | All contracts (proto files, event schemas, MongoDB schema, Redis key patterns) were designed in prior phases. Feature scope is bounded by the project charter in `.planning/PROJECT.md`. No feature was inferred from external sources. |
| Architecture | HIGH | Full source inspection of `schedule-service` and `academic-service` confirmed all patterns. Package structure, data flows, and component responsibilities are modeled on existing implementations that are verified working. |
| Pitfalls | HIGH | Pitfalls verified against actual codebase — `@Enumerated` JPA-only behavior, `auto-index-creation` disabled by default, known tech debt in `ScheduleGrpcServiceImpl.getLessonsByGroup()`, and `GrpcExceptionAdvice` gap for `IllegalArgumentException` are all observable in current source code. |

**Overall confidence:** HIGH

### Gaps to Address

- **`semester_id` on auto-absent records:** `LessonResponse` proto does not include `semester_id`. All attendance documents require it for report queries. Must pick an approach (call `GetActiveSemester` per write, or cache on startup) before Phase 2 implementation begins. This is the only unresolved design question.
- **5-minute pre-start checkin window:** `GetActiveLesson` gRPC returns only ACTIVE lessons; the pre-start window (±5 min) requires PLANNED lesson lookup. This is documented as scope-deferred to v4.1 — explicitly call it out in Phase 3 acceptance criteria so reviewers do not flag it as a bug.
- **`GetLessonsByGroup` tech debt:** The gRPC server returns all statuses including `cancelled`. Client-side filter `".filter(l -> "closed".equals(l.getStatus()))"` is mandatory in Phase 2. A future Schedule Service phase should fix `GrpcExceptionAdvice` to map `IllegalArgumentException` to `INVALID_ARGUMENT` (known gap in current codebase).
- **Dead Letter Queue for `lesson.closed` consumer:** PITFALLS.md explicitly marks skipping DLQ as never acceptable even for MVP. DLQ configuration must be included in Phase 2. A failed auto-absent that is silently dropped creates a permanently incomplete journal with no automated recovery path.

---

## Sources

### Primary (HIGH confidence — verified from this repo)
- `services/attendance-service/attendance-app/build.gradle.kts` — current dependency baseline
- `services/schedule-service/schedule-app/build.gradle.kts` — gRPC client + Testcontainers + Protobuf plugin pattern (proven working)
- `services/academic-service/academic-app/build.gradle.kts` — gRPC + Testcontainers + RabbitMQ publisher pattern (proven working)
- `proto/schedule.proto`, `proto/academic.proto` — gRPC service contracts (GetActiveLesson, GetLessonById, GetLessonsByGroup, GetGroupMembers, GetCampusGeofence, IsHeadman, GetActiveSemester)
- `event-schemas/lesson.closed.json`, `event-schemas/lesson.cancelled.json`, `event-schemas/attendance.marked.json` — event envelope contracts
- `docs/database-schema.md` — MongoDB document model, Redis key patterns, index definitions
- `docs/job-stories.md` — JS-HEADMAN-01..07, JS-STUDENT-01..08, JS-SYSTEM-05..10
- `.planning/PROJECT.md` — v4.0 milestone scope, Out of Scope list
- `CLAUDE.md` — AttendanceStatus enum values, domain isolation rules (`checkin/` vs `report/`), `@RequireRole` AOP, `@TransactionalEventListener(AFTER_COMMIT)` pattern
- `services/attendance-service/attendance-api-contract/` — existing enum definitions (AttendanceStatus, AttendanceSource, ExcuseType)
- `services/academic-service/academic-app/src/main/java/.../event/RabbitConfig.java` — ObjectMapper injection pitfall documented inline

### Secondary (MEDIUM confidence — external docs consistent with repo patterns)
- Spring Boot 3.4.1 BOM confirms Spring Data MongoDB 4.4.1 and Testcontainers BOM 1.20.4
- [Testcontainers MongoDB module docs](https://java.testcontainers.org/modules/databases/mongodb/) — confirms `org.testcontainers:mongodb` artifact name
- [Spring AMQP fanout tutorial](https://rabbitmq.com/tutorials/tutorial-three-spring-amqp.html) — queue-per-service consumer binding pattern
- Spring Data MongoDB issue tracker (DATAMONGO-891, DATAMONGO-2635) — confirms `@Enumerated` is ignored by MongoDB converter
- Spring Boot auto-index-creation issue [#28478](https://github.com/spring-projects/spring-boot/issues/28478) — confirms default disabled behavior
- [Movable Type Haversine formula](https://www.movable-type.co.uk/scripts/latlong.html) — Earth radius and formula accuracy at campus distances

### Tertiary (LOW confidence / inference-based)
- Race condition behavior (`$setOnInsert` vs plain insert under concurrent load) — derived from MongoDB documentation and codebase analysis; not verified by load test on this specific system
- Geofence cache effectiveness — 60-minute TTL recommendation is based on domain knowledge (campus geofence coordinates change at most once per semester); not measured

---
*Research completed: 2026-04-04*
*Ready for roadmap: yes*
