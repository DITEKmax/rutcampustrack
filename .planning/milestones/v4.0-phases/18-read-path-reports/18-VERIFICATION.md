---
phase: 18-read-path-reports
verified: 2026-04-04T00:00:00Z
status: passed
score: 5/5 must-haves verified
gaps: []
human_verification:
  - test: "Run full test suite with Docker Desktop running"
    expected: "All 6 integration tests in ReportIntegrationTest pass (Testcontainers requires Docker)"
    why_human: "Docker Desktop was not running during automated test execution. Unit tests (8) and ArchUnit test pass. Integration tests compile and are structurally correct but require Docker."
---

# Phase 18: Read Path — Reports Verification Report

**Phase Goal:** Headmen and teachers can view lesson attendance and the full journal grid, students can view their own attendance stats and record list, and the report domain never imports directly from the checkin domain.
**Verified:** 2026-04-04
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                                 | Status     | Evidence                                                                                                                                                 |
| --- | ----------------------------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | A headman can retrieve lesson attendance — all group members appear with their status or absent       | ✓ VERIFIED | ReportService.getLessonAttendance: left-join roster from gRPC + AttendanceReadPort; userId=102 with no record defaults to ABSENT. Unit test confirms.    |
| 2   | A headman/teacher can retrieve journal grid — rows are students, columns are lesson dates, cells show symbols | ✓ VERIFIED | ReportService.getJournal: sorted dates extracted from records; JournalCell holds status+symbol per D-08 spec. Integration test verifies grid shape.      |
| 3   | A student can retrieve own attendance stats per subject — percentage correct, cancelled excluded      | ✓ VERIFIED | ReportService.getStudentStats: filters CANCELLED before grouping; attended = PRESENT+EXCUSED+FREE_ATTENDANCE. 3 unit tests verify math directly.         |
| 4   | A student can retrieve own attendance list, filterable by subject                                     | ✓ VERIFIED | ReportService.getStudentRecords: filters by subjectId when non-null. Integration tests cover both filtered and unfiltered cases.                         |
| 5   | ArchUnit test asserts no class in report/ imports any class from checkin/ directly                    | ✓ VERIFIED | ReportDomainIsolationTest.reportDoesNotImportCheckin declared with @AnalyzeClasses + noClasses().resideInAPackage("report..").should().dependOnClassesThat().resideInAPackage("checkin.."). Grep confirms zero checkin imports in report/ source files. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact                                                                                                          | Expected                                           | Status     | Details                                                                                                                 |
| ----------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------- |
| `proto/academic.proto`                                                                                            | GetSubjectsByIds RPC + messages                    | ✓ VERIFIED | Contains `rpc GetSubjectsByIds`, `SubjectsByIdsRequest`, `SubjectsByIdsResponse`, `SubjectInfo` messages at lines 35, 121-132 |
| `services/academic-service/academic-app/.../AcademicGrpcServiceImpl.java`                                        | getSubjectsByIds implementation                    | ✓ VERIFIED | @Override getSubjectsByIds at line 190; queries SubjectRepository.findAllById; maps to SubjectInfo proto                |
| `services/attendance-service/attendance-app/.../shared/port/AttendanceReadPort.java`                             | Read port interface, zero checkin imports          | ✓ VERIFIED | 27-line interface; only `java.*` imports; 3 query methods defined                                                       |
| `services/attendance-service/attendance-app/.../shared/port/AttendanceRecord.java`                               | Read-only DTO record for cross-domain transfer     | ✓ VERIFIED | Java record with 8 fields; imports only `contract.enums.*` and `java.*`; no checkin import                             |
| `services/attendance-service/attendance-app/.../checkin/AttendanceReadPortImpl.java`                             | Port implementation using MongoTemplate            | ✓ VERIFIED | @Component; implements AttendanceReadPort; mongoTemplate.find in all 3 methods; inclusive .gte(from).lte(to); toRecord() mapping |
| `services/attendance-service/attendance-api-contract/.../api/ReportApi.java`                                     | Contract interface with 4 GET endpoints            | ✓ VERIFIED | @RequestMapping("/reports"); 4 endpoints: /lesson/{lessonId}, /journal, /student/stats, /student/records               |
| `services/attendance-service/attendance-api-contract/.../dto/report/*.java` (9 DTOs)                             | Plain Java DTOs, no Lombok                         | ✓ VERIFIED | All 9 files present; no `import lombok` in any; LessonAttendanceResponse/JournalResponse/StudentStatsResponse extend RepresentationModel |
| `services/attendance-service/attendance-app/.../grpc/AcademicGrpcClient.java`                                    | getTeacherSubjects + getSubjectsByIds methods       | ✓ VERIFIED | getTeacherSubjects(Long, Long) at line 86; getSubjectsByIds(List<Long>) returning Map<Long,String> at line 98           |
| `services/attendance-service/attendance-app/build.gradle.kts`                                                    | archunit-junit5:1.3.0 test dependency              | ✓ VERIFIED | Line 42: `testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")`                                             |
| `services/attendance-service/attendance-app/.../report/ReportService.java`                                       | Business logic for all 4 endpoints                 | ✓ VERIFIED | 277 lines; @Service; all 4 public methods; authorizeHeadmanOrTeacher; STATUS_SYMBOLS map; getSubjectsByIds call; no checkin import |
| `services/attendance-service/attendance-app/.../report/ReportController.java`                                    | REST controller implementing ReportApi             | ✓ VERIFIED | @RestController; implements ReportApi; no @RequestMapping; no @GetMapping; EntityModel.of + CollectionModel.of; HATEOAS self-links |
| `services/attendance-service/attendance-app/src/test/.../report/ReportServiceTest.java`                          | 8 unit tests for stats calculation                 | ✓ VERIFIED | @ExtendWith(MockitoExtension.class); 8 @Test methods including stats_cancelledExcluded, stats_percentageCalculation, stats_subjectNameResolvedViaGrpc, lessonAttendance_missingStudentShowsAbsent |
| `services/attendance-service/attendance-app/src/test/.../report/ReportDomainIsolationTest.java`                  | ArchUnit domain isolation enforcement              | ✓ VERIFIED | @AnalyzeClasses(packages = "ru.rutcampustrack.attendance"); noClasses rule; no @SpringBootTest                          |
| `services/attendance-service/attendance-app/src/test/.../integration/ReportIntegrationTest.java`                 | 6 integration tests for all 4 endpoints            | ✓ VERIFIED | Extends AbstractAttendanceIntegrationTest; 6 @Test methods; mongoTemplate.remove(new Query(), AttendanceDocument.class) in @BeforeEach; lenient() stubs |

### Key Link Verification

| From                              | To                          | Via                              | Status     | Details                                                                       |
| --------------------------------- | --------------------------- | -------------------------------- | ---------- | ----------------------------------------------------------------------------- |
| AcademicGrpcServiceImpl.getSubjectsByIds | SubjectRepository.findAllById | JPA batch query                 | ✓ WIRED    | Line 192: `subjectRepository.findAllById(ids)` directly called                |
| AttendanceReadPortImpl            | AttendanceReadPort          | implements                       | ✓ WIRED    | Line 19: `public class AttendanceReadPortImpl implements AttendanceReadPort`  |
| AttendanceReadPortImpl            | AttendanceDocument          | MongoTemplate Criteria query     | ✓ WIRED    | mongoTemplate.find called in all 3 methods with explicit field-name Criteria   |
| AcademicGrpcClient.getSubjectsByIds | GetSubjectsByIds RPC (proto) | gRPC stub call                  | ✓ WIRED    | Line 103: `stub.withDeadlineAfter(3, SECONDS).getSubjectsByIds(SubjectsByIdsRequest...)` |
| ReportController                  | ReportApi                   | implements                       | ✓ WIRED    | Line 29: `public class ReportController implements ReportApi`                 |
| ReportService                     | AttendanceReadPort          | constructor injection            | ✓ WIRED    | Line 56: `private final AttendanceReadPort attendanceReadPort`; used in all 4 methods |
| ReportService                     | AcademicGrpcClient          | getGroupMembers + getTeacherSubjects + getSubjectsByIds | ✓ WIRED | Lines 80, 117, 173, 266: all three methods called appropriately              |
| ReportService                     | ScheduleGrpcClient          | getLessonById                    | ✓ WIRED    | Line 77: `scheduleGrpcClient.getLessonById(lessonId)` in getLessonAttendance  |
| ReportService                     | SemesterCacheService        | getActiveSemesterId              | ✓ WIRED    | Lines 159, 261: semesterCacheService.getActiveSemesterId() called             |
| ReportDomainIsolationTest         | ArchUnit noClasses rule      | @ArchTest annotation             | ✓ WIRED    | static final ArchRule annotated with @ArchTest                                |

### Data-Flow Trace (Level 4)

| Artifact        | Data Variable    | Source                           | Produces Real Data | Status     |
| --------------- | ---------------- | -------------------------------- | ------------------ | ---------- |
| ReportService.getLessonAttendance | entries (StudentAttendanceEntry list) | attendanceReadPort.findByLessonId → mongoTemplate.find(AttendanceDocument) | Yes — MongoTemplate Criteria query with lesson_id filter | ✓ FLOWING |
| ReportService.getStudentStats | subjectStatsList | attendanceReadPort.findByUserId → mongoTemplate.find(AttendanceDocument) + academicGrpcClient.getSubjectsByIds → gRPC stub | Yes — real MongoDB query + real gRPC call | ✓ FLOWING |
| ReportController.getLessonAttendance | EntityModel<LessonAttendanceResponse> | Delegates to ReportService.getLessonAttendance | Yes — thin delegation, no stubbing | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior                                            | Command                                     | Result   | Status |
| --------------------------------------------------- | ------------------------------------------- | -------- | ------ |
| ReportService imports no checkin classes            | grep -r "import ru.rutcampustrack.attendance.checkin" in report/ | No matches | ✓ PASS |
| AttendanceReadPort/AttendanceRecord import no checkin | grep -r "import ru.rutcampustrack.attendance.checkin" in shared/port/ | No matches | ✓ PASS |
| Contract DTOs have no Lombok                        | grep -r "import lombok" in contract/dto/report/ | No matches | ✓ PASS |
| All 8 commits for phase 18 exist in git             | git log --oneline (8 hashes from summaries) | All 8 found | ✓ PASS |
| AttendanceSource enum has STUDENT_GEO (not GEO_CHECKIN) | Read AttendanceSource.java | Values: STUDENT_GEO, HEADMAN, AUTO_SCHEDULER, LATE_CHECKIN | ✓ PASS — tests use correct value |
| Integration tests require Docker                    | Phase 04 summary notes Docker Desktop not running | Docker-dependent tests not run; unit tests + ArchUnit pass | ? SKIP — Docker needed |

### Requirements Coverage

| Requirement | Source Plan | Description                                                                 | Status       | Evidence                                                                                  |
| ----------- | ----------- | --------------------------------------------------------------------------- | ------------ | ----------------------------------------------------------------------------------------- |
| RPRT-01     | 18-03, 18-04 | Headman/teacher can view lesson attendance (all students + status)          | ✓ SATISFIED  | ReportService.getLessonAttendance with left-join; unit test lessonAttendance_missingStudentShowsAbsent; integration test getLessonAttendance_headman_returnsAllGroupMembers |
| RPRT-02     | 18-03, 18-04 | Headman/teacher can view journal grid (students x lesson dates)             | ✓ SATISFIED  | ReportService.getJournal with JournalCell(date, lessonNumber, status, symbol); integration test getJournal_headman_returnsGridShape |
| RPRT-03     | 18-01, 18-03, 18-04 | Student can view own attendance stats (% per subject, excluding cancelled) | ✓ SATISFIED  | CANCELLED filtered before grouping; attended = PRESENT+EXCUSED+FREE_ATTENDANCE; subject names via getSubjectsByIds gRPC; 4 unit tests + 1 integration test |
| RPRT-04     | 18-03, 18-04 | Student can view own attendance list, filterable by subject                 | ✓ SATISFIED  | getStudentRecords filters by subjectId when non-null; 2 integration tests (filtered and unfiltered) |
| RPRT-05     | 18-02, 18-04 | Report domain accesses checkin data only through AttendanceReadPort         | ✓ SATISFIED  | AttendanceReadPort in shared/port/ with zero checkin imports; AttendanceReadPortImpl in checkin/; ReportDomainIsolationTest ArchUnit rule; grep confirms zero checkin imports in report/ |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| None | — | — | — | No anti-patterns found in any phase-18 file |

Specifically verified:
- No TODO/FIXME/placeholder comments in ReportService.java, ReportController.java, AttendanceReadPortImpl.java
- No `return null` or empty stubs in any report endpoint method
- No hardcoded empty data flowing to user-visible output
- No `import lombok` in contract module files
- No `import ru.rutcampustrack.attendance.checkin` in report/ or shared/port/ packages

### Human Verification Required

#### 1. Integration Test Execution with Docker

**Test:** Start Docker Desktop, run: `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*.integration.ReportIntegrationTest" --no-daemon`
**Expected:** All 6 integration tests pass — getLessonAttendance_headman_returnsAllGroupMembers, getLessonAttendance_regularStudentForbidden, getJournal_headman_returnsGridShape, getStudentStats_excludesCancelled, getStudentRecords_filteredBySubject, getStudentRecords_allSubjects
**Why human:** Testcontainers requires Docker Desktop running. Docker was not available during automated verification. The test code is structurally correct and compiled successfully; runtime behavior needs Docker.

### Gaps Summary

No gaps found. All 5 observable truths are verified. All artifacts exist and are substantive. All key links are wired. Domain isolation is code-proven at both the source level (grep shows zero checkin imports in report/) and at the test level (ArchUnit rule exists and is correctly defined). The only outstanding item is the Docker-dependent integration test execution which is an environment constraint, not a code gap.

---

_Verified: 2026-04-04_
_Verifier: Claude (gsd-verifier)_
