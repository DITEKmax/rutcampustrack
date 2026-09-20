# JDBC session authority — final status

Date: 2026-09-09
Risk: S3
Owner: session-jdbc source leaf; sole writer for the two adapter files and this evidence directory.
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` (detached HEAD at reservation).

## Scope

Implemented only the reserved JDBC source phase:

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java`
- this leaf's evidence under `.agent/student-auth-a/session-jdbc/final/`

The accepted Academic V24 migration source was imported exactly at
`services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql`.
No JWT, controller, DTO, domain-port, generated, build, config, or shared-contract files were changed by this leaf.

## Criteria and outcome

- JDBC adapter implements both atomic session ports with `JdbcTemplate` and the transaction manager.
- Mutations lock the owning user before session state; predicates include both owner and session/grant identity.
- Grant/status and `roles_version` are read from the authoritative SQL tables; role changes are versioned and same-role selection is idempotent.
- Refresh uses strict current-JTI compare-and-set, preserves absolute expiry, and clears an invalid active role coherently.
- Current/all-session revocation and password change append their security event in the same transaction.
- Password change rechecks the observed hash and treats an expired or revoked current session as `SESSION_REVOKED` before any write.
- The integration test source covers ownership, stale state, refresh races, expiry, terminal/suspended role boundaries, revocation, password atomicity, rollback, and absence of bearer logging.

The P2 expiry defect found during root review was corrected: the password path now gates on `!isLive(now)` rather than only `revoked_at`, and the IT asserts that an expired current session leaves hash, flags, sessions, and events unchanged.

Correction-02 addresses the parent compile reproduction. The exact focused command ran at `2026-09-09T19:00:05Z` in session `58508` and exited `1` at `:services:auth-service:auth-app:compileJava` after 45 seconds. The 36 compiler errors were bounded to this adapter: ambiguous unqualified `FailureCode` references from the two implemented ports and static mapper references written as `this::mapUser/mapSession/mapGrant`. All session failures now explicitly use `SessionStatePort.FailureCode`; the credential path remains explicitly qualified with `CredentialSessionTransactionPort.FailureCode`; JDBC queries use `JdbcSessionAuthority::mapUser/mapSession/mapGrant` RowMapper references. PostgreSQL/Testcontainers/Flyway did not start and no XML/container evidence exists.

Correction-03 addresses the real PostgreSQL test failure. Parent's exact run in session `20704` exited `1` after compileJava and compileTestJava passed; PostgreSQL 16.13 container `a9bb0f522b6ae579419a12090a60448f1b91254fbcfcee594d99b9ecd9781b29` and Flyway24 started, but 11 tests failed at the shared `@BeforeEach cleanOwnData` line 96 because append-only `account_security_events` correctly rejected `DELETE` with P0001. The cleanup method and unused `BeforeEach` import were removed. This is safe on the fresh `reuse=false` container because `userSequence` gives each `jdbc-it-*` user a unique identity and assertions scope rows by user.

## Status

Correction-04b source fixes, static evidence, and the exact post-correction runtime are complete (`SOURCE_READY`). The current IT SHA is `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933`; the parent focused run exited `0` with BUILD SUCCESSFUL, 18 tests, 0 failures, 0 errors, and 0 skipped. The current report and byte-exact copy are recorded below and in `manifest.json`; prior compile/PG failures and the pre-review runtime PASS remain provenance. No production action was taken.

## Current runtime closure (post-correction-04b)

- Command: `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`
- Session: `44942`; start guard `2026-09-10T00:37:01.3073549+03:00`; CLI exit `0`; BUILD SUCCESSFUL in `1m15s` (24 tasks: 2 executed, 22 up-to-date).
- Environment: `postgres:16`, container `012f219284c436b68196839c143d466e403bacf1ffb6123241af8b0dddaadfa3`, PostgreSQL `16.13`, Testcontainers `1.20.4`, Flyway validated `24`, database `rct_student_auth`, JDBC port `55886`, `reuse=false`.
- JUnit source: `services/auth-service/auth-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT.xml`; timestamp `2026-09-09T21:38:27Z`, 18 tests, 0 failures, 0 errors, 0 skipped, time `9.322`, SHA256 `FBFD63D70E69BA91458CA14207E8987CD65A09384864B408EA23990CA31D6354`, `10769` bytes. The copied report under `final/junit/` is byte-for-byte equal.
- Cleanup: `2026-09-10T00:39:38.6298813+03:00`; ports `18100-18119` free, Java/Gradle none, test PostgreSQL/Ryuk absent from `docker ps -a`; cleanup `RELEASE`.

## Correction-04 review findings and source correction

Independent Sol high review found three bounded findings: MEDIUM, fault coverage stopped at `password.credential` and did not prove rollback after revoke/password events or after `password.sessions`; MEDIUM, the IT lacked stale authoritative grant/version create rejection and a deterministic user-lock-ordered snapshot/grant-writer race; LOW, runtime evidence still presented the old 12/0 PASS as current after the IT changed. The correction adds four real-PG fault tests, a stale grant/version test, and a barrier-synchronized `TransactionTemplate` writer that locks the user before updating the grant. Only historical runtime metadata are retained as metadata-only; the old XML artifact was overwritten by the accepted current 18-test report, and the artifact is not retained. Parent owns the post-correction runtime.

Correction-04b refines the race test after the review gate: the writer now holds the user `FOR UPDATE`, signals `writerLockHeld`, and waits on `releaseWriter`; the snapshot starts while that lock is held, and a bounded `pg_stat_activity`/`pg_blocking_pids` poll proves a distinct active `wait_event_type='Lock'` on the users query before release. The writer then commits the suspended grant and the snapshot asserts only the wholly-new tuple. The IT SHA is now `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933`; adapter/V24/XML remain unchanged.

## Prior runtime provenance (pre-correction-04 IT)

- Retention: the following values are metadata-only; the old XML artifact is not retained because it was overwritten by the accepted current 18-test report.
- Command: `.\\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`
- Session: `40697`; start guard `2026-09-09T19:43:59.485Z`; CLI exit `0`; BUILD SUCCESSFUL in `1m44s`.
- Environment: `postgres:16`, container `122d07ce340678e8e7ea6e2be6e78daac66aad5066c316361ccc2cb27df71063`, PostgreSQL `16.13`, Testcontainers `1.20.4`, Flyway validated `24`, database `rct_student_auth`, port `59927`.
- JUnit: 12 tests, 0 failures, 0 errors, 0 skipped, time `5.611`; source report timestamp `2026-09-09T19:46:00Z`, SHA256 `4929D2EBC3DA7F829C422FB59A2A658F5C04D2C8460FD9DF3DC8AC1AA11937C1`, `9731` bytes.
- Cleanup: post-run `2026-09-09T19:47:22Z`; ports free, Java/Gradle/container removed; `RELEASE`.
