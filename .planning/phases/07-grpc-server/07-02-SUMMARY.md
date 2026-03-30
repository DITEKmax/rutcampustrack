---
phase: 07-grpc-server
plan: 02
subsystem: academic-service/grpc/test
tags: [grpc, testcontainers, integration-tests, academic-service]
dependency_graph:
  requires: [07-01]
  provides: [grpc-integration-tests-academic, 7-rpcs-verified]
  affects: []
tech_stack:
  added:
    - net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE (test, already in build.gradle.kts)
  patterns:
    - "@SpringBootTest(properties={grpc.server.in-process-name=...}) on subclass for isolated gRPC context"
    - "@GrpcClient(\"inProcess\") blocking stub injection in tests"
    - JdbcTemplate for inserting archived users that bypass @SQLRestriction
    - "@DirtiesContext(AFTER_CLASS) to isolate gRPC test context"
    - "grpc.server.port=-1 in application-test.yml prevents Netty port binding in all tests"
key_files:
  created:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java
  modified:
    - services/academic-service/academic-app/src/test/resources/application-test.yml
decisions:
  - "Use unique in-process name 'academic-grpc-test' (not 'test') to prevent name collision with other test contexts"
  - "Add grpc.server.port=-1 to application-test.yml so existing tests (EntityMapping, RestApi) don't bind port 19091"
  - "Override @SpringBootTest on test subclass with in-process properties (not in application-test.yml) to avoid starting in-process server for every test class"
  - "Use JdbcTemplate for archived user insertion — @SQLRestriction on User entity blocks JPA save for archived status"
metrics:
  duration_minutes: 14
  completed_date: "2026-03-30"
  tasks_completed: 2
  tasks_total: 2
  files_created: 1
  files_modified: 1
---

# Phase 07 Plan 02: gRPC Integration Tests Summary

**One-liner:** 15 Testcontainers-based integration tests verifying all 7 gRPC RPCs (GRPC-01 through GRPC-07) with positive cases, NOT_FOUND handling, and soft-delete filtering against real PostgreSQL.

## What Was Built

Created `AcademicGrpcIntegrationTest.java` extending `AbstractAcademicIntegrationTest` with 15 test methods covering all 7 RPCs. The test uses an in-process gRPC server (no TCP port binding) with `@SpringBootTest(properties=...)` annotation to isolate the gRPC context configuration from other test classes. Added `grpc.server.port=-1` to `application-test.yml` to globally disable Netty gRPC port binding in all test contexts.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Configure in-process gRPC server for tests | d3c9528 | application-test.yml |
| 2 | Implement AcademicGrpcIntegrationTest with all 7 RPCs | e26f082 | AcademicGrpcIntegrationTest.java, application-test.yml |

## Test Coverage

| RPC | Tests | Positive | Negative |
|-----|-------|----------|----------|
| GRPC-01 GetGroup | 2 | group info returned | NOT_FOUND on invalid ID |
| GRPC-02 GetGroupMembers | 2 | active students returned | archived user excluded |
| GRPC-03 GetTeacherSubjects | 2 | subjects+group info returned | empty list when no assignments |
| GRPC-04 IsHeadman | 3 | true for headman | false for non-headman, false for missing user |
| GRPC-05 GetActiveSemester | 2 | active semester returned | NOT_FOUND when none active |
| GRPC-06 GetCampusGeofence | 1 | lat/lng/radius returned | — |
| GRPC-07 GetUserById | 3 | user info returned | archived user still returned, NOT_FOUND on invalid ID |
| **Total** | **15** | | |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] gRPC port conflict between test contexts**
- **Found during:** Task 2 (full test suite run)
- **Issue:** Adding `grpc.server.in-process-name: test` with `port: -1` to `application-test.yml` caused all test classes to start an in-process server named "test". When multiple Spring contexts tried to start, the second registration of in-process name "test" failed with `IOException`. Additionally, EntityMappingIntegrationTest and RestApiIntegrationTest tried to bind Netty port 19091 (from `application.yml`) and failed due to port contention.
- **Fix (part 1):** Removed gRPC in-process config from `application-test.yml`. Instead, placed it directly on `AcademicGrpcIntegrationTest` via `@SpringBootTest(properties=...)`. Used unique name `academic-grpc-test` to prevent registration collision.
- **Fix (part 2):** Added `grpc.server.port: -1` to `application-test.yml` so all test classes disable Netty gRPC port binding — no test needs a real TCP gRPC server.
- **Files modified:** `application-test.yml`, `AcademicGrpcIntegrationTest.java`
- **Commit:** e26f082

## Verification

- `.\gradlew.bat :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest*"` passes — 15 tests, 0 failures
- `.\gradlew.bat :services:academic-service:academic-app:test` passes — 34 total tests, 0 failures

## Known Stubs

None — all 7 RPCs tested with real Testcontainers PostgreSQL data.

## Self-Check: PASSED
