# Student group ranking JS-STUDENT17 / AT05

## Scope

Implement the backend-only current-student group attendance ranking contract in this worktree. No UI, deployment, main merge, or push. The implementation does not change event transfer reconciliation, `LessonEventService`, `AttendanceDocument`, repositories, journal services, or base write services. Transfer reconciliation belongs to a separate writer/worktree; parent notes that merged acceptance must not count partially transferred marks silently.

## Contract and acceptance criteria

- `GET /api/v1/student/statistics/ranking?semesterId=...&page=...&size=...`; page is zero-based, omitted page anchors the page on the signed-in student; default size 20, maximum 100.
- Current student, group and cohort are resolved from signed student claims / Academic scope; there is no client `groupId` or other-peer detail route.
- Response contains `available`, `page`, `size`, `total`, `ownPosition`, and bounded rows containing `id`, `name`, `position`, `percentage`, `isSelf`.
- Ranking reuses existing metric calculation and the canonical semester cohort denominator. Exact count ratios order rows; stable student id resolves display order inside ties and equal ratios keep competition rank.
- Cohort names are read from Academic's current group roster and intersected with the authoritative projection roster. Missing/invalid identity fails closed; foreign names are not returned.
- Hidden/ineligible or unrankable own state returns an unavailable empty page; personal statistics DTO remains own-only.

Source/policy evidence: root approved the compact API and reserved `proto/attendance.proto` for this writer. Project workflow source in this worktree: `.agent/orchestration-v2/RULES.md`, `services/AGENTS.md`, `tests/AGENTS.md`; authoritative main `RULES.md` SHA-256: `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`. Canonical job story: `docs/product/job-stories.yaml`, JS-STUDENT-17. The relevant story requires group names/percent/position, group-only scope, current-student pagination anchor, and no peer detail.

## Changed files

- `proto/attendance.proto`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculatorTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionServiceTest.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentApiController.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentApiControllerCacheControlTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java`

The compile diagnostic log is `.agent/student-ranking-compileJava-info-20260929.log`; the retry and exact classpath/source diagnostic are in `.agent/student-ranking-attendance-rerun-20260929.log` and `.agent/student-ranking-classpath-diag-20260929.log`. The temporary diagnostic init script is `.agent/student-ranking-classpath-diag-20260929.init.gradle`. Pre-existing untracked `.agent` fixtures and other agents' files were left untouched.

## Correction after independent review

Review finding: Attendance derived the ranking denominator's subjects from the viewer's personal membership segments. A late joiner therefore lost group lessons for a subject whose assignment ended before the viewer joined. The correction adds a distinct full-semester `rankSubjects` set from the authorized rank group, serializes it in the existing Academic projection response, and uses it only for canonical ranking lessons. Personal `subjects`, membership segments, and metrics remain based on the viewer's own membership.

Regression evidence: the Academic fixture has a Sep 1–6 subject assignment and a viewer joining Sep 10; it asserts the rank subject is present while personal subjects/segment subject IDs stay empty. The Attendance fixture adds the pre-join held cohort lesson and asserts the viewer still has personal `heldCount=1`/`presentCount=1` while ranking is position 2 of 2.

Correction files (seven):

- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScope.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeServiceTest.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionServiceTest.java`

Correction diff against `9267c41a`: seven code/test files plus this evidence update, 156 insertions and 25 deletions.

## Checks and evidence

- `git diff --check` — exit 0 on the correction diff.
- Environment: `JAVA_HOME=C:\Users\maksd\.jdks\ms-21.0.10` (Microsoft OpenJDK 21); approved elevated execution; Gradle wrapper; one consumer at a time; `--no-problems-report --no-daemon --no-parallel --max-workers=1`.
- `:services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeServiceTest' --tests 'ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest' --no-problems-report --no-daemon --no-parallel --max-workers=1` — exit 0. JUnit XML: scope service 19/19 and gRPC serializer 7/7; failures/errors/skipped 0. Log: `.agent/student-ranking-academic-correction-tests-20260929.log`.
- `:services:attendance-service:attendance-app:test --tests 'ru.rutcampustrack.attendance.report.studentprojection.OwnRankCalculatorTest' --tests 'ru.rutcampustrack.attendance.report.studentprojection.StudentAttendanceProjectionServiceTest' --no-problems-report --no-daemon --no-parallel --max-workers=1` — exit 0. JUnit XML: rank calculator 9/9 and projection service 4/4; failures/errors/skipped 0. Log: `.agent/student-ranking-attendance-correction-tests-20260929.log`.
- The earlier feature BFF target passed under the same JDK21 setup with `--no-problems-report`; XML was 25/25 `MobileAttendanceClientErrorTest`, 4/4 `StudentApiControllerCacheControlTest`, and 9/9 `StudentQueryHomeworkTest`, all with zero failures/errors/skips. It was not rerun because this correction changes no BFF source or Attendance response contract. Logs: `.agent/student-ranking-bff-elevated-tests-no-problems-report-20260929.log`.
- Initial non-elevated Attendance compile attempts failed before ranking assertions while resolving cross-project classes. The approved elevated compile/test runs above pass; no product or build workaround was made. Earlier diagnostic logs remain local and are not part of the correction commit.
- Root reports the independent Sol source recheck passed for the corrected denominator finding. The full integration/final gate remains with root.
- Runtime endpoint evidence — not collected. No server was started; no HTTP runtime acceptance is claimed.

## Limits / follow-up

The separate transfer writer owns transfer event reconciliation and pending-operation reads. This ranking change does not alter `LessonEventService`, `AttendanceDocument`, repositories, journal services, or base write services. Before integrated runtime acceptance, verify pending/completed transfer marks are reconciled so a partial transfer is not silently counted against either cohort. No transfer-aware runtime result is claimed.
