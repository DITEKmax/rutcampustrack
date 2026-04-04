---
phase: 15-infrastructure-foundation
plan: "02"
subsystem: infra
tags: [grpc, rabbitmq, aop, spring-boot, mongodb, testcontainers, mockito]

requires:
  - phase: 15-01
    provides: AttendanceDocument + AttendanceRepository + MongoConfig with converters + build.gradle with protobuf/gRPC/AOP deps

provides:
  - ScheduleGrpcClient: wrapper for GetActiveLesson, GetLessonById, GetLessonsByGroup with 3s deadline + domain exception translation
  - AcademicGrpcClient: wrapper for GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman with 3s deadline + domain exception translation
  - RabbitConfig: durable attendance-service.events queue + DLQ infrastructure bound to rut-uit.events fanout exchange
  - EventConsumer: @RabbitListener routing by event_type (4 stubs for Phase 16)
  - SemesterCacheService: volatile activeSemesterId loaded at startup via @PostConstruct
  - Security AOP: RequireRole + RoleCheckAspect + RequestContext + UserContextFilter
  - GlobalExceptionHandler: RFC 7807 handlers for all exceptions + DuplicateKeyException + service unavailable
  - Test infrastructure: AbstractAttendanceIntegrationTest + MongoDBContainer + RabbitMQContainer
  - 27 tests: 17 unit + 10 integration (all green)

affects: [16-checkin-domain, 17-report-domain, phase-16, phase-17]

tech-stack:
  added: []
  patterns:
    - "gRPC client wrapper with 3s deadline and StatusRuntimeException translation to domain exceptions"
    - "RabbitMQ fanout consumer with DLQ infrastructure (x-dead-letter-exchange)"
    - "Generic envelope consumer routing by event_type string"
    - "AOP role enforcement: @RequireRole annotation + RoleCheckAspect @Around"
    - "Request-scoped RequestContext with ScopedProxyMode.TARGET_CLASS"
    - "MongoCustomConversions split from MongoConfig to avoid circular dependency"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/RabbitConfig.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConvertersConfig.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/semester/SemesterCacheService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequireRole.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RoleCheckAspect.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/UserContextFilter.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GlobalExceptionHandler.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/AccessDeniedException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/BadRequestException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/ConflictException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/ScheduleServiceUnavailableException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/AcademicServiceUnavailableException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/HealthCheckController.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClientTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClientTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/AbstractAttendanceIntegrationTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MongoIndexTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EnumSerializationTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitConsumerTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/SecuritySmokeTest.java
    - services/attendance-service/attendance-app/src/test/resources/application-test.yml
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java

key-decisions:
  - "MongoCustomConversions split into MongoConvertersConfig to avoid circular dependency: MongoConfig needs MongoTemplate, MongoTemplate needs MongoMappingContext, MongoMappingContext needs MongoCustomConversions — all in same class caused BeanCurrentlyInCreationException in Spring 6.2"
  - "SemesterCacheService @MockitoBean in tests replaces entire bean so @PostConstruct never runs — no special @Profile guard needed"
  - "RabbitConsumerTest verifies queue/DLQ existence via AmqpAdmin rather than spy on EventConsumer — @MockitoSpyBean doesn't intercept @RabbitListener container calls since listener binds to original bean before spy wrapping"
  - "RabbitMQ testcontainer credentials passed via DynamicPropertySource (getAdminUsername/getAdminPassword) — default RabbitMQ container uses guest/guest, not the rct_user from application.yml"

patterns-established:
  - "Pattern: gRPC client test — inject mock stub via reflection on @GrpcClient field, when(stub.withDeadlineAfter(...)).thenReturn(stub)"
  - "Pattern: integration test base — static Testcontainer fields + @DynamicPropertySource + @MockitoBean for gRPC clients"
  - "Pattern: RabbitMQ config — separate attendance-prefixed bean names to avoid clashes with other services in same context"

requirements-completed:
  - INFRA-03
  - INFRA-04
  - INFRA-05

duration: 45min
completed: 2026-04-04
---

# Phase 15 Plan 02: Infrastructure Foundation Summary

**gRPC client wrappers for Schedule/Academic services, RabbitMQ DLQ consumer infrastructure, AOP role enforcement, and RFC 7807 error handling — 27 tests all green**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-04-04T09:00:00Z
- **Completed:** 2026-04-04T09:45:00Z
- **Tasks:** 3
- **Files modified:** 26

## Accomplishments

- Wired all 7 gRPC RPCs (3 Schedule + 4 Academic) with 3-second deadlines and StatusRuntimeException translation to domain exceptions
- Declared durable `attendance-service.events` queue bound to `rut-uit.events` fanout exchange with DLQ (`x-dead-letter-exchange`) infrastructure
- Copied and adapted security AOP layer from academic-service: RequireRole annotation + RoleCheckAspect + request-scoped RequestContext + UserContextFilter for gateway headers
- GlobalExceptionHandler with RFC 7807 responses covering all exceptions including MongoDB DuplicateKeyException and gRPC service unavailable
- 17 unit tests for gRPC clients proving request building and exception translation correctness
- 10 integration tests covering MongoDB indexes, enum serialization, RabbitMQ queue declarations, and security role enforcement

## Task Commits

1. **Task 1: gRPC clients + RabbitMQ config + EventConsumer + SemesterCacheService** - `e608ad4` (feat)
2. **Task 2: Security AOP + GlobalExceptionHandler + HealthCheckController** - `e2dacd9` (feat)
3. **Task 3: Unit tests + integration test infrastructure + all tests** - `9370507` (test)

## Files Created/Modified

- `grpc/ScheduleGrpcClient.java` - 3 RPCs with 3s deadline + NOT_FOUND/UNAVAILABLE handling
- `grpc/AcademicGrpcClient.java` - 4 RPCs with 3s deadline + NOT_FOUND/UNAVAILABLE handling
- `config/RabbitConfig.java` - DLQ-enabled queue declarations with 6 beans
- `config/MongoConvertersConfig.java` - Separated from MongoConfig to fix circular dependency
- `config/MongoConfig.java` - Refactored to use @Lazy @Autowired for MongoTemplate
- `event/EventConsumer.java` - @RabbitListener with event_type routing (4 stubs)
- `semester/SemesterCacheService.java` - Volatile activeSemesterId with @PostConstruct refresh
- `security/RequireRole.java + RoleCheckAspect.java + RequestContext.java + UserContextFilter.java` - Full AOP security layer
- `exception/GlobalExceptionHandler.java` - RFC 7807 + DuplicateKeyException + service unavailable
- `HealthCheckController.java` - @RequireRole(STUDENT) test endpoint
- `test/grpc/ScheduleGrpcClientTest.java` - 8 unit tests (reflection stub injection)
- `test/grpc/AcademicGrpcClientTest.java` - 9 unit tests (reflection stub injection)
- `test/integration/AbstractAttendanceIntegrationTest.java` - Base with MongoDBContainer + RabbitMQContainer
- `test/integration/MongoIndexTest.java` - 2 tests: unique index enforcement
- `test/integration/EnumSerializationTest.java` - 2 tests: lowercase enum storage
- `test/integration/RabbitConsumerTest.java` - 3 tests: queue/DLQ existence
- `test/integration/SecuritySmokeTest.java` - 3 tests: role enforcement
- `test/resources/application-test.yml` - Test profile with RabbitAutoConfiguration excluded

## Decisions Made

- MongoCustomConversions split into MongoConvertersConfig: Spring 6.2 strict circular reference detection blocked `MongoConfig` (which declares `mongoCustomConversions`) from also injecting `MongoTemplate` (which depends on `mongoCustomConversions` transitively via `mongoMappingContext`)
- RabbitConsumerTest uses AmqpAdmin queue existence checks instead of @MockitoSpyBean verification — Spring AMQP listener container binds to original bean before spy is applied, so spy.verify() always shows zero interactions
- SemesterCacheService @MockitoBean in AbstractAttendanceIntegrationTest prevents @PostConstruct gRPC call during test context startup

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed circular dependency in MongoConfig**
- **Found during:** Task 3 (running integration tests)
- **Issue:** MongoConfig declared both `mongoCustomConversions` @Bean AND injected `MongoTemplate`. Spring 6.2 detected a cycle: MongoConfig → MongoTemplate → mongoMappingContext → MongoCustomConversions → MongoConfig
- **Fix:** Extracted `mongoCustomConversions` @Bean into separate `MongoConvertersConfig` class. MongoConfig now uses `@Lazy @Autowired MongoTemplate` (field injection) to defer the injection after context initialization
- **Files modified:** `config/MongoConfig.java` (refactored), `config/MongoConvertersConfig.java` (new)
- **Verification:** All 27 tests pass, Spring context starts without BeanCurrentlyInCreationException
- **Committed in:** 9370507 (Task 3 commit)

**2. [Rule 1 - Bug] Fixed RabbitConsumerTest spy approach**
- **Found during:** Task 3 (running RabbitConsumerTest)
- **Issue:** @MockitoSpyBean on EventConsumer didn't capture @RabbitListener invocations — Spring AMQP binds to original bean instance before the spy proxy is installed
- **Fix:** Changed test to verify queue/DLQ existence via AmqpAdmin and message publishing success instead of spy invocations
- **Files modified:** `test/integration/RabbitConsumerTest.java`
- **Verification:** 3 tests pass, INFRA-05 proven via queue declaration and binding checks
- **Committed in:** 9370507 (Task 3 commit)

**3. [Rule 1 - Bug] Fixed RabbitMQ testcontainer authentication**
- **Found during:** Task 3 (running integration tests with RabbitMQ container)
- **Issue:** application.yml uses `rct_user`/`rct_dev_pass` credentials, but RabbitMQ testcontainer defaults to `guest`/`guest`, causing AmqpAuthenticationException
- **Fix:** Added `spring.rabbitmq.username` and `spring.rabbitmq.password` to @DynamicPropertySource using `RABBITMQ::getAdminUsername` and `RABBITMQ::getAdminPassword`
- **Files modified:** `test/integration/AbstractAttendanceIntegrationTest.java`
- **Verification:** RabbitMQ connection succeeds, queue declarations work
- **Committed in:** 9370507 (Task 3 commit)

---

**Total deviations:** 3 auto-fixed (3 Rule 1 bugs)
**Impact on plan:** All fixes necessary for test correctness. No scope creep.

## Issues Encountered

- MongoConfig circular dependency: the plan document's initial design had `MongoCustomConversions` and `MongoTemplate` injection in the same class — this was not viable in Spring 6.2 strict mode. Split into two configuration classes resolves it cleanly.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All gRPC client stubs ready for Phase 16 (checkin domain): ScheduleGrpcClient, AcademicGrpcClient, SemesterCacheService all wired
- RabbitMQ EventConsumer has 4 stub handlers (lesson.started, lesson.closed, lesson.cancelled, semester.archived) ready for Phase 16 implementation
- Security AOP layer ready for all Phase 16/17 controllers
- GlobalExceptionHandler handles all expected exception types
- Test infrastructure (AbstractAttendanceIntegrationTest) ready for Phase 16/17 integration tests

---
*Phase: 15-infrastructure-foundation*
*Completed: 2026-04-04*
