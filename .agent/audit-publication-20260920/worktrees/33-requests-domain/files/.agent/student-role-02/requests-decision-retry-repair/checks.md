# Checks

Environment: Windows PowerShell on `DITEK-PK`, worktree
`C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\student-role-02\requests-domain`,
JDK `21.0.10`, Gradle `8.12`. Each Gradle command used `--no-daemon`.

| Check | Command | Exit code | Evidence |
|---|---|---:|---|
| Main compilation | `.\gradlew.bat --no-daemon :services:attendance-service:attendance-app:compileJava --rerun-tasks` | 0 | `BUILD SUCCESSFUL` (1m15s) |
| Test compilation | `.\gradlew.bat --no-daemon :services:attendance-service:attendance-app:compileTestJava --rerun-tasks` | 0 | `BUILD SUCCESSFUL` |
| Focused authorization regression | `.\gradlew.bat --no-daemon :services:attendance-service:attendance-app:test --rerun-tasks --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest` | 0 | XML timestamp `2026-09-07T14:32:37`; `tests=7`, `skipped=0`, `failures=0`, `errors=0` |
| Real Mongo domain integration | `.\gradlew.bat --no-daemon :services:attendance-service:attendance-app:integrationTest --rerun-tasks --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT` | 0 | XML timestamp `2026-09-07T14:28:23`; `tests=19`, `skipped=0`, `failures=0`, `errors=0` |
| Final source hash capture | `Get-FileHash services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java,services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java -Algorithm SHA256` | 0 | Service `5095C2A63EDF7E6561F6C08E2071C97270489A5C1CDA1B73EF64507B3132A662`; IT `968F4A4967D0AD2FDB0055C647D709F6A0A020B59DDB3396C64007E57A150DE6` |
| Whitespace check | `git diff --check` | 0 | No diff-check errors; only existing LF/CRLF conversion notices |
| Task container check | `docker ps --filter "name=rct-student-role-02-requests" --format "{{.ID}} {{.Names}} {{.Status}}"` | 0 | No rows after Testcontainers cleanup |

The XML files are:

- `services/attendance-service/attendance-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT.xml`
- `services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest.xml`

`test --tests StudentRequestDomainIT` is deliberately not used as an IT
acceptance check in this repository; the class is selected by `integrationTest`.
No broad unrelated regression suite was repeated after the bounded repair.
