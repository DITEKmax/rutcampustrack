---
phase: 11-rest-api-grpc-client
plan: 02
subsystem: schedule-service
tags: [schedule-item, hateoas, headman-authorization, grpc-validation, integration-tests, soft-delete]
dependency_graph:
  requires: [11-01]
  provides: [ScheduleItemService, ScheduleItemController, ScheduleItemAssembler, ScheduleItemApiTest]
  affects: [11-03-PLAN]
tech_stack:
  added: []
  patterns:
    - requireHeadmanForGroup helper: ADMIN bypasses, student must have isHeadman=true AND gRPC confirmation
    - Contract-first controller: implements ScheduleItemApi with no @RequestMapping on class
    - HATEOAS EntityModel with self + collection links from ScheduleItemAssembler
    - PagedModel via PagedResourcesAssembler parameter from contract interface method
    - Soft delete: setActive(false) not physical DELETE
    - groupId/semesterId immutable after creation (D-09): updateScheduleItem reads groupId from existing entity
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemAssembler.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java
decisions:
  - "PagedResourcesAssembler passed as method parameter (from contract interface) instead of injected field — avoids generic type mismatch"
  - "lessonNumberCounter instance field in test ensures unique (group, day, lesson, weekType, semester) combinations to avoid UNIQUE constraint violations"
metrics:
  duration_minutes: 35
  tasks_completed: 2
  files_created: 4
  files_modified: 1
  tests_added: 8
  completed_date: "2026-04-02"
---

# Phase 11 Plan 02: ScheduleItem CRUD Summary

ScheduleItem REST CRUD with headman authorization, gRPC group/semester validation, HATEOAS assembler, and 8 integration tests covering TMPL-01..05.

## What Was Built

### Task 1: ScheduleItemService + ScheduleItemController + ScheduleItemAssembler

**ScheduleItemService** (`ru.rutcampustrack.schedule.item`):
- `requireHeadmanForGroup(Long targetGroupId)` — ADMIN bypasses, STUDENT must have `isHeadman()=true` in RequestContext AND `academicGrpcClient.isHeadman(userId, groupId)` confirmation
- `createScheduleItem` — validates headman, calls `academicGrpcClient.validateGroup()` + `getActiveSemester()` + checks semesterId matches active semester
- `getScheduleItem`, `listScheduleItems` (paginated), `updateScheduleItem` (groupId/semesterId immutable), `deleteScheduleItem` (soft delete)

**ScheduleItemAssembler** — converts entity to `EntityModel<ScheduleItemResponse>` with self + collection HATEOAS links.

**ScheduleItemController** — `implements ScheduleItemApi`, `@RequireRole` on create/update/delete, delegates all logic to service.

**ScheduleItemRepository** — extended with `Page<ScheduleItem> findByGroupIdAndSemesterIdAndIsActiveTrue(Long, Long, Pageable)` overload.

### Task 2: ScheduleItemApiTest

8 integration tests covering:
- TMPL-01: Create as headman (201), create as admin (201), create as non-headman student (403)
- TMPL-02: PUT update with headman — room + lessonNumber changed, groupId/semesterId unchanged (200)
- TMPL-03: DELETE soft-delete — DB row has `is_active=false`, not physically removed (204)
- TMPL-04: GET list — only active templates returned (2 active, 1 deactivated = 2 results)
- TMPL-05: gRPC failure → 503, invalid groupId → 404

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Unique constraint violation in saveActiveItem test helper**
- **Found during:** Task 2 test execution
- **Issue:** `saveActiveItem()` created multiple ScheduleItem entities with identical `(group_id, day_of_week, lesson_number, week_type, semester_id)` values, violating the UNIQUE constraint defined in V1__baseline.sql
- **Fix:** Added `lessonNumberCounter` instance field, reset in `@BeforeEach`, incremented each call to ensure unique combinations
- **Files modified:** ScheduleItemApiTest.java
- **Commit:** 747e6e6

**2. [Rule 1 - Bug] PagedResourcesAssembler generic type mismatch in controller**
- **Found during:** Task 1 compilation
- **Issue:** Injecting `PagedResourcesAssembler<ScheduleItemResponse>` as constructor field then calling `toModel(page.map(fn))` caused type inference failure
- **Fix:** Used the `PagedResourcesAssembler<ScheduleItemResponse>` method parameter from the contract interface directly; removed constructor field injection
- **Files modified:** ScheduleItemController.java
- **Commit:** fb2c9b2

## Known Stubs

None — all ScheduleItem endpoints are fully wired to the service layer with real gRPC validation.

## Self-Check: PASSED

- FOUND: ScheduleItemService.java
- FOUND: ScheduleItemController.java
- FOUND: ScheduleItemAssembler.java
- FOUND: ScheduleItemApiTest.java
- FOUND: commit fb2c9b2 (Task 1)
- FOUND: commit 747e6e6 (Task 2)
- All 8 tests pass (0 failures)
