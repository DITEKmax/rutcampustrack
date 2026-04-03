---
phase: 14-grpc-server
plan: 01
subsystem: schedule-service
tags: [grpc, schedule, server, rpc]
dependency_graph:
  requires: []
  provides: [GRPC-01, GRPC-02, GRPC-03]
  affects: [attendance-service]
tech_stack:
  added: [net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE]
  patterns: [GrpcService, GrpcAdvice, GrpcExceptionHandler, native-query-status-cast]
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/GrpcExceptionAdvice.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java
  modified:
    - services/schedule-service/schedule-app/build.gradle.kts
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
decisions:
  - "gRPC queries repositories directly without caching — Attendance Service calls are real-time sensitive and infrequent"
  - "status::text cast in findActiveLessonForGroup native query follows repo-wide pattern to avoid PostgreSQL enum operator error"
metrics:
  duration: 4m
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_modified: 4
---

# Phase 14 Plan 01: gRPC Server Implementation Summary

**One-liner:** gRPC server with GetActiveLesson/GetLessonById/GetLessonsByGroup RPCs exposing schedule data via grpc-server-spring-boot-starter:3.1.0.RELEASE.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add gRPC server dependency + findActiveLessonForGroup query | 00b6259 | build.gradle.kts, LessonRepository.java |
| 2 | Create GrpcExceptionAdvice + ScheduleGrpcServiceImpl (3 RPCs) | 9ca3f05 | GrpcExceptionAdvice.java, ScheduleGrpcServiceImpl.java |

## What Was Built

### GrpcExceptionAdvice
Maps domain exceptions to gRPC statuses:
- `ResourceNotFoundException` → `NOT_FOUND`
- `IllegalArgumentException` → `INVALID_ARGUMENT`
- `Exception` → `INTERNAL` (with logging)

### ScheduleGrpcServiceImpl
Three RPCs extending `ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase`:

**getActiveLesson (GRPC-01):** Parses ISO-8601 timestamp to LocalDate, calls `findActiveLessonForGroup`, fetches ScheduleItem, builds LessonResponse. Returns NOT_FOUND if no active lesson for the group on that date.

**getLessonById (GRPC-02):** Fetches lesson by ID, fetches ScheduleItem, builds LessonResponse. Returns NOT_FOUND if lesson missing.

**getLessonsByGroup (GRPC-03):** Validates date range (INVALID_ARGUMENT if from > to), fetches schedule items by group+semester, fetches lessons filtered by statuses, builds list response. Returns empty response if no schedule items.

### LessonRepository
New `findActiveLessonForGroup` native query:
- `status::text = 'active'` cast — avoids PostgreSQL enum operator error
- JOIN with schedule_items on group_id filter
- `ORDER BY si.lesson_number ASC LIMIT 1` — first lesson per D-02

### build.gradle.kts
Added `grpc-server-spring-boot-starter:3.1.0.RELEASE` matching existing client starter version.

## Key Design Decisions

1. **Direct repository access** — no intermediate service layer, no caching. gRPC server queries repos directly, consistent with academic-service pattern (PROJECT.md Key Decisions).

2. **status::text cast pattern** — all native queries in this repo use this pattern to avoid `operator does not exist: lesson_status = text` PostgreSQL error.

3. **Null-safe proto building** — `item.getRoom() != null ? item.getRoom() : ""` because proto3 setters reject null.

4. **Enum case conversion** — `lesson.getStatus().name().toLowerCase()` converts Java UPPER_CASE enum to proto lowercase string field.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all three RPCs are fully implemented with real repository queries.

## Self-Check: PASSED

Files exist:
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/GrpcExceptionAdvice.java — FOUND
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java — FOUND
- services/schedule-service/schedule-app/build.gradle.kts — FOUND (contains grpc-server-spring-boot-starter)
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java — FOUND (contains findActiveLessonForGroup)

Commits exist:
- 00b6259 — FOUND
- 9ca3f05 — FOUND

Build: `compileJava` succeeds (BUILD SUCCESSFUL verified).
