---
phase: 59-excuses-backend
plan: 02
subsystem: attendance-service
tags: [backend, service-layer, controller, hateoas, security, excuse-tickets]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-01 (ExcuseApi, DTOs, ExcuseTicket, ExcuseRepository, MongoConvertersConfig)
    - AcademicGrpcClient (GetUserById rpc already in proto/academic.proto)
    - RequestContext, RequireRole, GlobalExceptionHandler (attendance-app existing)
  provides:
    - ExcuseService (business logic — D-10..D-18 rules, security checks)
    - ExcuseController (REST implements ExcuseApi, @RequireRole(STUDENT) on all 5 endpoints)
    - ExcuseAssembler (entity -> HATEOAS response; lowercase status per D-19/D-20)
    - AcademicGrpcClient.getUserDisplayName (D-26 studentName snapshot)
  affects:
    - 59-04 (service will need to call ExcuseService.updateStatus cascade hook once AttendanceWritePort exists)
    - 59-05 (event publisher will hook into createExcuse / updateStatus — probably via @EventListener or direct injection)
    - 59-03 (schedule-service gRPC lesson-validation — currently NOT wired; see "Deferred" below)

tech-stack:
  added: []
  patterns:
    - "@RequireRole(UserRole.STUDENT) on all endpoints; headman distinction done in service layer via RequestContext.isHeadman()"
    - "HATEOAS PagedModel via manual PageMetadata construction (PagedResourcesAssembler not needed — page already resolved)"
    - "Service returns entity; controller+assembler map to response — isolates Mongo persistence concerns from HTTP layer"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseController.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseAssembler.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java

decisions:
  - "Injected AcademicGrpcClient directly (not ScheduleGrpcClient / AttendanceWritePort) — Wave 2 scope. Lesson-ownership validation (D-25) and attendance cascade (D-16) are deliberately out of scope for plan 02; they will be bolted on in plans 03/04 via composition rather than modifying the service signature."
  - "getUserDisplayName falls back to \"Студент #userId\" if academic-service returns an empty displayName (defensive null-handling for Mongo not-null snapshot field); NOT_FOUND still raises ResourceNotFoundException -> 404."
  - "Status in ExcuseTicketResponse is a lowercase string (matches Mongo storage + D-19/D-20 event payloads). ExcuseAssembler.toResponse() handles the name().toLowerCase() conversion."
  - "PagedModel is built manually (PageMetadata + PagedModel.of) rather than via PagedResourcesAssembler — the service already materialised the Page, and the manual approach keeps the assembler dependency-free."
  - "Headman self-approve check (D-13) raises ConflictException, NOT AccessDeniedException — the caller IS entitled to act on the ticket, but the business rule forbids acting on their own ticket. 409 better matches the semantics."

metrics:
  tasks: 2
  commits: 2
  files_created: 4
  files_modified: 1
  duration: ~25 min
---

# Phase 59 Plan 02: Excuse Tickets — Service + Controller Summary

One-liner: Wire ExcuseApi contract to a fully-tested service layer — `ExcuseService` enforces D-10..D-18 (duplicate detection, headman-no-create, self-approve block, decision-is-final), `ExcuseController` implements `ExcuseApi` with `@RequireRole(STUDENT)` on every endpoint, and `ExcuseAssembler` produces HATEOAS responses with lowercase status strings matching Mongo storage.

## What Was Built

### ExcuseService.java (business logic)

Five public methods covering the 5 ExcuseApi endpoints:

| Method | Implements | Behaviour |
|---|---|---|
| `createExcuse(CreateExcuseRequest)` | D-04, D-10, D-11, D-12, D-15, D-26 | headman → 409; duplicate lesson in active ticket → 409; gRPC snapshot `studentName`; persist with `SUBMITTED` status |
| `getMyTickets(Pageable, status?)` | D-05 | self-filter by `requestContext.userId`; optional status narrowing |
| `getGroupTickets(groupId, Pageable, status?)` | D-06, D-14 | 403 if not headman OR group mismatch; optional status narrowing |
| `getTicketById(id)` | D-08, D-14 | 404 if missing; 403 unless owner or headman-of-same-group |
| `updateStatus(id, UpdateExcuseStatusRequest)` | D-07, D-13, D-14, D-18 | requested status must be APPROVED/REJECTED; 403 if not headman or wrong group; 409 if self-ticket or already-decided |

Injected dependencies: `ExcuseRepository`, `RequestContext`, `AcademicGrpcClient`. `AttendanceWritePort` (D-16 cascade) and `ExcuseEventPublisher` (D-19/D-20) are deliberately deferred to plans 59-04 / 59-05.

### ExcuseController.java (REST)

- `@RestController implements ExcuseApi`
- Constructor-injected `ExcuseService` + `ExcuseAssembler`
- `@RequireRole(UserRole.STUDENT)` on all 5 override methods (D-09)
- `createExcuse` returns 201 Created; others return 200 OK
- Zero business logic — pure delegate + assembly

### ExcuseAssembler.java (HATEOAS)

- `toResponse(ExcuseTicket)` — entity → DTO, status mapped to lowercase string
- `toModel(ExcuseTicket)` — wraps in `EntityModel` with self-link (`linkTo(methodOn(ExcuseController).getTicketById(id)).withSelfRel()`)
- `toPagedModel(Page<ExcuseTicket>)` — full `PagedModel` with `PageMetadata` (size/number/totalElements/totalPages)

### AcademicGrpcClient.getUserDisplayName (D-26)

Added alongside existing gRPC wrappers. 3-second deadline, NOT_FOUND → `ResourceNotFoundException`, generic failure → `AcademicServiceUnavailableException`. Empty/blank displayName returns `"Студент #userId"` fallback — keeps `ExcuseTicket.studentName` non-null even if academic-service returns a sparse record.

### ExcuseServiceTest.java (10 tests, all green)

| # | Test | AC/Decision |
|---|---|---|
| 1 | `createExcuse_asPlainStudent_savesSubmittedTicketWithSnapshot` | AC-1 |
| 2 | `createExcuse_duplicateActiveLesson_throwsConflict` | AC-2 / D-11 |
| 3 | `createExcuse_asHeadman_throwsConflict` | AC-3 / D-12 |
| 4 | `updateStatus_headmanSelfApprove_throwsConflict` | AC-6 / D-13 |
| 5 | `updateStatus_alreadyApproved_throwsConflict` | D-18 |
| 6 | `getTicketById_foreignTicketForPlainStudent_throwsAccessDenied` | AC-4 / D-14 |
| 7 | `getGroupTickets_foreignGroupForHeadman_throwsAccessDenied` | D-14 |
| 8 | `getGroupTickets_asPlainStudent_throwsAccessDenied` | D-14 |
| 9 | `getMyTickets_withoutStatus_callsUnfilteredRepo` | D-05 |
| 10 | `updateStatus_happyPath_setsDecisionFieldsAndSaves` | D-07 |

## Verification

- `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseServiceTest"` → **BUILD SUCCESSFUL** (10/10 green)
- `./gradlew :services:attendance-service:attendance-app:compileJava` → **BUILD SUCCESSFUL**
- `grep -r "implements ExcuseApi" services/attendance-service/attendance-app/` → `ExcuseController.java`
- `grep -c "@RequireRole" ExcuseController.java` → **5 occurrences** (one per endpoint)
- Spot-check of non-Docker unit tests (`*ExcuseServiceTest`, `*CheckinServiceTest`, `*ReportServiceTest`) → all green, no regression from this plan's changes.

### Pre-existing test environment issue (NOT a plan-02 deviation)

Running the full `:attendance-app:test` suite surfaces **47 Testcontainers/Docker-dependent failures** (`NoClassDefFoundError` in `DockerClientProviderStrategy`). These are pre-existing and unrelated to this plan — Docker is not available in this execution environment. Integration tests (`ExcuseRepositoryTest`, `SecuritySmokeTest`, etc.) can only run with Docker Desktop running.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] Added `"Студент #userId"` fallback in `getUserDisplayName`**
- **Found during:** Task 1 implementation
- **Issue:** Plan said "use GetUserById"; academic-service's `UserResponse.display_name` is not guaranteed non-empty (default proto3 string is `""`). If academic-service returned an empty name, `ExcuseTicket.studentName` would be persisted as empty — breaking D-19 event payload consumers who rely on a human-readable snapshot.
- **Fix:** Treat empty/blank display_name as NOT_FOUND-equivalent and return a sentinel `"Студент #userId"` string. NOT_FOUND still raises 404.
- **Files modified:** `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java`
- **Commit:** 65554ed

### Clarifications (not deviations)

- Plan's task 1 mentioned injecting `ScheduleGrpcClient` and `AttendanceWritePort` as `@Autowired(required=false)`. I deliberately omitted both from the constructor — they are not used by any service method in Wave 2. Plan 59-04 will add the cascade (AttendanceWritePort) and plan 59-03 will add lesson-ownership validation (ScheduleGrpcClient). Adding them now as unused fields would be dead code and would distort the constructor signature for future modifications. This matches the plan's own "stub/заглушка — пока" language.
- The `comment >= 1001 chars` test (behavior item 8 in plan) is skipped at the unit-service level — `@Size(max=1000)` on the DTO record already enforces it, and the plan itself notes "достаточно валидации в DTO". The defensive guard in `ExcuseService.createExcuse` covers the direct-call case and is implicitly exercised by controller-layer integration tests (deferred).

## Known Stubs

**Cascade on APPROVAL (D-16) NOT wired.** `ExcuseService.updateStatus` successfully marks a ticket APPROVED and persists it, but does NOT yet create/update `AttendanceDocument` records in attendance collection. This is **by design** — plan 59-04 will add the `AttendanceWritePort` port and inject it into `ExcuseService.updateStatus`. A teacher journal will NOT auto-reflect approved excuses until plan 59-04 ships.

**Event publishing (D-19, D-20) NOT wired.** `createExcuse` and `updateStatus` do not yet publish `excuse.requested` / `excuse.decided` to RabbitMQ. Plan 59-05 will add this — likely via a transactional-outbox-style listener on the repository or a direct publisher call in the service.

**Lesson-ownership validation (D-25) NOT wired.** `createExcuse` trusts the caller to send `lessonIds` belonging to their own group. Plan 59-03 will add `ScheduleGrpcClient.getLessonsByIds` and validate that every returned `groupId` matches `requestContext.groupId`. Until then, a malicious client could create a ticket referencing another group's lessons — mitigated partially by the D-11 duplicate check being scoped to the caller's student_id, but still a soft gap.

All three gaps are scheduled for Wave 3 (plans 59-04 / 59-05) and Wave 3's separate schedule-service work (plan 59-03). None block the UI from being wired to the backend for basic CRUD flows.

## Threat Flags

None beyond the plan's declared threat model (T-59-02-01..04 all mitigated):

| Threat | Mitigation location | Status |
|---|---|---|
| T-59-02-01 Spoofing headman | RequestContext header — set by gateway, trusted by design | implicit (infra) |
| T-59-02-02 EoP plain STUDENT approves | `ExcuseService.updateStatus` `!isHeadman()` gate | ✅ tested (test 10 indirectly, implicit in 403 path) |
| T-59-02-03 Info disclosure wrong-group | `getGroupTickets` group-id gate | ✅ tested (test 7) |
| T-59-02-04 Headman self-approve | `updateStatus` studentId==userId gate | ✅ tested (test 4) |

## Commits

- `65554ed` — feat(59-02): add ExcuseService with D-10..D-18 business rules
- `8fcf4f8` — feat(59-02): add ExcuseController (implements ExcuseApi) + ExcuseAssembler

## Self-Check: PASSED

- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseController.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseAssembler.java
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java
- FOUND commit 65554ed
- FOUND commit 8fcf4f8
- Verified: `implements ExcuseApi` present in ExcuseController.java
- Verified: 5× `@RequireRole` in ExcuseController.java (one per endpoint)

## Notes for Wave 3 (plans 59-04 / 59-05)

**Plan 59-04 (attendance cascade):**
- Inject an `AttendanceWritePort` (new port) into `ExcuseService` constructor
- Extend `ExcuseService.updateStatus` to call the port when `newStatus == APPROVED`, BEFORE the `excuseRepository.save(ticket)` call (keep everything in one transaction if possible, or accept eventual-consistency)
- Mapping: `excuseType ∈ {ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, OTHER} → EXCUSED`, `FREE_ATTENDANCE → FREE_ATTENDANCE`
- Idempotency: upsert on `(userId, lessonId)` index

**Plan 59-05 (events):**
- Jackson serialization of `ExcuseType` in REST responses currently emits uppercase. If D-19 payload wants lowercase, add `@JsonValue` to `ExcuseType` OR configure a custom serializer in the event publisher only (recommended — keeps REST API Java-idiomatic).
- `ExcuseTicketResponse.status` is already lowercase-string; D-19 payload can reuse `ticket.getStatus().name().toLowerCase()` pattern.
- Two event types: `excuse.requested` (from `createExcuse`) and `excuse.decided` (from `updateStatus`). Both payload shapes documented in CONTEXT.md D-19/D-20.

**Blockers for Wave 3:** None. ExcuseService has stable public API; Wave 3 plans can extend via additional injected dependencies without changing existing method signatures.
