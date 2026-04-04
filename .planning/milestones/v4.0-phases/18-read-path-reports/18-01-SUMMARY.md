---
phase: 18-read-path-reports
plan: 01
subsystem: grpc
tags: [grpc, protobuf, academic-service, java]

# Dependency graph
requires:
  - phase: 15-infrastructure-foundation
    provides: gRPC infrastructure and clients for attendance service
provides:
  - GetSubjectsByIds RPC in academic.proto (GRPC-08)
  - Server-side implementation in AcademicGrpcServiceImpl
affects:
  - 18-02 (attendance-service gRPC client for subject name resolution)
  - 18-03 (student stats report uses subject names)

# Tech tracking
tech-stack:
  added: []
  patterns: [proto-first gRPC extension, batch ID lookup via JPA findAllById]

key-files:
  created: []
  modified:
    - proto/academic.proto
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java

key-decisions:
  - "GetSubjectsByIds not cached — infrequent batch lookup for report generation, caching overhead not justified"
  - "SubjectInfo proto message is distinct from TeacherSubjectInfo — only subject_id + subject_name fields"

patterns-established:
  - "GRPC-08 pattern: batch lookup via JPA findAllById returns only found entities (no error on missing IDs)"

requirements-completed:
  - RPRT-03

# Metrics
duration: 15min
completed: 2026-04-04
---

# Phase 18 Plan 01: GetSubjectsByIds gRPC RPC Summary

**GetSubjectsByIds RPC added to academic.proto (GRPC-08) with server implementation via SubjectRepository.findAllById batch query**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-04T15:45:00Z
- **Completed:** 2026-04-04T16:00:41Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added GetSubjectsByIds RPC to AcademicGrpcService service definition in academic.proto
- Added SubjectsByIdsRequest, SubjectsByIdsResponse, and SubjectInfo proto messages
- Implemented getSubjectsByIds override in AcademicGrpcServiceImpl using JPA batch findAllById
- Proto generation and Java compilation succeed cleanly

## Task Commits

Each task was committed atomically:

1. **Task 1: Add GetSubjectsByIds RPC to academic.proto** - `e6397dd` (feat)
2. **Task 2: Implement getSubjectsByIds in AcademicGrpcServiceImpl** - `96c0590` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified
- `proto/academic.proto` - Added GetSubjectsByIds RPC and SubjectsByIdsRequest/SubjectsByIdsResponse/SubjectInfo messages
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` - Added GRPC-08 getSubjectsByIds method implementation

## Decisions Made
- GetSubjectsByIds is not cached (unlike getGroup/getGroupMembers) — report generation is infrequent, and caching batch lookups adds complexity without clear benefit
- SubjectInfo proto message has only subject_id + subject_name (no type field) — attendance reports only need names for display, not type

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 02 (attendance-service AcademicGrpcClient) can now add GetSubjectsByIds stub call — the RPC definition exists and compiles
- Plan 03 (student stats report) depends on this subject name resolution path being available

---
*Phase: 18-read-path-reports*
*Completed: 2026-04-04*
