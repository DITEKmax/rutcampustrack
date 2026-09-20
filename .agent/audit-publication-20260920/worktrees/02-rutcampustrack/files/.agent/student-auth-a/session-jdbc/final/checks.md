# Checks

## Current runtime closure (post-correction-04b)

| Check | Command / evidence | Exit | Result |
|---|---|---:|---|
| Focused real-PG integration runtime | session `44942`; start guard `2026-09-10T00:37:01.3073549+03:00`; exact focused Gradle command recorded in `runtime-evidence.md`; BUILD SUCCESSFUL in `1m15s` (24 tasks: 2 executed, 22 up-to-date) | 0 | PASS |
| Current JUnit report | source and copied XML are byte-equal; SHA256 `FBFD63D70E69BA91458CA14207E8987CD65A09384864B408EA23990CA31D6354`; 10769 bytes; 18 tests, 0 failures, 0 errors, 0 skipped | 0 | PASS |
| Runtime cleanup | ports `18100-18119` free; no Java/Gradle process; test PostgreSQL/Ryuk absent from `docker ps -a`; cleanup `2026-09-10T00:39:38.6298813+03:00` | 0 | PASS / RELEASE |

Revision context: detached baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; working tree contains unrelated foreign changes that were preserved.

| Check | Command / evidence | Exit | Result |
|---|---|---:|---|
| Whitespace | `git diff --check -- services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java` | 0 | PASS |
| Forbidden authority/log patterns | `rg` scan of adapter and IT for Redis, `System.out`, `printStackTrace`, logger/log calls; wrapper reported `forbidden-authority-patterns: none` | 0 | PASS |
| Structural source scan | `rg` scan for port implementations, transaction methods, user/session `FOR UPDATE`, strict JTI CAS, password update, failure injector, and all required IT scenarios | 0 | PASS |
| Source hashes | `Get-FileHash -Algorithm SHA256` for adapter, IT, and V24; V24 byte length also captured | 0 | PASS |
| Git preservation guard | `git status --short` | 0 | PASS; foreign JWT/profile/.agent work remains present |

## Correction-02 gate

Parent compile reproduction (before correction): `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`, started `2026-09-09T19:00:05Z`, session `58508`, exit `1`, BUILD FAILED in 45s at `:services:auth-service:auth-app:compileJava`. It reported 36 adapter errors: ambiguous unqualified `FailureCode` and `this::mapUser/mapSession/mapGrant` mapper references. Correction-02 is limited to the adapter and changes those references as described in `current-status.md`.

Post-correction compile rerun is intentionally delegated to the parent; no Gradle/Docker/runtime command was run by this leaf. Static checks above remain the applicable exit-0 evidence for this handoff.

Post-correction static rerun (this leaf): trailing-whitespace wrapper, forbidden-authority-pattern wrapper, required structural `rg`, manifest JSON parse, `git diff --check`, and SHA capture all completed with exit `0`; output included `trailing-whitespace: none`, `forbidden-authority-patterns: none`, `manifest-json: valid`, and `static-checks: PASS`. Corrected adapter SHA: `05B2FE5045CF8AA9CCAF90010D31726A311E85C5633892EAF1F233E6D8231B48`.

## Correction-03 gate

Parent real-PG reproduction: session `20704`, exit `1`; compileJava and compileTestJava passed, PostgreSQL 16.13 container `a9bb0f522b6ae579419a12090a60448f1b91254fbcfcee594d99b9ecd9781b29` and Flyway24 ran, then 12 tests produced 1 pass, 11 failures, 0 errors, 0 skipped. Every failure shared `@BeforeEach cleanOwnData` line 96: `DELETE FROM account_security_events` was rejected with P0001 by the append-only trigger. Correction-03 removes only that cleanup method and the unused `BeforeEach` import. It does not add `TRUNCATE`, disable a trigger, or weaken V24.

Post-correction IT static guard: cleanup-forbidden-pattern scan exit `0` (`cleanup-forbidden-patterns: none`); hash capture exit `0`; adapter/V24 hashes are unchanged. Post-correction compile/runtime is parent-owned and was not run by this leaf.

Focused runtime command reserved for the parent after runtime lease (not executed here):

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`

No runtime command was run by this source leaf. The parent runtime closure is recorded below.

## Prior runtime closure (parent evidence, pre-correction-04 IT only)

Exact command: `.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT' --no-daemon --no-parallel --max-workers=1 --console=plain`; session `40697`; start guard `2026-09-09T19:43:59.485Z`; CLI exit `0`; BUILD SUCCESSFUL in `1m44s`.

Environment: `postgres:16`, container `122d07ce340678e8e7ea6e2be6e78daac66aad5066c316361ccc2cb27df71063`, PostgreSQL `16.13`, Testcontainers `1.20.4`, Flyway validated `24`, database `rct_student_auth`, port `59927`.

JUnit source `services/auth-service/auth-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT.xml`: timestamp `2026-09-09T19:46:00Z`, `tests=12`, `failures=0`, `errors=0`, `skipped=0`, time `5.611`, SHA256 `4929D2EBC3DA7F829C422FB59A2A658F5C04D2C8460FD9DF3DC8AC1AA11937C1`, `9731` bytes. The byte-for-byte copy is under `final/junit/` with the same hash. Post-run `2026-09-09T19:47:22Z`: ports free, Java/Gradle/container removed; cleanup `RELEASE`.

This 12/0 PASS applies only to IT SHA `BC689BF4265EF4B3247559A88D8A417300EBF49EFDF2BCD80ACCCD2771362FA3`. Correction-04b changed the IT to SHA `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933`; the current 18/0 post-correction runtime PASS is recorded above, while the copied XML has been replaced with the current report. The 12/0 report remains historical provenance.

Correction-04b structural recheck (this leaf): manifest JSON parse, source hash capture, required lock-wait and wholly-new-tuple `rg` scan, forbidden authority/log scan, cleanup-forbidden scan, and `git diff --check` are rerun after the final IT edit; each exits `0`. No Gradle, Docker, or product runtime command is run by this leaf.

Correction-04 static source guard: cleanup-forbidden scan, required new test/hook/lock-order scan, trailing-whitespace scan, forbidden-authority scan, manifest JSON parse, `git diff --check`, and hash capture each exited `0` (all recorded as `PASS`).
