# ACCESS-A2 source checkpoint

Implemented the additive resolver contract and its fail-closed Academic
source boundary on the frozen E base.  The resolver authenticates signed
student claims against current STUDENT grant status and `roles_version`,
projects the student's own complete clipped history and effective subject IDs,
and computes a separate rank cohort.  Past transferred history returns HIDDEN
before any roster read.  Terminal users retain only own authorized history and
remain ineligible.  Legacy user group/status fields do not infer or suppress
the authoritative grant scope.  The read transaction explicitly uses
`REPEATABLE_READ`; unresolved student scope maps to gRPC `PERMISSION_DENIED`,
while dependency/source failures retain `UNAVAILABLE`.

The focused unit and gRPC cases are authored, and the PostgreSQL16/Flyway
adapter cases exercise multiple assignment groups and grant status authority.
The PostgreSQL class also contains a gated concurrent authority/history update
case invoking the resolver through a transaction proxy built from its actual
`@Transactional` annotation, proving the resolver keeps one repeatable-read
snapshot.
They have not been executed because this stage is source authoring only.

The next checker may run the following exact selectors from this worktree after
the required lease is granted:

```text
.\gradlew.bat :services:academic-service:academic-app:test --tests "ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeServiceTest" --tests "ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest" --tests "ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest"
.\gradlew.bat :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.studentprojection.StudentProjectionQueryAdapterIT"
```

`integrationTest` is required for the `*IT` PostgreSQL class because the root
build separates unit and integration selectors.  Generated-proto compilation,
Spring proxy creation, SQL parameter binding, Testcontainers execution,
full integration, and fresh independent Sol review remain pending.  Real
transfer/grant synchronization behavior is a later runtime gate; no writer or
migration change was made here.

No product checks, build, Docker, gRPC server, or runtime command was run at
this checkpoint; the sole mechanical check was `git diff --check` (exit 0).
See `packet.md`, `evidence.md`, `checks.json`, and `diff.md` for the contract,
evidence, exit-code ledger, and complete scope manifest.

The A3 bounded repair closes two independent findings.  The resolver now
re-reads `auth_sessions` in its repeatable-read transaction and requires exact
sid/user, strict live expiry/revocation, session and roles versions, the
selected current STUDENT grant, and matching signed selected identity.  This
authorization completes before any semester/history/subject/roster read.
`StudentProjectionGrpcErrors` recognizes only Spring transaction/data-access
failures (including proxy-boundary causes) as typed dependency unavailable;
unknown programmer failures remain internal/unspecified.  The existing
unresolved-scope permission mapping remains covered by a focused transport
assertion.  A3 unit, gRPC, and PostgreSQL sources are authored and remain
unexecuted pending the main heavy lease; `checks.json` records the sole
source-stage `git diff --check` result.

A4 F3 is a bounded fixture correction in
`StudentProjectionQueryAdapterIT.insertSession`: the SQL has seven bind
placeholders, so the extra eighth `Timestamp.from(createdAt)` argument was
removed.  This leaves the RR concurrency fixture's intended session fields and
does not alter F1/F2 source behavior.  The post-correction diff check is
recorded in `checks.json` with exit 0; all product tests and runtime evidence
remain pending the main heavy lease.

A5 F4 is a narrow authority-error classification repair.  With valid ACTIVE
selected-semester membership, missing current history and current-group
mismatch now return `INCONSISTENT_SOURCE`/`UNAVAILABLE`; the earlier empty
clipped-membership guard remains `STUDENT_SCOPE_UNRESOLVED`/`PERMISSION_DENIED`.
Focused unit cases cover both divergence forms and genuine missing membership,
and the gRPC case verifies the packed inconsistent-source detail.  Product
tests remain NOT RUN pending the main lease.

H12 execution used the frozen 15-file product manifest.  Canonical manifest
SHA-256 is `E9C165EAC47D71B8EA07CDD379324F07A5C20166C14848BCF1B069B36C4234E0`;
the exact rows are in `manifest.sha256`.  The three focused unit selectors
passed (32 tests total, zero skipped/failures/errors, Gradle exit 0).
The exact PostgreSQL integration selector then exited 1: all 3 tests failed
at the shared fixture `StudentProjectionQueryAdapterIT.java:261` because the
PostgreSQL relation `groups` did not exist.  Testcontainers PostgreSQL 16.13
and Flyway ran successfully through 26 migrations per generated schema before
the fixture insert.  The report observed a JDBC URL with
`?loggerLevel=OFF?currentSchema=...`; this is recorded for the next owner to
diagnose, with no repair made here.  Post-run ID-filtered `docker ps -a`
returned no rows for PostgreSQL or Ryuk, confirming owned resource cleanup.
No further checks, source edits, or retries were made after the failure.

H14 applied the approved one-line test-fixture correction: schema URL building
uses `&` when the Testcontainers URL already has `?loggerLevel=OFF`, and `?`
when no query exists.  The source helper check and post-correction
`git diff --check` passed; the updated canonical 15-file manifest is
`E92FF0B071038B7E3EF5F7ED3ABE6A752130E9ED3C608DC2BFE7EEFDC6262F83`.
The H14 integration selector still exited 1 with 3/3 failures, now at the
fixture's `INSERT INTO groups` because migrated `groups` has no `code` column.
The report confirms corrected `&currentSchema` URLs and 26 migrations per
schema, so URL/schema selection is no longer the observed failure.  No source
repair or retry followed this new failure; owned Postgres and Ryuk resources
were confirmed cleaned up.

H15 removed the obsolete `groups.code` fixture column after V8 schema review.
All other StudentProjectionQueryAdapterIT fixture helpers were reconciled
statically against the complete V26 migration chain with no additional
discrepancy.  The frozen 15-file manifest is
`C74CC6601ED9B20E3367E894D0F6C5BC7FF2406F5EBEFE6170A9513116C738D4`.
The exact H15 PostgreSQL integration selector passed (3/3 tests, exit 0),
including assignment binding and the actual annotation-backed repeatable-read
concurrency case.  Flyway applied 26 migrations per generated schema with
corrected `&currentSchema` URLs; PostgreSQL/Ryuk cleanup was confirmed by
ID-filtered `docker ps -a`.  No further source changes, checks, or retries
were made after the successful run.

H18 corrected only the A7 regression fixture: the closed history ends at
2026-10-01, the selected semester is explicitly 2026-09-01 through
2026-09-30 inclusive, and the service clock is explicitly 2026-10-02.  This
keeps the production A7 rule under test: an ACTIVE student with selected
semester membership but no current history at the server date fails as
`INCONSISTENT_SOURCE` before any roster read.  The frozen 15-file product
manifest is `0A033E8E07EC57AD6219844512D0232C666BC71385F3A844D24F4C28C13A7923`.

The exact H18 unit selector passed with exit 0 and 34/34 tests: 18 scope,
7 gRPC, and 9 homework interceptor tests, all with zero skipped/failures/errors.
The exact H18 PostgreSQL selector passed with exit 0 and 3/3 adapter tests,
including the annotation-backed repeatable-read concurrency case.  PostgreSQL
16.13/Testcontainers and Flyway 26-migration-per-schema evidence is in
`evidence.md`; both owned container IDs were confirmed absent by filtered
`docker ps -a`.  No checks were rerun after the passing selectors.  Product
source is released for fresh independent Sol review; real transfer/grant
synchronization remains outside this fixture scope.
