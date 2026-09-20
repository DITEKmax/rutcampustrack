# Evidence

## Scope

The correction writes exactly these product paths:

1. `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`
2. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`

Evidence is confined to `.agent/student-academic-b/sql17-v26-runtime-fix/`.
The shared checkout contains foreign dirty and untracked work; no unrelated
path was reverted, normalized or claimed.

## Criteria and evidence

- V26 line 5 is `CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;`.
  Its asset hash constraint at line 152 calls `public.digest(content,
  'sha256')`.
- Academic IT lines 99, 183 and 191 use `public.digest(...)`; the negative
  qualified-name guard found no unqualified `digest(` in either owned file.
- The pre-fix test calls `raceDeletingDifferentTypes(..., true)` and keeps
  `isEqualTo(2)` commits plus zero remaining child rows. It installs the
  deferred `subject_lesson_types_z_pre_fix_barrier_trg` after
  `subject_lesson_types_minimum_trg` by lexical name order. Its trigger
  function calls `pg_advisory_xact_lock_shared` with the test-specific
  two-int key. The coordinator holds `pg_advisory_lock`, releases
  `commitGo`, observes two distinct ungranted `pg_locks` PIDs, then unlocks
  before waiting on the commit futures. The monotonic deadline bounds only
  database-state observation; there is no timing sleep or race retry.
- The corrected test calls `raceDeletingDifferentTypes(..., false)` and keeps
  exactly one commit, one expected `P0001`/`40001` failure and the existing
  SQLSTATE classifier. It does not install or acquire the unsafe test barrier.
- Posthashes and bytes are recorded in `manifest.json`: V26
  `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`,
  `17076`; Academic IT
  `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B`,
  `51464`.

## Runtime evidence

The prior Academic PG16 session `81124` remains preserved as evidence: the
failure XML SHA256 is
`D474F46C3F2976D4119BB590B3FB75C19C9E4AE9EA1CCB0D9264651F56044C2C`,
`133618` bytes, with 15 tests, 7 failures, 0 errors and 0 skipped. The
failure file is the existing
`.agent/student-academic-b/sql17-v26-content-repair/runtime-fail-2026-09-10.md`
and its current SHA/bytes are recorded in `manifest.json`.

The second exclusive Academic PG16 session `85479` is recorded in
`runtime-fail-2-2026-09-10.md`: exit `1`, `BUILD FAILED` after `1m55s`, 15
tests, 2 failures, 0 errors, 0 skipped, test time `35.579s`. Its XML SHA256 is
`C228856B16FAD58609851C4E902166BA4C253D1D247FFF6F1AC081649838D348`,
`100991` bytes, mtime `2026-09-10T18:49:52.5396514Z`. The failures are the
ready asset test at line 103 and published campus test at line 186; both
reproduce SQLSTATE `42702` (`ambiguous column reference plan_version_id`) in
`validate_campus_map_ready_asset()` line 30.

The heavy lease is `RELEASED`, Schedule was not run, and Java/Javaw plus ports
`18210`/`18211` were clear. Latest runtime status is
`FAILED_ACADEMIC_LEASE`. This follow-up is evidence-only: no product runtime
source was edited and no Terra escalation is authorized by this record. Root
must decide the bounded correction and any escalation gate.

## Limitations

Static source readback, delimiter balance and whitespace checks do not prove
Java compilation or PostgreSQL behavior. The SQL namespace and trigger-order
claims remain followed by a second request-linked runtime defect in the
changed V26 function. No claim is made that either failure XML contains
post-fix results.
