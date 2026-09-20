# Stage2 Boot/PG gate failure record

## Scope

Primary real Spring Boot integration gate for `SessionAuthFlowIT` and `AuthIT`,
including PostgreSQL/Redis-backed session issuance, admission, refresh, role
selection, logout and password-change behavior.

## Criteria

The selected integration group must start its application context and complete
all 19 tests. This run reached `0/19` behavioral tests because every test was
blocked by the same application-context construction defect.

## Evidence

Exact command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.SessionAuthFlowIT --tests ru.rutcampustrack.auth.integration.AuthIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `59338`
- Exit code: `1`
- Result: `BUILD FAILED in 46s`; `24 actionable tasks: 1 executed, 23 up-to-date`
- Test result: `19 tests completed, 19 failed`; all failures were context-load failures.
- Environment: Windows PowerShell, host `DITEK-PK`, Gradle wrapper, revision
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Post-run Docker evidence: `docker ps` was empty.

Preserved raw JUnit XML under `junit/`:

| File | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `TEST-ru.rutcampustrack.auth.integration.AuthIT.xml` | 11 | 0 | 11 | 0 | `5D9948B8A9F6C4DE28009A7B0FE679645B1B74AA2A528E3023280F03E6B63274` |
| `TEST-ru.rutcampustrack.auth.integration.SessionAuthFlowIT.xml` | 8 | 0 | 8 | 0 | `747A1D93E134AFB5AEB49BCF0F49509C81C2BD8BD106F5E41E22C5C7F3EA446F` |

The AuthIT stack trace identifies the same root cause at its first failure:
Spring AOP attempts to create a CGLIB proxy for the `@Repository` bean
`ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority`, which is injected
into `JwtAuthenticationFilter`, and fails with
`java.lang.IllegalArgumentException: Cannot subclass final class ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority`.
The repeated SessionAuthFlowIT failures are context-failure-threshold
consequences of this same defect.

## Checks

| Check | Result | Exit |
| --- | --- | ---: |
| Copy source integration XML to this archive | Both destination hashes match their source hashes; byte comparison `True` for both files | 0 |
| Parse archived suite counts | AuthIT `11/11` failed; SessionAuthFlowIT `8/8` failed; total `19/19`; zero skipped/errors | 0 |

## Runtime evidence

The supplied session `59338` output is the runtime evidence for this gate. It
proves application-context startup failure before any requested auth behavior
could execute. No retry or corrective Gradle run has been performed at this
stage.

## Diff

At capture time this failure archive contained only the two byte-identical XML
reports and this record; no product or test source was changed while capturing
the failure.

## Correction gate

Root approved a bounded correction after the recorded runtime defect. The
request is to remove only the `final` class modifier that prevents Spring's
CGLIB proxy creation, while preserving `@Repository`, `TransactionTemplate`,
SQL/locks and ports. No AOP configuration or interface-proxy workaround was
introduced, and no Gradle command was run for the correction.

Before/after source hashes:

| File | Before SHA-256 | After SHA-256 |
| --- | --- | --- |
| `JdbcSessionAuthority.java` | `05B2FE5045CF8AA9CCAF90010D31726A311E85C5633892EAF1F233E6D8231B48` | `3CFFE7E059FF6E7D00467988ACAFBC493E1EF3B3F10A1D3D50BCBDD4A343A002` |
| `JdbcAuthSessionQueryAdapter.java` | `11063B58998A1BB71103D574A76839AAE58DA6D3DA235CB6DF709D92780512DC` | `0CFD2C0D53330EF97F842874B5682309325FDD1A8B43C1F93A9C49F1DBA54CC2` |

Minimal source diff:

```diff
- public final class JdbcSessionAuthority implements SessionStatePort, CredentialSessionTransactionPort {
+ public class JdbcSessionAuthority implements SessionStatePort, CredentialSessionTransactionPort {
- public final class JdbcAuthSessionQueryAdapter implements AuthSessionQueryPort {
+ public class JdbcAuthSessionQueryAdapter implements AuthSessionQueryPort {
```

The reconstructed pre-edit content hashes exactly match both recorded before
hashes, and `git diff --check` exited `0`. The correction is ready for root to
rerun the same 19-test integration group under its heavy lease.

## Limitations

This run does not establish any PostgreSQL/Redis behavior result, concurrency
result, or endpoint contract result because Spring context creation failed
first. The failure was a production wiring defect in the frozen Stage1 classes,
not a test-fixture failure. Behavioral claims remain blocked until root's same
primary-group rerun completes.
