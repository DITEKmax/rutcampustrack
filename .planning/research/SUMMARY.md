# Project Research Summary

**Project:** RutCampusTrack v3.0 — Schedule Service
**Domain:** University lesson scheduling, lifecycle management, cron-based transitions, gRPC integration
**Researched:** 2026-03-31
**Confidence:** HIGH

## Executive Summary

Schedule Service is the pivot between the completed Academic Service (upstream data owner) and the upcoming Attendance Service (downstream consumer). Its primary job is two-fold: manage a schedule template (schedule_items) that headmen configure, and convert those templates into concrete lesson rows that transition through states (PLANNED -> ACTIVE -> CLOSED) driven by cron jobs running every minute. Every lesson state transition produces a RabbitMQ event; Attendance Service and Notification Services depend on these events for auto-absent generation and student push notifications. Without a fully functional Schedule Service, Attendance Service cannot check students in at all.

The recommended approach is to build Schedule Service as a direct extension of the Academic Service patterns already proven in this codebase. The contract-first interface pattern, LowercaseEnumConverter, @RequireRole AOP, @TransactionalEventListener(AFTER_COMMIT) event publishing, and Testcontainers integration tests all carry over unchanged — only the domain changes. The single meaningful new capability is the lesson lifecycle automation via Spring @Scheduled (not Quartz: single-VPS deployment makes Quartz overhead unjustified) and gRPC dual-role (Schedule serves Attendance as gRPC server; Schedule calls Academic as gRPC client).

The dominant risks are concentrated in three areas: (1) timezone handling — schedule_items store TIME columns without timezone info; the JVM must run in Europe/Moscow or all cron transitions fire 3 hours late in production; (2) cron-to-event correctness — @TransactionalEventListener(AFTER_COMMIT) silently drops events if the cron method has no @Transactional boundary, and events must only publish when the DB row count confirms the transition actually occurred; (3) lesson generation idempotency — the UNIQUE(schedule_item_id, date) constraint catches duplicates at the DB level, but the application layer must use INSERT ... ON CONFLICT DO NOTHING rather than propagating a DataIntegrityViolationException as a 500. Address all three before writing any cron or generation logic.

---

## Key Findings

### Recommended Stack

The schedule-app build.gradle.kts already has all Spring Boot starters (web, data-jpa, validation, hateoas, amqp) and the Flyway + PostgreSQL driver. The only additions needed are: gRPC server and client starters (net.devh 3.1.0.RELEASE, same version as Academic Service), the protobuf Gradle plugin (0.9.4), Testcontainers BOM (1.20.4) with postgresql and rabbitmq containers, spring-boot-starter-aop for the @RequireRole aspect, and Awaitility for polling assertions on cron tests. H2 cannot emulate the custom PostgreSQL ENUMs (week_type, lesson_status) — Testcontainers PostgreSQL is mandatory.

**Core technologies:**
- `@Scheduled` (Spring built-in, no extra dependency): cron jobs every minute for PLANNED->ACTIVE and ACTIVE->CLOSED transitions — Quartz would require 8 extra DB tables and SchedulerFactory config for no benefit on a single-instance VPS
- `net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE`: serve Attendance Service (GetActiveLesson, GetLessonById, GetLessonsByGroup) — same version proven in Academic Service
- `net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE`: call Academic Service for group/teacher/semester validation at schedule_item creation
- `com.google.protobuf` plugin `0.9.4` + `protoc:3.25.3` + `protoc-gen-grpc-java:1.63.0`: generates Java stubs from proto/schedule.proto and proto/academic.proto — identical pins to Academic Service (do not change one without the other)
- `org.testcontainers:testcontainers-bom:1.20.4`: real PostgreSQL 16 and RabbitMQ containers for integration tests
- `org.awaitility:awaitility` (Spring Boot BOM manages version since 3.2): polling assertions for @Scheduled tests — replaces Thread.sleep
- `java.time.Clock` injected as a bean (no extra library): enables LocalTime.now(clock) in cron comparisons so tests can use Clock.fixed(...) for deterministic time

### Expected Features

All table-stakes features are binary: Attendance Service is completely blocked without every single one of them.

**Must have (table stakes — all P1):**
- CRUD schedule_items (POST/GET list/GET by id/PUT + PATCH /deactivate) with Academic gRPC validation — headman only
- Auto-generate all lesson rows for a semester on schedule_item creation, respecting week_type (ALL/ODD/EVEN) — idempotent via ON CONFLICT DO NOTHING
- GET /lessons by group + date range — all roles
- Cancel single lesson (PLANNED or ACTIVE) with cancel_reason — publishes lesson.cancelled event
- Restore cancelled lesson (future dates only, 422 for past dates)
- Toggle is_geo_blocked on a lesson
- Cron PLANNED -> ACTIVE transition + lesson.started RabbitMQ event
- Cron ACTIVE -> CLOSED transition + lesson.closed RabbitMQ event (critical: triggers auto-absent in Attendance Service)
- gRPC server: GetActiveLesson, GetLessonById, GetLessonsByGroup
- gRPC client: Academic Service GetGroup, GetTeacherSubjects, GetActiveSemester (always with .withDeadlineAfter(3s))

**Should have (competitive, P2 — add after Schedule Service is in pilot use):**
- Bulk cancel all future lessons for a schedule_item
- Redis cache for GetActiveLesson gRPC (only when checkin rate exceeds ~10 concurrent/minute)
- Room change PATCH endpoint on specific lesson (when Notification Service is live)

**Defer (v4+):**
- Holiday calendar / blackout dates — manual cancellation covers the MVP need
- Lesson swap / ad-hoc one-off lessons — breaks the schedule_item invariant, needs separate design
- Substitute teacher assignment — low frequency, significant cross-service complexity
- Exam period scheduling — out of scope for attendance tracking entirely

### Architecture Approach

Schedule Service sits between Academic Service (upstream, called as gRPC client for validation) and Attendance Service (downstream, calls Schedule as gRPC server). It is the sole owner of the lesson lifecycle and the only publisher of lesson.* events to the rut-uit.events fanout exchange. The service follows the same layered structure as Academic Service: contract module (pure java-library, no Spring, no Lombok) + app module (Spring Boot). Controllers implement contract interfaces; Swagger annotations live in the contract interface. The gRPC service impl queries JPA repositories directly (not through the service layer) to avoid RequestContext scope issues in gRPC Netty threads. DomainEventListener bridges Spring ApplicationEvents to RabbitMQ via @TransactionalEventListener(AFTER_COMMIT) — same pattern as Academic Service.

**Major components:**
1. `schedule-api-contract` — REST interface definitions, DTOs (records for request, HATEOAS classes for response), enums; no Lombok, no Spring
2. `ScheduleItemService` — CRUD schedule templates with gRPC validation calls to Academic Service via AcademicGrpcClient
3. `LessonGenerationService` — expand schedule_items into concrete lesson rows using week parity logic; idempotent via ON CONFLICT DO NOTHING; respects semester boundary cap
4. `LessonService` — cancel, restore, geo-block operations on individual lessons
5. `LessonTransitionService` — @Transactional per-lesson transition methods called by the cron; publishes Spring ApplicationEvents; per-lesson transactions isolate failures
6. `LessonStatusScheduler` — two @Scheduled(cron="0 * * * * *") methods (openLessons, closeLessons); delegates to LessonTransitionService; includes staleness guard for lesson.started (skip if > 10 min past start_time)
7. `ScheduleGrpcServiceImpl` — serves 3 RPCs to Attendance Service; queries repositories directly; no authorization check (internal Docker network only); uses time-window query for GetActiveLesson
8. `AcademicGrpcClient` — wraps AcademicGrpcServiceBlockingStub; always calls .withDeadlineAfter(academicDeadlineSeconds, TimeUnit.SECONDS)
9. `DomainEventListener` — @TransactionalEventListener(AFTER_COMMIT) converts Spring ApplicationEvents to RabbitMQ fanout messages on rut-uit.events exchange

**Build order (dependency-driven, each step unblocks the next):**
Step 1: Entities + Repositories -> Step 2: Security infrastructure (copy from academic-app) + timezone + gRPC port config -> Step 3: AcademicGrpcClient + @ControllerAdvice StatusRuntimeException handler -> Step 4: Domain services (LessonGenerationService, ScheduleItemService, LessonService) -> Step 5: REST API contract + controllers + assemblers -> Step 6: gRPC server (ScheduleGrpcServiceImpl) -> Step 7: RabbitMQ events (DomainEvent, DomainEventListener, RabbitConfig) -> Step 8: Cron scheduler (LessonTransitionService + LessonStatusScheduler)

### Critical Pitfalls

1. **@TransactionalEventListener silently drops events from cron threads** — @Scheduled method has no transaction by default; publishEvent() fires but AFTER_COMMIT never triggers; events are silently discarded with no error. Prevention: always delegate to a @Transactional service method (LessonTransitionService.transitionToActive(id)) from the cron. Verify with an integration test that asserts the event actually reaches the RabbitMQ container, not just that publishEvent() was called.

2. **Timezone mismatch — lessons transition 3 hours late in Docker** — start_time/end_time stored as TIME (no timezone); Docker JVM defaults to UTC; university is UTC+3. Prevention: set TZ=Europe/Moscow in docker-compose.yml for schedule-service and postgres-schedule; set spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow in application.yml; inject a Clock bean and use LocalTime.now(clock) everywhere so tests can use Clock.fixed(...).

3. **Week parity off-by-one (ISO week vs academic week)** — Using isoWeek % 2 gives wrong parity when the semester starts on an ISO-even week. Prevention: calculate parity relative to semester start only: weeksSinceStart = WEEKS.between(semesterStart.with(MONDAY), lessonDate.with(MONDAY)); isOdd = (weeksSinceStart % 2 == 0). Implement in LessonDateUtils and unit-test with hand-calculated semester dates before writing any generation loop.

4. **gRPC client without deadline exhausts Tomcat thread pool** — AcademicGrpcServiceBlockingStub with no deadline waits indefinitely if Academic Service is slow; 10 concurrent headman requests exhaust all Tomcat threads. Prevention: always attach .withDeadlineAfter(academicDeadlineSeconds, TimeUnit.SECONDS) on every gRPC stub call. Map DEADLINE_EXCEEDED -> HTTP 503 in @ControllerAdvice via @ExceptionHandler(StatusRuntimeException.class).

5. **lesson.started published for cancelled lessons (cron race condition)** — Concurrent cancel and cron UPDATE both target a PLANNED row at the same instant; if the cron wins, lesson.started fires for a lesson the headman just cancelled. Prevention: check affected row count > 0 before publishing any event; only transition status rows that the UPDATE actually changed; integrate a row-count check as an invariant, never publish based on "should be active by now" logic.

6. **Duplicate lesson generation returns HTTP 500 on retry** — UNIQUE(schedule_item_id, date) correctly prevents duplicates at DB level but DataIntegrityViolationException propagates to the user as 500. Prevention: use INSERT ... ON CONFLICT DO NOTHING via a @Modifying native query; return 200 (not 201) on idempotent second call.

7. **gRPC server port defaults to 9090, conflicting with Auth Service** — net.devh starter defaults to 9090 which is Auth Service's port in this system. Prevention: set grpc.server.port: 19092 in application.yml as the very first step before adding any @GrpcService bean; use expose: (not ports:) in docker-compose.yml so the gRPC port is internal-only.

---

## Implications for Roadmap

Based on the dependency graph discovered in research, Schedule Service must be built in strict layered order: data layer first, then integration infrastructure, then domain logic, then automation. Every downstream phase depends on the previous one compiling and passing tests. All 5 phases together constitute the complete v3.0 Schedule Service milestone.

### Phase 1: Foundation — Entities, Repositories, Security, Timezone, gRPC Port Config
**Rationale:** JPA entities and repositories are compile-time dependencies for all other phases. Security infrastructure (UserContextFilter, RequestContext, RequireRole, RoleCheckAspect) is needed by all controllers. Timezone configuration and gRPC port must be set before any time comparison or @GrpcService bean is written — both are trivially cheap to add now and expensive to fix later.
**Delivers:** Compiled, tested ScheduleItem + Lesson entities; custom JPQL queries (findPlannedToOpen, findActiveToClose, findByGroupIdAndDateBetween, findActiveByGroupIdAndSemesterId, findActiveByGroupAndTime); security filter chain; TZ=Europe/Moscow in docker-compose.yml; grpc.server.port: 19092 in application.yml; abstract Testcontainers base test class.
**Avoids:** Pitfall 9 (gRPC port conflict), Pitfall 2 (timezone — set here before any cron code).
**Research flag:** Standard patterns. Direct copy from academic-app with package adjustments. No external research needed.

### Phase 2: REST API — Schedule Items, Lessons, gRPC Client Validation
**Rationale:** REST endpoints enable manual E2E verification of all domain logic before automation is added. The gRPC client to Academic Service and the StatusRuntimeException handler in @ControllerAdvice must be set up alongside the first endpoint that uses them — putting these in their own phase would leave REST endpoints unprotected against Academic Service errors.
**Delivers:** schedule-api-contract module (ScheduleItemApi, LessonApi, all DTOs, enums); AcademicGrpcClient with .withDeadlineAfter(3s); @ControllerAdvice with StatusRuntimeException handler; ScheduleItemController and LessonController; HATEOAS assemblers; OpenAPI documentation; all HEADMAN and ALL-ROLES REST endpoints; Testcontainers integration tests for CRUD + cancel/restore/geo-block.
**Avoids:** Pitfall 5 (no gRPC deadline), Pitfall 6 (unhandled StatusRuntimeException -> HTTP 500), Pitfall 3 (idempotent generation via ON CONFLICT DO NOTHING).
**Research flag:** Standard patterns. Contract-first, HATEOAS assemblers, @ControllerAdvice — identical structure to academic-app.

### Phase 3: Lesson Generation — Week Parity + Semester Boundary
**Rationale:** LessonGenerationService is isolated as its own phase because the week parity algorithm is the highest-risk pure-logic component in this service. A wrong parity formula invalidates the entire semester's lesson dataset and requires a costly re-generation with manual cleanup. It must be 100% tested before the cron automation relies on it.
**Delivers:** LessonGenerationService with correct ODD/EVEN/ALL week parity (relative to semester start, not ISO week number); semester boundary cap (actualDateTo = min(requestedDateTo, semesterDateTo)); batch insert via saveAll() with hibernate.jdbc.batch_size=50; ON CONFLICT DO NOTHING idempotency; LessonDateUtils.isOddWeek() with exhaustive unit tests using hand-calculated semester dates; integration test: generate twice, assert no exception and no duplicate rows.
**Avoids:** Pitfall 4 (week parity off-by-one), Pitfall 3 (duplicate generation on retry), Pitfall 10 (lessons beyond semester boundary).
**Research flag:** The parity formula is fully specified in PITFALLS.md. No external research needed. Validation is via unit tests with known dates.

### Phase 4: RabbitMQ Events + Cron Status Transitions
**Rationale:** Events must be set up before the cron because the cron publishes events. The @TransactionalEventListener(AFTER_COMMIT) pattern must be verified end-to-end with a RabbitMQ Testcontainers test before the scheduler is wired. The cron must use per-lesson @Transactional service methods (not the scheduler method) so one lesson failure does not block others. Staleness guard prevents stale notifications after service restart.
**Delivers:** DomainEvent base class + LessonStartedEvent/LessonClosedEvent/LessonCancelledEvent; DomainEventListener (@TransactionalEventListener(AFTER_COMMIT)); RabbitConfig (FanoutExchange rut-uit.events, channelTransacted=false); LessonTransitionService (per-lesson @Transactional, affected-row-count guard before event publication); LessonStatusScheduler (two @Scheduled cron methods: openLessons + closeLessons; staleness guard for lesson.started: skip if > 10 min past start_time); @Profile("!test") or @ConditionalOnProperty guard on SchedulingConfig; integration tests verifying event actually reaches RabbitMQ container.
**Avoids:** Pitfall 11 (@TransactionalEventListener on cron thread — no transaction), Pitfall 7 (lesson.started for cancelled lesson), Pitfall 8 (stale notifications after restart), Pitfall 1 (duplicate transitions in multi-instance — row-count guard).
**Research flag:** This phase has the highest implementation risk. The @Scheduled + @Transactional + @TransactionalEventListener interaction is a known footgun. Integration tests must verify the full chain (DB update committed AND RabbitMQ receives event), not just that publishEvent() was called.

### Phase 5: gRPC Server — Serve Attendance Service
**Rationale:** The gRPC server depends only on entities and repositories (Phase 1). It is placed last because its consumer (Attendance Service) does not exist in v3.0, and because the in-process gRPC test pattern requires the full application context (including RabbitMQ config from Phase 4) to be set up cleanly.
**Delivers:** ScheduleGrpcServiceImpl extending ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase; GetActiveLesson with time-window query (start_time <= currentTime <= end_time + status=active) returning NOT_FOUND when no match; GetLessonById (PK lookup, NOT_FOUND if absent); GetLessonsByGroup (date range filter, returns repeated LessonResponse); in-process gRPC integration tests using grpc.server.in-process-name pattern.
**Avoids:** Pitfall 12 (GetActiveLesson returns wrong lesson — use time-window query), Anti-Pattern 4 (never inject RequestContext into gRPC service impl — runs on Netty thread, not Tomcat thread).
**Research flag:** Standard patterns. AcademicGrpcServiceImpl is the direct template. No external research needed.

### Phase Ordering Rationale

- Phase 1 before everything: entities and repositories are compile-time dependencies; timezone and port configuration are infrastructure decisions that must not be deferred
- Phase 2 before automation: REST endpoints enable manual E2E verification and catch domain logic bugs before the cron automation magnifies them across hundreds of lessons
- Phase 3 isolated: week parity is the only piece of novel algorithmic logic in this service; it earns its own phase to ensure exhaustive testing
- Phase 4 before gRPC server: event infrastructure needs to be solid before gRPC server testing adds another layer of complexity to the integration test setup
- Phase 5 last: gRPC consumer does not exist in v3.0; in-process tests are self-contained and do not require external services

### Research Flags

Phases needing careful implementation validation (not external research):
- **Phase 3 (Generation):** Validate the week parity formula with hand-calculated test cases before using it in generation. Recovery from a wrong formula requires re-generating an entire semester (HIGH cost per PITFALLS.md recovery table).
- **Phase 4 (Events + Cron):** End-to-end integration test must confirm the RabbitMQ message is received by a consumer, not just that the event was published in-process. Add this test before considering the phase done.

Phases with fully standard patterns (copy from academic-app, no research needed):
- **Phase 1:** Security filter chain, Testcontainers abstract base, EnumConverters — copy from academic-app
- **Phase 2:** Contract-first API, HATEOAS assemblers, @ControllerAdvice — identical structure to academic-app
- **Phase 5:** AcademicGrpcServiceImpl is the template; change domain, keep structure

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified from academic-app/build.gradle.kts — the working system in this repo is the source of truth; no external version guessing required |
| Features | HIGH | Sourced from docs/phases-plan.md, proto/schedule.proto, event-schemas/, database-schema.md — all authoritative project contracts; no feature was inferred from external sources |
| Architecture | HIGH | All component boundaries and data flows verified against actual codebase: migration SQL, proto files, academic-app source patterns — zero speculation |
| Pitfalls | HIGH | All pitfalls verified from actual code patterns (DomainEventListener, RabbitConfig, GrpcExceptionAdvice) and schema inspection (TIME vs TIMETZ, UNIQUE constraints, partial indexes); no pitfall is theoretical |

**Overall confidence:** HIGH

### Gaps to Address

- **Academic Service gRPC port:** ARCHITECTURE.md derives the port as 19091 by convention (REST port 9091 + 10000). Verify this matches the actual grpc.server.port value in `academic-app/application.yml` at the start of Phase 2 before hardcoding the client address.
- **Lesson generation trigger — lazy vs eager:** ARCHITECTURE.md section 3.4 documents lazy generation (generate on first GET for a date range). FEATURES.md specifies generation at schedule_item creation (eager). The roadmap must pick one and document it. Recommendation: eager generation at schedule_item creation keeps the system simpler (no lazy trigger in every GET path); use ON CONFLICT DO NOTHING for idempotency so it is safe to call twice.
- **@EnableScheduling test isolation strategy:** Two approaches documented in STACK.md (profile guard vs @MockitoBean ScheduledAnnotationBeanPostProcessor). Choose one at Phase 1 and apply consistently. The @Profile("!test") guard on SchedulingConfig is simpler and matches the @ActiveProfiles("test") pattern already in AbstractAcademicIntegrationTest.

---

## Sources

### Primary (HIGH confidence — verified from this repo)
- `services/schedule-service/schedule-app/build.gradle.kts` — existing dependencies baseline
- `services/academic-service/academic-app/build.gradle.kts` — proven gRPC versions, Testcontainers BOM, Awaitility
- `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql` — schema: TIME columns, UNIQUE constraints, partial index idx_lessons_status
- `proto/schedule.proto` — 3 RPC signatures and message shapes confirmed
- `proto/academic.proto` — 7 client-side RPCs confirmed (GetGroup, GetTeacherSubjects, GetActiveSemester)
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json` — event payload shapes
- `services/academic-service/academic-app/src/main/java/.../event/DomainEventListener.java` — AFTER_COMMIT pattern to replicate
- `services/academic-service/academic-app/src/main/java/.../event/RabbitConfig.java` — channelTransacted=false constraint
- `services/academic-service/academic-app/src/main/java/.../grpc/AcademicGrpcServiceImpl.java` — repository-direct gRPC pattern; no RequestContext injection
- `services/academic-service/academic-app/src/main/java/.../grpc/GrpcExceptionAdvice.java` — server-side gRPC exception handling pattern
- `services/academic-service/academic-app/src/main/java/.../security/` — RequestContext, RequireRole, RoleCheckAspect, UserContextFilter
- `services/academic-service/academic-app/src/main/resources/application.yml` — grpc.server.port: 19091 convention confirmed
- `docs/phases-plan.md` — Phase 3 Schedule Service authoritative specification
- `docs/database-schema.md` — schedule_items and lessons column types (TIME not TIMETZ confirmed)
- `docs/architecture.md` — topology, gRPC ports, lazy generation documentation

### Secondary (MEDIUM confidence — external docs consistent with repo patterns)
- Spring Framework scheduling reference — @EnableScheduling, @Scheduled(cron, zone) parameters
- grpc-spring-boot-starter client configuration docs — @GrpcClient, grpc.client.* properties
- Baeldung: Testing @Scheduled — Awaitility + @SpyBean pattern
- Baeldung: Disable @EnableScheduling in Tests — @Profile guard and @MockitoBean ScheduledAnnotationBeanPostProcessor patterns
- Spring @TransactionalEventListener docs — silent drop behavior when no active transaction is present
- PostgreSQL docs — SELECT FOR UPDATE SKIP LOCKED for cron mutual exclusion

---
*Research completed: 2026-03-31*
*Ready for roadmap: yes*
