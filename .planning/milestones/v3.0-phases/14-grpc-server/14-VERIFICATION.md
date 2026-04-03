---
phase: 14-grpc-server
verified: 2026-04-04T00:00:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 14: gRPC Server Verification Report

**Phase Goal:** Implement schedule.proto gRPC server — GetActiveLesson, GetLessonById, GetLessonsByGroup for Attendance Service consumption.
**Verified:** 2026-04-04
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from Plan 14-01 must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GetActiveLesson returns the active lesson for a group on a given date, ordered by lesson_number ASC LIMIT 1 | VERIFIED | `findActiveLessonForGroup` native query uses `ORDER BY si.lesson_number ASC LIMIT 1`; test `getActiveLesson_multipleActiveLessons_returnsFirstByLessonNumber` confirms D-02 ordering |
| 2 | GetActiveLesson returns gRPC NOT_FOUND when no active lesson exists for the group | VERIFIED | `orElseThrow(() -> new ResourceNotFoundException(...))` in `getActiveLesson`; `GrpcExceptionAdvice` maps `ResourceNotFoundException` to `Status.NOT_FOUND`; test `getActiveLesson_noActiveLessons_throwsResourceNotFoundException` passes |
| 3 | GetLessonById returns full lesson details enriched with ScheduleItem data | VERIFIED | `getLessonById` fetches Lesson then ScheduleItem, passes both to `buildResponse`; test `getLessonById_happyPath_returnsLessonResponse` asserts id, groupId, subjectId, teacherId, date, lessonNumber, status, room, isGeoBlocked |
| 4 | GetLessonById returns gRPC NOT_FOUND for non-existent lesson ID | VERIFIED | `findById(...).orElseThrow(ResourceNotFoundException)` present; test `getLessonById_nonExistentId_throwsResourceNotFoundException` passes |
| 5 | GetLessonsByGroup returns all lessons for a group in a date range via ScheduleItem JOIN | VERIFIED | `findByGroupIdAndSemesterIdAndIsActiveTrue` + `findByScheduleItemIdInAndDateBetweenAndStatusIn`; test `getLessonsByGroup_happyPath_returnsAllMatchingLessons` asserts 3 lessons returned |
| 6 | GetLessonsByGroup returns gRPC INVALID_ARGUMENT when date_from > date_to | VERIFIED | `if (from.isAfter(to)) throw new IllegalArgumentException("date_from must not be after date_to")`; `GrpcExceptionAdvice` maps `IllegalArgumentException` to `Status.INVALID_ARGUMENT`; test `getLessonsByGroup_dateFromAfterDateTo_throwsIllegalArgumentException` asserts exact message |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java` | gRPC service implementing 3 RPCs | VERIFIED | 132 lines; `@GrpcService`; extends `ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase`; all 3 RPC methods present with full implementation |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/GrpcExceptionAdvice.java` | gRPC exception-to-Status mapping | VERIFIED | 29 lines; `@GrpcAdvice`; 3 `@GrpcExceptionHandler` methods; imports `ru.rutcampustrack.schedule.exception.ResourceNotFoundException` (correct, not academic-service's) |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java` | findActiveLessonForGroup native query | VERIFIED | Query present at line 60-71; uses `l.status::text = 'active'` cast; `ORDER BY si.lesson_number ASC LIMIT 1`; returns `Optional<Lesson>` |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java` | Integration tests for all 3 gRPC RPCs | VERIFIED | 280 lines (above 100 minimum); 8 `@Test` methods; extends `AbstractScheduleIntegrationTest`; `@MockitoBean AcademicGrpcClient`; `@AfterEach` cleanup |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ScheduleGrpcServiceImpl` | `LessonRepository` | `lessonRepository.find*` | WIRED | `lessonRepository.findActiveLessonForGroup`, `lessonRepository.findById`, `lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn` all called |
| `ScheduleGrpcServiceImpl` | `ScheduleItemRepository` | `scheduleItemRepository.find*` | WIRED | `scheduleItemRepository.findById` and `scheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue` both called |
| `GrpcExceptionAdvice` | `ResourceNotFoundException` | `@GrpcExceptionHandler` | WIRED | Import `ru.rutcampustrack.schedule.exception.ResourceNotFoundException` confirmed on line 8; handler method at line 14 |
| `ScheduleGrpcServiceImplTest` | `ScheduleGrpcServiceImpl` | `@Autowired` + direct method call | WIRED | `@Autowired ScheduleGrpcServiceImpl grpcService`; `grpcService.getActiveLesson`, `grpcService.getLessonById`, `grpcService.getLessonsByGroup` called directly |
| `ScheduleGrpcServiceImplTest` | `AbstractScheduleIntegrationTest` | `extends` | WIRED | `class ScheduleGrpcServiceImplTest extends AbstractScheduleIntegrationTest` on line 38 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ScheduleGrpcServiceImpl.getActiveLesson` | `lesson` | `lessonRepository.findActiveLessonForGroup` native query — SELECT from `lessons` JOIN `schedule_items` | Yes — real DB query with group_id + date filter | FLOWING |
| `ScheduleGrpcServiceImpl.getLessonById` | `lesson` | `lessonRepository.findById` (JPA standard) | Yes — queries `lessons` table by PK | FLOWING |
| `ScheduleGrpcServiceImpl.getLessonsByGroup` | `lessons` | `lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn` native query | Yes — real DB query with itemIds, date range, statuses | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: Direct runtime execution not applicable — tests require Testcontainers PostgreSQL. Test results are attested by commits and SUMMARY self-checks. Skipped with reason: requires live database container.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| GRPC-01 | 14-01, 14-02 | GetActiveLesson returns the currently active lesson for a group | SATISFIED | `getActiveLesson` RPC in `ScheduleGrpcServiceImpl`; `findActiveLessonForGroup` query; 3 tests covering happy path, not-found (D-01), multi-overlap ordering (D-02) |
| GRPC-02 | 14-01, 14-02 | GetLessonById returns lesson details by ID | SATISFIED | `getLessonById` RPC in `ScheduleGrpcServiceImpl`; 2 tests covering happy path and not-found |
| GRPC-03 | 14-01, 14-02 | GetLessonsByGroup returns all lessons for a group in a date range | SATISFIED | `getLessonsByGroup` RPC in `ScheduleGrpcServiceImpl`; 3 tests covering happy path, invalid date range (D-04), empty result |

**Orphaned requirements check:** REQUIREMENTS.md traceability table maps GRPC-01, GRPC-02, GRPC-03 to Phase 14 — all three are claimed by plans 14-01 and 14-02. No orphaned requirements.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | No anti-patterns found |

Scanned: `ScheduleGrpcServiceImpl.java`, `GrpcExceptionAdvice.java`, `LessonRepository.java` for TODO/FIXME/placeholder/stub patterns. Zero hits.

---

### Human Verification Required

#### 1. End-to-End gRPC Call from Attendance Service

**Test:** Start schedule-service (docker compose up + `./gradlew :services:schedule-service:schedule-app:bootRun`) and invoke GetActiveLesson from a gRPC client (e.g., grpcurl) targeting port 19092.
**Expected:** Server responds with a `LessonResponse` proto message; no `UNIMPLEMENTED` or `INTERNAL` error.
**Why human:** Requires live infrastructure (PostgreSQL, gRPC port binding). Cannot verify with grep — tests use direct method invocation, not an actual gRPC channel.

#### 2. gRPC Server Port Registration

**Test:** After bootRun, confirm the server listens on port 19092 (`netstat -an | grep 19092` or equivalent).
**Expected:** Port 19092 open and LISTENING.
**Why human:** Port binding requires running JVM. The `grpc.server.port: 19092` config is present and `grpc-server-spring-boot-starter:3.1.0.RELEASE` is in build.gradle.kts, but actual socket binding cannot be verified statically.

---

### Gaps Summary

No gaps. All 6 observable truths verified. All 4 artifacts exist, are substantive, and are wired. All 3 requirements (GRPC-01, GRPC-02, GRPC-03) satisfied. Build dependency and configuration verified (grpc-server-spring-boot-starter:3.1.0.RELEASE in build.gradle.kts; `grpc.server.port: 19092` in application.yml; `grpc.server.port: -1` in application-test.yml).

---

_Verified: 2026-04-04_
_Verifier: Claude (gsd-verifier)_
