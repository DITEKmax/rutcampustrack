# Checks — paused, not acceptance evidence

| Check | Command | Exit | Result |
|---|---|---:|---|
| Service source boundary scan | `rg -n "requestContext|validateOptionSnapshots\\(" services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java` | 0 | no remaining `requestContext`; identity validation call sites listed |
| Compile attempt | `.\\gradlew.bat :services:attendance-service:attendance-app:compileJava --no-daemon` | not observed | interrupted by owner pause before final Gradle status; not PASS |
| Runtime process check | `Get-Process -Name java -ErrorAction SilentlyContinue \| Select-Object Id,StartTime,Path` | 0 | no visible Java process output |

Environment: Windows PowerShell, worktree `C:\\Users\\maksd\\IntelliJIDEA\\rutcampustrack\\.agent\\worktrees\\student-role-02\\requests-domain`, HEAD `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

No focused or Mongo runtime tests were run in this repair turn. Runtime acceptance remains pending.

## 2026-09-07 resumed repair checks — current evidence

Environment for every command: Windows PowerShell; worktree `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\student-role-02\requests-domain`; HEAD `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; Gradle 8.12/JDK 21; all commands used `--no-daemon`.

| Check | Command | Exit | Evidence |
|---|---|---:|---|
| Main compile | `.\gradlew.bat :services:attendance-service:attendance-app:compileJava --no-daemon` | 0 | `BUILD SUCCESSFUL in 47s`; after repair of the two reproduced identity-refactor compile errors. |
| Test compile | `.\gradlew.bat :services:attendance-service:attendance-app:compileTestJava --no-daemon` | 0 | `BUILD SUCCESSFUL in 43s`. |
| Focused unit/contract | `.\gradlew.bat :services:attendance-service:attendance-app:test --tests 'ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest' --tests 'ru.rutcampustrack.attendance.latecheckin.LateCheckinEventContractTest' --tests 'ru.rutcampustrack.attendance.excuse.ExcuseEventPublisherTest' --tests 'ru.rutcampustrack.attendance.excuse.ExcuseServiceTest' --tests 'ru.rutcampustrack.attendance.latecheckin.LateCheckinServiceTest' --no-daemon` | 0 | `BUILD SUCCESSFUL in 48s`; current XML: 7 + 6 + 5 + 10 + 21 = **49**, all zero skipped/failures/errors. |
| Real Mongo replica set | `.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests 'ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT' --no-daemon` | 0 | `BUILD SUCCESSFUL in 1m 37s`; fresh XML timestamp `2026-09-07T11:33:03`, tests=15, skipped=0, failures=0, errors=0. The test creates a unique Testcontainers `mongo:7.0` replica set/database and uses production `MongoOutboxStorage` collection `student_request_test_outbox`. |
| Diff whitespace | `git -C <worktree> diff --check` | 0 | No diff-check error; Git emitted only existing CRLF conversion notices. |

Rejected intermediate evidence, retained for transparency:

| Attempt | Exit | Why it is not acceptance evidence |
|---|---:|---|
| `:attendance-app:test --tests StudentRequestDomainIT` | 0 | Wrong Gradle task filter: the IT source file is selected by this repository's `integrationTest` task. The XML remained the old timestamp `08:03:48`, tests=10; it did not execute the current IT. |
| First correct `integrationTest` | 0 | Fresh 14/14, but the test still used `TestOutboxStorage` in memory; it did not prove durable transactional outbox behavior. |
| Production-outbox `integrationTest` before writer correction | 1 | Fresh 15 tests, one Mongo 112 `TransientTransactionError` at test-only PRESENT writer's `PairWriteCoordinator.lock`; the writer was adjusted to the real four-attempt retry boundary, not the attendance invariant. |

Historical count correction: prior focused evidence was 45 (= 5+4+10+5+21), while its separate Mongo run was 10; the aggregate was 55. It was never a focused count of 55. This resumed repair's current focused count is 49 because it adds two authorization, one EXCUSE publisher and one late event-contract test; current Mongo count is 15 after five scope-relevant additions.

Post-run ownership check: `docker ps --filter "name=rct-student-role-02-requests" --format "{{.ID}} {{.Names}} {{.Status}}"` exited 0 with no rows. Testcontainers removed its task-owned container; no shared container, port, volume or data cleanup was performed.
