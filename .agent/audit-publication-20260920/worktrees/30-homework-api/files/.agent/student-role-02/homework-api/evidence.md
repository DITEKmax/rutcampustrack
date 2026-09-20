# Student homework API evidence

Date: 2026-09-07. Branch: `codex/student-role-02-homework-api`. Baseline:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Risk: S3 because the change adds
student identity/authz and concurrent desired-state writes. The scope is the
frozen homework domain/gRPC and Mobile BFF contract/client lane, plus the
root-authorized lint correction in the existing fixture transport test.

The source changes add a student-only desired completion RPC guarded by a
signed Internal JWT identity interceptor. The domain reloads the current
active STUDENT from the database, requires `AccountStatus.ACTIVE`, matches the
JWT group, active semester and homework group, then uses the existing unique
completion constraint with atomic PostgreSQL `ON CONFLICT DO NOTHING` for
concurrent complete requests and an idempotent delete for uncomplete requests.
Reads use the existing bounded homework query and return Moscow/default or
validated explicit in-semester ranges with stable sorting and null-link
preservation. The BFF emits no-store responses and typed ProblemDetails
status mappings; mobile-core now has Java-first generated types and typed
`StudentApi` methods for both operations.

Java-first `OpenApiSnapshotIT` exported `docs/openapi/mobile-bff.json`, and
`npm run generate:types` produced the matching
`frontends/mobile-core/src/api/generated/mobile-bff.ts`. No OpenAPI snapshot or
generated client was hand-authored. The only existing production file touched
outside the feature path is `MobileAttendanceClient.java`, where the new
scoped `HOMEWORK_NOT_FOUND` enum is mapped to its existing HTTP 404 class so
the exhaustive switch remains compilable.

Final verification passed with exit 0:

- Gradle contract/protobuf generation and full `mobile-bff-app:compileJava`.
- Eight focused Academic tests for signed identity, role/group/semester/account
  guards and desired-state idempotency.
- Three focused Mobile BFF query tests for Moscow/default range, clamping,
  ordering, null links and invalid explicit range.
- Java-first OpenAPI export through `OpenApiSnapshotIT`.
- `npm run generate:types`, `generate:types:check`, mobile-core `typecheck`,
  mobile-core `lint`, frontend contract tests and fixture validation.
- `git -c core.whitespace=cr-at-eol diff --check`.

The initial sandbox Gradle failures were reproduced by root as an environment
classpath visibility issue and final gates passed with `require_escalated`;
the initial strict-stubbing failures were corrected in the new test fixture.
The first OpenAPI attempt had a PowerShell property quoting error and was
rerun successfully with the quoted property. Details and exact commands are
in `checks.json` and `build-diagnostics.md`.

Runtime evidence is limited to the in-process Spring context used by
`OpenApiSnapshotIT`. No product port, deployment, migration or external
service was started. Real PostgreSQL concurrency and the independent Sol
review remain parent integration gates.
