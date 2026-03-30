---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Academic Service
status: executing
stopped_at: Completed 09-01-PLAN.md
last_updated: "2026-03-30T23:58:33.581Z"
last_activity: 2026-03-30
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 12
  completed_plans: 11
  percent: 0
---

# Project State

## Current Milestone

v2.0 Academic Service — Full CRUD for university structure with gRPC and Redis.

## Current Position

Phase: 09 (rabbitmq-events) — EXECUTING
Plan: 2 of 2
Status: Ready to execute
Last activity: 2026-03-30

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-30)

**Core value:** Working authentication and authorization perimeter — all downstream services receive validated user context through the Gateway.
**Current focus:** Phase 09 — rabbitmq-events

## Accumulated Context

### Decisions

- [v1.0]: Auth Service reads academic_db via JPA with ddl-auto: validate — any column removal in Academic Service migrations breaks Auth Service at startup. Treat `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id` as a shared contract.
- [v2.0 research]: gRPC port set to 19091 (not default 9090 — conflicts with Auth Service).
- [v2.0 research]: V1/V2 Flyway migrations are immutable; all new schema changes start at V3.
- [v2.0 research]: RabbitMQ events must use @TransactionalEventListener(AFTER_COMMIT) — never publish inside @Transactional before commit.
- [v2.0 research]: Login generation must use PostgreSQL sequences (not MAX()+1) to avoid race conditions.
- [Phase 05]: Semester.date_from/date_to use LocalDate (DATE columns), not OffsetDateTime — aligns with V1 schema
- [Phase 05]: AbstractAcademicIntegrationTest excludes RabbitMQ and Redis autoconfigurations to avoid connection failures in tests
- [Phase 05]: CampusSetting PK is SERIAL (not BIGSERIAL) but mapped to Long — JDBC widens safely
- [Phase 05]: V4 migration: campus_settings.id was SERIAL (INT4) but CampusSetting entity maps to Long (BIGINT); Hibernate ddl-auto:validate fails — fix with ALTER COLUMN TYPE BIGINT
- [Phase 05]: HeadmanAssistant.permissions uses String[] with @JdbcTypeCode(SqlTypes.ARRAY) — conversion to List<AssistantPermission> is service layer responsibility
- [Phase 06]: @RequestMapping paths use /academic/* — Gateway StripPrefix=1 strips /api before forwarding
- [Phase 06]: ScopedProxyMode.TARGET_CLASS mandatory on RequestContext — singleton AOP aspects must receive scoped proxy
- [Phase 06]: AOP @RequireRole (no Spring Security) — role enforcement via X-User-Role header injected by Gateway
- [Phase 06 Plan 02]: spring-data-commons added to academic-api-contract — contract interfaces use Pageable/PagedResourcesAssembler
- [Phase 06 Plan 02]: Page<Entity>->Page<ResponseDto> mapping done in controller before PagedResourcesAssembler (matches contract signature with <ResponseDto>)
- [Phase 06 Plan 02]: Semester activateSemester uses deactivateAllActive() + entityManager.flush() + saveAndFlush() to avoid UNIQUE constraint violation on concurrent activation
- [Phase 07-grpc-server]: Direct repository injection in gRPC service — avoids RequestContext scope issues when injecting REST service layer
- [Phase 07-grpc-server]: javax.annotation-api:1.3.2 compileOnly — generated gRPC stubs use @javax.annotation.Generated removed in Java 9+
- [Phase 07]: Use unique in-process name 'academic-grpc-test' to prevent gRPC name collision between test contexts
- [Phase 07]: grpc.server.port=-1 in application-test.yml disables Netty port binding in all test classes
- [Phase 08 Plan 01]: AcademicReadService is a separate @Service bean — avoids AOP self-invocation where @Cacheable proxy would be bypassed if methods were in AcademicGrpcServiceImpl
- [Phase 08 Plan 01]: String literal keys used for zero-arg @Cacheable methods: key="'current'" and key="'global'" produce readable Redis keys instead of SimpleKey.EMPTY
- [Phase 08 Plan 01]: Programmatic CacheManager eviction used in UserService for runtime-only keys (old group on transfer, groups+group_members on headman change per D-10)
- [Phase 08-redis-caching]: ObjectProvider<RedisConnectionFactory> in CacheConfig avoids @ConditionalOnBean timing bug — user @Configuration beans evaluated before Redis autoconfiguration registers the factory
- [Phase 08-redis-caching]: NON_FINAL default typing required for GenericJackson2JsonRedisSerializer — OBJECT_AND_NON_CONCRETE omits @class for concrete types causing deserialization failure
- [Phase 09-rabbitmq-events]: SemesterRepository.findByIsActiveTrue() returns Optional<Semester> -- activateSemester uses ifPresent to publish SemesterArchivedEvent
- [Phase 09-rabbitmq-events]: RabbitConfig injects Spring Boot autoconfigured ObjectMapper (no @class fields) -- CacheConfig ObjectMapper is only a local variable, not a Spring bean

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 6: AssistantPermission mapped as VARCHAR(64)[] PostgreSQL array — JPA @Converter interaction with Hibernate arrays is non-standard; verify mapping approach with Testcontainers test early in Phase 5.
- Phase 8: @Cacheable self-invocation via Spring AOP proxy is a runtime-only failure — requires Testcontainers integration tests for every cache path, not just unit tests.

## Session Continuity

Last session: 2026-03-30T23:58:33.577Z
Stopped at: Completed 09-01-PLAN.md
Resume file: None
