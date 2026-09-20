# SQL17/V26 runtime correction packet

Date: 2026-09-10. Assigned role: fresh bounded implementation leaf,
`gpt-5.6-luna` / `max`. Risk: S3 (PostgreSQL migration namespace and
concurrency proof). Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Root decision: correct only the two reproduced Academic runtime failures under
the frozen scope; do not redesign V17/Schedule sources or widen ownership.

## 1. Goal

Make V26's pgcrypto lookup deterministic across schema-isolated PG16 tests and
make the pre-fix deferred child-only race a strict, deterministic READ
COMMITTED proof that both unsafe checks complete before either transaction can
commit. Preserve the corrected production race and all accepted content
behavior.

## 2. Context/evidence

Academic PG16 session `81124` ran on 2026-09-10 in a Windows local worktree
with Java 21/PostgreSQL 16 Testcontainers. Exact command:
`./gradlew.bat :services:academic-service:academic-app:integrationTest --tests
ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon
--no-parallel --max-workers=1 --console=plain --continue`; exit code `1`.
The preserved XML is
`services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
with SHA256
`D474F46C3F2976D4119BB590B3FB75C19C9E4AE9EA1CCB0D9264651F56044C2C`,
`133618` bytes, 15 tests, 7 failures, 0 errors and 0 skipped. Six failures
hit V26 line 129 with SQLSTATE `42883`, `digest(bytea, unknown) does not
exist`, because an unqualified `CREATE EXTENSION IF NOT EXISTS pgcrypto` is
installed in the first isolated schema while later schemas see the extension
already existing but do not have its schema on `search_path`. The separate
failure is `StudentFoundationMigrationIT` line 532: `preFix...` expected 2
commits but observed 1; `deletesReady` passed, but commit release did not
ensure both deferred EXISTS checks ran before one transaction committed.

Pre-edit source guards are V26 SHA256
`51AF63A7A591E278353B66816A01294E4823A705269D88C16BFABF007A3F13B8`
(`17050` bytes) and Academic IT SHA256
`FCF37A0EA283CB159FC9BEF638B5FD2366775B8F519AF7399A85A97866095F5B`
(`47643` bytes). The preserved failure evidence is
`.agent/student-academic-b/sql17-v26-content-repair/runtime-fail-2026-09-10.md`;
its XML hash and byte count remain immutable evidence.

## 3. Relevant scope

Product writes are limited to exactly:

1. `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`
2. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`

Evidence writes are limited to this new directory:
`.agent/student-academic-b/sql17-v26-runtime-fix/`. Existing foreign dirty and
untracked work in the shared checkout is preserved.

## 4. Required behavior

- V26 installs pgcrypto with `CREATE EXTENSION IF NOT EXISTS pgcrypto WITH
  SCHEMA public;`, uses `public.digest(content, 'sha256')`, and leaves no
  unqualified `digest(` in this owned Academic scope.
- Academic IT ad-hoc content hashes use `public.digest(...)` as well.
- `preFixDeferredChildOnlyCheckReproducesConcurrentZero` remains at the
  default PostgreSQL READ COMMITTED isolation and requires exactly 2 commits
  and zero remaining `subject_lesson_types` rows.
- In pre-fix mode only, install a second test-only deferred AFTER DELETE
  constraint trigger with a lexically later name than
  `subject_lesson_types_minimum_trg`. Its function takes a shared
  transaction-level advisory lock on a test-specific gate. Before releasing
  commitGo, the coordinator owns the matching exclusive session-level lock;
  after release, wait on database state until `pg_locks` reports two distinct
  waiting worker PIDs for that gate, then release the coordinator lock.
- The barrier is after the unsafe EXISTS trigger by PostgreSQL same-kind trigger
  name ordering, so both unsafe checks finish before either commit completes.
- Corrected production race remains natural parent-row serialization with one
  commit, one expected `P0001`/`40001` failure, and the existing classifier.

## 5. Constraints

No changes to V17, Schedule sources, V24/V25, Flyway IT, other app code,
generated/frontend/proto/build files, shared status or manifest paths, or
reserved Auth API files. No Gradle, Docker, Testcontainers, PostgreSQL or
product runtime in this turn; no retry, timing sleep, isolation-level change,
production advisory barrier, staging, commit, reset, clean, migration,
deployment or secret access. Do not broaden the exception classifier. Do not
create children.

## 6. Existing patterns

Reuse `StudentFoundationMigrationIT`'s schema-isolated `JdbcTemplate`,
`dataSource`/connection helpers, `CountDownLatch`, `ExecutorService`,
`SAME_THREAD`, and SQLSTATE assertion helper. PostgreSQL fires same-kind
triggers in name order; the barrier name must sort after the unsafe trigger.
Use a two-int advisory key so its `pg_locks` classid/objid can be observed
without ambiguity. Bound phase observation by a monotonic deadline and
database-state polling only; do not sleep.

## 7. Acceptance criteria

- Exactly the two product files above plus this evidence directory are changed
  by this correction leaf; all foreign changes remain intact.
- Every owned V26/Academic IT `digest(` call is schema-qualified with
  `public.digest`, and the extension is explicitly installed in `public`.
- The unsafe proof stays READ COMMITTED, asserts exactly 2 commits and zero
  rows, and uses the observed two-waiter test-only phase barrier.
- The corrected race has no test-only unsafe barrier and keeps its one-commit,
  one-expected-failure assertions and SQLSTATE classifier.
- The preserved XML evidence hash/bytes are recorded unchanged.
- Full changed-method readback, raw posthash/byte guards, targeted markers,
  Java delimiter balance, trailing-whitespace scan and scoped `git diff
  --check` pass. Runtime remains pending the root heavy lease.

## 8. Verification

Run only static checks in this turn: `git rev-parse HEAD`; SHA256 and byte
counts for both product files; targeted `rg` for qualified pgcrypto/digest,
barrier symbols and assertions; full readback of changed SQL and Java methods;
Java delimiter balance; trailing-whitespace scan; and scoped
`git diff --check`. Record each command, exit code, environment and evidence
path in `checks.md`/`evidence.md`. Record runtime as
`PENDING_ROOT_HEAVY_LEASE`; the prior failed command/XML remain preserved and
must not be rerun here.

## 9. Do not

Do not touch V17, Schedule IT, V24/V25, FlywayMigrationIT, any path outside the
two product files and new evidence directory, or the shared status/manifest.
Do not change default isolation, weaken exact assertions, accept 1-or-2
commits, retry the race, add sleeps, or put the barrier in production SQL. Do
not claim runtime PASS, alter the failure XML, escalate to Terra without a
new recorded defect/complexity gate, stage/commit/reset/clean, or create
children. Release ownership after static evidence is recorded.
