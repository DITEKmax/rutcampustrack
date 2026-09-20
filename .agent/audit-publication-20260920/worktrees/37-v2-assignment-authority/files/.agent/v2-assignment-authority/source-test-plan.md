# L5A source-stage test plan — 2026-09-19

## Scope

S3 source-stage verification for `codex/v2-assignment-authority` at base
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. This plan covers Academic subject,
lesson type, assignment, semester date, REST, and in-process gRPC authority.
It does not cover L5B closure activation, Schedule/Attendance production code,
clients, schemas/migrations, generated/proto output, or integration release.

## Contract and ownership

The frozen contract is
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/LESSONS-L5.md`
(SHA256 `287641EC02BCE97C2F587AE7DB12A3143B076A57394FCC7648697F11BC54B28E`).
The resume packet is
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/LESSONS-L5A-RESUME-2026-09-19.md`
(SHA256 `3095B52548C2B3090C49567E62EEF40F3770000E934A12D28EC4EDC9AFC34DE2`).
Rules are
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`
(SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437`).
The worktree is the sole writer for this scope; its existing 37-file WIP is
preserved. No Gradle, Docker, database, or product-runtime command is run while
the heavy lease is unavailable.

## Confirmed source corrections

* `SubjectService.requireActiveSemester()` has a missing semicolon in the saved
  WIP and must be corrected before the one permitted compile lease.
* `UpdateSubjectRequest.lessonTypes` is nullable for full PUT. When absent, the
  scalar `type` must be treated as the singleton canonical lesson type, and
  referenced removed types must still be rejected. This is a bounded correction
  to the confirmed DTO/service contract mismatch.

## Exact integration test classes and selectors

### `ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT`

* `headmanCreateSubjectWithOneTwoThreeLessonTypesAndInitialAssignmentsPersistsExactIds`
* `legacyTeacherIdsAreRejectedAtomically`
* `fullPutWithoutLessonTypesUsesSingletonAndReferencedTypeRemovalIsRejected`
* `sameNameSubjectsRemainDistinctAndRenamePreservesAssignmentIdentity`
* `closureRoutesReturnTyped409AndLeaveAssignmentHistoryAndOutboxUnchanged`

### `ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT`

* `addTeacherPersistsFullIdentityAndRestKeepsNullEffectiveEnd`
* `sameTeacherSameTypeOverlapRaceCommitsOneAndReturnsOne409`
* `differentTeachersAndAdjacentPeriodsAreAllowed`
* `inactiveOrMissingTeacherGrantIsRejectedWithoutWrite`

### `ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT`

* `assignmentsByIdsReturnRetainedIdentityAndConcreteEffectiveEnd`
* `assignmentsByIdsRejectPositiveMissingIdsAtomically`
* `teacherSubjectsExcludeFutureAndExpiredAssignmentsAndRequireActiveGrant`

### `ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT`

* `referencedSemesterDateChangeIsRejectedButNameOnlyUpdateKeepsEligibility`
* `semesterDateMutationAndAssignmentCreateRaceIsSerialized`

## Fixture and evidence plan

Use `AbstractAcademicIntegrationTest` and its existing PostgreSQL 16/Flyway,
MockMvc, and in-process gRPC setup. Headman requests use the existing STUDENT
role plus trusted `isHeadman` and group context. Teacher reads require an ACTIVE
V24 `user_role_grants` TEACHER row. Producers create subjects, lesson types,
semesters, and assignments through the authority entrypoints; direct SQL is
limited to isolated fixture setup and row-count/effect assertions.

Assertions must cover exact persisted subject/assignment IDs and identities,
same-name subject identity across rename, singleton full PUT normalization,
referenced lesson-type deletion rejection, atomic legacy `teacherIds` rejection,
active grant/group/semester/date boundaries, REST null `validUntilExclusive`,
non-null gRPC effective end (`semester.dateTo + 1`), historical positive/missing
batch atomicity, current future/expired filtering, and one-commit/one-409
same-teacher overlap concurrency. Both closure routes must authenticate exact
subject/assignment relation, return the dedicated RFC problem type, and leave
assignment/date/history rows unchanged.

## Planned checks

The compile lease was completed once after the two confirmed source fixes. The
focused unit selectors also completed once; integration selectors remain
`NOT RUN` in this source-only milestone:

* `./gradlew :services:academic-service:academic-app:compileJava --no-daemon --no-parallel --max-workers=1 --no-problems-report` — H43 exit code `0`; raw evidence is in `evidence/compile-java-2026-09-19.log`.
* `./gradlew :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.assignment.AssignmentAuthorityContractTest --tests ru.rutcampustrack.academic.assignment.AssignmentClosureContractTest --tests ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcContractTest --tests ru.rutcampustrack.academic.semester.SemesterAssignmentLockContractTest --tests ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityContractTest` — H46 exit code `0`; five fresh XML reports contain 13 tests, zero failures/errors/skips.
* `$env:TESTCONTAINERS_REUSE_ENABLE='false'; ./gradlew :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT --tests ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT --tests ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT --tests ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT` — H47 exit code `1` in normal context (Docker provider); H48 exit code `1` in escalated context after Docker/PostgreSQL startup because fixture group names exceed V8 `VARCHAR(32)`. No application assertion passed.

Environment for the future checks is the assigned worktree with the main heavy
lease, isolated test PostgreSQL, and existing harness. Runtime evidence is
`NOT RUN`; no runtime PASS is claimed here. Static evidence is limited to the
source diff, test-plan selectors, and the confirmed defect locations.

## Boundaries and limitations

No claim of L5A completion, clean build, unit pass, PostgreSQL pass, race pass,
or independent review is made by this plan. Any new WARN/ERROR must be tied to a
criterion and reproduced before changing code. New product or contract decisions
must be sent to root as a bounded delta; this worktree does not redesign L5B or
the shared transport.
