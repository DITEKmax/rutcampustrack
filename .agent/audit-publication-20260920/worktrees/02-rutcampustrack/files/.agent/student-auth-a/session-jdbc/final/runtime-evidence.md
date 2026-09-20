# Runtime evidence

## Current runtime closure (post-correction-04b)

Current runtime status: **PASS — parent exact post-correction-04b runtime**.

The parent exact focused command `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain` ran in session `44942` with start guard `2026-09-10T00:37:01.3073549+03:00`, exited `0`, and reported BUILD SUCCESSFUL in `1m15s` (24 tasks: 2 executed, 22 up-to-date).

Environment: `postgres:16`, container `012f219284c436b68196839c143d466e403bacf1ffb6123241af8b0dddaadfa3`, PostgreSQL `16.13`, Testcontainers `1.20.4`, Flyway validated `24`, fresh database `rct_student_auth`, JDBC port `55886`, `reuse=false`. Source assertion confirms Flyway applied through `24 - auth session authority`.

JUnit source `services/auth-service/auth-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT.xml`: timestamp `2026-09-09T21:38:27Z`, 18 tests, 0 failures, 0 errors, 0 skipped, time `9.322`, SHA256 `FBFD63D70E69BA91458CA14207E8987CD65A09384864B408EA23990CA31D6354`, `10769` bytes. The copy at `final/junit/TEST-ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT.xml` was replaced from that source and is byte-for-byte equal.

Cleanup: `2026-09-10T00:39:38.6298813+03:00`; ports `18100-18119` free, no Java/Gradle process, and test PostgreSQL/Ryuk absent from `docker ps -a`; cleanup `RELEASE`.

Prior runtime status: **PASS — parent exact runtime; pre-correction-04 provenance only**.

The contract required a fresh PostgreSQL 16 Testcontainers database named `rct_student_auth`, `reuse=false`, with the accepted Academic V1..V24 chain. Earlier correction-02 and correction-03 failures remain above as provenance. The parent exact rerun passed: `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`, session `40697`, start guard `2026-09-09T19:43:59.485Z`, CLI exit `0`, BUILD SUCCESSFUL in `1m44s`.

Environment was `postgres:16`, container `122d07ce340678e8e7ea6e2be6e78daac66aad5066c316361ccc2cb27df71063`, PostgreSQL `16.13`, Testcontainers `1.20.4`, Flyway validated `24`, database `rct_student_auth`, port `59927`. Historical JUnit metadata recorded 12 tests, 0 failures, 0 errors, 0 skipped, time `5.611`, timestamp `2026-09-09T19:46:00Z`, `9731` bytes, SHA256 `4929D2EBC3DA7F829C422FB59A2A658F5C04D2C8460FD9DF3DC8AC1AA11937C1`. The old XML artifact was overwritten by the accepted current 18-test report and is not retained.

Cleanup RELEASE was recorded at `2026-09-09T19:47:22Z`: ports free, Java/Gradle/container removed. The old XML artifact is not retained; its metadata remains provenance for the pre-correction-04 IT SHA `BC689BF4265EF4B3247559A88D8A417300EBF49EFDF2BCD80ACCCD2771362FA3`.

## Correction-04b source gate

The IT SHA `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933` includes correction-04b's real `pg_stat_activity`/`pg_blocking_pids` lock-wait proof and wholly-new tuple assertion. The current runtime closure is recorded above; the pre-review 12/0 report remains provenance only. No runtime was started by this correction leaf.

No production migration, deployment, destructive rollback, secret access, or external application write was performed.
