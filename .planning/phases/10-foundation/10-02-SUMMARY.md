---
phase: 10-foundation
plan: 02
subsystem: security, testing
tags: [aop, testcontainers, postgresql, rfc7807, spring-boot]

requires:
  - phase: 10-01
    provides: "JPA entities (ScheduleItem, Lesson), enums (UserRole, WeekType, LessonStatus), Flyway V1 migration, repositories"
provides:
  - "Security filter chain: UserContextFilter -> RequestContext -> RoleCheckAspect -> AccessDeniedException -> GlobalExceptionHandler -> 403"
  - "ErrorResponse record in schedule-api-contract for RFC 7807 responses"
  - "AbstractScheduleIntegrationTest base class with PostgreSQL 16 Testcontainer"
  - "application-test.yml with gRPC port -1 and ddl-auto validate"
  - "HealthCheckController placeholder endpoint at /schedule/health-check"
affects: [11-rest-api, 12-lesson-generation, 13-events-cron, 14-grpc-server]

tech-stack:
  added: [testcontainers-postgresql]
  patterns: ["@RequireRole AOP security", "RFC 7807 GlobalExceptionHandler", "AbstractScheduleIntegrationTest base class"]

key-files:
  created:
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/exception/ErrorResponse.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/RequestContext.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/RequireRole.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/RoleCheckAspect.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/UserContextFilter.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/AccessDeniedException.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/HealthCheckController.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java
    - services/schedule-service/schedule-app/src/test/resources/application-test.yml
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/EntityMappingIntegrationTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/SecuritySmokeTest.java
  modified: []

key-decisions:
  - "V2__implicit_casts.sql was NOT needed -- Hibernate validated custom PostgreSQL enums without explicit casts"
  - "Ported security layer identically from academic-service, only changing package and UserRole import"
  - "Only AccessDeniedException and general Exception handlers in Phase 10 GlobalExceptionHandler -- domain exceptions deferred to Phase 11"

patterns-established:
  - "@RequireRole + RoleCheckAspect AOP pattern for schedule-service role enforcement"
  - "AbstractScheduleIntegrationTest as shared Testcontainers base for all schedule-service tests"
  - "ErrorResponse record in schedule-api-contract for RFC 7807 error responses"

requirements-completed: [LSSN-03, CRON-04]

duration: 3min
completed: 2026-04-01
---

# Phase 10 Plan 02: Security + Tests Summary

**AOP role-based security chain with RFC 7807 errors, Testcontainers PostgreSQL base class, and 3 passing integration tests**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-01T19:55:28Z
- **Completed:** 2026-04-01T19:58:48Z
- **Tasks:** 2/2
- **Files created:** 12

## Accomplishments

- Security filter chain fully wired: UserContextFilter reads gateway headers, populates RequestContext, RoleCheckAspect enforces @RequireRole, GlobalExceptionHandler maps AccessDeniedException to HTTP 403 with RFC 7807 body
- Testcontainers abstract base class with PostgreSQL 16 container (schedule_db) ready for all future test classes
- 3 integration tests passing: EntityMappingIntegrationTest (Hibernate validates both entities), SecuritySmokeTest (403 without headers + 200 with valid headers)
- V2__implicit_casts.sql was NOT needed -- custom PostgreSQL enum types (week_type, lesson_status) validated successfully without explicit casts

## Task Commits

Each task was committed atomically:

1. **Task 1: Security infrastructure + exception handling + placeholder endpoint** - `fbc7057` (feat)
2. **Task 2: Testcontainers base class + application-test.yml + integration tests** - `8183e10` (test)

## Files Created/Modified

- `schedule-api-contract/.../exception/ErrorResponse.java` - RFC 7807 Problem Details record (no Lombok)
- `schedule-app/.../security/RequestContext.java` - Request-scoped bean with TARGET_CLASS proxy
- `schedule-app/.../security/RequireRole.java` - Method-level role annotation
- `schedule-app/.../security/RoleCheckAspect.java` - AOP aspect enforcing role checks
- `schedule-app/.../security/UserContextFilter.java` - OncePerRequestFilter reading X-User-* headers
- `schedule-app/.../exception/AccessDeniedException.java` - Custom 403 exception (not Spring Security)
- `schedule-app/.../exception/GlobalExceptionHandler.java` - @RestControllerAdvice with 403 + 500 handlers
- `schedule-app/.../security/HealthCheckController.java` - Placeholder endpoint for smoke test
- `schedule-app/.../integration/AbstractScheduleIntegrationTest.java` - Testcontainers base class
- `schedule-app/src/test/resources/application-test.yml` - Test config (gRPC -1, validate, Flyway)
- `schedule-app/.../integration/EntityMappingIntegrationTest.java` - Hibernate schema validation test
- `schedule-app/.../integration/SecuritySmokeTest.java` - Security 403/200 smoke test

## Decisions Made

- **V2__implicit_casts.sql not needed:** Unlike academic-service which required V5__implicit_casts.sql, schedule-service's custom PostgreSQL enum types (week_type, lesson_status) passed Hibernate ddl-auto: validate without explicit varchar-to-enum casts. This may be due to the autoApply EnumConverters handling conversion correctly.
- **Minimal GlobalExceptionHandler:** Only AccessDeniedException and general Exception handlers for Phase 10. Domain-specific exceptions (ResourceNotFoundException, BadRequestException, ConflictException) will be added in Phase 11 when domain logic is implemented.
- **Identical security port from academic-service:** Four security classes copied with only package and UserRole import changes, ensuring consistent security behavior across services.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - V2__implicit_casts.sql fallback was not needed.

## User Setup Required

None - no external service configuration required.

## Known Stubs

- `HealthCheckController.java` at `/schedule/health-check` is a placeholder endpoint for smoke testing. Will be superseded by real schedule endpoints in Phase 11.

## Next Phase Readiness

- Security infrastructure complete: any new endpoint just needs `@RequireRole` annotation
- Test base class ready: future tests extend `AbstractScheduleIntegrationTest`
- ErrorResponse available in contract module for future exception types
- GlobalExceptionHandler ready to receive additional `@ExceptionHandler` methods in Phase 11
- Phase 10 foundation is fully complete (Plan 01 + Plan 02)

## Self-Check: PASSED

- All 12 files created: FOUND
- Commit fbc7057 (Task 1): FOUND
- Commit 8183e10 (Task 2): FOUND
- All 3 integration tests: PASSING

---
*Phase: 10-foundation*
*Completed: 2026-04-01*
