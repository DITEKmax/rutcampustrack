---
phase: 07-grpc-server
plan: 01
subsystem: academic-service/grpc
tags: [grpc, protobuf, spring-boot, academic-service]
dependency_graph:
  requires: [05-entity-and-repository-foundation]
  provides: [grpc-server-academic, 7-rpcs-implemented]
  affects: [schedule-service, attendance-service, notification-service]
tech_stack:
  added:
    - com.google.protobuf plugin v0.9.4
    - net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE
    - net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE (test)
    - javax.annotation:javax.annotation-api:1.3.2 (compileOnly, generated stub compat)
  patterns:
    - Extend AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase
    - @GrpcService annotation
    - @GrpcAdvice + @GrpcExceptionHandler for centralized exception mapping
    - Repositories injected directly (NOT REST services) to avoid RequestContext scope
key_files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdvice.java
  modified:
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/resources/application.yml
decisions:
  - "Direct repository injection in gRPC service — avoids RequestContext scope issues that occur when injecting REST service layer"
  - "javax.annotation-api:1.3.2 compileOnly — generated gRPC stubs use @javax.annotation.Generated which was removed in Java 9+"
metrics:
  duration_minutes: 15
  completed_date: "2026-03-30"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 2
---

# Phase 07 Plan 01: gRPC Server Configuration and Implementation Summary

**One-liner:** gRPC server on port 19091 with protobuf plugin compiling academic.proto into Java stubs and all 7 RPCs implemented via direct repository queries.

## What Was Built

Configured the Gradle build to compile `proto/academic.proto` via the `com.google.protobuf` plugin (v0.9.4), wired `grpc-server-spring-boot-starter` as the server runtime, and implemented `AcademicGrpcServiceImpl` with all 7 RPCs. A centralized `GrpcExceptionAdvice` maps `ResourceNotFoundException` to gRPC `NOT_FOUND` status.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Configure Gradle protobuf plugin and gRPC dependencies | 392e4d0 | build.gradle.kts, application.yml |
| 2 | Implement AcademicGrpcServiceImpl and GrpcExceptionAdvice | 71d9f76 | AcademicGrpcServiceImpl.java, GrpcExceptionAdvice.java |

## RPC Implementations

| RPC | Method | Repository Query | Notes |
|-----|--------|-----------------|-------|
| GRPC-01 | getGroup | `groupRepository.findById` | Throws NOT_FOUND if missing |
| GRPC-02 | getGroupMembers | `userRepository.findByGroupId` | @SQLRestriction filters archived |
| GRPC-03 | getTeacherSubjects | `assignmentRepository.findByTeacherIdAndSemesterId` | Enriches with subject/group names, skips missing |
| GRPC-04 | isHeadman | `userRepository.findById` | Returns false (not NOT_FOUND) if user missing |
| GRPC-05 | getActiveSemester | `semesterRepository.findByIsActiveTrue` | Throws NOT_FOUND if no active semester |
| GRPC-06 | getCampusGeofence | `campusSettingRepository.findById(1L)` | Throws NOT_FOUND if not configured |
| GRPC-07 | getUserById | `userRepository.findByIdIncludingArchived` | Native query bypasses @SQLRestriction |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed javax.annotation.Generated missing for Java 21**
- **Found during:** Task 1 (first compilation attempt)
- **Issue:** Generated gRPC stubs (AcademicGrpcServiceGrpc.java, ScheduleGrpcServiceGrpc.java) reference `@javax.annotation.Generated` which was removed from the JDK in Java 9+. Compilation failed with "cannot find symbol: class Generated, location: package javax.annotation".
- **Fix:** Added `compileOnly("javax.annotation:javax.annotation-api:1.3.2")` dependency.
- **Files modified:** `services/academic-service/academic-app/build.gradle.kts`
- **Commit:** 392e4d0

**2. [Rule 1 - Bug] Fixed Kotlin DSL syntax for protobuf plugins block**
- **Found during:** Task 1 (first Gradle script compilation attempt)
- **Issue:** Plan used `id("grpc") {}` inside protobuf plugins block which is Groovy DSL syntax. Kotlin DSL requires `create("grpc") {}`.
- **Fix:** Changed `id("grpc")` to `create("grpc")` in both `plugins {}` and `generateProtoTasks` blocks.
- **Files modified:** `services/academic-service/academic-app/build.gradle.kts`
- **Commit:** 392e4d0

## Verification

- `.\gradlew.bat :services:academic-service:academic-app:compileJava` exits 0
- Generated stubs exist in `build/generated/source/proto/main/java/ru/rutcampustrack/academic/grpc/`
- `AcademicGrpcServiceGrpc.java` present in `build/generated/source/proto/main/grpc/`
- No REST service dependencies in gRPC implementation

## Known Stubs

None — all 7 RPCs are fully implemented with real repository queries.

## Self-Check: PASSED
