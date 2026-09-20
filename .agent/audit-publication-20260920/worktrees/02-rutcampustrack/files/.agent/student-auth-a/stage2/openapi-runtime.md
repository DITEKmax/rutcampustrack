# Stage2 Java-first Auth OpenAPI runtime evidence

Status: `OPENAPI_READY`.

## Scope

This gate covers the Java-first export and compare of
`ru.rutcampustrack.auth.integration.OpenApiSnapshotIT`. The JSON was generated
by the accepted test property; it was not manually edited. The initial command
parsing failure is preserved separately at
`junit/openapi/failure-94003.md`.

## Criteria

The property-enabled export must pass and produce the current `auth.json`; the
property-free compare must pass against that generated file; both JUnit reports
must contain one test with zero skipped, failures and errors; and Docker must
have no leftover containers after the two runs.

## Evidence

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Environment: Windows PowerShell on `DITEK-PK`, shared checkout, Gradle wrapper.

Pre-export `docs/openapi/auth.json`: `37,654` bytes,
SHA-256 `5B97E363B891AE0A4B9B4148992E05FD474527C3896D2998780AF60818C1D35E`.

Export command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.OpenApiSnapshotIT "-Popenapi.snapshot.update=true" --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `1097`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL in 49s`; `24 actionable tasks: 1 executed, 23 up-to-date`.
- Generated `docs/openapi/auth.json`: `58,141` bytes,
  SHA-256 `61BFB083D71A4EB7F40C7BF7C0B464798CE7F884F00E52E7FE579E816A5CE9D2`.
- Preserved XML:
  `junit/openapi/export/TEST-ru.rutcampustrack.auth.integration.OpenApiSnapshotIT.xml`
  (`25,978` bytes, SHA-256
  `8E4B3AB4006AA123CA4C2ECEA8BE8618E7FE49A5154A9196AE7A8327B978FA91`).

Compare command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.OpenApiSnapshotIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `54903`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL in 49s`; `24 actionable tasks: 1 executed, 23 up-to-date`.
- `docs/openapi/auth.json` remained `58,141` bytes with SHA-256
  `61BFB083D71A4EB7F40C7BF7C0B464798CE7F884F00E52E7FE579E816A5CE9D2`.
- Preserved XML:
  `junit/openapi/compare/TEST-ru.rutcampustrack.auth.integration.OpenApiSnapshotIT.xml`
  (`25,875` bytes, SHA-256
  `9D4999AA4CCF68D6E2234726D92D0AEC1E45CCB4B89383D10371CCF58B289BF2`).

| Suite | Tests | Skipped | Failures | Errors |
|---|---:|---:|---:|---:|
| Export `OpenApiSnapshotIT` | 1 | 0 | 0 | 0 |
| Compare `OpenApiSnapshotIT` | 1 | 0 | 0 | 0 |

## Checks

| Check | Result | Exit |
|---|---|---:|
| Pre-export JSON hash/bytes | `37,654` bytes; SHA recorded above | 0 |
| Property-enabled export | Session `1097`, suite `1/1`, zero skipped/failures/errors | 0 |
| Preserve export XML | Source/destination byte comparison `True`; SHA recorded above | 0 |
| Property-free compare | Session `54903`, suite `1/1`, zero skipped/failures/errors | 0 |
| Preserve compare XML and assert JSON stability | Source/destination byte comparison `True`; export/compare JSON hash equal | 0 |
| Docker cleanup | `docker ps --format "{{.ID}} {{.Image}} {{.Names}}"` returned no containers | 0 |

## Runtime evidence

Session `1097` proves the Java-first snapshot export completed successfully and
updated the generated contract. Session `54903` proves the generated contract
matches the property-free snapshot. The JSON hash stayed unchanged across the
compare, and the post-run Docker listing was empty.

## Diff

The export command generated the intended update to `docs/openapi/auth.json`.
Evidence adds the two byte-identical XML reports under
`junit/openapi/{export,compare}/`, this record, and the separate parsing-failure
record. No source or test file was manually edited; the pre-existing dirty
`OpenApiSnapshotIT.java` was preserved.

## Limitations

This gate covers only `OpenApiSnapshotIT` export/compare. It does not claim the
broader auth integration suite, frontend contracts, deployment, or independent
final review.
