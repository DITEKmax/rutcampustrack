# P1 Gradle failure gate

Recorded at revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

- Start: `2026-09-08T11:49:54.2917364+03:00`.
- End: `2026-09-08T11:50:39.1736972+03:00`.
- Working directory: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.
- Environment: Windows PowerShell/PTY; Gradle wrapper; `--no-parallel`;
  `--max-workers=1`; `--console=plain`.
- Exact command (exit code `1`, run once):
  `./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.AttendanceRequestBotGrpcServiceTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcErrorsTest --no-parallel --max-workers=1 --console=plain`
- Failure task: `:services:attendance-service:attendance-app:compileTestJava`.
- Reproduction: `AttendanceRequestBotGrpcServiceTest.java` references
  `GetStudentAttendanceSnapshotRequest` and
  `GetStudentAttendanceSnapshotResponse` without importing the generated
  message classes. The compiler reported five `cannot find symbol` errors on
  lines 193, 195 and 197; tests did not execute.
- XML result count: `0`; test result directory was absent, so no XML hashes
  exist for this run.
- New evidence: compile output is preserved in the command result; no product
  correction or rerun was performed after this failure.

Required next gate: root decision on the bounded test-only import correction,
then a new explicit GO before any rerun. No Terra escalation is requested.

## Root correction addendum

The initial diagnosis above was corrected by root after source inspection: the
compiler failure is caused by incorrect type names, not missing imports.
`proto/attendance.proto:11-12` declares
`StudentAttendanceSnapshotRequest` and `StudentAttendanceSnapshotResponse`
without the `Get` prefix. The bounded test correction replaced the two
`GetStudentAttendanceSnapshot*` type references with those actual generated
types and retained the generated accessor
`getGetStudentAttendanceSnapshotMethod()`. This correction is test-only; no
production file changed and no Gradle rerun has occurred.
