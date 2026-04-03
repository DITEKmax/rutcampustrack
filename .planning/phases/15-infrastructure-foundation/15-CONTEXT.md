# Phase 15: Infrastructure Foundation - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Attendance Service starts up fully connected: MongoDB indexes created, enums serialized as lowercase strings, gRPC client stubs wired to Schedule and Academic services, durable RabbitMQ consumer queue bound to the fanout exchange, @RequireRole AOP security in place. No business logic — pure infrastructure wiring.

</domain>

<decisions>
## Implementation Decisions

### MongoDB Document Design
- **D-01:** Full denormalization — each attendance document stores `lesson_id`, `user_id`, `group_id`, `subject_id`, `semester_id`, `lesson_number`, `date`, `status`, `source`, `marked_by`, `created_at`, `updated_at`. Report queries are pure MongoDB aggregations with no gRPC calls at read time.
- **D-02:** `semester_id` resolved via cached GetActiveSemester gRPC call on service startup. Cache held in a @Service bean, refreshed on `semester.archived` event. Acceptable because semester changes are rare (twice a year).
- **D-03:** Single `attendances` collection for all attendance records. Compound indexes handle all query patterns (by lesson, by student+semester, by group+subject+semester).
- **D-04:** Default MongoDB ObjectId as `_id`. Unique constraint on `{lesson_id, user_id}` as a separate compound unique index.

### RabbitMQ Consumer Setup
- **D-05:** Declare DLQ infrastructure in Phase 15 (DLQ exchange + queue + binding). Phase 16 adds the error handler logic. Clean separation: infra in 15, logic in 16.
- **D-06:** Generic envelope deserialization — single `@RabbitListener` receives all events as a generic envelope (Map or JsonNode), routes by `event_type` field to typed handler methods. Matches the fanout pattern where one queue receives everything.

### gRPC Client Scope
- **D-07:** Wire ALL gRPC RPCs needed for v4.0 in Phase 15: `GetActiveLesson`, `GetLessonById`, `GetLessonsByGroup` (Schedule) + `GetGroupMembers`, `GetCampusGeofence`, `GetActiveSemester`, `IsHeadman` (Academic). Each is a one-liner wrapper following the pattern in schedule-service's `AcademicGrpcClient`.
- **D-08:** No `attendance.proto` for v4.0. Attendance Service is a gRPC consumer only, not a provider. Add proto if/when another service needs attendance data via gRPC.

### Testing Strategy
- **D-09:** Mock gRPC blocking stubs with Mockito for unit tests. Real gRPC integration tests deferred to E2E. Matches schedule-service test patterns.
- **D-10:** Testcontainers for both MongoDB and RabbitMQ. MongoDB for index verification (success criteria 1-2), RabbitMQ for consumer queue binding verification (criteria 4). Redis not needed until Phase 17.
- **D-11:** `@ActiveProfiles("test")` + abstract base test class with `@Testcontainers` and `@DynamicPropertySource` for MongoDB + RabbitMQ URLs. Same pattern as schedule-service.

### Error Handling
- **D-12:** Copy + extend GlobalExceptionHandler from academic-service. RFC 7807 pattern with MongoDB-specific handlers: `DuplicateKeyException` -> 409, gRPC `StatusRuntimeException` -> 502/503. Same `ErrorResponse` record from contract module.

### Configuration
- **D-13:** Test application.yml overrides gRPC channels to localhost. `@Profile("!test")` on any startup beans that require live services.

### Claude's Discretion
- Package structure within attendance-app (config/, grpc/, event/, security/ etc.)
- Specific MongoDB index definitions beyond the unique {lesson_id, user_id} compound index
- RabbitMQ queue naming convention (e.g., `attendance-service.events`)
- DLQ exchange/queue naming
- Order of bean initialization for semester cache

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture & Conventions
- `docs/architecture.md` -- Overall system architecture, service ports, communication patterns
- `docs/database-schema.md` -- Database schema definitions (attendance_db section for MongoDB)
- `docs/phases-plan.md` -- Phase plan with dependencies and scope
- `CLAUDE.md` -- Coding rules, enum conventions, package structure, REST patterns

### Proto Contracts (gRPC)
- `proto/academic.proto` -- Academic gRPC service definition (GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman)
- `proto/schedule.proto` -- Schedule gRPC service definition (GetActiveLesson, GetLessonById, GetLessonsByGroup)

### Event Schemas (RabbitMQ)
- `event-schemas/lesson.started.json` -- Lesson started event payload schema
- `event-schemas/lesson.closed.json` -- Lesson closed event payload schema
- `event-schemas/lesson.cancelled.json` -- Lesson cancelled event payload schema
- `event-schemas/attendance.marked.json` -- Attendance marked event payload schema (for Phase 17)

### Existing Patterns to Follow
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java` -- gRPC client wrapper pattern (3s deadline, domain exception translation)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/RabbitConfig.java` -- RabbitMQ exchange declaration pattern
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequireRole.java` -- @RequireRole annotation
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java` -- AOP role check implementation

### Requirements
- `.planning/REQUIREMENTS.md` -- INFRA-01 through INFRA-05 (this phase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AcademicGrpcClient` (schedule-service) -- Pattern for gRPC client wrappers with deadline and exception handling
- `RabbitConfig` (schedule-service) -- Pattern for fanout exchange + Jackson converter declaration
- `RequireRole` + `RoleCheckAspect` (academic/schedule services) -- AOP security annotation, copy to attendance
- `GlobalExceptionHandler` (academic-service) -- RFC 7807 error response pattern
- `ErrorResponse` record (academic-api-contract) -- Reusable error response DTO pattern
- `AttendanceStatus` enum (attendance-api-contract) -- Already defined: PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE, CANCELLED
- `AttendanceSource` enum (attendance-api-contract) -- Already defined for marking source

### Established Patterns
- gRPC: `@GrpcClient("service-name")` blocking stub injection, 3-second deadlines, `StatusRuntimeException` catch -> domain exception
- RabbitMQ: `FanoutExchange("rut-uit.events", true, false)`, Jackson2JsonMessageConverter with Spring ObjectMapper
- Testing: Testcontainers with `@DynamicPropertySource`, `@ActiveProfiles("test")`, `@MockitoBean RabbitTemplate` in non-event tests
- Enums: `LowercaseEnumConverter` pattern for JPA (MongoDB needs `MongoCustomConversions` equivalent)

### Integration Points
- `build.gradle.kts` already has `spring-boot-starter-data-mongodb` and `spring-boot-starter-amqp`
- Missing: `net.devh:grpc-spring-boot-starter` and `spring-boot-starter-data-redis` dependencies
- Missing: protobuf plugin for gRPC code generation
- Gateway already routes `/api/attendance/**` to port 9093
- `application.yml` exists but is minimal (needs MongoDB, RabbitMQ, gRPC channel config)

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- open to standard approaches following established patterns from academic and schedule services.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope.

</deferred>

---

*Phase: 15-infrastructure-foundation*
*Context gathered: 2026-04-04*
