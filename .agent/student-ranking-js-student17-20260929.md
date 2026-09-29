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

## Checks and evidence

- `git diff --check` — exit 0 (after implementation and diagnostic run).
- `:services:attendance-service:attendance-app:test --tests 'ru.rutcampustrack.attendance.report.studentprojection.OwnRankCalculatorTest' --tests 'ru.rutcampustrack.attendance.report.studentprojection.StudentAttendanceProjectionServiceTest' --no-daemon --no-parallel --max-workers=1` — exit 1. Proto generation completed, but `attendance-app:compileJava` failed before tests with 100 unresolved-package/symbol diagnostics.
- `:services:attendance-service:attendance-app:compileJava --info --no-daemon --no-parallel --max-workers=1` — exit 1. Full output saved in the diagnostic log. It says the app classpath snapshot removed old `attendance-api-contract` and `shared-web` jars and added class output files; then javac cannot resolve attendance contract enum packages and cross-module packages even though sources and class files exist in the worktree. Gradle required full recompilation due changed module-info metadata for `docx4j_xalan_serializer`. This is not evidence of a ranking assertion failure; compiler/classpath state remains undiagnosed.
- The same two Attendance tests with `--rerun-tasks --no-daemon --no-parallel --max-workers=1` — exit 1. Rebuilding dependencies exposed the first error one module earlier: `attendance-api-contract:compileJava` cannot resolve `ru.rutcampustrack.shared.web.api.exception.ErrorResponse` from `shared-web-api`.
- `:services:attendance-service:attendance-api-contract:compileJava --init-script .agent/student-ranking-classpath-diag-20260929.init.gradle --rerun-tasks --no-daemon --no-parallel --max-workers=1` — exit 1. The diagnostic printed all contract Java source paths and an 18-entry classpath that includes `services/shared/shared-web-api/build/classes/java/main`. `ErrorResponse.class` exists there; `javap` reports the expected package/class and Java 21 class version. Despite this, javac still reports the package missing. Root cause remains undiagnosed; no more compiler retry is claimed.
- BFF targeted tests — not run, blocked by upstream attendance compile failure per root direction.
- Runtime endpoint evidence — not collected. No server was started; no runtime acceptance is claimed.

## Limits / follow-up

The separate transfer writer owns transfer event reconciliation and pending-operation reads. This implementation deliberately does not alter those paths. Before integrated runtime acceptance, confirm attendance reads exclude marks for pending transfers and reconcile completed transfers; otherwise a transfer may be counted against the source or destination cohort incorrectly. Do not interpret this change as transfer-aware acceptance until that contract is integrated and checked.

The attendance compile failure blocks a verified commit / Sol handoff. Resolve the worktree/Gradle project-classpath compilation state without broad cleaning or ranking-code workarounds, then rerun the targeted attendance tests and, sequentially, BFF tests under the shared HEAVY lease.
