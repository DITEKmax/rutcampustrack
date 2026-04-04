---
phase: 17-write-path-geo-checkin-manual-marking
plan: "01"
subsystem: attendance-service
tags: [contracts, redis, geofence, rate-limit, events, tdd]
dependency_graph:
  requires: []
  provides:
    - attendance-api-contract:CheckinApi
    - attendance-api-contract:MarkingApi
    - attendance-api-contract:CheckinRequest
    - attendance-api-contract:CheckinResponse
    - attendance-api-contract:MarkRequest
    - attendance-api-contract:MarkResponse
    - attendance-app:GeofenceService
    - attendance-app:GeoUtils
    - attendance-app:CheckinRateLimiter
    - attendance-app:AttendanceEventPublisher
    - attendance-app:GeofenceViolationException
    - attendance-app:GeofenceBlockedException
    - attendance-app:RateLimitException
  affects:
    - attendance-app:GlobalExceptionHandler (422/403-geo/429 added)
    - attendance-app:AbstractAttendanceIntegrationTest (Redis Testcontainer added)
tech_stack:
  added:
    - spring-boot-starter-data-redis (StringRedisTemplate, RedisAutoConfiguration)
  patterns:
    - SemesterCacheService volatile cache pattern (reused for GeofenceService)
    - Boolean.TRUE.equals for null-safe Redis setIfAbsent
    - expire only on count==1L to avoid TTL reset race condition
    - LinkedHashMap envelope for ordered JSON serialization
key_files:
  created:
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/checkin/CheckinRequest.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/checkin/CheckinResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/marking/MarkRequest.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/marking/MarkResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/CheckinApi.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/MarkingApi.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GeofenceViolationException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GeofenceBlockedException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/RateLimitException.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/geofence/GeoUtils.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/geofence/GeofenceService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/ratelimit/CheckinRateLimiter.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceEventPublisher.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/geofence/GeoUtilsTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/ratelimit/CheckinRateLimiterTest.java
  modified:
    - services/attendance-service/attendance-app/build.gradle.kts (added spring-boot-starter-data-redis)
    - services/attendance-service/attendance-app/src/main/resources/application.yml (added redis section)
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GlobalExceptionHandler.java (added 422/403-geo/429 handlers)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/AbstractAttendanceIntegrationTest.java (added Redis GenericContainer)
decisions:
  - "testcontainers:redis BOM module does not exist — Redis Testcontainer uses GenericContainer from core testcontainers (no separate module needed)"
  - "GeofenceService follows SemesterCacheService pattern exactly: volatile field, @PostConstruct with try/catch, 30m TTL"
  - "Boolean.TRUE.equals(set) used for Redis setIfAbsent null-safety on connection failure"
  - "expire called only on count==1L to prevent TTL reset race condition on concurrent increments"
metrics:
  duration: "7 minutes"
  completed_date: "2026-04-04"
  tasks: 3
  files: 15
---

# Phase 17 Plan 01: Contracts, Infrastructure, and Shared Services Summary

**One-liner:** Contract DTOs/APIs (CheckinRequest, MarkRequest), Redis dependency with GenericContainer testcontainer, Haversine geofence service with volatile cache, Redis dedup+rate-limit, and RabbitMQ attendance.marked event publisher.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Contract DTOs/APIs, Redis dep, exceptions, Redis Testcontainer | d47ff0a | 13 files (6 contract, 3 exception, build.gradle.kts, application.yml, GlobalExceptionHandler, AbstractAttendanceIntegrationTest) |
| 2 (RED) | Failing GeoUtilsTest | 4070904 | GeoUtilsTest.java |
| 2 (GREEN) | GeoUtils + GeofenceService | 3ed51ba | GeoUtils.java, GeofenceService.java |
| 3 (RED) | Failing CheckinRateLimiterTest | 61c1941 | CheckinRateLimiterTest.java |
| 3 (GREEN) | CheckinRateLimiter + AttendanceEventPublisher | 6dacf18 | CheckinRateLimiter.java, AttendanceEventPublisher.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] testcontainers:redis does not exist in the BOM**
- **Found during:** Task 1 (Gradle dependency resolution)
- **Issue:** Plan specified `testImplementation("org.testcontainers:redis")` but no such module exists in the testcontainers BOM 1.20.4. Redis testing uses `GenericContainer` from core `testcontainers` which is already included.
- **Fix:** Removed the `testcontainers:redis` dependency — `GenericContainer<>("redis:7.2")` from core testcontainers is sufficient.
- **Files modified:** `services/attendance-service/attendance-app/build.gradle.kts`
- **Impact:** None — AbstractAttendanceIntegrationTest uses `GenericContainer` which was already available.

## Test Results

- `GeoUtilsTest`: 5 tests, all passing
- `CheckinRateLimiterTest`: 6 tests, all passing
- Contract compilation: BUILD SUCCESSFUL

## Known Stubs

None — all implementations are complete functional code with no placeholders.

## Self-Check: PASSED
