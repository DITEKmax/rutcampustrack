---
phase: 59-excuses-backend
plan: 01
subsystem: attendance-service
tags: [backend, mongodb, contract, dto, excuse-tickets]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - services/attendance-service/attendance-api-contract/.../enums/ExcuseType.java (pre-existing)
    - services/attendance-service/attendance-api-contract/.../enums/ExcuseTicketStatus.java (pre-existing)
  provides:
    - ExcuseApi (REST contract interface, 5 endpoints)
    - CreateExcuseRequest / UpdateExcuseStatusRequest (request records)
    - ExcuseTicketResponse (HATEOAS response class)
    - ExcuseTicket (@Document excuse_tickets)
    - ExcuseRepository (Spring Data Mongo, incl. D-11 duplicate query)
    - MongoCustomConversions for ExcuseType + ExcuseTicketStatus (lowercase)
  affects:
    - plan 59-02 (controller will implement ExcuseApi)
    - plan 59-04 (service will call ExcuseRepository for duplicate check / queries)
    - plan 59-05 (event publisher will read ExcuseTicket fields for RabbitMQ payload)

tech-stack:
  added:
    - spring-data-commons (Pageable in contract module)
  patterns:
    - MongoDB @Document with Lombok (app module only)
    - @Field snake_case mapping
    - WritingConverter / ReadingConverter for enum lowercase in Mongo
    - Java records for request DTOs (no Lombok in contract)
    - RepresentationModel for HATEOAS response

key-files:
  created:
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ExcuseApi.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/CreateExcuseRequest.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/UpdateExcuseStatusRequest.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/ExcuseTicketResponse.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/entity/ExcuseTicket.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseRepository.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseRepositoryTest.java
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConvertersConfig.java
    - services/attendance-service/attendance-api-contract/build.gradle.kts

decisions:
  - status default in ExcuseTicket is ExcuseTicketStatus.SUBMITTED (D-02 says "pending" — mapped to SUBMITTED as the existing enum has only DRAFT/SUBMITTED/APPROVED/REJECTED)
  - ExcuseTicketResponse.status exposed as plain String (lowercase) to match Mongo storage + event payloads (D-19, D-20); excuseType kept as ExcuseType enum in response since Jackson will serialize as uppercase by default — downstream plan 59-02 will add JsonValue or custom serializer if needed (flagged below)
  - Added spring-data-commons to contract module for Pageable (needed by ExcuseApi list endpoints)
  - ExcuseRepository adds status-filtered variants (findByStudentIdAndStatus, findByGroupIdAndStatus) for D-05/D-06 filter query param

metrics:
  tasks: 2
  commits: 2
  files_created: 7
  files_modified: 2
  duration: ~15 min
---

# Phase 59 Plan 01: Excuse Tickets — Domain & Contract Summary

One-liner: Contract-first interfaces and MongoDB data layer for excuse tickets — `ExcuseApi` with 5 endpoints, request/response DTOs, `ExcuseTicket` document with lowercase enum converters, and repository with D-11 duplicate-detection query.

## What Was Built

### Contract module (`attendance-api-contract`)

- **`ExcuseApi`** — REST interface at `/attendance/excuses` with 5 operations:
  - `POST` → `createExcuse(CreateExcuseRequest)` → `EntityModel<ExcuseTicketResponse>`
  - `GET /me` → `getMyTickets(Pageable, status?)` → `PagedModel<EntityModel<ExcuseTicketResponse>>`
  - `GET /group/{groupId}` → `getGroupTickets(...)` → `PagedModel<...>`
  - `GET /{id}` → `getTicketById(id)` → `EntityModel<...>`
  - `PATCH /{id}/status` → `updateStatus(id, UpdateExcuseStatusRequest)` → `EntityModel<...>`
  - All endpoints documented with `@Operation`/`@ApiResponses` covering 201/400/403/404/409.
- **`CreateExcuseRequest`** — Java record, validation `@NotEmpty lessonIds`, `@NotNull excuseType`, `@Size(max=1000) comment`.
- **`UpdateExcuseStatusRequest`** — Java record, `@NotNull ExcuseTicketStatus status`, optional `decisionComment`.
- **`ExcuseTicketResponse`** — class extending `RepresentationModel<ExcuseTicketResponse>`, constructor + getters, no Lombok.

### App module (`attendance-app`)

- **`ExcuseTicket`** — `@Document(collection = "excuse_tickets")` with 13 fields mapped via `@Field("snake_case")`. Default status `SUBMITTED` via `@Builder.Default`.
- **`ExcuseRepository extends MongoRepository<ExcuseTicket, String>`** with:
  - `existsByStudentIdAndLessonIdsInAndStatusIn(...)` — D-11 duplicate check
  - `findByStudentId(Pageable)` / `findByStudentIdAndStatus(...)` — D-05
  - `findByGroupId(Pageable)` / `findByGroupIdAndStatus(...)` — D-06
- **`MongoConvertersConfig`** extended with 4 new converters (`ExcuseTypeWriter/Reader`, `ExcuseTicketStatusWriter/Reader`) — enums stored as lowercase strings per INFRA-02 convention.
- **`ExcuseRepositoryTest`** — 3 test cases over the D-11 query (overlap conflict, rejected-ticket ignored, cross-student isolation).

## Verification

- `./gradlew :services:attendance-service:attendance-api-contract:compileJava` → BUILD SUCCESSFUL
- `./gradlew :services:attendance-service:attendance-app:compileJava` → BUILD SUCCESSFUL
- `grep -r "interface ExcuseApi"` → file found
- `grep -r "ExcuseTypeWriter"` → found in `MongoConvertersConfig.java`
- `grep -r "record CreateExcuseRequest"` → file found

Test suite not executed in this plan (integration tests gated by Testcontainers — deferred to plan 59-02+ where behavior exists to verify).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `spring-data-commons` dependency to contract module**
- **Found during:** Task 2 compile
- **Issue:** `ExcuseApi` signatures use `org.springframework.data.domain.Pageable` but `attendance-api-contract/build.gradle.kts` did not declare the dependency. Compile failed with "cannot find symbol: class Pageable".
- **Fix:** Added `api("org.springframework.data:spring-data-commons:3.4.1")` alongside existing `spring-web` and `spring-hateoas`.
- **Files modified:** `services/attendance-service/attendance-api-contract/build.gradle.kts`
- **Commit:** 92690b2

### Clarifications (not deviations)

- Plan specified `@Size(max=1000)` on `comment` but no `@Size` on `decisionComment`; added `@Size(max=1000)` on both for consistency with D-15.
- `ExcuseTicket.status` uses `@Builder.Default = SUBMITTED` — CONTEXT D-02 says "pending" but enum has no PENDING value, so `SUBMITTED` is the semantically equivalent initial state per plan guidance.

## Known Stubs

None. All files are complete interfaces/data structures ready for implementation in plan 59-02.

## Threat Flags

None beyond plan's declared threat model (T-59-01-01..03 remain valid; mitigations will be implemented in plan 59-02 service layer).

## Commits

- `f73f242` — feat(59-01): add ExcuseTicket MongoDB document, repository, and enum converters
- `92690b2` — feat(59-01): add ExcuseApi contract interface and DTOs

## Self-Check: PASSED

All declared artifacts verified on disk:
- FOUND: services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ExcuseApi.java
- FOUND: services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/CreateExcuseRequest.java
- FOUND: services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/UpdateExcuseStatusRequest.java
- FOUND: services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/ExcuseTicketResponse.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/entity/ExcuseTicket.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseRepository.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConvertersConfig.java (modified — contains ExcuseTypeWriter)
- FOUND commit f73f242
- FOUND commit 92690b2

## Notes for Wave 2 (plans 59-02 / 59-03)

- **`ExcuseApi` is the contract 59-02 must `implements`** — controller in `excuse/` package.
- **D-12 enforcement (headman-create 409)** must be done in the service layer; contract allows 403/409 responses.
- **`ExcuseType` JSON serialization**: currently Jackson will emit uppercase enum names in responses. For event payload D-19 (lowercase), either:
  - Add `@JsonValue` method on enum, or
  - Configure Jackson `ACCEPT_CASE_INSENSITIVE_ENUMS` + custom serializer in the publisher.
  Recommend picking the approach in plan 59-05 (event publisher) and aligning response serialization in 59-02.
- **gRPC `LessonsByIds`** (D-25) required by service validation — flagged for plan 59-03.
- **No blockers** for Wave 2.
