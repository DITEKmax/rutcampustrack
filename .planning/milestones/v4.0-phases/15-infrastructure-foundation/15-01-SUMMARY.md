---
phase: 15-infrastructure-foundation
plan: 01
subsystem: infra
tags: [mongodb, grpc, protobuf, testcontainers, spring-data-mongodb, rabbitmq]

# Dependency graph
requires: []
provides:
  - attendance-app build config with protobuf plugin, gRPC starters, AOP, Testcontainers
  - AttendanceDocument MongoDB entity with full denormalization (12 fields)
  - MongoConfig with 4 programmatic indexes (INFRA-01) and enum converters (INFRA-02)
  - attendance-api-contract with UserRole, ErrorResponse, ResourceNotFoundException
  - application.yml with gRPC client channels for schedule-service and academic-service
affects:
  - 15-02
  - 16-event-consumers
  - 17-write-path
  - 18-read-path

# Tech tracking
tech-stack:
  added:
    - com.google.protobuf:0.9.4 (Gradle plugin)
    - net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE
    - net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE
    - spring-boot-starter-aop
    - org.testcontainers:mongodb
    - org.testcontainers:rabbitmq
    - org.testcontainers:testcontainers-bom:1.20.4
  patterns:
    - Programmatic MongoDB index creation via @PostConstruct + mongoTemplate.indexOps()
    - Enum lowercase serialization via MongoCustomConversions with @WritingConverter/@ReadingConverter
    - gRPC server disabled (port=-1) for consumer-only services
    - application-test.yml overrides gRPC channels to localhost for Testcontainers

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceDocument.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/UserRole.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/exception/ErrorResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/exception/ResourceNotFoundException.java
    - services/attendance-service/attendance-app/src/main/resources/application-test.yml
  modified:
    - services/attendance-service/attendance-app/build.gradle.kts
    - services/attendance-service/attendance-app/src/main/resources/application.yml

key-decisions:
  - "MongoConfig uses @PostConstruct + ensureIndex (not auto-index-creation=true) — idempotent, no silent loss on restart"
  - "org.springframework.data.convert (not .mongodb.core.convert) is correct package for @WritingConverter/@ReadingConverter in Spring Data MongoDB 4.x"
  - "grpc.server.port=-1 for attendance-service — pure gRPC consumer, does not expose gRPC server"

patterns-established:
  - "Enum converters: @WritingConverter converts to .toLowerCase(), @ReadingConverter does .toUpperCase() before valueOf()"
  - "4 indexes: uniq_lesson_user (unique compound), idx_user_semester_date, idx_group_semester_subject, idx_lesson_id"

requirements-completed: [INFRA-01, INFRA-02]

# Metrics
duration: 22min
completed: 2026-04-04
---

# Phase 15 Plan 01: Infrastructure Foundation Summary

**attendance-app compiles with protobuf+gRPC+Testcontainers dependencies; MongoDB AttendanceDocument entity with 4 programmatic indexes and lowercase enum converters registered via MongoCustomConversions**

## Performance

- **Duration:** 22 min
- **Started:** 2026-04-04T08:35:24Z
- **Completed:** 2026-04-04T08:57:00Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- attendance-app now compiles with the full protobuf/gRPC stack (generates stubs from proto/ at build time)
- AttendanceDocument captures D-01 full denormalization: lessonId, userId, groupId, subjectId, semesterId, lessonNumber, lessonDate, status, source, markedBy, createdAt, updatedAt
- MongoConfig creates 4 indexes at startup (uniq_lesson_user unique compound + 3 query indexes) and registers converters for lowercase enum storage

## Task Commits

1. **Task 1: Build config + contract additions + application YAML** - `a430669` (feat)
2. **Task 2: MongoDB document entity + MongoConfig** - `ed66fae` (feat)

## Files Created/Modified

- `services/attendance-service/attendance-app/build.gradle.kts` - Added protobuf plugin, gRPC starters, AOP, Testcontainers BOM and dependencies
- `services/attendance-service/attendance-app/src/main/resources/application.yml` - Added gRPC client channels for schedule-service and academic-service
- `services/attendance-service/attendance-app/src/main/resources/application-test.yml` - Created: gRPC channel overrides to localhost for test profile
- `services/attendance-service/attendance-api-contract/.../enums/UserRole.java` - Created: ADMIN, TEACHER, STUDENT
- `services/attendance-service/attendance-api-contract/.../exception/ErrorResponse.java` - Created: RFC 7807 record with FieldError nested record
- `services/attendance-service/attendance-api-contract/.../exception/ResourceNotFoundException.java` - Created: RuntimeException with resourceName/fieldName/fieldValue
- `services/attendance-service/attendance-app/.../checkin/AttendanceDocument.java` - Created: MongoDB @Document entity with 12 fields
- `services/attendance-service/attendance-app/.../checkin/AttendanceRepository.java` - Created: MongoRepository<AttendanceDocument, String>
- `services/attendance-service/attendance-app/.../config/MongoConfig.java` - Created: @PostConstruct indexes + MongoCustomConversions bean

## Decisions Made

- `@WritingConverter` / `@ReadingConverter` must be imported from `org.springframework.data.convert`, NOT `org.springframework.data.mongodb.core.convert` — the latter does not contain these annotations in Spring Data MongoDB 4.x. Fixed during Task 2 compilation.
- `grpc.server.port: -1` chosen because attendance-service is a gRPC consumer only (no exposed server). Pattern follows D-08 research recommendation.
- Programmatic index creation via `@PostConstruct` + `ensureIndex` chosen over `spring.data.mongodb.auto-index-creation=true` to match the research anti-pattern warning and ensure indexes are created with explicit names for future reference.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong import package for @WritingConverter/@ReadingConverter**
- **Found during:** Task 2 (MongoConfig compilation)
- **Issue:** Plan specified `org.springframework.data.mongodb.core.convert.WritingConverter` but this class doesn't exist in that package in Spring Data MongoDB 4.x. Correct package is `org.springframework.data.convert`.
- **Fix:** Changed import statements to `org.springframework.data.convert.ReadingConverter` and `org.springframework.data.convert.WritingConverter`
- **Files modified:** `MongoConfig.java`
- **Verification:** `compileJava` exits 0 after fix
- **Committed in:** ed66fae (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Necessary correctness fix for wrong package. No scope creep.

## Issues Encountered

- None beyond the import fix documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- attendance-app compiles with all dependencies needed for Phase 15-02 (AbstractAttendanceIntegrationTest) and Phase 16 (event consumers)
- MongoConfig wires indexes and converters; Phase 15-02 will verify them with a Testcontainers integration test
- No blockers

---
*Phase: 15-infrastructure-foundation*
*Completed: 2026-04-04*
