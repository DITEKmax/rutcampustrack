---
phase: 18-read-path-reports
plan: "03"
subsystem: api
tags: [java, spring-boot, hateoas, grpc, mongodb, reports, attendance]

requires:
  - phase: 18-02
    provides: "ReportApi contract, all report DTOs, AttendanceReadPort, AcademicGrpcClient with getSubjectsByIds"
  - phase: 18-01
    provides: "GetSubjectsByIds gRPC RPC in AcademicGrpcClient"

provides:
  - "ReportService: business logic for all 4 report endpoints with authorization"
  - "ReportController: REST layer implementing ReportApi contract with HATEOAS"
  - "authorizeHeadmanOrTeacher: headman (own group) or teacher (getTeacherSubjects gRPC) auth"
  - "Left-join roster logic: group members from gRPC, attendance from MongoDB, ABSENT default"
  - "Student stats: CANCELLED excluded, attended=PRESENT+EXCUSED+FREE_ATTENDANCE, subject names via getSubjectsByIds"

affects:
  - "18-04-tests (reads these service/controller implementations)"

tech-stack:
  added: []
  patterns:
    - "ReportService delegates to AttendanceReadPort (shared/port) — no direct checkin imports"
    - "Controller thin delegation pattern: delegates to service, wraps in HATEOAS EntityModel/CollectionModel"
    - "Left-join attendance: gRPC roster + MongoDB records, default ABSENT for missing entries"
    - "Batch gRPC subject name resolution via getSubjectsByIds before per-subject stats assembly"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java
  modified: []

key-decisions:
  - "ReportService uses AttendanceReadPort exclusively — zero imports from checkin/ package (domain isolation)"
  - "authorizeHeadmanOrTeacher: STUDENT role → headman flag + group match; TEACHER role → getTeacherSubjects gRPC; ADMIN → 403"
  - "Left-join default is ABSENT for students with no attendance record (not null, not missing entry)"
  - "getStudentStats filters CANCELLED before grouping — cancelled lessons don't affect percentage"
  - "Subject names resolved via batch getSubjectsByIds call — never null, defaults to 'Unknown' if gRPC misses ID"

patterns-established:
  - "Report domain isolation: report/ only imports shared/port/, contract.*, grpc.*, semester.*, security.*"
  - "HATEOAS: EntityModel.of with linkTo(methodOn(Controller.class).method()).withSelfRel()"
  - "CollectionModel.of(entityModels, selfLink) for list endpoints"

requirements-completed:
  - RPRT-01
  - RPRT-02
  - RPRT-03
  - RPRT-04

duration: 5min
completed: 2026-04-04
---

# Phase 18 Plan 03: ReportService + ReportController Summary

**ReportService with 4-endpoint read-path logic (left-join roster, journal grid, CANCELLED-excluded stats with gRPC subject name resolution, filterable records) plus thin ReportController implementing ReportApi with HATEOAS wrapping**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-04T16:07:57Z
- **Completed:** 2026-04-04T16:12:25Z
- **Tasks:** 2 of 2
- **Files modified:** 2

## Accomplishments

- ReportService implements all 4 RPRT requirements with proper authorization: headman checks group ownership, teacher checks via getTeacherSubjects gRPC
- Left-join pattern: group roster from AcademicGrpcClient, attendance from AttendanceReadPort, students with no record default to ABSENT
- Student stats correctly exclude CANCELLED and count attended as PRESENT+EXCUSED+FREE_ATTENDANCE; subject names resolved via getSubjectsByIds batch call (D-13)
- ReportController delegates to ReportService and wraps responses in EntityModel/CollectionModel with HATEOAS self-links
- Domain isolation maintained: no import from checkin/ in any report/ file

## Task Commits

1. **Task 1: ReportService with all 4 endpoint methods + authorization** - `2419f72` (feat)
2. **Task 2: ReportController implementing ReportApi** - `db53b6c` (feat)

## Files Created/Modified

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java` - Business logic: getLessonAttendance, getJournal, getStudentStats, getStudentRecords, authorizeHeadmanOrTeacher
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java` - REST layer: implements ReportApi, delegates to ReportService, wraps in HATEOAS

## Decisions Made

- `StudentInfo.display_name` used from proto (not first_name+last_name which don't exist in proto schema)
- `TeacherSubjectInfo.getSubjectId()`/`getGroupId()` return primitive `long` from proto; used `.longValue()` for safe comparison with boxed `Long` parameters
- Kept `uid` as `Long` (not `long`) in stream lambdas for type-safe `.equals()` comparisons against `Long` record fields

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed proto field name mismatch for StudentInfo display name**
- **Found during:** Task 1 (ReportService implementation)
- **Issue:** Plan specified `student.getFirstName() + " " + student.getLastName()` but `StudentInfo` proto only has `display_name` field
- **Fix:** Changed to `student.getDisplayName()` in both getLessonAttendance and getJournal
- **Files modified:** ReportService.java
- **Verification:** compileJava succeeded
- **Committed in:** 2419f72 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed Long/long comparison in authorizeHeadmanOrTeacher**
- **Found during:** Task 1 (ReportService implementation)
- **Issue:** Plan used `s.getSubjectId() == subjectId` comparing proto `long` with boxed `Long` parameter — would work due to autoboxing but semantically fragile; used `.longValue()` for clarity
- **Fix:** `s.getSubjectId() == subjectId.longValue() && s.getGroupId() == groupId.longValue()`
- **Files modified:** ReportService.java
- **Verification:** compileJava succeeded
- **Committed in:** 2419f72 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes essential for correctness. No scope creep.

## Issues Encountered

None beyond the proto field name deviation above.

## Known Stubs

None — all 4 endpoints are fully wired with real gRPC calls and MongoDB reads.

## Next Phase Readiness

- ReportService and ReportController are complete and compile
- Ready for Phase 18-04: integration tests for report endpoints
- No blockers

---
*Phase: 18-read-path-reports*
*Completed: 2026-04-04*
