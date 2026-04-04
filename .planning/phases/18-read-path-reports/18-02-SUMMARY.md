---
phase: 18-read-path-reports
plan: 02
subsystem: attendance-service
tags: [contracts, grpc, port-interface, hateoas, archunit]
dependency_graph:
  requires: [proto/academic.proto (GetSubjectsByIds RPC from plan 01)]
  provides: [AttendanceReadPort, AttendanceRecord, ReportApi, 9 response DTOs, AcademicGrpcClient.getSubjectsByIds]
  affects: [attendance-service/attendance-app, attendance-service/attendance-api-contract, academic-service]
tech_stack:
  added: [archunit-junit5:1.3.0]
  patterns: [port-interface isolation, MongoTemplate Criteria queries, plain Java DTOs with RepresentationModel]
key_files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceReadPort.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceRecord.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceReadPortImpl.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/LessonAttendanceResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/StudentAttendanceEntry.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalStudentRow.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalCell.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/StudentStatsResponse.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/SubjectStats.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/OverallStats.java
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/AttendanceRecordEntry.java
  modified:
    - proto/academic.proto
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java
    - services/attendance-service/attendance-app/build.gradle.kts
decisions:
  - AttendanceReadPort in shared/port/ with zero checkin imports — report/ domain accesses checkin data only via this interface
  - AttendanceReadPortImpl in checkin/ uses MongoTemplate (not AttendanceRepository) for flexible query construction
  - getSubjectsByIds returns Map<Long, String> (id->name) so callers do simple getOrDefault lookup
metrics:
  duration: ~15 minutes
  completed: 2026-04-04
  tasks: 2
  files: 17
---

# Phase 18 Plan 02: Contracts, Port Interface, and gRPC Additions Summary

**One-liner:** ReportApi contract (4 GET endpoints), 9 plain-Java response DTOs, AttendanceReadPort isolation layer, AttendanceReadPortImpl with MongoTemplate Criteria queries, and AcademicGrpcClient.getSubjectsByIds returning Map<Long,String>.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Shared port layer + AttendanceReadPortImpl + gRPC additions + build config | 8f0cba8 | AttendanceReadPort.java, AttendanceRecord.java, AttendanceReadPortImpl.java, AcademicGrpcClient.java, build.gradle.kts, academic.proto, AcademicGrpcServiceImpl.java |
| 2 | Contract DTOs and ReportApi interface | 58f7ed1 | ReportApi.java, 9 DTO files |

## What Was Built

### Shared Port Layer (domain isolation)

- `AttendanceReadPort` — interface in `shared/port/` with 3 query methods: `findByLessonId`, `findByUserId`, `findByGroupAndSubject`. Zero imports from `checkin/`.
- `AttendanceRecord` — read-only Java record in `shared/port/`, imports only `contract.enums.*` and `java.*`.
- `AttendanceReadPortImpl` — `@Component` in `checkin/` package, implements port using `MongoTemplate` + Criteria API. Uses inclusive date boundaries (`.gte(from).lte(to)`). Maps `AttendanceDocument` to `AttendanceRecord` via private `toRecord()` method.

### gRPC Client Additions

- `AcademicGrpcClient.getTeacherSubjects(Long teacherId, Long semesterId)` — calls `GetTeacherSubjects` RPC with 3s deadline.
- `AcademicGrpcClient.getSubjectsByIds(List<Long> subjectIds)` — returns `Map<Long, String>` (subjectId -> subjectName), handles null/empty list with `Map.of()`, uses `Collectors.toMap` with merge function.

### Proto Extension (prerequisite for plan 01 — applied inline)

Added to `academic.proto`:
- `GetSubjectsByIds` RPC in service definition
- `SubjectsByIdsRequest`, `SubjectsByIdsResponse`, `SubjectInfo` messages

Implemented `getSubjectsByIds` in `AcademicGrpcServiceImpl` (GRPC-08) using `SubjectRepository.findAllById`.

### Contract DTOs (9 files, all plain Java, no Lombok)

| File | Extends | Purpose |
|------|---------|---------|
| `LessonAttendanceResponse` | `RepresentationModel` | RPRT-01 top-level |
| `StudentAttendanceEntry` | — | nested per-student entry |
| `JournalResponse` | `RepresentationModel` | RPRT-02 top-level |
| `JournalStudentRow` | — | one row per student |
| `JournalCell` | — | one cell (date+lesson+status+symbol) |
| `StudentStatsResponse` | `RepresentationModel` | RPRT-03 top-level |
| `SubjectStats` | — | per-subject stats with resolved name |
| `OverallStats` | — | aggregated across all subjects |
| `AttendanceRecordEntry` | — | RPRT-04 individual record |

### ReportApi Interface

4 endpoints per D-02:
- `GET /reports/lesson/{lessonId}` → `EntityModel<LessonAttendanceResponse>`
- `GET /reports/journal?groupId&subjectId&dateFrom&dateTo` → `EntityModel<JournalResponse>`
- `GET /reports/student/stats` → `EntityModel<StudentStatsResponse>`
- `GET /reports/student/records?subjectId` → `CollectionModel<EntityModel<AttendanceRecordEntry>>`

### Build Config

Added `testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")` for plan 04 architecture tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added GetSubjectsByIds proto + AcademicGrpcServiceImpl implementation**
- **Found during:** Task 1
- **Issue:** Plan 02 depends on `SubjectsByIdsRequest/SubjectsByIdsResponse` gRPC-generated classes in `AcademicGrpcClient`. These are generated from `academic.proto`, but plan 01 (which adds them to the proto) runs in the same wave and may not have executed yet.
- **Fix:** Added `GetSubjectsByIds` RPC + messages to `academic.proto` and implemented `getSubjectsByIds` in `AcademicGrpcServiceImpl` inline within this plan.
- **Files modified:** `proto/academic.proto`, `services/academic-service/academic-app/.../AcademicGrpcServiceImpl.java`
- **Commit:** 8f0cba8

## Known Stubs

None — this plan creates interfaces and contracts only, no service logic with placeholder data.

## Self-Check: PASSED

Files verified:
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceReadPort.java` — FOUND
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceRecord.java` — FOUND
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceReadPortImpl.java` — FOUND
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` — FOUND

Commits verified:
- 8f0cba8 — FOUND (task 1)
- 58f7ed1 — FOUND (task 2)

Both compile tasks pass: `BUILD SUCCESSFUL`
