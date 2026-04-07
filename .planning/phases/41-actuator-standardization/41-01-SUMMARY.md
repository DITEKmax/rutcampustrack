---
phase: 41-actuator-standardization
plan: 01
subsystem: infra
tags: [spring-boot-actuator, health-check, monitoring, java, spring-security]

requires:
  - phase: prior java services
    provides: Auth, Academic, Schedule, Attendance Spring Boot services with GlobalExceptionHandlers

provides:
  - Spring Boot Actuator health+info endpoints on all 4 Java services
  - application-prod.yml per service restricting exposure to health,info
  - 24 ActuatorIT integration tests (6 per service)
  - Fixed GlobalExceptionHandlers to return 404 for NoHandlerFoundException/NoResourceFoundException

affects:
  - 43-docker-compose-prod (needs /actuator/health for container healthchecks)
  - 47-swagger-ui (benefits from standardized management config)

tech-stack:
  added:
    - spring-boot-starter-actuator (all 4 Java services)
  patterns:
    - management.endpoints.web.exposure.include: health,info only (never expose env/beans/heapdump)
    - management.endpoint.health.show-details: never (prevents connection string leakage)
    - GlobalExceptionHandler must include NoHandlerFoundException + NoResourceFoundException handlers to prevent generic catch-all converting 404 to 500

key-files:
  created:
    - services/auth-service/src/main/resources/application-prod.yml
    - services/academic-service/academic-app/src/main/resources/application-prod.yml
    - services/schedule-service/schedule-app/src/main/resources/application-prod.yml
    - services/attendance-service/attendance-app/src/main/resources/application-prod.yml
    - services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ActuatorIT.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ActuatorIT.java
  modified:
    - services/auth-service/build.gradle.kts
    - services/auth-service/src/main/resources/application.yml
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java
    - services/auth-service/src/test/resources/application-test.yml
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/resources/application.yml
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/test/resources/application-test.yml
    - services/schedule-service/schedule-app/build.gradle.kts
    - services/schedule-service/schedule-app/src/main/resources/application.yml
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java
    - services/schedule-service/schedule-app/src/test/resources/application-test.yml
    - services/attendance-service/attendance-app/build.gradle.kts
    - services/attendance-service/attendance-app/src/main/resources/application.yml
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GlobalExceptionHandler.java

key-decisions:
  - "Actuator shares main application port (9090-9093) — no management.server.port set"
  - "Only health and info endpoints exposed — env/beans/heapdump return 404 by design"
  - "show-details: never prevents DB/Redis connection string leakage in health response"
  - "GlobalExceptionHandler catch-all (Exception.class) masks NoHandlerFoundException — must add explicit handlers for Spring routing exceptions"
  - "Test profiles disable Redis/RabbitMQ health indicators to prevent 503 when those infra are excluded via autoconfigure.exclude"

patterns-established:
  - "Pattern: Every GlobalExceptionHandler must handle NoHandlerFoundException and NoResourceFoundException before the generic Exception catch-all"
  - "Pattern: Test profiles should set management.health.{indicator}.enabled=false for any autoconfigure-excluded infrastructure"
  - "Pattern: application-prod.yml per service for production management overrides"

requirements-completed:
  - MON-01
  - MON-02

duration: 90min
completed: 2026-04-07
---

# Phase 41 Plan 01: Actuator Standardization Summary

**Spring Boot Actuator with health+info-only exposure added to 4 Java services, secured with application-prod.yml overrides and 24 passing ActuatorIT integration tests**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-04-07T16:00:00Z
- **Completed:** 2026-04-07T16:31:53Z
- **Tasks:** 1 (single combined task per plan)
- **Files modified:** 24

## Accomplishments

- Added `spring-boot-starter-actuator` to auth, academic, schedule, and attendance services
- Restricted actuator web exposure to `health,info` only — env, beans, heapdump return 404 in all profiles including production
- Created `application-prod.yml` for each service with identical management restrictions
- Fixed `auth-service` `SecurityFilterChain` to `permitAll()` for `/actuator/**` (required for Docker healthchecks)
- Fixed all 4 `GlobalExceptionHandler` classes: added `NoHandlerFoundException` and `NoResourceFoundException` handlers so unexposed actuator endpoints return proper 404 (not 500)
- Fixed academic-service and schedule-service test profiles to disable Redis/RabbitMQ health indicators that caused 503 in tests
- 24 ActuatorIT integration tests across 4 services — all passing green

## Task Commits

1. **Task 1: Add Actuator + YAML config + SecurityFilterChain + GlobalExceptionHandler fixes + tests** - `caaaf73` (feat)

## Files Created/Modified

- `services/auth-service/build.gradle.kts` — added actuator dependency
- `services/auth-service/src/main/resources/application.yml` — added management block (health,info only)
- `services/auth-service/src/main/resources/application-prod.yml` — new: production management override
- `services/auth-service/src/main/java/.../SecurityConfig.java` — added `/actuator/**` to permitAll
- `services/auth-service/src/main/java/.../GlobalExceptionHandler.java` — added NoHandlerFoundException, NoResourceFoundException, ErrorResponseException, ResponseStatusException handlers
- `services/auth-service/src/test/resources/application-test.yml` — added spring.mvc/web settings
- `services/auth-service/src/test/java/.../ActuatorIT.java` — new: 6 integration tests
- `services/academic-service/academic-app/build.gradle.kts` — added actuator dependency
- `services/academic-service/academic-app/src/main/resources/application.yml` — added management block
- `services/academic-service/academic-app/src/main/resources/application-prod.yml` — new: production override
- `services/academic-service/academic-app/src/main/java/.../GlobalExceptionHandler.java` — added routing exception handlers
- `services/academic-service/academic-app/src/test/resources/application-test.yml` — disabled Redis+RabbitMQ health indicators
- `services/academic-service/academic-app/src/test/java/.../ActuatorIT.java` — new: 6 integration tests
- `services/schedule-service/schedule-app/build.gradle.kts` — added actuator dependency
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — added management block
- `services/schedule-service/schedule-app/src/main/resources/application-prod.yml` — new: production override
- `services/schedule-service/schedule-app/src/main/java/.../GlobalExceptionHandler.java` — added routing exception handlers
- `services/schedule-service/schedule-app/src/test/resources/application-test.yml` — disabled RabbitMQ health indicator
- `services/schedule-service/schedule-app/src/test/java/.../ActuatorIT.java` — new: 6 integration tests
- `services/attendance-service/attendance-app/build.gradle.kts` — added actuator dependency
- `services/attendance-service/attendance-app/src/main/resources/application.yml` — added management block
- `services/attendance-service/attendance-app/src/main/resources/application-prod.yml` — new: production override
- `services/attendance-service/attendance-app/src/main/java/.../GlobalExceptionHandler.java` — added routing exception handlers
- `services/attendance-service/attendance-app/src/test/java/.../ActuatorIT.java` — new: 6 integration tests

## Decisions Made

- **Actuator shares main port**: No `management.server.port` set — actuator endpoints accessible on same port as the service (9090-9093). Required for Docker healthchecks in Phase 43.
- **health+info only**: `management.endpoints.web.exposure.include: health,info` with `show-details: never`. This satisfies threat model T-41-01 through T-41-04 (no env/heapdump/beans exposure, no connection string leakage).
- **GlobalExceptionHandler fix is a system-wide pattern**: Spring Boot 3.4 / Spring 6.2 throws `NoHandlerFoundException` (extends `ServletException`) for unmatched requests. A generic `@ExceptionHandler(Exception.class)` intercepts this before `DefaultHandlerExceptionResolver` can produce a 404, resulting in 500. All 4 handlers now explicitly handle routing exceptions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] All 4 GlobalExceptionHandlers converted 404 to 500 for unexposed actuator endpoints**
- **Found during:** Task 1 (GREEN test phase — `envEndpointReturns404`, `beansEndpointReturns404`, `heapdumpEndpointReturns404` all returned 500)
- **Issue:** In Spring 6.2, `NoHandlerFoundException` (thrown when no handler found for a URL) extends `ServletException`. The generic `@ExceptionHandler(Exception.class)` catch-all intercepted it and returned HTTP 500 instead of letting `DefaultHandlerExceptionResolver` produce a 404.
- **Fix:** Added explicit `@ExceptionHandler(NoHandlerFoundException.class)`, `@ExceptionHandler(NoResourceFoundException.class)`, `@ExceptionHandler(ErrorResponseException.class)`, and `@ExceptionHandler(ResponseStatusException.class)` handlers before the generic `Exception` handler in all 4 `GlobalExceptionHandler` classes.
- **Files modified:** All 4 `GlobalExceptionHandler.java` files
- **Verification:** All 24 ActuatorIT tests pass green including 3 "returns404" tests per service
- **Committed in:** `caaaf73`

**2. [Rule 1 - Bug] Academic-service health returned 503 in tests**
- **Found during:** Task 1 (GREEN test phase — `healthEndpointReturnsUp` returned 503)
- **Issue:** `AbstractAcademicIntegrationTest` excludes `RedisAutoConfiguration` and `RabbitAutoConfiguration` via `spring.autoconfigure.exclude`, but Spring Boot Actuator's Redis and RabbitMQ health indicators still ran and reported DOWN.
- **Fix:** Added `management.health.redis.enabled=false` and `management.health.rabbit.enabled=false` to `application-test.yml`.
- **Files modified:** `academic-service/academic-app/src/test/resources/application-test.yml`
- **Verification:** `healthEndpointReturnsUp` passes with HTTP 200 + body containing "UP"
- **Committed in:** `caaaf73`

**3. [Rule 1 - Bug] Schedule-service health returned 503 in tests**
- **Found during:** Task 1 (same pattern as academic-service)
- **Issue:** `AbstractScheduleIntegrationTest` excludes `RabbitAutoConfiguration` but RabbitMQ health indicator still ran.
- **Fix:** Added `management.health.rabbit.enabled=false` to `application-test.yml`.
- **Files modified:** `schedule-service/schedule-app/src/test/resources/application-test.yml`
- **Verification:** `healthEndpointReturnsUp` passes
- **Committed in:** `caaaf73`

---

**Total deviations:** 3 auto-fixed (all Rule 1 — bugs)
**Impact on plan:** All auto-fixes required for correctness. The GlobalExceptionHandler fix is an important system-wide improvement: every service now correctly propagates HTTP status codes for Spring routing exceptions instead of masking them as 500.

## Issues Encountered

- **Pre-existing test failures (not caused by this plan):** 2 `CacheIntegrationTest` failures (academic-service: `io.grpc.StatusRuntimeException: NOT_FOUND: Semester с isActive=true не найден`) and 4-5 `CheckinServiceTest` + `EventConsumerIntegrationTest` failures (attendance-service). Verified pre-existing via `git stash` test.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All 4 Java services have `/actuator/health` returning HTTP 200 with `{"status":"UP"}` body
- Phase 43 (docker-compose.prod.yml) can use `healthcheck: test: curl -f http://localhost:{port}/actuator/health` for all 4 services
- Threat model T-41-01 through T-41-05 all mitigated or accepted as documented

---
*Phase: 41-actuator-standardization*
*Completed: 2026-04-07*
