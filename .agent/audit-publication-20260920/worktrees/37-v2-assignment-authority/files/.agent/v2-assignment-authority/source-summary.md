# L5A source milestone — 2026-09-19

## Scope and criteria

This worktree owns source-stage L5A subject, lesson type, assignment, semester,
REST, and in-process gRPC authority. The existing 37-file WIP is preserved.
The source corrections are limited to the confirmed `SubjectService` missing
semicolon and full PUT handling when `lessonTypes` is absent. Four real
PostgreSQL/MockMvc/gRPC IT classes plus one shared fixture base cover exact
identity, canonical lesson types, atomic legacy rejection, assignment grants,
date boundaries, retained gRPC identity, positive/missing historical batches,
same-teacher overlap serialization, semester update serialization, and typed
closure no-mutation snapshots.

## Evidence

* Base revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
* Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437`.
* Resume packet SHA256: `3095B52548C2B3090C49567E62EEF40F3770000E934A12D28EC4EDC9AFC34DE2`.
* Canonical contract SHA256: `287641EC02BCE97C2F587AE7DB12A3143B076A57394FCC7648697F11BC54B28E`.
* H43 `compileJava` completed once with exit code 0; raw merged output and
  warnings are recorded in `evidence/compile-java-2026-09-19.log`.
* Static `git diff --check` completed with exit code 0. Its LF/CRLF notices are
  retained as environment output and did not trigger unrelated edits.
* H45 `compileTestJava` completed once with exit code 0 and H46's five focused
  unit selectors completed with exit code 0; their raw output and fresh XML
  counts are recorded in the corresponding evidence files.
* H47 normal-context integration exited 1 before Docker initialization. H48
  escalated integration exited 1 after reaching PostgreSQL; its raw log and
  XML are preserved before later evidence. H49 reached application logic and
  exited 1 with 15 tests, 14 failures, 0 errors, and 0 skips.
* H49 owned containers were removed by Ryuk; post-wait inspection returned no
  object for either owned ID and the owned/Ryuk filters were empty.
* After H49, root approved a bounded source correction: a local Hibernate
  `UserType<SubjectType>` with explicit `Types.OTHER` lower-case binding on the
  `SubjectLessonType` entity and composite ID. A pure unit regression covers
  three labels plus null read/write semantics. No post-correction Gradle or
  Docker check has run.

## Source hashes

* `SubjectService.java`: `377BF9B1E2F6E2B88529DD362177F0C47318D7A6C31561C4E516FA89491AFFA3`.
* `UpdateSubjectRequest.java`: `0379CB5ABCAEAC2C936BCE3FD385711D668DE00CCF2D53D167C53EB300A669F5`.
* `SubjectLessonType.java`: `6298421F9994C00719F482FA7197DB9DD1751E9DDE85B2535CB65FCCC9DFDF4A`.
* `SubjectLessonTypeId.java`: `C31F4E2069C8EC0E1199C88FCDAA0545E954FEA3A12DA945B2AD9D3B2DCC93AD`.
* `SubjectTypeUserType.java`: `12B6D04203F42FF5C51E31107B0A725595E73C097281CCD9EF90D95A71614B82`.
* `SubjectTypeUserTypeTest.java`: `535256763834BD9600006A0134C84596831533AFF857F14E4961D3E54B32407F`.
* `AbstractAssignmentAuthorityIT.java`: `90175954C1176A3A831F2C21EC2E9879C505ECA9E6A82DE53C338601F92A2EC9`.
* `SubjectAssignmentAuthorityIT.java`: `AC378F66F41B709BABA1AE9DFEF4A3CC725047257DC2FA7D975B1746E6EFAFBC`.
* `AssignmentAuthorityIT.java`: `623AA52AE21A577E656E6C5EF427F523A7CA8E660F9F10103E7586CFB88DDBB7`.
* `AcademicAssignmentGrpcIT.java`: `36FDC3EA45954006B2050F8EB175F947A6508C60EE681D22B9BB2223BF29E8C0`.
* `SemesterAssignmentAuthorityIT.java`: `E50D5C0F30F9103874111E575175F527CA325B0805C0890FEFBD0E7315E50C32`.
* `source-test-plan.md`: `3F1A8FD49B88D0B30DBB9629B08824F948B8452EB7D649B7762F6DCB7C7813DA`.

## Checks and limitations

`compileJava`, `compileTestJava`, and the five focused unit selectors are
PASS/0. H47 normal-context integration failed before Docker initialization;
H48 escalated context reached Docker 28.5.2/PostgreSQL but all 15 cases failed
in fixture setup because V8 constrains `groups.name` to `VARCHAR(32)`. The
bounded fixture correction allowed H49 to reach application logic, where the
four selectors produced 14 failures from a common `VarbinaryJdbcType` binding
of `SubjectType` to `byte[]`; the attempted `@Convert` plus `SqlTypes.OTHER`
mapping is therefore not accepted as fixed. See `mapping-proposal.md` for the
read-only UserType decision gate. No application assertion or product runtime
pass is claimed.

The closure snapshot includes the subject name, all assignment identity/date
rows, lesson type rows, referenced semester name/date/active state, and the full
academic outbox row content. It does not assert foreign-group/headman-false or
nonpositive/empty gRPC batches because those selectors were not authored in this
milestone. The fixture naming correction is test-only and complete. The
production mapping correction is source-frozen but runtime-unverified; H50
heavy work is not held. No claims are made for the focused unit regression or
the four IT classes after this source correction until a new lease records
their exit codes and fresh XML.

## Lock-order regression disposition — 2026-09-20

The test-only correction in `AssignmentAuthorityIT.java` is frozen at
`B3F4A942F3EAFBF27FB56B7E95EA5B9A5EF0D8CE6EA8B25406F235D007B2E10E`.
It delegates `@MockitoSpyBean` repository callbacks through Spring Test's
saved Mockito `defaultAnswer`, preserving calls to the original Spring Data
proxy, and reports an early `MockMvc` failure before a barrier timeout. The
production `SubjectService.java` remains unchanged at
`377BF9B1E2F6E2B88529DD362177F0C47318D7A6C31561C4E516FA89491AFFA3`.

Root H72 exit 1 remains a harness failure: `callRealMethod()` was invoked on
an abstract repository method before PostgreSQL was reached. H74 then ran the
same focused selector against unchanged f59 and passed with exit 0, one test,
zero failures/errors/skips. Both actual REST entrypoints returned 201 and the
exact two-row identity/type assertions passed. H74 XML shows Hibernate emitted
`FOR NO KEY UPDATE` for both semester and subject queries; therefore the earlier
static `FOR UPDATE`/FK `KEY SHARE` deadlock assertion is withdrawn and no
production lock-order correction is authorized. H73 was cancelled and not
started; no artificial inverse locks or forced negative test were added.

H74 raw/context/XML/cleanup evidence is retained at the absolute paths recorded
in `evidence/lock-order-checks.json`. Exact owned PostgreSQL/Ryuk resources were
absent after cleanup. The test-only regression remains available for fresh
independent review; this evidence does not claim that all future writer pairs
are race-free.

## H75 cleanup correction freeze — 2026-09-20

The only post-H74 source change is the test cleanup correction in
`AssignmentAuthorityIT.java`, frozen at
`50E533093C36D1B0409D6F27CEE01859310CBE115B02CD7C3BE1C99BBD88F621`.
The concurrent method now captures its primary `Throwable`, releases the latch,
cancels unfinished request futures, chooses `shutdownNow()` only when a request
is unfinished, and performs a bounded executor join. Cleanup failures are
suppressed onto the primary failure; if the test body succeeds, a cleanup failure
is surfaced on its own. Interruption is treated as a bounded signal and is not
claimed to forcibly terminate arbitrary JDBC work. No query timeout or product
change was added.

H75 then ran once over the whole
`ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT` class and passed:
exit 0, XML `7` tests, `0` failures, `0` errors, and `0` skips. The exact
PostgreSQL/Ryuk resources were absent after cleanup; context/XML/cleanup hashes
are recorded in `evidence/lock-order-checks.json`. H74 PASS and its withdrawn
deadlock inference remain immutable historical evidence; H75 is a whole-class
cleanup-safety check and must not be described as a negative deadlock
reproduction.

## Local commit acceptance — 2026-09-20

The accepted test-only source was committed locally as
`27860f24eb380c782311bbe0cbb65c3d2c75d85d`, with parent
`f59b3b9951c971bde42265ae67df8754dc594a58`. The commit contains exactly one
path, `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/
academic/assignment/AssignmentAuthorityIT.java`, with `257` insertions and no
deletions. Its post-commit source hash remains
`50E533093C36D1B0409D6F27CEE01859310CBE115B02CD7C3BE1C99BBD88F621`.
Untracked `.agent/v2-assignment-authority` evidence remains outside the commit;
no push, merge, promotion, or retest was performed.
