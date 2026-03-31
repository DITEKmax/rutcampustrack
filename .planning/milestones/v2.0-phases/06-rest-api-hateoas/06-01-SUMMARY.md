---
phase: 06-rest-api-hateoas
plan: 01
subsystem: api
tags: [java, spring-boot, hateoas, rest, contract-first, aop, security, swagger]

# Dependency graph
requires:
  - phase: 05-entity-and-repository-foundation
    provides: JPA entities and repositories that the API will expose
provides:
  - Request-scoped user context (RequestContext) populated from Gateway headers
  - AOP role enforcement via @RequireRole annotation and RoleCheckAspect
  - RFC 7807 GlobalExceptionHandler with 400/403/404/409/500 handlers
  - 9 API contract interfaces (UserApi, GroupApi, SemesterApi, SubjectApi, AssignmentApi, HomeworkApi, AssistantApi, ThresholdApi, DashboardApi)
  - 25+ DTO types — records for requests, RepresentationModel classes for responses
affects:
  - 06-02 (ADMIN controllers), 06-03 (HEADMAN/STUDENT/TEACHER controllers) — all implement these interfaces

# Tech tracking
tech-stack:
  added:
    - spring-security-crypto (BCrypt password encoding for user creation)
    - spring-boot-starter-aop (AOP support for @RequireRole enforcement)
  patterns:
    - Contract-first: controller implements interface, @RequestMapping only in interface
    - Request-scoped bean with ScopedProxyMode.TARGET_CLASS for concurrency safety
    - AOP @Around for role checks — no Spring Security, just plain header-based auth
    - RFC 7807 Problem Details via ErrorResponse record from contract module
    - No Lombok in contract module — manual getters/setters/constructors only

key-files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContext.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContextFilter.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequireRole.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/AccessDeniedException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/BadRequestException.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ (9 interfaces)
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/ (25+ DTOs)
  modified:
    - services/academic-service/academic-app/build.gradle.kts (spring-security-crypto + spring-boot-starter-aop)

key-decisions:
  - "@RequestMapping paths use /academic/* not /api/academic/* — Gateway StripPrefix=1 strips /api prefix before forwarding"
  - "ScopedProxyMode.TARGET_CLASS mandatory on RequestContext — singleton RoleCheckAspect must receive scoped proxy"
  - "AOP-based role check (no Spring Security) — service validates roles from X-User-Role header injected by Gateway"
  - "UserCreatedResponse includes initialPassword field visible only on create (per D-10)"
  - "DeleteSemesterRequest requires confirmation phrase to prevent accidental active semester deletion (per D-12)"
  - "AssistantApi uses CollectionModel (not PagedModel) for listAssistants — group assistants are small bounded list"

patterns-established:
  - "Security pattern: RequestContextFilter populates RequestContext → @RequireRole on method → RoleCheckAspect reads context"
  - "Error pattern: business exceptions (Access/Conflict/BadRequest) → GlobalExceptionHandler → RFC 7807 ErrorResponse"
  - "DTO pattern: request=record+validation, response=RepresentationModel class with manual getters"
  - "API pattern: interface defines all mappings, controller implements interface (no duplication)"

requirements-completed:
  - USER-01
  - USER-02
  - USER-03
  - USER-04
  - USER-05
  - USER-06
  - USER-07
  - USER-08
  - GSEM-01
  - GSEM-02
  - GSEM-03
  - GSEM-04
  - SUBJ-01
  - SUBJ-02
  - SUBJ-03
  - ASST-01
  - ASST-02
  - ASST-03
  - HW-01
  - HW-02
  - HW-03
  - THRSH-01
  - THRSH-02
  - THRSH-03
  - THRSH-04
  - DASH-01

# Metrics
duration: 15min
completed: 2026-03-30
---

# Phase 06 Plan 01: REST API Infrastructure + Contract Interfaces Summary

**RequestContext+AOP authorization chain, RFC 7807 GlobalExceptionHandler, and 9 HATEOAS contract interfaces with 25+ request/response DTOs — foundation for all Academic Service REST controllers**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-30T18:20:36Z
- **Completed:** 2026-03-30T18:30:12Z
- **Tasks:** 2
- **Files modified:** 46 (8 app-module security/exception, 37 contract-module API+DTO, 1 build.gradle.kts)

## Accomplishments

- Security infrastructure: RequestContext (request-scoped with TARGET_CLASS proxy), RequestContextFilter (parses Gateway headers), @RequireRole annotation, and RoleCheckAspect (@Around AOP) form the complete authorization chain
- GlobalExceptionHandler handles ResourceNotFoundException (404), AccessDeniedException (403), BadRequestException (400), ConflictException (409), MethodArgumentNotValidException (400 with fieldErrors), and generic Exception (500) using RFC 7807 ErrorResponse
- 9 API contract interfaces with correct @RequestMapping paths (/academic/*), full method signatures, and Swagger @Operation/@ApiResponse annotations
- 25+ DTO types compiled: request records with Jakarta Validation, response classes extending RepresentationModel — no Lombok in contract module

## Task Commits

1. **Task 1: Security infrastructure** — `fad2699` (feat)
2. **Task 2: API contract interfaces and DTOs** — `92ce624` (feat)

## Files Created/Modified

- `academic-app/build.gradle.kts` — Added spring-security-crypto and spring-boot-starter-aop
- `academic-app/security/RequestContext.java` — Request-scoped bean with TARGET_CLASS proxy
- `academic-app/security/RequestContextFilter.java` — Parses X-User-Id/Role/Group-Id/Is-Headman headers
- `academic-app/security/RequireRole.java` — Method annotation for role enforcement
- `academic-app/security/RoleCheckAspect.java` — AOP enforcement via @Around
- `academic-app/exception/GlobalExceptionHandler.java` — RFC 7807 centralized error handler
- `academic-app/exception/AccessDeniedException.java`, `ConflictException.java`, `BadRequestException.java` — Custom runtime exceptions
- `academic-api-contract/api/UserApi.java` — /academic/users with createUser, getMe, transferStudent
- `academic-api-contract/api/GroupApi.java` — /academic/groups with getMyGroupMembers
- `academic-api-contract/api/SemesterApi.java` — /academic/semesters with activateSemester, deleteSemester (phrase confirmation)
- `academic-api-contract/api/SubjectApi.java`, `AssignmentApi.java`, `HomeworkApi.java` — CRUD + markComplete/unmarkComplete
- `academic-api-contract/api/AssistantApi.java`, `ThresholdApi.java`, `DashboardApi.java` — Business domain APIs
- `academic-api-contract/dto/user/` — 5 DTOs (Create/Update/Patch/Transfer requests + User/UserCreated responses)
- `academic-api-contract/dto/group|semester|subject|assignment|homework|assistant|threshold|dashboard/` — All domain DTOs

## Decisions Made

- @RequestMapping paths use `/academic/*` (not `/api/academic/*`) — Gateway StripPrefix=1 strips the `/api` prefix before forwarding, so the service sees `/academic/users` not `/api/academic/users`
- `ScopedProxyMode.TARGET_CLASS` is mandatory on RequestContext — without it, singleton beans like RoleCheckAspect capture a stale instance at startup instead of the per-request proxy
- No Spring Security dependency — role validation is done via AOP reading the X-User-Role header injected by the Gateway. This avoids SecurityContext complexity.
- `UserCreatedResponse` includes `initialPassword` (per D-10) — shown only on create, never returned again
- `DeleteSemesterRequest` uses a confirmation phrase (per D-12) to prevent accidental deletion of active semesters
- `AssistantApi.listAssistants` returns `CollectionModel` not `PagedModel` — group assistants are a small bounded list, pagination unnecessary

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Known Stubs

None — this plan establishes interfaces and infrastructure only. Controllers (02/03 plans) will provide data-wired implementations.

## User Setup Required

None — no external service configuration required.

## Self-Check: PASSED

- All 9 API interface files found under academic-api-contract/api/
- All 25+ DTO files found in academic-api-contract/dto/
- Security package (4 files) found in academic-app/security/
- Exception package (4 files) found in academic-app/exception/
- Task commits fad2699 and 92ce624 verified in git log
- Both modules compile: academic-api-contract and academic-app compileJava SUCCESS
- No Lombok in contract module verified

## Next Phase Readiness

- Security infrastructure and all API contracts are ready for Phase 06-02 (ADMIN controllers: UserController, GroupController, SemesterController, DashboardController)
- Phase 06-03 (HEADMAN/STUDENT/TEACHER controllers) depends on the same API interfaces established here
- All response DTOs extend RepresentationModel — controllers will add HATEOAS _links in service layer

---
*Phase: 06-rest-api-hateoas*
*Completed: 2026-03-30*
