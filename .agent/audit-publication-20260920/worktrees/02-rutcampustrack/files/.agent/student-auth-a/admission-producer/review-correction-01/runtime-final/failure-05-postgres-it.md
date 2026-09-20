# Failure 05 — focused PostgreSQL admission IT

- Revision/baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus the frozen Stage 1 source and review-correction-01 source manifest.
- Command: `./gradlew :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT --no-daemon --no-parallel --max-workers=1 --console=plain`
- Environment: Windows PowerShell, escalated only because the sandbox cannot read an existing Gradle dependency-cache jar; Testcontainers PostgreSQL 16.13 on ephemeral port 61106; Flyway validated and applied V1..V24.
- Started UTC: `2026-09-10T20:26:39.5997284Z`.
- Ended UTC: `2026-09-10T20:27:44.7090736Z`.
- Exit code: `1` (`BUILD FAILED in 1m 4s`).
- Result: 6 tests, 5 passed, 1 failed, 0 errors, 0 skipped.
- Failure: `httpTypedFailuresMapToProblemDetailsAndNoStore()` expected HTTP 403 for the suspended fixture but received HTTP 503 at `InternalSessionAdmissionIT.java:302`, asserted by `assertProblem` at line 370.
- Interpretation: the direct PostgreSQL suspended-grant test passed, but that does not prove the multi-case HTTP failure is only a fixture defect. `JdbcSessionAuthority.snapshotInTransaction` calls `clearUnselectableActive`, while its transaction boundary collapses every runtime exception to `AUTHORITY_UNAVAILABLE`; therefore the observed 503 can still represent a real SQL, transaction, or invariant failure. No expectation or authority may be replaced until a bounded diagnostic captures the underlying safe exception class/SQLState/constraint and explains the difference between the direct fixed-clock case and the production-clock HTTP case. A deterministic typed-mapping double may be added later for controller coverage, but it cannot erase the failing real-PostgreSQL scenario.
- Product code was not changed in response to this failure.
- Source guard immediately before the command: 0 unexpected files/hashes; Auth13 13/13.
- Container cleanup: Testcontainers/Ryuk exited after the Gradle process. A post-run `docker ps -a` showed no remaining Stage 1 PostgreSQL or Ryuk container.

Evidence:

- `command-03-escalated-postgres-it.log`: SHA-256 `F11CE7B01D668650E90AE87381C2E06EF1E317490B3D315FD2307A23388AEF9D`, 3710 bytes.
- `command-03-escalated-meta.json`: SHA-256 `88C24CE968C4EEB5F2522AB13F77A6C10A30791C1E4492581961878374EFF17F`, 345 bytes.
- `failure-05-junit/TEST-ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT.xml`: SHA-256 `E6942C019856054243E3CF1064DD5BDC9AD17AAD514A62CD5A6A70D4B1FF64C2`, 12590 bytes; byte-identical to the Gradle result copied immediately after the run.
