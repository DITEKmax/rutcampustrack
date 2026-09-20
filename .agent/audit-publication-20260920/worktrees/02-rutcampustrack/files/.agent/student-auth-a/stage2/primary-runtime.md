# Stage2 primary Boot/PG/Redis runtime evidence

## Scope

Primary real Spring Boot integration gate for `SessionAuthFlowIT` and `AuthIT`,
covering session-backed login, admission, refresh, role selection, logout and
password change through the PostgreSQL/Redis test environment.

## Criteria

The selected integration group must start its application context and complete
all 19 tests with zero skipped, failures or errors. The two final JUnit reports
are preserved under `junit/integration-primary/`.

## Evidence

Exact command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.SessionAuthFlowIT --tests ru.rutcampustrack.auth.integration.AuthIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `53268`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL in 57s`; `24 actionable tasks: 3 executed, 21 up-to-date`
- Environment: Windows PowerShell, host `DITEK-PK`, Gradle wrapper, revision
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Post-run Docker evidence: escalated `docker ps` was empty.

Preserved PASS XML:

| File | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `TEST-ru.rutcampustrack.auth.integration.AuthIT.xml` | 11 | 0 | 0 | 0 | `C378078C68D2F68FEA25CEEF47E3CEB7715E40FC1DC10182759BC11C1A314DEA` |
| `TEST-ru.rutcampustrack.auth.integration.SessionAuthFlowIT.xml` | 8 | 0 | 0 | 0 | `654A9C8C678AFB175A1D74724B631695E716F3FCF56449F6161BA6764D97DB1E` |

Combined result: `19/19` PASS with all result fields zero.

## Prior boot failures and exact corrections

The first primary attempt (`boot-01`) recorded one CGLIB defect across 19
context-load failures: `JdbcSessionAuthority` was `final` while Spring needed a
proxy. The bounded correction changed only `public final class` to
`public class` in both `JdbcSessionAuthority` and
`JdbcAuthSessionQueryAdapter`. The raw failed XML remains at
`failures/boot-01/junit/` with AuthIT SHA-256
`5D9948B8A9F6C4DE28009A7B0FE679645B1B74AA2A528E3023280F03E6B63274` and
SessionAuthFlowIT SHA-256
`747A1D93E134AFB5AEB49BCF0F49509C81C2BD8BD106F5E41E22C5C7F3EA446F`.

The second attempt (`boot-02`) recorded the next constructor-selection defect:
the query adapter had two public constructors and no `@Autowired`, so Spring
looked for a no-argument constructor. The bounded correction added only the
`Autowired` import and annotation to
`JdbcAuthSessionQueryAdapter(JdbcTemplate)` while preserving the two-argument
`JdbcTemplate, Clock` seam and logic. The raw failed XML remains at
`failures/boot-02/junit/` with AuthIT SHA-256
`1672C5ED18DD69117F08C77FB325EE522B54F2E3F2D7C8AC3F4486973C81ED43` and
SessionAuthFlowIT SHA-256
`58DB5EE1306571DD8107AD2923AAFFD40E26A3D2FFAA14EE5B800BFCF2AAC64D`.

## Checks

| Check | Result | Exit |
| --- | --- | ---: |
| Copy current integration XML to `junit/integration-primary/` | Both source/destination hashes match and byte comparison is `True` | 0 |
| Parse final suite reports | AuthIT `11/11`, SessionAuthFlowIT `8/8`; total `19/19`; zero skipped/failures/errors | 0 |

## Runtime evidence

Session `53268` is the successful runtime evidence for this primary gate. The
application context started after both recorded wiring corrections, and the
selected 19 tests completed successfully. Docker cleanup was confirmed by an
empty post-run `docker ps` result.

## Diff

This evidence-only handoff adds the two byte-identical PASS XML reports and this
compact record. The boot failure archives remain unchanged; no source, test or
generated contract file was edited during evidence preservation.

## Limitations

This evidence covers only the selected primary `AuthIT` and `SessionAuthFlowIT`
group. It does not claim the separate query-adapter integration gate, other
auth integration selectors, OpenAPI snapshot comparison, or frontend/runtime
checks.
