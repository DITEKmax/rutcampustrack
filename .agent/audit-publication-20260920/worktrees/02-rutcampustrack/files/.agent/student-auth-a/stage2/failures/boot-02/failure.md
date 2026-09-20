# Stage2 Boot/PG retry failure record

## Scope

Primary real Spring Boot integration gate for `SessionAuthFlowIT` and `AuthIT`,
covering PostgreSQL/Redis-backed auth session issuance, admission, refresh, role
selection, logout and password-change behavior.

## Criteria

The selected integration group must start its application context and complete
all 19 tests. This retry again reached `0/19` behavioral tests because every
test was blocked by one application-context construction defect.

## Evidence

Exact command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.SessionAuthFlowIT --tests ru.rutcampustrack.auth.integration.AuthIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `13151`
- Exit code: `1`
- Result: `BUILD FAILED in 51s`; `24 actionable tasks: 3 executed, 21 up-to-date`
- Test result: `19 tests completed, 19 failed`; all failures were context-load
  threshold consequences.
- Environment: Windows PowerShell, host `DITEK-PK`, Gradle wrapper, revision
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Post-run Docker evidence: `docker ps` was empty.

Preserved raw JUnit XML under `junit/`:

| File | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `TEST-ru.rutcampustrack.auth.integration.AuthIT.xml` | 11 | 0 | 11 | 0 | `1672C5ED18DD69117F08C77FB325EE522B54F2E3F2D7C8AC3F4486973C81ED43` |
| `TEST-ru.rutcampustrack.auth.integration.SessionAuthFlowIT.xml` | 8 | 0 | 8 | 0 | `58DB5EE1306571DD8107AD2923AAFFD40E26A3D2FFAA14EE5B800BFCF2AAC64D` |

The first AuthIT stack trace identifies the root cause: Spring cannot construct
the `@Repository` bean
`ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapter`, injected into
`AuthService`, because it sees two public constructors and neither is annotated
for injection. It attempts the default path and fails with
`java.lang.NoSuchMethodException: ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapter.<init>()`.
The repeated SessionAuthFlowIT failures are context-failure-threshold
consequences of this same defect. This is a production wiring defect, not a
fixture failure.

## Checks

| Check | Result | Exit |
| --- | --- | ---: |
| Copy source integration XML to this archive | Both destination hashes match source hashes; byte comparison `True` for both files | 0 |
| Parse archived suite counts | AuthIT `11/11` failed; SessionAuthFlowIT `8/8` failed; total `19/19`; zero skipped/errors | 0 |

## Runtime evidence

The supplied session `13151` output is the runtime evidence for this retry. It
proves that the class-proxy defect from boot-01 is cleared, but application
context creation now stops at constructor selection before requested auth
behavior can execute. No retry or correction Gradle run has been performed for
this record.

## Diff

At capture time this failure archive contained only the two byte-identical XML
reports and this record; no source or test file was changed while capturing the
failure.

## Correction gate

Root approved the bounded wiring correction after this recorded defect. The
one-argument production constructor now has an explicit `@Autowired` annotation
and its import. The two-argument `JdbcTemplate, Clock` constructor, all query
logic and the clock seam remain unchanged.

Before/after source hashes for `JdbcAuthSessionQueryAdapter.java`:

- Before: `0CFD2C0D53330EF97F842874B5682309325FDD1A8B43C1F93A9C49F1DBA54CC2`
- After: `30C34F7C8D3F77A5F6AC4B3CA37F76FA6E40AEA2A9B1E646849EDF2320E6ABD1`

Minimal source diff:

```diff
+ import org.springframework.beans.factory.annotation.Autowired;

+     @Autowired
      public JdbcAuthSessionQueryAdapter(JdbcTemplate jdbcTemplate) {
```

The reconstructed pre-correction content hash matches the recorded before
hash. Both public constructors remain present, and `git diff --check` exited
`0`. No Gradle command was run for this correction. The fix is ready for root
to rerun the same primary 19-test integration group.

## Limitations

This run establishes no PostgreSQL/Redis behavior, concurrency, or endpoint
contract result because Spring context creation failed first. A narrow
constructor-injection correction is recorded above, but the same primary-group
rerun is still required before behavioral claims.
