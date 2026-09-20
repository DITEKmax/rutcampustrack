# L5A scoped runtime gate — 2026-09-19

This is a plan for a later, separately leased integration-test run. It is not
runtime evidence and it does not authorize Docker or database commands.

`AbstractAcademicIntegrationTest` currently constructs a static PostgreSQL 16
Testcontainers instance with `withReuse(true)`. The selected future setup is
the process-local `TESTCONTAINERS_REUSE_ENABLE=false` override from the
verified Testcontainers 1.20.4 configuration evidence at
`.agent/orchestration-v2/evidence/testcontainers-1.20.4-config-bytecode.txt`.
Before running the four L5A IT classes, root must use that override and record
the task-owned container identity, database/port scope, and cleanup result.
The setup must not edit the global `~/.testcontainers.properties`, reset a
shared database, or remove foreign data.

The later lease must run the exact four `--tests` selectors with the root-owned
`integrationTest` task-scoped `failOnNoMatchingTests=false` setting. Afterward,
fresh XML under `build/test-results/integrationTest` must contain all four
classes, have nonzero test counts, and have no skipped tests. A missing XML,
zero test count, or skipped test is a failed evidence gate. The selectors are
exactly:

* `ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT`
* `ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT`
* `ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT`
* `ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT`

H49 was the one separately leased runtime attempt after the fixture correction.
It ran the four selectors under elevated Docker context with the process-local
reuse override, reached PostgreSQL 16.13, and exited 1 with 15 tests, 14
failures, 0 errors, and 0 skips. Fresh XML and the raw stream are under
`evidence/h49-xml/` and `evidence/integration-tests-h49-2026-09-19.log`.
All four classes share a first application cause: `SubjectType` is passed to
`VarbinaryJdbcType` and cast to `byte[]`; the attempted explicit converter plus
`SqlTypes.OTHER` mapping is not a runtime fix. H49-owned containers were
removed by Ryuk; after a 20-second wait both owned IDs returned `No such
object`, and owned/Ryuk filters were empty. There is no runtime PASS claim.

H50 subsequently ran the focused unit set including `SubjectTypeUserTypeTest`
and the five existing contract classes: 15 tests passed with zero failures,
errors, or skips. H51 then ran the four selectors once with the same isolated
reuse setting and reached PostgreSQL 16.13. It exited 1 after 15 tests, with
AssignmentAuthorityIT 5/0/0/0 and SemesterAssignmentAuthorityIT 2/0/0/0,
while SubjectAssignmentAuthorityIT was 5/4/0/0 and
AcademicAssignmentGrpcIT was 3/3/0/0. The subject/gRPC first cause was
SQLSTATE `0A000`: the composite-id tuple lookup
`(lesson_type, subject_id) IN ((?, ?))` could not resolve a row-comparison
operator for the V11 cross-type `subject_type = text` operator. H51 raw/XML
and the exact owned-cleanup evidence are under `evidence/integration-tests-h51-2026-09-19.log`,
`evidence/h51-xml/`, and `evidence/h51-owned-cleanup-2026-09-19.log`;
the post-run owned and Ryuk inventories were empty and foreign containers were
untouched.

Root then approved a bounded PGobject correction in the local UserType:
`PGobject` is typed as `subject_type` and carries the lower-case label, while
typed null uses `setNull(..., Types.OTHER, "subject_type")`. The app adds only
the managed-version `compileOnly` PostgreSQL driver declaration; Assignment,
schema, migrations, and global converters remain unchanged. This latest
correction is source-frozen but has not been compiled or runtime-tested. Any
next runtime attempt requires a new root lease and must use the existing four
IT selectors, process-local reuse disablement, fresh XML, and task-owned
cleanup. No foreign-container cleanup is authorized by this plan.
