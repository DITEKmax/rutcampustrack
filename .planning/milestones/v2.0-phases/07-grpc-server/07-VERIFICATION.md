---
phase: 07-grpc-server
verified: 2026-03-30T21:00:00Z
status: passed
score: 12/12 must-haves verified
re_verification: false
---

# Phase 7: gRPC Server Verification Report

**Phase Goal:** Internal services can call all 7 Academic Service gRPC RPCs on port 19091 and receive correct, serialized responses.
**Verified:** 2026-03-30T21:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                         | Status     | Evidence                                                                                           |
|----|-----------------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------|
| 1  | Proto files compile to Java stubs during Gradle build                                         | ✓ VERIFIED | `build/generated/source/proto/main/` contains all message types and `AcademicGrpcServiceGrpc.java` |
| 2  | gRPC server starts on port 19091 alongside HTTP server on 9091                                | ✓ VERIFIED | `application.yml` has `grpc.server.port: 19091`; HTTP `server.port: 9091`                         |
| 3  | All 7 RPCs are implemented and route to repository queries                                    | ✓ VERIFIED | `AcademicGrpcServiceImpl.java` overrides all 7 methods; all call repository directly               |
| 4  | Domain exceptions map to gRPC NOT_FOUND status via centralized advice                        | ✓ VERIFIED | `GrpcExceptionAdvice.java` with `@GrpcAdvice` + `@GrpcExceptionHandler(ResourceNotFoundException.class)` → `Status.NOT_FOUND` |
| 5  | GetGroup returns group name, code, active flag; invalid ID returns NOT_FOUND                  | ✓ VERIFIED | Test `getGroup_validId_returnsGroupInfo` + `getGroup_invalidId_throwsNotFound`                    |
| 6  | GetGroupMembers returns only active (non-archived) students                                   | ✓ VERIFIED | Test `getGroupMembers_archivedUserInGroup_notReturned` — archived user excluded via `@SQLRestriction` |
| 7  | GetActiveSemester returns active semester; NOT_FOUND when none active                         | ✓ VERIFIED | Tests `getActiveSemester_activeSemesterExists_returnsSemester` + `getActiveSemester_noActiveSemester_throwsNotFound` |
| 8  | IsHeadman returns true for headman of the group, false otherwise                              | ✓ VERIFIED | Tests `isHeadman_headmanOfGroup_returnsTrue` + `isHeadman_notHeadman_returnsFalse` + `isHeadman_userNotFound_returnsFalse` |
| 9  | GetTeacherSubjects returns subject+group info for teacher in semester                         | ✓ VERIFIED | Test `getTeacherSubjects_returnsSubjectsWithGroups` asserts `subjectName`, `groupName`, `subjectType`, `groupId` |
| 10 | GetCampusGeofence returns lat, lng, radius                                                    | ✓ VERIFIED | Test `getCampusGeofence_returnsCampusSettings` asserts lat=55.7699, lng=37.7039, radiusM=200      |
| 11 | GetUserById returns user info including archived users; NOT_FOUND on invalid ID               | ✓ VERIFIED | Tests `getUserById_archivedUser_stillReturnsUser` (findByIdIncludingArchived) + `getUserById_invalidId_throwsNotFound` |
| 12 | All 7 RPCs covered by Testcontainers integration tests (>=13 test methods)                    | ✓ VERIFIED | 15 test methods in `AcademicGrpcIntegrationTest.java` (364 lines, min_lines=150 satisfied)        |

**Score:** 12/12 truths verified

---

### Required Artifacts

| Artifact                                                                                                              | Expected                                                  | Status     | Details                                                                   |
|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|------------|---------------------------------------------------------------------------|
| `services/academic-service/academic-app/build.gradle.kts`                                                            | Protobuf plugin + grpc-server-spring-boot-starter dep     | ✓ VERIFIED | `id("com.google.protobuf") version "0.9.4"`, `grpc-server-spring-boot-starter:3.1.0.RELEASE`, `protoc:3.25.3`, `protoc-gen-grpc-java:1.63.0`, `srcDir(rootProject.file("proto"))` |
| `services/academic-service/academic-app/src/main/resources/application.yml`                                          | `grpc.server.port: 19091`                                 | ✓ VERIFIED | Port 19091 configured at `grpc.server.port`                               |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`  | All 7 gRPC RPC implementations                            | ✓ VERIFIED | `@GrpcService`, extends `AcademicGrpcServiceImplBase`, 7 `@Override` methods, direct repository injection, no REST service dependencies |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdvice.java`      | Centralized exception-to-status mapping                   | ✓ VERIFIED | `@GrpcAdvice`, `@GrpcExceptionHandler(ResourceNotFoundException.class)`, returns `Status.NOT_FOUND` |
| `services/academic-service/academic-app/src/test/resources/application-test.yml`                                     | In-process gRPC config (port=-1)                          | ✓ VERIFIED | `grpc.server.port: -1` disables Netty binding in all test contexts        |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java` | Integration tests for all 7 RPCs (>=150 lines)   | ✓ VERIFIED | 364 lines, 15 test methods, extends `AbstractAcademicIntegrationTest`, `@GrpcClient("inProcess")` blocking stub |
| `build/generated/source/proto/main/java/ru/rutcampustrack/academic/grpc/`                                            | All message types compiled from academic.proto             | ✓ VERIFIED | `GroupRequest.java`, `GroupResponse.java`, `UserRequest.java`, `UserResponse.java`, all 7 RPC message types present |
| `build/generated/source/proto/main/grpc/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceGrpc.java`                | Service stub base class                                   | ✓ VERIFIED | File exists; `AcademicGrpcServiceImplBase` extends from it                |

---

### Key Link Verification

| From                         | To                                                                                                              | Via                                          | Status     | Details                                                                                         |
|------------------------------|-----------------------------------------------------------------------------------------------------------------|----------------------------------------------|------------|-------------------------------------------------------------------------------------------------|
| `AcademicGrpcServiceImpl`    | `GroupRepository, UserRepository, SemesterRepository, SubjectRepository, TeacherSubjectGroupRepository, CampusSettingRepository` | Constructor injection — `private final` fields | ✓ WIRED    | All 6 repositories declared as `private final` and constructor-injected; no REST service imports |
| `GrpcExceptionAdvice`        | `ResourceNotFoundException`                                                                                     | `@GrpcExceptionHandler`                      | ✓ WIRED    | `@GrpcExceptionHandler(ResourceNotFoundException.class)` maps to `Status.NOT_FOUND`             |
| `AcademicGrpcIntegrationTest`| `AcademicGrpcServiceImpl`                                                                                       | `@GrpcClient("inProcess")` blocking stub     | ✓ WIRED    | `@GrpcClient("inProcess") AcademicGrpcServiceBlockingStub stub` injected; in-process properties on `@SpringBootTest` |
| `AcademicGrpcIntegrationTest`| Testcontainers PostgreSQL                                                                                       | `extends AbstractAcademicIntegrationTest`    | ✓ WIRED    | Class extends `AbstractAcademicIntegrationTest` which provides Testcontainers PostgreSQL with Flyway V1+V2 seed data |
| `getUserById` in service     | `userRepository.findByIdIncludingArchived`                                                                      | Native query bypasses `@SQLRestriction`      | ✓ WIRED    | Method `findByIdIncludingArchived(@Param("id") Long id)` exists in `UserRepository`; used at line 195 of impl |
| `getGroupMembers` in service | `userRepository.findByGroupId`                                                                                  | `@SQLRestriction` filters archived users     | ✓ WIRED    | `findByGroupId(Long groupId)` exists in `UserRepository` line 21; used in `getGroupMembers`     |

---

### Data-Flow Trace (Level 4)

All 7 RPCs directly query JPA repositories — no intermediate REST service layer, no static returns. Data flows from PostgreSQL through JPA entities to protobuf message builders in each RPC.

| RPC                   | Data Variable      | Source                                       | Produces Real Data | Status      |
|-----------------------|--------------------|----------------------------------------------|--------------------|-------------|
| `getGroup`            | `group`            | `groupRepository.findById`                   | Yes — DB query     | ✓ FLOWING   |
| `getGroupMembers`     | `users`            | `userRepository.findByGroupId`               | Yes — DB query     | ✓ FLOWING   |
| `getTeacherSubjects`  | `assignments`      | `assignmentRepository.findByTeacherIdAndSemesterId` | Yes — DB query | ✓ FLOWING |
| `isHeadman`           | `userOpt`          | `userRepository.findById`                    | Yes — DB query     | ✓ FLOWING   |
| `getActiveSemester`   | `semester`         | `semesterRepository.findByIsActiveTrue`      | Yes — DB query     | ✓ FLOWING   |
| `getCampusGeofence`   | `setting`          | `campusSettingRepository.findById(1L)`       | Yes — DB query     | ✓ FLOWING   |
| `getUserById`         | `user`             | `userRepository.findByIdIncludingArchived`   | Yes — native query | ✓ FLOWING   |

---

### Behavioral Spot-Checks

Step 7b: Spot-checks require a running Testcontainers PostgreSQL — server cannot be started in isolation during verification. Tests were already executed by the implementation phase per the summaries.

| Behavior                                            | Evidence                                              | Status  |
|-----------------------------------------------------|-------------------------------------------------------|---------|
| All 7 RPCs pass integration tests                   | 07-02-SUMMARY.md: "15 tests, 0 failures"              | ✓ PASS  |
| Full test suite still passes (no regression)        | 07-02-SUMMARY.md: "34 total tests, 0 failures"        | ✓ PASS  |
| Proto compilation produces Java stubs               | `build/generated/source/proto/main/` directory exists and contains all types | ✓ PASS |
| gRPC port configured as 19091                       | `application.yml` line 44: `grpc.server.port: 19091`  | ✓ PASS  |

---

### Requirements Coverage

All 7 requirement IDs are claimed by both 07-01-PLAN.md and 07-02-PLAN.md (`requirements: [GRPC-01, GRPC-02, GRPC-03, GRPC-04, GRPC-05, GRPC-06, GRPC-07]`). All 7 are marked `[x]` Complete in REQUIREMENTS.md and mapped to Phase 7 in the Traceability table.

| Requirement | Source Plan | Description                                          | Status       | Evidence                                                  |
|-------------|-------------|------------------------------------------------------|--------------|-----------------------------------------------------------|
| GRPC-01     | 07-01, 07-02 | GetGroup returns group info by ID                   | ✓ SATISFIED  | `getGroup` method + `getGroup_validId_returnsGroupInfo` test |
| GRPC-02     | 07-01, 07-02 | GetGroupMembers returns active students in a group  | ✓ SATISFIED  | `getGroupMembers` method + `getGroupMembers_archivedUserInGroup_notReturned` test |
| GRPC-03     | 07-01, 07-02 | GetTeacherSubjects returns teacher's subjects+groups | ✓ SATISFIED  | `getTeacherSubjects` method + `getTeacherSubjects_returnsSubjectsWithGroups` test |
| GRPC-04     | 07-01, 07-02 | IsHeadman checks if user is headman of a group      | ✓ SATISFIED  | `isHeadman` method + 3 test cases including user-not-found returns false |
| GRPC-05     | 07-01, 07-02 | GetActiveSemester returns current active semester   | ✓ SATISFIED  | `getActiveSemester` method + 2 test cases (active + NOT_FOUND) |
| GRPC-06     | 07-01, 07-02 | GetCampusGeofence returns campus coordinates/radius | ✓ SATISFIED  | `getCampusGeofence` method + `getCampusGeofence_returnsCampusSettings` test |
| GRPC-07     | 07-01, 07-02 | GetUserById returns user info including archived    | ✓ SATISFIED  | `getUserById` uses `findByIdIncludingArchived` + 3 test cases including archived user |

No orphaned requirements: REQUIREMENTS.md maps exactly GRPC-01 through GRPC-07 to Phase 7 with no additional IDs.

---

### Anti-Patterns Found

No anti-patterns detected in the implementation files.

| File                          | Line | Pattern   | Severity | Impact |
|-------------------------------|------|-----------|----------|--------|
| (none found)                  | —    | —         | —        | —      |

Specific checks run:
- No `TODO/FIXME/PLACEHOLDER` comments in grpc package files
- No `return null` or empty stub implementations — all 7 RPCs call repositories and build response objects
- No REST service classes (`GroupService`, `UserService`, `SemesterService`) injected into `AcademicGrpcServiceImpl`
- No `RequestContext` usage in gRPC service (comment in Javadoc only)
- `getTeacherSubjects` uses `.filter(info -> info != null)` which is a legitimate null-guard for missing subject/group lookups, not a stub

---

### Human Verification Required

None — all observable truths can be verified programmatically. The integration test suite with Testcontainers covers end-to-end behavior including NOT_FOUND error paths and soft-delete filtering.

---

## Gaps Summary

No gaps. All 12 truths verified, all 8 artifacts exist and are substantive, all 6 key links are wired, data flows from PostgreSQL through JPA to protobuf in all 7 RPCs, and all 7 requirement IDs are fully satisfied with integration test coverage.

---

_Verified: 2026-03-30T21:00:00Z_
_Verifier: Claude (gsd-verifier)_
