# Phase 10: Foundation - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Schedule Service infrastructure: JPA entities for `schedule_items` and `lessons`, Spring Data JPA repositories, role-based security infrastructure (ported from academic-service), timezone configuration (Europe/Moscow), gRPC port placeholder (19092), and a Testcontainers abstract base class for integration tests.

No domain logic, no REST endpoints, no gRPC implementation, no cron jobs. This phase makes the service compile and pass Hibernate validation against the live schema.

</domain>

<decisions>
## Implementation Decisions

### Security Porting
- **D-01:** Copy UserContextFilter, RequestContext, @RequireRole, RoleCheckAspect from academic-service into `ru.rutcampustrack.schedule.security` package. Change UserRole import to `ru.rutcampustrack.schedule.contract.enums.UserRole`. Same logic, different import.
- **D-02:** RequestContext fields identical to academic-service: `userId` (Long), `role` (UserRole), `groupId` (Long), `isHeadman` (boolean). No additional fields.
- **D-03:** Security smoke test: only verify that a request without `X-User-Id`/`X-User-Role` headers returns 403. Role-specific endpoint restrictions tested in Phase 11 when actual endpoints exist.

### Testcontainers Setup
- **D-04:** Mirror academic-service's AbstractAcademicIntegrationTest pattern: static `PostgreSQLContainer<>("postgres:16")` with `schedule_db` database name, `rct_user`/`rct_dev_pass` credentials. Flyway auto-runs `V1__baseline.sql`.
- **D-05:** Mock `RabbitTemplate`, exclude `RabbitAutoConfiguration`. No Redis in schedule-service so skip Redis exclusion. Exclude gRPC server auto-configuration since no gRPC tests in this phase.

### Entity Mapping
- **D-06:** `ScheduleItem.startTime` and `ScheduleItem.endTime` mapped as `java.time.LocalTime` — native Hibernate mapping for PostgreSQL TIME columns.
- **D-07:** Logical FK fields (`groupId`, `subjectId`, `teacherId`, `semesterId`) in ScheduleItem mapped as plain `Long` with `@Column`. No JPA relationships — validated via gRPC to Academic Service at application layer (Phase 11).
- **D-08:** Keep existing inline enum converters in `EnumConverters.java` (WeekType, LessonStatus). No base class — only 2 enums, already committed and working.
- **D-09:** ~~`Lesson.scheduleItem` mapped as `@ManyToOne(fetch = LAZY)`~~ **OVERRIDDEN:** Plain `Long scheduleItemId` — follow CLAUDE.md general rule (FK as Long, no JPA associations). Consistent with all other FK fields project-wide.

### Build Dependencies
- **D-10:** Minimal dependencies for Phase 10: add `spring-boot-starter-aop` (for @RequireRole aspect) and Testcontainers BOM + `testcontainers-postgresql` module. gRPC server starter and protobuf plugin deferred to Phase 14.
- **D-11:** `application.yml` still sets `grpc.server.port: 19092` as a placeholder/documentation for Phase 14 — the property is harmless without the gRPC starter on classpath.

### Claude's Discretion
- Package structure within `schedule-app` (subpackage layout for entities, repos, config, security)
- Exact Flyway migration content (V1__baseline.sql already exists in the repo)
- @RequireRole annotation design (method-level, what parameters)
- RoleCheckAspect implementation details (how it reads RequestContext and enforces roles)
- ClockConfig and SchedulingConfig implementation details

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `docs/database-schema.md` — schedule_db schema: schedule_items and lessons table definitions, constraints, indexes, enum types (week_type, lesson_status)

### Architecture & Conventions
- `docs/architecture.md` — Service map, communication patterns, port assignments
- `CLAUDE.md` — Coding rules (contract-first, enum handling, HATEOAS, naming)

### Existing Patterns (reference implementations)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/UserContextFilter.java` — Security filter to port
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContext.java` — Request-scoped context bean to port
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequireRole.java` — Role annotation to port
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java` — AOP aspect to port
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java` — Testcontainers base class pattern

### Existing Schedule Service Code
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` — Already committed WeekType + LessonStatus converters
- `services/schedule-service/schedule-app/build.gradle.kts` — Current dependency set (to be extended)
- `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/WeekType.java` — WeekType enum
- `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/LessonStatus.java` — LessonStatus enum

### Phase Plan
- `docs/phases-plan.md` — Detailed phase descriptions and dependencies

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EnumConverters.java` in schedule-service: WeekType and LessonStatus converters already working with autoApply=true
- `ScheduleApplication.java`: Entry point already committed
- Academic-service security package (4 files): Direct port target — same pattern, different UserRole import

### Established Patterns
- Contract-first: `schedule-api-contract` holds enums, DTOs, interfaces. `schedule-app` holds Spring Boot code.
- Enum conversion: inline converters in EnumConverters class with `@Converter(autoApply = true)`
- Testcontainers: static container + @DynamicPropertySource + @ActiveProfiles("test") + @MockitoBean for RabbitTemplate
- Security: OncePerRequestFilter reads X-User-Id/X-User-Role/X-Group-Id/X-Is-Headman headers → populates @RequestScope bean → AOP aspect checks @RequireRole

### Integration Points
- `docker-compose.yml`: needs TZ=Europe/Moscow for schedule-service container
- `application.yml`: hibernate.jdbc.time_zone, grpc.server.port, datasource config
- Flyway V1__baseline.sql: creates schedule_items and lessons tables with custom enum types

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches following established patterns from academic-service.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 10-foundation*
*Context gathered: 2026-04-01*
