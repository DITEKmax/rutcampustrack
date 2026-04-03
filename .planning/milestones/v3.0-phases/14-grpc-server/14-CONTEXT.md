# Phase 14: gRPC Server - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement schedule.proto gRPC server — 3 RPCs (GetActiveLesson, GetLessonById, GetLessonsByGroup) for Attendance Service consumption. Add grpc-server-spring-boot-starter dependency, create ScheduleGrpcServiceImpl, GrpcExceptionAdvice, and integration tests.

No Redis caching (deferred to CACHE-01/02 if needed), no attendance integration (v4.0), no new proto fields beyond what's defined in schedule.proto.

</domain>

<decisions>
## Implementation Decisions

### GetActiveLesson Semantics
- **D-01:** When no active lesson exists for the group at the given timestamp, return gRPC `NOT_FOUND` status with descriptive message. Caller (Attendance Service) handles absence explicitly.
- **D-02:** When multiple lessons overlap for the same group (rare scheduling error), return the first by `lesson_number ASC` (deterministic, `ORDER BY lesson_number ASC LIMIT 1`).

### Error Handling
- **D-03:** Port `GrpcExceptionAdvice` from academic-service into `ru.rutcampustrack.schedule.grpc` package. Map: `ResourceNotFoundException` → `NOT_FOUND`, `IllegalArgumentException` → `INVALID_ARGUMENT`, `Exception` → `INTERNAL`.
- **D-04:** Validate `GetLessonsByGroup` date range: if `date_from > date_to`, return `INVALID_ARGUMENT` with description. Validate in the service impl before querying.

### Server Dependency
- **D-05:** Add `grpc-server-spring-boot-starter` alongside existing `grpc-client-spring-boot-starter`. Both coexist — client connects to academic-service (port 19091), server listens on port 19092 (already configured in application.yml).

### Testing Strategy
- **D-06:** Test `ScheduleGrpcServiceImpl` methods directly — inject repositories, call methods with mock `StreamObserver`. Same pattern as academic-service tests. No in-process gRPC channel setup needed.

### Claude's Discretion
- Whether to create a `ScheduleReadService` (like academic-service's `AcademicReadService`) or query repositories directly from the gRPC impl (no caching needed yet, so direct is simpler)
- Repository query design for GetActiveLesson: new native query or compose from existing `findPlannedDueForActivation`-style queries
- Repository query for GetLessonsByGroup: filter by group_id + semester_id + date range, JOIN schedule_items for enrichment
- Whether to parse `timestamp` string in GetActiveLesson into `LocalDateTime` or `OffsetDateTime`
- Test data setup helpers (reuse from existing test classes or create new)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Proto Contract
- `proto/schedule.proto` — Defines all 3 RPCs, request/response messages, field types

### Academic-Service gRPC Pattern (reference implementation)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — @GrpcService pattern, StreamObserver usage, response building
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdvice.java` — @GrpcAdvice error mapping pattern
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java` — Cached read delegation (optional pattern for schedule)

### Schedule-Service Existing Code
- `services/schedule-service/schedule-app/build.gradle.kts` — Current dependencies (grpc-client-starter, proto codegen)
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — grpc.server.port: 19092 already configured
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java` — Existing queries to build on
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java` — ScheduleItem lookups for response enrichment
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java` — Test base class with Testcontainers

### Requirements
- `.planning/REQUIREMENTS.md` §gRPC Server — GRPC-01, GRPC-02, GRPC-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AcademicGrpcServiceImpl` pattern: `@GrpcService extends *ImplBase`, each RPC method builds proto response from JPA entities
- `GrpcExceptionAdvice`: copy with schedule-service exceptions (ResourceNotFoundException exists in schedule contract)
- `AbstractScheduleIntegrationTest`: Testcontainers base with `@MockitoBean RabbitTemplate`, `@ActiveProfiles("test")`
- Proto codegen already configured in build.gradle.kts — `ScheduleGrpcServiceGrpc` classes auto-generated

### Established Patterns
- gRPC queries repositories directly (not REST services) — avoids RequestContext scope issues in gRPC threads (key decision from Phase 10)
- `@MockitoBean AcademicGrpcClient` in tests to prevent outbound gRPC connections
- Nullable proto fields: use empty string or 0 for null values (e.g., `room = ""`, `groupId = 0L`)

### Integration Points
- `grpc.server.port: 19092` — already in application.yml
- Need `grpc-server-spring-boot-starter` dependency added to build.gradle.kts
- Generated proto classes in `ru.rutcampustrack.schedule.grpc` package — `ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase` to extend
- `LessonRepository` + `ScheduleItemRepository` for data access in gRPC impl

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follows academic-service gRPC server pattern closely. This is the last phase of v3.0 Schedule Service milestone.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-grpc-server*
*Context gathered: 2026-04-03*
