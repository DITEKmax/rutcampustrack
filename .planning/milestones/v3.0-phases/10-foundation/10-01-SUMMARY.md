---
phase: 10-foundation
plan: 01
subsystem: database
tags: [jpa, spring-data, testcontainers, gradle, timezone, clock]

requires: []
provides:
  - "ScheduleItem JPA entity mapping schedule_items table"
  - "Lesson JPA entity mapping lessons table with UNIQUE(schedule_item_id, date) idempotency constraint"
  - "ScheduleItemRepository and LessonRepository Spring Data interfaces"
  - "UserRole enum in schedule-api-contract for security layer"
  - "ClockConfig bean with Europe/Moscow timezone"
  - "SchedulingConfig with @Profile('!test') guard"
  - "AOP starter and Testcontainers BOM in build.gradle.kts"
  - "grpc.server.port: 19092 placeholder in application.yml"
affects: [10-02, 11-rest-api, 12-lesson-generation, 13-events-cron, 14-grpc-server]

tech-stack:
  added: [spring-boot-starter-aop, testcontainers-bom-1.20.4, spring-boot-testcontainers]
  patterns: [Clock injection for testable time, Profile-guarded scheduling, FK-as-Long-ID entities]

key-files:
  created:
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/UserRole.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/ClockConfig.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/SchedulingConfig.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
  modified:
    - services/schedule-service/schedule-app/build.gradle.kts
    - services/schedule-service/schedule-app/src/main/resources/application.yml
    - docker-compose.yml

key-decisions:
  - "No gRPC starters added — deferred to Phase 14 per D-10; only port placeholder in application.yml"
  - "No @Convert annotations on entity fields — autoApply=true converters handle WeekType and LessonStatus"
  - "No @ManyToOne associations — FK columns stored as Long IDs per project convention"

patterns-established:
  - "Clock injection: inject Clock bean (not LocalTime.now()) for testable time comparisons"
  - "@Profile('!test') on SchedulingConfig: cron jobs disabled in integration tests"
  - "FK-as-Long-ID: no JPA associations, schedule_item_id stored as Long in Lesson entity"

requirements-completed: [LSSN-03, CRON-04]

duration: 3min
completed: 2026-04-01
---

# Phase 10 Plan 01: Build Scaffold and Core Domain Layer Summary

**Schedule Service build scaffold with AOP/Testcontainers deps, JPA entities for schedule_items and lessons tables, Clock bean for Moscow timezone, and UserRole enum for security layer**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-01T19:48:37Z
- **Completed:** 2026-04-01T19:51:30Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- Build.gradle.kts updated with AOP starter, Testcontainers BOM, and test dependencies
- UserRole enum created in schedule-api-contract (ADMIN, TEACHER, STUDENT)
- application.yml configured with hibernate.jdbc.time_zone=Europe/Moscow and grpc.server.port=19092
- docker-compose.yml updated with TZ=Europe/Moscow for postgres-schedule
- ClockConfig bean wired with ZoneId Europe/Moscow for testable time injection
- SchedulingConfig guarded with @Profile("!test") to disable cron in tests
- ScheduleItem entity maps all schedule_items columns with correct Java types (LocalTime, WeekType, OffsetDateTime)
- Lesson entity maps lessons table with UNIQUE(schedule_item_id, date) idempotency anchor for LSSN-03
- Both Spring Data repositories created with useful query methods

## Task Commits

Each task was committed atomically:

1. **Task 1: Build dependencies + UserRole enum + application config** - `ae6905d` (feat)
2. **Task 2: JPA entities + Spring Data repositories** - `0331deb` (feat)

## Files Created/Modified
- `services/schedule-service/schedule-app/build.gradle.kts` - Added AOP starter, Testcontainers BOM and test deps
- `services/schedule-service/schedule-api-contract/.../enums/UserRole.java` - User roles for security layer
- `services/schedule-service/schedule-app/src/main/resources/application.yml` - Timezone and gRPC port config
- `docker-compose.yml` - TZ=Europe/Moscow for postgres-schedule
- `services/schedule-service/schedule-app/.../config/ClockConfig.java` - Clock bean with Moscow timezone
- `services/schedule-service/schedule-app/.../config/SchedulingConfig.java` - Profile-guarded @EnableScheduling
- `services/schedule-service/schedule-app/.../item/entity/ScheduleItem.java` - JPA entity for schedule_items
- `services/schedule-service/schedule-app/.../lesson/entity/Lesson.java` - JPA entity for lessons
- `services/schedule-service/schedule-app/.../item/repository/ScheduleItemRepository.java` - Spring Data repo
- `services/schedule-service/schedule-app/.../lesson/repository/LessonRepository.java` - Spring Data repo

## Decisions Made
- No gRPC starters added (deferred to Phase 14 per D-10) — only grpc.server.port placeholder in application.yml
- No @Convert annotations on entity fields — autoApply=true converters handle WeekType and LessonStatus automatically
- No @ManyToOne associations — FK columns stored as Long IDs per project convention
- No V2 implicit cast migration created — will be addressed in Plan 02 when Hibernate validation runs against real DB

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all entities are fully mapped with correct types and all repositories have query methods wired.

## Next Phase Readiness
- UserRole enum ready for @RequireRole AOP aspect in Plan 02
- Entities and repositories ready for service layer in Phase 11
- ClockConfig ready for time-comparison logic in Phase 12-13
- Testcontainers BOM ready for integration tests in Plan 02

---
*Phase: 10-foundation*
*Completed: 2026-04-01*
