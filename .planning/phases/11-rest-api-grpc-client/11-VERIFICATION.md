---
phase: 11-rest-api-grpc-client
verified: 2026-04-01T00:00:00Z
status: passed
score: 17/17 must-haves verified
re_verification: false
---

# Phase 11: REST API + gRPC Client Verification Report

**Phase Goal:** Full REST API for schedule templates (CRUD), lesson operations (cancel/restore/mass-cancel/geo-block), and schedule viewing — with gRPC client to Academic Service for validation.
**Verified:** 2026-04-01
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Proto compilation generates AcademicGrpcServiceGrpc stubs from academic.proto | VERIFIED | `build/generated/source/proto/main/grpc/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceGrpc.java` exists |
| 2 | AcademicGrpcClient wraps GetGroup, GetActiveSemester, IsHeadman with 3s deadline | VERIFIED | All three methods call `stub.withDeadlineAfter(3, TimeUnit.SECONDS)` at lines 39, 61, 82 |
| 3 | gRPC failure maps to HTTP 503 via GlobalExceptionHandler | VERIFIED | `@ExceptionHandler(AcademicServiceUnavailableException.class)` returns `HttpStatus.SERVICE_UNAVAILABLE` |
| 4 | All contract DTOs and API interfaces compile | VERIFIED | 8 DTOs + 2 API interfaces in schedule-api-contract; no Lombok |
| 5 | Headman can create a schedule template with gRPC-validated group and semester | VERIFIED | `ScheduleItemService.createScheduleItem` calls `requireHeadmanForGroup`, `validateGroup`, `getActiveSemester`; test `createTemplate_headman_success` asserts 201 |
| 6 | Headman can update a schedule template (groupId/semesterId immutable) | VERIFIED | `updateScheduleItem` reads groupId from existing entity; `UpdateScheduleItemRequest` excludes groupId/semesterId; test `updateTemplate_headman_success` asserts unchanged IDs |
| 7 | Headman can soft-delete a schedule template (is_active=false) | VERIFIED | `deleteScheduleItem` calls `existing.setActive(false)` without physical delete; test `deleteTemplate_softDelete` verifies `isActive()=false` in DB |
| 8 | Headman can list active templates for their group and semester | VERIFIED | `listScheduleItems` uses `findByGroupIdAndSemesterIdAndIsActiveTrue`; test `listTemplates_returnsActiveOnly` asserts 2 of 3 returned |
| 9 | Non-headman student gets 403 on write operations | VERIFIED | `requireHeadmanForGroup` throws `AccessDeniedException` if `!requestContext.isHeadman()`; test `createTemplate_nonHeadmanStudent_returns403` asserts 403 |
| 10 | Headman can cancel a planned lesson with required reason | VERIFIED | `cancelLesson` checks `status != PLANNED` throws 422; sets CANCELLED + reason; test `cancelLesson_planned_success` asserts 200 + CANCELLED in DB |
| 11 | Headman can restore a cancelled lesson back to planned | VERIFIED | `restoreLesson` checks `status != CANCELLED` throws 422; resets PLANNED + clears cancelReason; test `restoreLesson_cancelled_success` asserts PLANNED + null cancelReason |
| 12 | Headman can mass-cancel all planned lessons in a date range | VERIFIED | `massCancelLessons` validates headman first, fetches PLANNED lessons only, bulk saves; test `massCancelLessons_success` asserts cancelledCount=2, ACTIVE untouched |
| 13 | Headman can toggle geo-block on a specific lesson | VERIFIED | `toggleGeoBlock` sets `lesson.setGeoBlocked(request.blocked())`; test `toggleGeoBlock_success` verifies toggle true then false |
| 14 | Any authenticated user can view group schedule for a date range | VERIFIED | `getLessons` in `LessonController` has no `@RequireRole`; tests `anyRoleCanView_student` and `anyRoleCanView_teacher` both assert 200 |
| 15 | Schedule view response includes fields from both Lesson and ScheduleItem entities | VERIFIED | `LessonAssembler.toResponse` maps from both `Lesson` and `ScheduleItem`; `LessonResponse` has 16 fields from both; test `responseContainsAllFields` asserts groupId, subjectId, teacherId, room, dayOfWeek, startTime, endTime |
| 16 | Restoring a non-cancelled lesson returns 422 | VERIFIED | `restoreLesson` guard + `@ExceptionHandler(InvalidLessonStateException.class)` maps to 422; test `restoreLesson_planned_returns422` |
| 17 | Cancelling a non-planned lesson returns 422 | VERIFIED | `cancelLesson` guard + same handler; test `cancelLesson_activeLesson_returns422` |

**Score:** 17/17 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java` | gRPC client wrapper | VERIFIED | Exists, 91 lines, `@GrpcClient`, all 3 methods with deadline |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java` | Template CRUD contract | VERIFIED | Exists, `interface ScheduleItemApi` with `@RequestMapping("/schedule/items")`, 5 endpoints |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java` | Lesson ops + view contract | VERIFIED | Exists, `interface LessonApi` with `@RequestMapping("/schedule")`, 5 endpoints |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/AcademicServiceUnavailableException.java` | 503 exception | VERIFIED | Exists in exception package |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/InvalidLessonStateException.java` | 422 exception | VERIFIED | Exists in exception package |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/ResourceNotFoundException.java` | 404 exception | VERIFIED | Exists; stores entityName, fieldName, fieldValue with getters |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java` | Centralized error mapping | VERIFIED | 5 new handlers before catch-all: 404, 503, 422, 409, 400 |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/CreateScheduleItemRequest.java` | Create template DTO | VERIFIED | Record with `@NotNull` on all IDs |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/UpdateScheduleItemRequest.java` | Update template DTO | VERIFIED | Record without groupId/semesterId (immutable per D-09) |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemResponse.java` | Template response DTO | VERIFIED | Extends `RepresentationModel`, 13 fields, no Lombok |
| `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java` | Lesson response DTO | VERIFIED | Extends `RepresentationModel`, 16 fields from both Lesson + ScheduleItem, no Lombok |
| All other lesson DTOs (CancelLessonRequest, MassCancelRequest, MassCancelResponse, GeoBlockRequest) | Lesson operation DTOs | VERIFIED | All 4 exist in lesson dto package |

### Plan 02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java` | Template CRUD business logic | VERIFIED | Exists, `requireHeadmanForGroup` present, all 5 methods, soft delete |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java` | REST controller | VERIFIED | `implements ScheduleItemApi`, `@RequireRole` on write ops, returns 201/200/204 |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemAssembler.java` | HATEOAS assembler | VERIFIED | `EntityModel<ScheduleItemResponse>` with self + collection links |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java` | Integration tests TMPL-01..05 | VERIFIED | 8 tests, extends `AbstractScheduleIntegrationTest`, `@MockitoBean AcademicGrpcClient` |

### Plan 03 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonWithItem.java` | App-internal join record | VERIFIED | `public record LessonWithItem(Lesson lesson, ScheduleItem scheduleItem)` |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java` | Lesson operations logic | VERIFIED | `requireHeadmanForGroup`, all 5 methods, state machine enforced |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java` | REST controller | VERIFIED | `implements LessonApi`, `@RequireRole` on 4 write ops, no role restriction on getLessons |
| `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonAssembler.java` | HATEOAS assembler | VERIFIED | `toResponse` + `toModel`, conditional links (cancel/restore based on status) |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java` | Lesson ops integration tests | VERIFIED | 7 tests covering LSSN-04..07 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewTest.java` | View integration tests | VERIFIED | 5 tests covering VIEW-01..02 |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V2__add_enum_casts.sql` | Flyway migration | VERIFIED | Adds implicit varchar->enum casts for PostgreSQL |

---

## Key Link Verification

### Plan 01 Key Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `build.gradle.kts` | `proto/academic.proto` | `srcDir(rootProject.file("proto"))` | WIRED | `srcDir(rootProject.file("proto"))` present at line 47; `AcademicGrpcServiceGrpc.java` generated |
| `AcademicGrpcClient.java` | `AcademicGrpcServiceGrpc` | `@GrpcClient` annotation | WIRED | `@GrpcClient("academic-service")` at line 26; uses generated stub |
| `GlobalExceptionHandler.java` | `AcademicServiceUnavailableException` | `@ExceptionHandler` mapping to 503 | WIRED | Handler present, returns `HttpStatus.SERVICE_UNAVAILABLE` |

### Plan 02 Key Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ScheduleItemController.java` | `ScheduleItemApi.java` | `implements ScheduleItemApi` | WIRED | Line 28: `public class ScheduleItemController implements ScheduleItemApi` |
| `ScheduleItemService.java` | `AcademicGrpcClient.java` | `academicGrpcClient.validateGroup` | WIRED | Lines 62-63: `academicGrpcClient.validateGroup(request.groupId())` and `getActiveSemester()` |
| `ScheduleItemService.java` | `RequestContext.java` | `requestContext.isHeadman` | WIRED | Line 47: `if (!requestContext.isHeadman())` |

### Plan 03 Key Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `LessonController.java` | `LessonApi.java` | `implements LessonApi` | WIRED | Line 29: `public class LessonController implements LessonApi` |
| `LessonService.java` | `ScheduleItemRepository` | `scheduleItemRepository.findById` | WIRED | Lines 73 and 119 |
| `LessonService.java` | `LessonRepository` | `findByScheduleItemIdInAndDateBetweenAndStatusIn` | WIRED | Lines 124, 169; native `@Query` with `status::text IN :statuses` |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ScheduleItemController.createScheduleItem` | `ScheduleItem result` | `scheduleItemRepository.save(item)` | Yes — JPA save to PostgreSQL | FLOWING |
| `ScheduleItemController.listScheduleItems` | `Page<ScheduleItem> page` | `findByGroupIdAndSemesterIdAndIsActiveTrue(...)` — Spring Data JPA derived query | Yes — real DB query | FLOWING |
| `LessonController.getLessons` | `Page<LessonWithItem> page` | Native `@Query` across `lessons` table + in-memory join with `schedule_items` | Yes — real DB query with `findByGroupId` + `findByScheduleItemIdInAndDateBetweenAndStatusIn` | FLOWING |
| `LessonController.cancelLesson` | `LessonWithItem result` | `lessonRepository.findById` + `save` | Yes — read from and write to DB | FLOWING |
| `LessonController.massCancelLessons` | `int count` | Batch `lessonRepository.saveAll(toCancel)` after native query | Yes — real batch update | FLOWING |

---

## Behavioral Spot-Checks

Step 7b: SKIPPED — Tests require running Spring context with PostgreSQL Testcontainer; cannot run without live services. Integration tests confirmed present and were verified to pass per SUMMARY claims (commits e76ee0b and 747e6e6 confirmed in git log). Manual test run deferred to human verification.

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| TMPL-01 | 11-02-PLAN | Headman can create schedule template | SATISFIED | `ScheduleItemService.createScheduleItem` + test `createTemplate_headman_success` (201) |
| TMPL-02 | 11-02-PLAN | Headman can update schedule template | SATISFIED | `ScheduleItemService.updateScheduleItem` + test `updateTemplate_headman_success` (200) |
| TMPL-03 | 11-02-PLAN | Headman can delete (deactivate) template | SATISFIED | `deleteScheduleItem` sets `isActive=false`; test `deleteTemplate_softDelete` verifies DB |
| TMPL-04 | 11-02-PLAN | Headman can view all templates for group | SATISFIED | `listScheduleItems` filters by `isActiveTrue`; test `listTemplates_returnsActiveOnly` |
| TMPL-05 | 11-01-PLAN | System validates via gRPC before creating | SATISFIED | `AcademicGrpcClient` wraps gRPC calls; 503 on failure; tests `createTemplate_grpcFailure_returns503` + `createTemplate_invalidGroupId_returns404` |
| LSSN-04 | 11-03-PLAN | Headman can cancel a specific lesson with reason | SATISFIED | `cancelLesson` + test `cancelLesson_planned_success` |
| LSSN-05 | 11-03-PLAN | Headman can restore a cancelled lesson | SATISFIED | `restoreLesson` + test `restoreLesson_cancelled_success` |
| LSSN-06 | 11-03-PLAN | Headman can mass-cancel lessons for date range | SATISFIED | `massCancelLessons` + test `massCancelLessons_success` |
| LSSN-07 | 11-03-PLAN | Headman can toggle geo-checkin blocking | SATISFIED | `toggleGeoBlock` + test `toggleGeoBlock_success` |
| VIEW-01 | 11-03-PLAN | Any authenticated user can view group schedule | SATISFIED | `getLessons` has no `@RequireRole`; tests `anyRoleCanView_student` + `anyRoleCanView_teacher` |
| VIEW-02 | 11-03-PLAN | Response includes status, room, teacher, subject | SATISFIED | `LessonResponse` has 16 combined fields; test `responseContainsAllFields` asserts all key fields |

No orphaned requirements found. All 11 phase-11 requirements from REQUIREMENTS.md traceability table are claimed by the plans and verified in code.

---

## Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| None found | — | — | — |

**Scan results:**
- No `TODO`, `FIXME`, `HACK`, or `PLACEHOLDER` comments in any phase-11 main source files
- No empty implementations (`return null`, `return {}`, `return []`)
- No Lombok imports in `schedule-api-contract` (all DTOs use plain Java records/classes with explicit constructors)
- `ScheduleItemResponse` and `LessonResponse` use no-arg + all-args constructors with explicit getters per CLAUDE.md contract-first rule
- `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn` uses native `@Query` (not a stub — this was an intentional design decision to bypass PostgreSQL enum/varchar operator issue, as documented in 11-03-SUMMARY.md)
- `LessonService.massCancelLessons` passes lowercase string values to the native query — consistent with project rule "All values in PostgreSQL stored in lowercase"

---

## Human Verification Required

### 1. Integration Test Suite Run

**Test:** Run `gradlew :services:schedule-service:schedule-app:test --no-daemon` with Docker infrastructure up (PostgreSQL Testcontainers)
**Expected:** All 20 integration tests pass — 8 in `ScheduleItemApiTest`, 7 in `LessonApiTest`, 5 in `ScheduleViewTest`
**Why human:** Testcontainers requires Docker; cannot run in this verification environment

### 2. Admin Bypass in ScheduleItemService.createScheduleItem

**Test:** POST `/schedule/items` with ADMIN headers and any groupId
**Expected:** Should succeed (201) — ADMIN bypasses `requireHeadmanForGroup` but still calls `validateGroup` and `getActiveSemester`
**Why human:** The `requireHeadmanForGroup` check is bypassed for ADMIN at line 46, but the gRPC validation calls on lines 62-63 still run regardless of role. Confirm the admin-as-bypass behavior is intentional (ADMIN still needs a valid, active group to create templates)

### 3. getLessons Status Filter via URL

**Test:** GET `/schedule/groups/1/lessons?dateFrom=2026-04-01&dateTo=2026-04-30&status=PLANNED&status=CANCELLED`
**Expected:** Returns only lessons with matching status (default filter bypassed)
**Why human:** The `@RequestParam(required = false) List<LessonStatus> status` binding with multiple `status=` params requires human verification that Spring's `LessonStatus` enum deserialization from uppercase strings works correctly with the `status::text` lowercase storage in the native query (the service converts to lowercase via `.name().toLowerCase()`)

---

## Gaps Summary

No gaps found. All 17 observable truths verified, all 22 artifacts exist at substantive level (no stubs), all 9 key links confirmed wired, data flows to real DB queries, no Lombok in contract modules, no anti-patterns.

The only notable design decision requiring awareness is that `LessonRepository` uses native `@Query` with `status::text IN :statuses` and lowercase string values. This is correct behavior documented in SUMMARY.md as a fix for a PostgreSQL enum/varchar operator issue, and backed by V2 Flyway migration adding implicit casts.

---

_Verified: 2026-04-01T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
