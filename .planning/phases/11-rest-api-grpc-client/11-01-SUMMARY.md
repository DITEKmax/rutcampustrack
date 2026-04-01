---
phase: 11-rest-api-grpc-client
plan: 01
subsystem: schedule-service
tags: [grpc-client, contract-first, hateoas, exception-handling, protobuf]
dependency_graph:
  requires: [phase-10-foundation]
  provides: [schedule-api-contract, AcademicGrpcClient, exception-infrastructure]
  affects: [11-02-PLAN, 11-03-PLAN]
tech_stack:
  added:
    - grpc-client-spring-boot-starter:3.1.0.RELEASE
    - com.google.protobuf plugin 0.9.4
    - protoc 3.25.3 + protoc-gen-grpc-java:1.63.0
    - javax.annotation-api:1.3.2 (compileOnly)
    - spring-data-commons:3.4.1 (added to schedule-api-contract)
  patterns:
    - gRPC client with 3s deadline on all calls
    - StatusRuntimeException translated to domain exceptions
    - RFC 7807 Problem Details for all error cases
    - Contract-first: HTTP mappings only in api-contract interfaces
    - HATEOAS RepresentationModel for all response DTOs
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/AcademicServiceUnavailableException.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/InvalidLessonStateException.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/ResourceNotFoundException.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/CreateScheduleItemRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/UpdateScheduleItemRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemResponse.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/CancelLessonRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelResponse.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/GeoBlockRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java
  modified:
    - services/schedule-service/schedule-app/build.gradle.kts
    - services/schedule-service/schedule-app/src/main/resources/application.yml
    - services/schedule-service/schedule-app/src/test/resources/application-test.yml
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
    - services/schedule-service/schedule-api-contract/build.gradle.kts
decisions:
  - "spring-data-commons added to schedule-api-contract for Pageable/PagedResourcesAssembler (was missing, blocked compilation)"
  - "grpc.client.academic-service.address=static://academic-service:19091 in application.yml"
  - "grpc.client.academic-service in application-test.yml points to localhost to prevent context load failure"
  - "LessonResponse combines fields from both Lesson + ScheduleItem for VIEW-02 endpoint"
  - "UpdateScheduleItemRequest excludes groupId and semesterId (immutable after creation, D-09)"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-01"
  tasks_completed: 2
  files_changed: 21
---

# Phase 11 Plan 01: gRPC Client Infrastructure + Contract Foundation Summary

**One-liner:** Protobuf plugin generates AcademicGrpcServiceGrpc stubs; AcademicGrpcClient wraps GetGroup/GetActiveSemester/IsHeadman with 3s deadline and domain exception translation; all schedule-service contract DTOs and API interfaces compiled.

## What Was Built

### Task 1: Gradle + Config

- Added `com.google.protobuf 0.9.4` plugin to `schedule-app/build.gradle.kts`
- Added `grpc-client-spring-boot-starter:3.1.0.RELEASE` and `javax.annotation-api:1.3.2 (compileOnly)` dependencies
- Added `sourceSets.main.proto` pointing to root `proto/` directory
- Added `protobuf` block with `protoc:3.25.3` and `protoc-gen-grpc-java:1.63.0`
- Extended `application.yml` with `grpc.client.academic-service` config and `spring.data.web.pageable` defaults
- Extended `application-test.yml` with matching gRPC client config to prevent test context load failure

### Task 2: Java Infrastructure

**AcademicGrpcClient** (`ru.rutcampustrack.schedule.grpc`):
- `validateGroup(Long)` — calls GetGroup with 3s deadline, throws `ResourceNotFoundException` if group inactive
- `getActiveSemester()` — calls GetActiveSemester, maps NOT_FOUND to `ResourceNotFoundException`
- `isHeadman(Long, Long)` — calls IsHeadman, returns boolean
- All methods catch `StatusRuntimeException` and throw `AcademicServiceUnavailableException`

**Exception Classes** (`ru.rutcampustrack.schedule.exception`):
- `AcademicServiceUnavailableException` → HTTP 503
- `InvalidLessonStateException` → HTTP 422
- `ResourceNotFoundException` → HTTP 404 (stores entityName, fieldName, fieldValue)

**GlobalExceptionHandler** — added 5 handlers before catch-all:
- `ResourceNotFoundException` → 404
- `AcademicServiceUnavailableException` → 503
- `InvalidLessonStateException` → 422
- `DataIntegrityViolationException` → 409
- `MethodArgumentNotValidException` → 400 with field errors

**Contract DTOs** (all without Lombok):
- `CreateScheduleItemRequest` — record with @NotNull on groupId/subjectId/teacherId/semesterId
- `UpdateScheduleItemRequest` — record excluding groupId/semesterId (immutable)
- `ScheduleItemResponse` — extends RepresentationModel with 13 fields
- `CancelLessonRequest` — record with @NotBlank reason
- `MassCancelRequest` — record with groupId, dateFrom, dateTo, reason
- `MassCancelResponse` — record with cancelledCount
- `GeoBlockRequest` — record with @NotNull blocked boolean
- `LessonResponse` — extends RepresentationModel combining Lesson + ScheduleItem fields (16 fields)

**API Interfaces** (HTTP mappings only in contract):
- `ScheduleItemApi` — 5 endpoints: POST, GET/{id}, GET (list), PUT/{id}, DELETE/{id}
- `LessonApi` — 5 endpoints: cancel, restore, mass-cancel, geo-block, getLessons

**Repository Extensions**:
- `ScheduleItemRepository.findByGroupId(Long)` for VIEW endpoint
- `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(...)` for VIEW and mass-cancel

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added spring-data-commons to schedule-api-contract**
- **Found during:** Task 2 (contract compile)
- **Issue:** `Pageable` and `PagedResourcesAssembler` are from `spring-data-commons` which was missing from `schedule-api-contract/build.gradle.kts`. The academic-api-contract already had it but schedule-api-contract did not.
- **Fix:** Added `api("org.springframework.data:spring-data-commons:3.4.1")` to schedule-api-contract dependencies
- **Files modified:** `services/schedule-service/schedule-api-contract/build.gradle.kts`
- **Commit:** b61784d

## Verification

Both modules compile with zero errors:
- `gradlew :services:schedule-service:schedule-api-contract:compileJava` — PASSED
- `gradlew :services:schedule-service:schedule-app:compileJava` — PASSED
- Proto stubs generated at `schedule-app/build/generated/source/proto/main/grpc/` and `java/`
- `AcademicGrpcServiceGrpc` class generated from `academic.proto`

## Known Stubs

None. This plan creates infrastructure only (client wrapper, DTOs, interfaces, exceptions) — no data flows to UI rendering. Controllers and services are implemented in Plans 02 and 03.

## Self-Check: PASSED

Files confirmed present:
- AcademicGrpcClient.java: FOUND
- ScheduleItemApi.java: FOUND
- LessonApi.java: FOUND
- build.gradle.kts with protobuf: FOUND

Commits confirmed:
- 618bcfc (chore: build/config): FOUND
- b61784d (feat: Java infrastructure): FOUND
