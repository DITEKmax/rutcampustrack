# SQL26 ambiguous-column fix packet

Date: 2026-09-10. Agent: `/root/sql26_ambiguous_column_fix`. Assigned model/effort: `gpt-5.6-luna` / `max`. Risk: S3. Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. The shared checkout is dirty; foreign changes remain untouched.

## 1. Goal

Remove the confirmed SQLSTATE 42702 ambiguity in `validate_campus_map_ready_asset()` while preserving the existing ready-asset metadata assignment and all other V26 semantics.

## 2. Context/evidence

The accepted V26 source preimage was SHA256 `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`, 17076 bytes. The exact academic selector session `85479` exited 1; fresh XML SHA256 was `C228856B16FAD58609851C4E902166BA4C253D1D247FFF6F1AC081649838D348`, with 15 tests and 2 failures sharing the PL/pgSQL line 30 ambiguity for `plan_version_id`. The failing lookup used unqualified `id`, `plan_version_id`, and `format` against a local `plan_version_id` variable.

## 3. Relevant scope

Product scope is exactly `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`. Evidence scope is exactly `.agent/student-academic-b/sql26-ambiguous-column-fix/`. No Java, test, V17, build, config, manifest outside this evidence directory, or shared status file is in scope.

## 4. Required behavior

Use the stable alias `asset` in the ready-asset lookup. Select with `asset.*` into the existing `asset_record` local variable and qualify each `campus_map_asset` predicate column as `asset.id`, `asset.plan_version_id`, and `asset.format`. Preserve the `NEW` values, metadata comparison, published-graph behavior, lock behavior, TTL behavior, format behavior, and digest behavior.

## 5. Constraints

Use one minimal source hunk. Do not execute SQL, Flyway, Gradle, Docker, Testcontainers, or other heavy runtime commands. Do not change schema behavior beyond disambiguation. Do not stage, commit, reset, clean, deploy, or modify foreign work. Terra escalation is not applicable.

## 6. Existing patterns

The function uses `SELECT ... INTO` with a `%ROWTYPE` local variable. PostgreSQL table aliases and `SELECT asset.* INTO asset_record` preserve row assignment while separating table columns from PL/pgSQL variables.

## 7. Acceptance criteria

- Exactly one minimal hunk changes the V26 lookup.
- Every selected and referenced `campus_map_asset` column in that lookup is qualified by `asset`.
- The existing `asset_record` assignment remains in use.
- No other semantic or formatting changes occur.
- Pre/post SHA256 and byte counts, exact diff, checks with exit codes, and runtime limitation are recorded here.

## 8. Verification

Use the guarded prehash check, source readback, focused marker search, negative search for the three dangerous unqualified predicates, trailing-whitespace scan, posthash/byte count, and scoped `git diff --check`. Runtime is N/A by the frozen contract; no SQL execution is evidence for this leaf.

## 9. Do not

Do not redesign V26, edit V17 or Java/IT files, add tests, broaden product scope, claim runtime PASS, or invent cleanup. If a new defect appears, stop and send its reproduction and bounded expansion request to root.
