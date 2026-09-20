# Runtime failure 01

Status: `FAIL / HEAVY RELEASE`.

- Command: `:services:shared:shared-security:test` with the exact
  `InternalJwtValidatorTest` and `DualModeUserContextFilterTest` selectors,
  `--no-daemon --no-parallel --max-workers=1 --console=plain`.
- Started: `2026-09-10T20:09:16.2212687Z`.
- Ended: `2026-09-10T20:09:47.9117404Z`.
- Exit code: `1`; `BUILD FAILED in 30s`.
- Failure boundary: `compileTestJava`; tests did not execute and no new JUnit
  XML is claimed.
- Evidence: `command-01-shared.log` and `command-01-meta.json` in this
  directory.

The compiler reported 25 unresolved same-package symbols from
`InternalJwtValidatorTest`. Immediately after the failure, the corresponding
production and test-fixture `.class` files existed and were readable with
`javap`; Gradle had reported both compile tasks `UP-TO-DATE`. Gradle also
reported an independent `AccessDeniedException` while writing
`build/reports/problems/problems-report.html`. This evidence does not yet
classify the failure as a product or test-source defect.

The sequential runtime chain stopped before the Auth unit and PostgreSQL IT
commands. No source or test file was changed. No Java process remained after
the failed command. Further diagnosis requires a new root runtime decision.
