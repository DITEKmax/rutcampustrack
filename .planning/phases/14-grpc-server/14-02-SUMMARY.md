---
phase: 14-grpc-server
plan: 02
subsystem: schedule-service
tags: [grpc, tests, integration, schedule, testcontainers]
dependency_graph:
  requires: [14-01]
  provides: [GRPC-01, GRPC-02, GRPC-03]
  affects: []
tech_stack:
  added: []
  patterns: [direct-grpc-method-invocation, mock-StreamObserver, MockitoBean-AcademicGrpcClient, AfterEach-cleanup]
key_files:
  created:
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java
  modified: []
decisions:
  - "Test via direct method invocation with mock StreamObserver — no in-process gRPC channel needed per D-06"
  - "@MockitoBean AcademicGrpcClient mandatory — prevents Spring context startup failure from outbound gRPC connection to academic-service"
metrics:
  duration: 5m
  completed_date: "2026-04-04"
  tasks_completed: 1
  files_modified: 1
---

# Phase 14 Plan 02: gRPC Integration Tests Summary

**One-liner:** 8 integration tests covering GetActiveLesson/GetLessonById/GetLessonsByGroup RPCs with Testcontainers PostgreSQL, validating all happy-path and error-scenario behaviors.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Integration tests for all 3 gRPC RPCs | 4d739d8 | ScheduleGrpcServiceImplTest.java |

## What Was Built

### ScheduleGrpcServiceImplTest

8 integration tests extending `AbstractScheduleIntegrationTest` (Testcontainers PostgreSQL 16):

**GRPC-01 GetActiveLesson (3 tests):**
- `getActiveLesson_happyPath_returnsLessonResponse` — Creates ScheduleItem + ACTIVE Lesson, asserts LessonResponse with correct id, groupId, status, lessonNumber, room
- `getActiveLesson_noActiveLessons_throwsResourceNotFoundException` — Empty DB, asserts `ResourceNotFoundException`
- `getActiveLesson_multipleActiveLessons_returnsFirstByLessonNumber` — Two ACTIVE lessons for same group (lessonNumber=1 and lessonNumber=2), asserts first is returned (D-02)

**GRPC-02 GetLessonById (2 tests):**
- `getLessonById_happyPath_returnsLessonResponse` — Full field mapping assertion (id, groupId, subjectId, teacherId, date, lessonNumber, status, room, isGeoBlocked)
- `getLessonById_nonExistentId_throwsResourceNotFoundException` — Non-existent ID asserts `ResourceNotFoundException`

**GRPC-03 GetLessonsByGroup (3 tests):**
- `getLessonsByGroup_happyPath_returnsAllMatchingLessons` — 3 lessons in date range, asserts `getLessonsList().size() == 3`
- `getLessonsByGroup_dateFromAfterDateTo_throwsIllegalArgumentException` — Invalid date range asserts `IllegalArgumentException` with message "date_from must not be after date_to" (D-04)
- `getLessonsByGroup_noScheduleItems_returnsEmptyResponse` — Group with no schedule items asserts empty LessonsResponse

**Test class structure:**
- Extends `AbstractScheduleIntegrationTest` (Testcontainers PostgreSQL, `@ActiveProfiles("test")`, `@MockitoBean RabbitTemplate`)
- `@MockitoBean AcademicGrpcClient` prevents outbound gRPC to academic-service port 19091
- `@AfterEach cleanup()` calls `lessonRepository.deleteAll()` + `scheduleItemRepository.deleteAll()` (no `@Transactional`)
- Helper methods `createScheduleItem()` and `createLesson()` consistent with `LessonStatusTransitionJobTest` pattern

## Key Design Decisions

1. **Direct method invocation** — Call `grpcService.getActiveLesson(request, mockObserver)` directly instead of spinning up an in-process gRPC channel. Simpler, faster, avoids Netty port binding in tests.

2. **@MockitoBean AcademicGrpcClient** — `AcademicGrpcClient` is wired into the Spring context via grpc-client-spring-boot-starter, which tries to connect to academic-service (port 19091) on startup. Without the mock, the context fails with `StatusRuntimeException: UNAVAILABLE`.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all 8 tests are fully implemented with real assertions.

## Self-Check: PASSED

Files exist:
- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java — FOUND

Commits exist:
- 4d739d8 — FOUND

Build: All 8 tests green, full suite BUILD SUCCESSFUL verified.
