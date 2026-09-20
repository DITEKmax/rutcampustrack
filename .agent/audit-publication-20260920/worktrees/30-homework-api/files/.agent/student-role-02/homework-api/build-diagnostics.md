# Build diagnostics and corrections

## Environment classpath gate

The first Gradle attempts from the sandbox exited 1 while unchanged shared
modules could not resolve classes that were present on their absolute build
classpath. Root's bounded ClasspathProbe reproduced the distinction: the
relative classpath exited 0, the same absolute classpath exited 1 inside the
sandbox, and the exact command exited 0 with `require_escalated`. No shared
source or build file was changed. All final Gradle gates use
`--no-daemon --no-problems-report` with that recorded escalation.

## Required BFF compile correction

Adding `HOMEWORK_NOT_FOUND` to the scoped `ProblemCode` enum made the existing
`MobileAttendanceClient.httpStatus` switch incomplete. The reproduction was
`mobile-bff-app:compileJava`, which reported `MobileAttendanceClient.java:68`
with “switch expression does not cover all possible input values”. The
minimal correction maps `HOMEWORK_NOT_FOUND` alongside `LESSON_NOT_FOUND` to
HTTP 404. The same compile then exited 0.

## Focused test fixture correction

The first focused Academic test run compiled successfully but four negative
`HomeworkStudentServiceTest` cases failed with Mockito
`UnnecessaryStubbingException`. Their authz branches intentionally return
before downstream semester/homework stubs. The correction marks only those
branch-dependent common setup stubs `lenient`; production code and positive
interaction assertions are unchanged. The rerun passed all 8 focused tests
with exit 0.

## OpenAPI command correction

The first export invocation used an unquoted PowerShell
`-Popenapi.snapshot.update=true`, which Gradle parsed as a task name and
exited 1 before running tests. The corrected quoted property reached
`OpenApiSnapshotIT`, exported the canonical snapshot and exited 0. A separate
Gradle problems-report file collision was avoided with the documented
`--no-problems-report` option; no product source was altered for that tooling
issue.
