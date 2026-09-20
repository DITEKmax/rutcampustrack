# Stage2 query-adapter integration evidence

## Scope

Focused PostgreSQL integration check for
`ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapterIT`.

## Criteria

The correct fully qualified selector must execute all three query-adapter
integration tests with zero skipped, failures and errors. An earlier selector
using the wrong package is recorded as invalid evidence and carries no pass
claim.

## Evidence

Correct command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapterIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `8969`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL in 32s`; `3/3` PASS.
- Environment: Windows PowerShell, host `DITEK-PK`, Gradle wrapper, revision
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

The current JUnit XML is preserved byte-for-byte at
`junit/integration-query/TEST-ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapterIT.xml`:

| Suite | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapterIT` | 3 | 0 | 0 | 0 | `F511B4D0CF69D00A9DDCB7A8AD2486A60476E351039F618F3F5EBEBFCA2D5CB8` |

## Prior invalid selector

The preceding command used the wrong package:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.JdbcAuthSessionQueryAdapterIT --no-daemon --no-parallel --max-workers=1 --console=plain`

It ran in session `75254`, exited `0` and reported `BUILD SUCCESSFUL in 36s`,
but produced no XML or executed test result because the actual package is
`ru.rutcampustrack.auth.session.jdbc`. It is retained only as a selector
diagnostic; it contributes no PASS evidence.

## Checks

| Check | Result | Exit |
| --- | --- | ---: |
| Copy source XML to `junit/integration-query/` | Source/destination SHA-256 match; byte comparison `True` | 0 |
| Parse preserved XML | Three tests, zero skipped/failures/errors | 0 |

## Runtime evidence

Session `8969` is the authoritative runtime evidence for this focused gate. It
uses the corrected fully qualified selector and proves `3/3` integration tests
passed. The invalid session `75254` is explicitly excluded from the evidence
count.

## Diff

This evidence-only handoff adds the byte-identical query-adapter JUnit XML and
this compact record. No source, test or generated contract file was edited.

## Limitations

This check covers only `JdbcAuthSessionQueryAdapterIT`; it does not claim the
primary auth group, other auth integration selectors, OpenAPI snapshot checks,
or frontend/runtime behavior.
