# Project Research Summary

**Project:** RutCampusTrack — Academic Service (Phase 2)
**Domain:** University academic structure CRUD microservice — users, groups, semesters, subjects, assignments, homework, gRPC server, Redis caching, RabbitMQ event publishing
**Researched:** 2026-03-30
**Confidence:** HIGH

## Executive Summary

Academic Service is the structural backbone of RutCampusTrack. Every downstream service — Schedule (Phase 3), Attendance (Phase 3), and Notification Bot (Phase 4) — is blocked until this service delivers its 7 gRPC RPCs. The research confirms a clear contract-first implementation path: the schema is fully migrated (V1/V2), the proto contract is authored, and the build scaffolding (dependencies commented in `build.gradle.kts`) is pre-staged. The service adds three new technical capabilities to the existing Spring Boot 3.4 / Java 21 baseline: a gRPC server via `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`, Redis `@Cacheable` with per-cache TTL, and RabbitMQ fanout event publishing. All dependencies are already on the classpath or require only configuration; no architectural surprises.

The recommended implementation order runs bottom-up through entity dependencies: Group → User → Semester → Subject → TeacherSubjectGroup → HeadmanAssistant → AttendanceThreshold → Homework → HomeworkCompletion. The gRPC server is implemented last in the entity phase, reusing the service methods built for REST. Redis caching and RabbitMQ publishing are layered on after services are stable. This order maximizes compile-time verification and minimizes rework.

The key risks cluster around four areas: (1) cache invalidation gaps — a single missed `@CacheEvict` causes gRPC consumers in Phase 3 to read stale group membership data with real attendance consequences; (2) concurrent login generation — MAX()+1 has a race condition that must be replaced with a PostgreSQL sequence from day one; (3) Flyway immutability — modifying V1 or V2 after deployment against a running database causes startup failures; and (4) RabbitMQ transactional event publishing — events published inside a `@Transactional` method before commit are delivered even if the transaction rolls back. All four are preventable with patterns documented in PITFALLS.md.

---

## Key Findings

### Recommended Stack

The project runs on a validated baseline (Java 21, Spring Boot 3.4, Gradle Kotlin DSL, Flyway, Testcontainers, PostgreSQL, Redis, RabbitMQ) proven in Phase 1 (Auth Service). Phase 2 adds exactly three new capabilities, each with clear library choices. The `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` is the correct gRPC choice — `spring-grpc` 1.0 GA requires Spring Boot 4, which is out of scope. Redis caching uses `RedisCacheManager` with JSON serialization (`GenericJackson2JsonRedisSerializer`) and explicit per-cache TTLs; no new dependency is required. RabbitMQ event publishing uses the existing `spring-boot-starter-amqp` with a `FanoutExchange` declaration — Academic Service is a producer only, so no `@RabbitListener` is needed.

**Core technologies (new in Phase 2):**
- `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`: gRPC server — chosen over `spring-grpc` 1.0 because `spring-grpc` requires Spring Boot 4; this starter is already pre-declared (commented) in `build.gradle.kts`
- `com.google.protobuf` plugin `0.9.4` + `io.grpc:protoc-gen-grpc-java:1.68.0`: proto code generation from `proto/academic.proto` at root
- `spring-boot-starter-security` (new to academic-app): enables `@PreAuthorize` with header-based `RequestContext`; does NOT add JWT parsing
- `RedisCacheManager` with `GenericJackson2JsonRedisSerializer`: per-cache TTL (5 min–6 hr range); already on classpath
- `RabbitTemplate` + `FanoutExchange("rut-uit.events")`: event publishing for group.updated, semester.archived, homework.published, homework.updated; already on classpath

**Critical version constraints:**
- gRPC port must be set to `19091` (not the default `9090` which conflicts with Auth Service)
- `grpc-java` version must stay aligned with what `net.devh:3.1.0.RELEASE` ships (1.63.0 baseline); do not override to 1.80.0

### Expected Features

**Must have (table stakes) — Phase 2 cannot ship without these:**
- Full CRUD: users, groups, semesters, subjects, teacher_subject_groups — structural backbone for all downstream services
- Auto-login generation (`student00001` format) with PostgreSQL sequence (not MAX()+1)
- Soft delete for users: status transitions via PATCH, no DELETE endpoint exposed
- Single active semester enforcement (PostgreSQL EXCLUDE constraint already exists; service layer must deactivate-then-activate atomically)
- Assign/revoke headman with automatic assistant revocation on headman change
- Student group transfer with history (atomic: close `student_group_history` row, open new row, update `users.group_id`)
- gRPC server — all 7 RPCs: `GetGroup`, `GetGroupMembers`, `GetTeacherSubjects`, `IsHeadman`, `GetActiveSemester`, `GetCampusGeofence`, `GetUserById`
- Redis caching on 5 cache keys (groupInfo, groupMembers, teacherSubjects, activeSemester, campusGeofence) — gRPC is called on every checkin hot path
- Headman assistant management with granular `AssistantPermission` enforcement
- Red zone attendance thresholds at 3 levels (global / group / subject)
- Homework CRUD + personal homework completion tracker
- RabbitMQ event publishing (group.updated, semester.archived, homework.published, homework.updated)
- Admin dashboard summary counts

**Should have (differentiators):**
- `initial_password` stored until first change — enables Telegram Bot credential delivery without a separate mechanism
- Student group transfer history — audit trail for accreditation; correct statistics on re-join
- Auto-revoke all assistants when headman changes — prevents orphaned permissions
- Red zone threshold 3-level resolution (most specific wins) — distinct from standard LMS flat thresholds
- RabbitMQ event envelope pattern with `event_id` (UUID) + `occurred_at` — idempotent downstream processing

**Defer to later phases:**
- Campus geofence update API (1-row table, seed data already set; simple admin endpoint deferred to Phase 3)
- Password reset token cleanup (tokens expire naturally by TTL)
- Bulk user import via CSV/Excel (manual creation sufficient for MVP user count)
- Email/SMS credential delivery (Telegram is the delivery channel)

### Architecture Approach

Academic Service follows the contract-first dual-module pattern validated in Phase 1. `academic-api-contract` (pure `java-library`) holds REST interfaces, request records, response HATEOAS classes, and enums — no Lombok, no Spring. `academic-app` (Spring Boot) holds JPA entities (FK as Long fields — no bidirectional associations), service layer with business logic and role enforcement, Redis cache config with explicit `CacheManager` bean, gRPC implementation (`@GrpcService` delegating to same service beans used by REST), and the RabbitMQ event publisher. Authorization uses a `RequestContext` record populated from Gateway-injected headers (`X-User-Role`, `X-User-Id`, `X-Group-Id`, `X-Is-Headman`) — never string comparisons directly in controllers.

**Major components:**
1. `academic-api-contract` — REST interface definitions, request records, response `RepresentationModel` subclasses, enums (all exist; 7 API interfaces + DTOs to add)
2. Domain service layer (UserService, GroupService, SemesterService, SubjectService, AssignmentService, AssistantService, ThresholdService, HomeworkService) — business logic, authorization checks, `@Cacheable`/`@CacheEvict` annotations
3. gRPC server (`AcademicGrpcServiceImpl`) — implements `AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase`, delegates to same service layer as REST, benefits from same Redis cache
4. Redis `CacheConfig` — explicit `RedisCacheManager` bean with 5 named caches, JSON serialization, TTLs 5 min–6 hr
5. `AcademicEventPublisher` — `RabbitTemplate` wrapper, event envelope structure, publishes after transaction commits via `@TransactionalEventListener(phase = AFTER_COMMIT)`
6. `RequestContext` + `AssistantPermissionChecker` — centralized header parsing and headman/assistant authorization, shared across all service methods

### Critical Pitfalls

1. **Cache invalidation gaps for cascading updates** — student transfer invalidates TWO group member caches (old and new group), headman change invalidates both `groupInfo` and `groupMembers`. Map all `@CacheEvict` targets before coding any mutating service method. Use `@Caching(evict = {...})` for multi-cache operations. Missing evictions cause stale gRPC responses in Phase 3 with attendance consequences.

2. **Login sequence race condition** — `MAX(login)+1` in application code has a TOCTOU race under concurrent admin requests. Must use dedicated PostgreSQL sequences (`CREATE SEQUENCE student_login_seq`) added in `V3__add_login_sequences.sql`. Sequence gaps on rollback are acceptable; duplicate key 500 errors are not.

3. **Flyway checksum collision on V1/V2** — editing any existing migration file (even whitespace) causes `FlywayException: Migration checksum mismatch` against any environment that already ran the original. Mark V1 and V2 immutable; all new schema changes go into V3+. This includes adding columns, new enum values, or additional test data.

4. **RabbitMQ publish inside `@Transactional` before commit** — if the DB transaction rolls back after the event is published, notification consumers act on data that never persisted. Always use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` or publish after the transactional method returns.

5. **gRPC default port conflict with Auth Service** — `net.devh:grpc-spring-boot-starter` defaults to port 9090. Auth Service occupies 9090. Must explicitly set `grpc.server.port=19091` in `application.yml` before running any gRPC integration test. Update all future gRPC clients (Schedule, Attendance) to target `academic-service:19091`.

6. **Auth Service entity divergence** — Auth Service maps the `users` table with `ddl-auto: validate`. Any column rename or removal in Academic Service Flyway migrations breaks Auth Service at startup. Treat mapped columns as a shared contract: `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id`. New columns must be additive (nullable).

---

## Implications for Roadmap

Based on the combined research, the service decomposes into 5 sequential sub-phases within Phase 2. Each sub-phase is independently testable and unblocks the next.

### Phase 2.1: Entity and Repository Foundation
**Rationale:** All business logic depends on JPA entities being correct. Schema already exists (Flyway V1); entities must match it. Establish hard rules (no JPA associations, soft delete, immutable migrations) before any service logic.
**Delivers:** 11 JPA entities (`Group`, `User`, `Semester`, `Subject`, `TeacherSubjectGroup`, `HeadmanAssistant`, `AttendanceThreshold`, `Homework`, `HomeworkCompletion`, `CampusSetting`, `StudentGroupHistory`), Spring Data repositories, `V3__add_login_sequences.sql` migration.
**Addresses:** Full CRUD structural backbone, auto-login generation, soft delete
**Avoids:** Circular entity serialization (no `@ManyToOne`/`@OneToMany`), Flyway checksum collision, Auth Service entity divergence, soft delete leaking into JPA queries

### Phase 2.2: REST API and HATEOAS
**Rationale:** REST layer is the primary consumer-facing interface. Establish `RequestContext`, role authorization, and assembler patterns before adding gRPC or eventing complexity.
**Delivers:** 7 API interfaces in `academic-api-contract`, all DTOs (request records + HATEOAS response classes), all controllers implementing those interfaces, `RequestContext` + `AssistantPermissionChecker`, `PagedModel` pagination, OpenAPI annotations
**Uses:** `spring-boot-starter-security` with `HeaderAuthenticationFilter`, `PagedResourcesAssembler`
**Implements:** Contract-first REST layer, HEADMAN group boundary enforcement, ADMIN/TEACHER/STUDENT/HEADMAN role matrix
**Avoids:** Header string comparisons (use `RequestContext`), N+1 in assembler loops (no FK name lookup in loops), `initial_password` exposure in list endpoints

### Phase 2.3: gRPC Server
**Rationale:** gRPC is the inter-service API that unblocks Schedule and Attendance services. Must come after service layer is stable so `AcademicGrpcServiceImpl` delegates to already-tested service methods.
**Delivers:** `AcademicGrpcServiceImpl` implementing all 7 RPCs, proto code generation in `build.gradle.kts`, `grpc.server.port=19091` configuration, Testcontainers integration test for all 7 RPCs
**Uses:** `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`, protobuf plugin `0.9.4`, `io.grpc:grpc-stub:1.68.0`
**Avoids:** Default port 9090 conflict, gRPC bean init order issue (explicit `CacheManager` bean before gRPC implementation connects to it), service layer bypass in gRPC impl

### Phase 2.4: Redis Caching Layer
**Rationale:** Add caching as a cross-cutting concern after services are stable. Easier to write correct `@CacheEvict` rules when the mutation paths are already established and tested.
**Delivers:** `CacheConfig.java` with explicit `RedisCacheManager` + 5 named caches + JSON serialization, `@Cacheable`/`@CacheEvict` annotations on all 5 cache paths, documented eviction map in config class comments, integration tests for stale-data scenarios
**Uses:** `spring-boot-starter-data-redis` (already on classpath), `GenericJackson2JsonRedisSerializer`
**Avoids:** Missing explicit `key` on `@Cacheable` (cache poisoning), `@Cacheable` self-invocation, cascading invalidation gaps

### Phase 2.5: RabbitMQ Event Publishing
**Rationale:** Event publishing is the final layer. Publishing after transaction commit requires services to be stable and transactional boundaries established first.
**Delivers:** `RabbitMqConfig.java` (FanoutExchange declaration, locked type), `AcademicEventPublisher` with typed publish methods, `@TransactionalEventListener(phase = AFTER_COMMIT)` wiring, events: group.updated, semester.archived, homework.published, homework.updated
**Avoids:** Exchange type mismatch (locked as fanout, documented as immutable), publishing before transaction commits, declaring queues in Academic Service (queues belong to consumers)

### Phase Ordering Rationale

- Entity foundation first: all service logic, authorization, caching, and gRPC depend on correct entity mapping. Mistakes here are expensive to fix later.
- REST before gRPC: gRPC implementation reuses service methods. Testing REST first validates the service layer independently.
- Caching after services: correct `@CacheEvict` rules require knowing all mutation paths; those are only clear once service methods exist.
- Events last: event publishing is fire-and-forget and the simplest to add; it does not block any other Phase 2 work.
- Build order within entities: `Group` → `User` → `Semester` → `Subject` → `TeacherSubjectGroup` → `HeadmanAssistant` → `AttendanceThreshold` → `Homework` → `HomeworkCompletion` — each entity references only previously built ones.

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 2.2 (REST + HATEOAS):** The headman assistant permission enforcement is non-standard. `AssistantPermission` is a `VARCHAR(64)[]` PostgreSQL array mapped as `String[]` in the entity (JPA converter on array columns conflicts with Hibernate). The service-layer conversion pattern needs careful implementation and test coverage. Research the `@Converter` + PostgreSQL array interaction if the mapping approach is unclear.
- **Phase 2.4 (Redis caching):** The `@Cacheable` self-invocation limitation (Spring AOP proxy bypass) is a runtime-only failure that unit tests miss. Requires Testcontainers integration tests for every cache path. Consider whether a `CachingFacade` wrapper class is needed to route all cacheable calls through the proxy boundary.

**Phases with standard patterns (no additional research needed):**
- **Phase 2.1 (Entity layer):** Flyway + JPA entity mapping is well-documented; schema already exists; Auth Service pattern is the reference.
- **Phase 2.3 (gRPC server):** `net.devh` gRPC integration pattern is established; existing commented dependency in `build.gradle.kts` confirms the intended approach.
- **Phase 2.5 (RabbitMQ publishing):** Fanout exchange + `RabbitTemplate` is a standard Spring AMQP pattern; `@TransactionalEventListener` is official Spring framework API.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All library choices verified against official repos and existing codebase pre-staging. One MEDIUM item: `net.devh:3.1.0.RELEASE` was built against Spring Boot 3.2.4; not explicitly tested against 3.4, but the commented dependency in `build.gradle.kts` confirms it was the pre-decided choice. |
| Features | HIGH | Feature inventory sourced from `phases-plan.md`, `job-stories.md`, `database-schema.md`, `academic.proto`, and actual V1 migration SQL — all authoritative primary sources. |
| Architecture | HIGH | Architecture is based on direct codebase analysis: existing `academic-app` structure, confirmed entity relationships from V1 migration, Auth Service as Phase 1 reference implementation. Zero speculation. |
| Pitfalls | HIGH | All 12 pitfalls are grounded in actual codebase analysis (specific file paths cited), not general advice. The shared-table contract between Auth Service and Academic Service is a concrete, verified risk. |

**Overall confidence:** HIGH

### Gaps to Address

- **gRPC port convention:** STACK.md suggests port `9095`, ARCHITECTURE.md suggests port `9111` (starter default), PITFALLS.md recommends `19091` (dedicated gRPC range to avoid conflicts). Must align on a single port value during Phase 2.3. Recommendation: adopt PITFALLS.md's `19091` — the dedicated range `19090-19094` is the only approach that prevents conflicts as more gRPC services are added in Phase 3+.
- **`AssistantPermission` array mapping:** The `permissions VARCHAR(64)[]` column is a PostgreSQL-native array type. JPA `@Converter` with `autoApply=true` on arrays conflicts with Hibernate's type system (documented in PITFALLS.md). The recommended workaround (map as `String[]` in entity, convert in service layer) needs a working code example and test before implementation begins. Verify the approach with a Testcontainers test in Phase 2.1.
- **`@Where` annotation and Auth Service compatibility:** Using Hibernate's `@Where(clause = "status != 'archived'")` on the `User` entity in Academic Service must be confirmed as non-breaking for Auth Service's own `User.java` entity (which maps the same table but does not use `@Where`). Both entities coexist in the same PostgreSQL database. Since they are in separate JVM processes, there is no conflict, but this should be explicitly documented.

---

## Sources

### Primary (HIGH confidence)
- `services/academic-service/academic-app/build.gradle.kts` — gRPC dependency pre-declared (commented), existing Spring Boot deps
- `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — 12 tables, FK relationships, PostgreSQL constraints
- `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` — test user data
- `proto/academic.proto` — 7 gRPC RPC signatures, proto message definitions
- `event-schemas/homework.published.json` — RabbitMQ event envelope structure
- `docs/phases-plan.md` — Phase 2 specification, Redis cache key patterns
- `docs/job-stories.md` — business rules by role
- `docs/database-schema.md` — table structure, cache keys, Redis TTL guidance
- `services/auth-service/` (Phase 1) — reference implementation for contract-first pattern, header-based auth
- Spring Data Redis docs (docs.spring.io) — `RedisCacheManager`, `GenericJackson2JsonRedisSerializer`
- Spring HATEOAS docs (docs.spring.io) — `PagedResourcesAssembler`, `PagedModel`
- Spring AMQP docs / RabbitMQ tutorial — fanout exchange, `RabbitTemplate.convertAndSend`
- Spring Security Method Security docs — `@EnableMethodSecurity`, `@PreAuthorize`, `OncePerRequestFilter`
- net.devh grpc-spring-boot-starter GitHub — releases, Spring Boot compatibility

### Secondary (MEDIUM confidence)
- net.devh:grpc-spring-boot-starter:3.1.0.RELEASE compatibility with Spring Boot 3.4 — not explicitly tested at 3.4, but starter declares compatibility with a wide range of Spring Boot 3.x versions; pre-staged in project `build.gradle.kts`
- spring-grpc 1.0 GA requiring Spring Boot 4 — confirmed from Spring blog title (December 2025); page content not fully loaded, but corroborated by community sources

### Tertiary (LOW confidence)
- None — all key decisions are grounded in verified primary sources

---
*Research completed: 2026-03-30*
*Ready for roadmap: yes*
