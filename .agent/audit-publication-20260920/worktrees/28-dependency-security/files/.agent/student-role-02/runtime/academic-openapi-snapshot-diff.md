# OpenAPI snapshot recheck — line ending defect gate

Date: 2026-09-07 18:17 MSK  
Revision: `codex/student-role-02-dependency-security` after Springdoc `2.8.9`

## Request and reproduction

As the compatibility recheck for the Springdoc correction, run:

```text
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.integration.OpenApiSnapshotIT" --no-daemon --console=plain --stacktrace
```

Exit code: `1`. The application starts and serves `/api-docs`; the assertion
fails at `OpenApiSnapshotIT.java:68` with snapshot drift.

## Evidence

The failing report is retained as
`academic-openapi-snapshot-fail.xml` (SHA-256 recorded in the adjacent command
output). The report's expected and actual strings, after removing AssertJ's
display indentation, have identical content on all `6,954` lines. A byte
comparison shows only line-ending representation:

- checked-out `docs/openapi/academic.json`: `6,954` LF bytes, all preceded by
  CR (`CRLF`); 223,645 bytes;
- generated normalized response extracted from the report: `6,954` LF bytes,
  no CR; 216,691 bytes;
- non-line-ending content: no differing lines;
- direct focused failure: `1` test failed, exit `1`.

The extracted expected/actual files are retained under this runtime evidence
directory. This is a deterministic checkout line-ending mismatch, not a
Springdoc schema or route change. The application reached the endpoint and
logged Springdoc initialization successfully.

## Bounded decision needed

The existing manifest scope does not own this test or `docs/openapi` snapshots.
Root must choose a small repository-level normalization delta (for example,
normalize snapshot comparison to LF or normalize the affected committed
snapshot files) before any out-of-scope file is changed. Do not regenerate or
rewrite snapshots until that decision is recorded; semantic API changes are
not evidenced here.
