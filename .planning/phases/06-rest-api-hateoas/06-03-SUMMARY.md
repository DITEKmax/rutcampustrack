---
phase: 06-rest-api-hateoas
plan: 03
subsystem: academic-service
tags: [rest-api, hateoas, headman, authorization, subjects, assignments, assistants, homework, thresholds]
dependency_graph:
  requires: ["06-01 (prerequisite — created inline as deviation)"]
  provides: ["SubjectApi", "AssignmentApi", "AssistantApi", "HomeworkApi", "ThresholdApi"]
  affects: ["academic-service", "API Gateway routing", "plans 06-02/06-04 (share contract/security)"]
tech_stack:
  added: ["spring-security-crypto", "spring-boot-starter-aop", "spring-data-commons (contract module)"]
  patterns: ["RepresentationModelAssembler", "@RequireRole AOP", "headman delegation", "most-specific-wins resolution"]
key_files:
  created:
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SubjectApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssignmentApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssistantApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SemesterApi.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/DashboardApi.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContext.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContextFilter.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequireRole.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/AccessDeniedException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/BadRequestException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdAssembler.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/subject/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/assignment/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/assistant/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/semester/
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/dashboard/
  modified:
    - services/academic-service/academic-api-contract/build.gradle.kts
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/TeacherSubjectGroup.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/HeadmanAssistant.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/HomeworkCompletion.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/AttendanceThreshold.java
decisions:
  - "Contract API interfaces do not include PagedResourcesAssembler parameters (Spring Data web concern — handled in controller layer)"
  - "spring-data-commons added to contract module to support Pageable in API interface signatures"
  - "Entity @PrePersist lifecycle callbacks added for createdAt/updatedAt fields (entities had no setter or factory method)"
  - "TeacherSubjectGroup and HeadmanAssistant gained constructor overloads for service-layer instantiation"
  - "ThresholdResponse.updatedAt maps to entity createdAt (no separate updatedAt column in DB schema)"
metrics:
  duration: "16m 15s"
  completed: "2026-03-30"
  tasks: 2
  files: 68
---

# Phase 6 Plan 03: Headman Domains Summary

**One-liner:** Headman CRUD for subjects/assignments/assistants/homeworks/thresholds with AOP role enforcement, assistant delegation (lowercase permission comparison), and 3-level most-specific-wins threshold resolution.

## What Was Built

### Task 1: Subject + Assignment + Assistant Domains

All three domains plus the prerequisite security infrastructure (created as a Rule 3 deviation since plan 06-01 had not been executed).

**Security infrastructure created:**
- `RequestContext` — request-scoped bean (TARGET_CLASS proxy) populated from Gateway headers
- `RequestContextFilter` — parses X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman
- `@RequireRole` — method annotation for role enforcement
- `RoleCheckAspect` — AOP `@Around` that reads RequestContext and throws AccessDeniedException
- `GlobalExceptionHandler` — RFC 7807 responses for all exception types

**API contracts created (full set for contract module compilation):**
- 9 API interfaces: SubjectApi, AssignmentApi, AssistantApi, HomeworkApi, ThresholdApi, UserApi, GroupApi, SemesterApi, DashboardApi
- ~25 DTO types as Java records (requests) and RepresentationModel classes (responses)

**Domain implementations:**
- `SubjectService`: headman-only CRUD using `requestContext.isHeadman()` check
- `AssignmentService`: teacher search by `findByEmployeeNumber`, conflict check, `findByTeacherIdAndSemesterId` for teacher's own assignments
- `AssistantService`: `.name().toLowerCase()` permission conversion (Pitfall 4), `revokedAt` set on revoke

### Task 2: Homework + Threshold Domains

**Homework domain:**
- `HomeworkService`: MANAGE_HOMEWORK delegation check with lowercase "manage_homework" comparison
- `HomeworkCompletionRepository.existsByHomeworkIdAndStudentId` for toggle completion
- `HomeworkAssembler` with `toModel(hw, completed)` overload for per-student completion flag
- `markComplete`/`unmarkComplete` creates/deletes `HomeworkCompletion` records

**Threshold domain:**
- `ThresholdService`: 3-level resolution: `findByGroupIdAndSubjectId` → `findByGroupIdAndSubjectIdIsNull` → `findByGroupIdIsNullAndSubjectIdIsNull`
- `ResolvedThresholdResponse` with `level` field: "subject" / "group" / "global" / "default"
- Upsert pattern (find-or-create) for all set operations

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Prerequisite infrastructure from plan 06-01 not yet executed**
- **Found during:** Task 1 start — API contract interfaces (SubjectApi, etc.) did not exist
- **Issue:** Plan 06-03 depends on 06-01 but 06-01 had not been executed in this worktree
- **Fix:** Created full prerequisite set inline: 9 API contract interfaces, ~25 DTOs, security infrastructure, exception classes, build.gradle.kts updates
- **Files modified:** All files listed under "created" above
- **Commits:** 2de5fa5, 1a67f18

**2. [Rule 2 - Missing Critical] Entity @PrePersist not present on entities**
- **Found during:** Task 1 — Subject, TeacherSubjectGroup, HeadmanAssistant had no mechanism to set timestamp columns
- **Fix:** Added `@PrePersist` lifecycle callbacks and constructor overloads to Subject, TeacherSubjectGroup, HeadmanAssistant, Homework, HomeworkCompletion, AttendanceThreshold
- **Files modified:** All 6 entity files

**3. [Rule 3 - Blocking] spring-data-commons missing from contract module**
- **Found during:** Task 1 compilation — `Pageable` unresolved in API interface signatures
- **Fix:** Added `spring-data-commons:3.4.1` to academic-api-contract/build.gradle.kts

## Known Stubs

None — all service methods perform real database operations via Spring Data repositories. No hardcoded values flow to responses.

## Self-Check: PASSED

All key files verified present:
- SubjectService.java - FOUND
- ThresholdService.java - FOUND
- HomeworkService.java - FOUND
- RequestContext.java - FOUND
- SubjectApi.java - FOUND

Commits verified:
- 2de5fa5: feat(06-03): Subject + Assignment + Assistant domains
- 1a67f18: feat(06-03): Homework + Threshold domains
