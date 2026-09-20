# L5A review packet — 2026-09-19

## 1. Goal

Complete the source-stage L5A subject lesson-type and immutable assignment
authority work in the assigned `v2-assignment-authority` worktree. Preserve the
existing 37-file WIP, adapt the legacy subject/gRPC fixtures to V25 authority,
and leave the result ready for an independent review. L5B, clients, deployment,
and the final full solution gate remain open.

## 2. Context and evidence

- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-assignment-authority`.
- Branch: `codex/v2-assignment-authority`.
- Frozen base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
- Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Current state: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md`, SHA256 `65105C438D2D6EEE0219BE6448EDE8A0F4BE8F8AA67909ECFAA14D9B296041CD`.
- Resume contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/LESSONS-L5A-RESUME-2026-09-19.md`, SHA256 `3095B52548C2B3090C49567E62EEF40F3770000E934A12D28EC4EDC9AFC34DE2`.
- Canonical contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/LESSONS-L5.md`, SHA256 `287641EC02BCE97C2F587AE7DB12A3143B076A57394FCC7648697F11BC54B28E`.
- Coverage packet: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/LESSONS-L5A-COVERAGE-2026-09-19.md`, SHA256 `B376E7276ED5304D5E3598A4F715EA4186D9C04930975BAACC5ECE88095974D6`.
- H60/H61 gate: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/H60-H61-L5A-CHECKS.md`, SHA256 `274CB29A3CFA2FBE1405006C8C067D6135C8D26CAA87C0C64644880F74643753`.

H63 reached real PostgreSQL and Testcontainers with the frozen six-selector
command. Five classes passed; `SubjectServiceIT.listSubjects_filteredByGroup`
failed because the admin assertion inspected only page zero while the response
reported three pages. The response showed `totalElements=46`, `totalPages=3`;
this was a test pagination defect, with no product authorization finding.
After the bounded pagination correction, H64 reran the same six selectors and
passed all 47 tests.
Owned H63 resources were observed in fresh XML and absent from the post-run
Docker inventory: Ryuk `ae47898cf4f018d0bef01eecbf3ad82eb4a55e940dd761cb2e7aceb7998c3c24`
and PostgreSQL `788bb15ae17c4ee27e9354c36595ace51897449d037488e103081d37d20b8fdb`.
The owned-resource record is in `evidence/h63-owned-cleanup-2026-09-19.log`.

## 3. Relevant scope

The source inventory includes the preserved product WIP, the approved UserType
and build dependency correction, the new API/entity/repository authority
sources, four new Academic IT classes plus the shared fixture, legacy
`SubjectServiceIT`/`AcademicGrpcIT` adaptations, the bounded Attendance
consumer test, and this null-element validation correction. The complete
46-file source/build hash inventory is `evidence/l5a-source-hash-manifest.json`.

The exact Academic selectors are:

```
ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT
ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT
ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT
ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT
ru.rutcampustrack.academic.subject.SubjectServiceIT
ru.rutcampustrack.academic.integration.AcademicGrpcIT
```

The Attendance consumer selector is:

```
ru.rutcampustrack.attendance.report.ReportServiceTest
```

The latest key hashes are `CreateSubjectRequest=
CEAEC78F499110A56C684302DF90B97EEDC7BAD35E7F8952D2B0C6F189CD96F2`,
`SubjectAssignmentAuthorityIT=
64BAB23B0AD5333D1B6F607EC9ECE541392284D55FE2440EF8157B7B6AE9E0A6`,
`SubjectServiceIT=C43718E2B5172A356B3E70E7B31605796A73427CF5EC5320722E8966B01263E5`,
`AcademicGrpcIT=57AE1E3CEB13219413B66007070B9AA2308695BBC28351BA1A5D798009E6B2FF`,
`SubjectTypeUserType.java=6864CB53EEB949509F37C10D7A309B085E5FE230FE8A012D54DB76908630322D`,
and `academic-app/build.gradle.kts=AFAF07A54FB477F74FF8444614ACAC53CC89309E9A1C0112650963054081AEE6`.

## 4. Required behavior

- Derive subject group from the trusted headman context and keep foreign-group,
  non-headman, and assignment-grant checks fail closed.
- Create one to three distinct canonical lesson types and complete immutable
  assignment tuples atomically; reject nonempty legacy `teacherIds`.
- Enforce active teacher grants, semester date bounds, same-teacher overlap
  serialization, different-teacher and adjacent-window allowance, and stable
  assignment identity on rename/update.
- Preserve REST null end dates and expose effective semester end plus one day in
  gRPC; support historical positive, missing, nonpositive, and empty batches.
- Return typed closure conflicts with zero writes after full assignment,
  semester, history, outbox, and related snapshot checks.
- Keep unreferenced subject deletion allowed only after a successful Schedule
  boundary check; fail closed on references or Schedule unavailability.
- Traverse admin subject pages using the server-reported `page.totalPages` so
  the legacy list assertion covers all pages without a fixed oversized page.
- Reject a null element in `initialAssignments` at HTTP validation with 400 and
  preserve subject, lesson-type, and assignment row counts.

## 5. Constraints

No schema/migration/proto/generated-client changes, no production Attendance or
Schedule changes, no global converter/config edits, no data cleanup, no source
changes during runtime gates, no push/deploy/main merge, and no children or
Terra escalation. Existing foreign Docker containers remain untouched.

## 6. Existing patterns

The tests use `AbstractAcademicIntegrationTest`/MockMvc/in-process gRPC,
RequestContext and the existing headman authority model. The fixture creates
isolated groups/users and canonical V25 rows, uses Moscow date semantics, and
preserves durable grants. Real PostgreSQL/Testcontainers is used for Academic
IT; mocks are limited to unrelated Schedule/Academic consumer boundaries.

## 7. Acceptance criteria

The source criteria in the canonical contract and coverage packet are covered
by the frozen selectors and the Attendance consumer test. H60 passed all 16
Attendance tests. H63 recorded the page-zero test failure; H64 produced fresh
XML for all six Academic classes with `47` tests, `47` passes, `0` failures,
`0` errors, and `0` skips. The H64 result is the bounded runtime evidence for
this source stage; independent review and the full-solution gate remain open.

## 8. Verification

- H43 `compileJava`: exit 0; preserved in `evidence/compile-java-2026-09-19.log`.
- H45 `compileTestJava`: exit 0; SHA `CBE344ECC2F0A87117E6F748C8BCF1526C4C00A8601E351785CE1D3E673C3B6E`.
- H46 five focused contract selectors: exit 0, fresh XML `13` tests, zero failures/errors/skips.
- H54 six UserType/contract selectors: exit 0, fresh XML `15` tests, zero failures/errors/skips.
- H60 Attendance selector: exit 0, fresh XML `16` tests, zero failures/errors/skips; raw/evidence in `h60-attendance-2026-09-19.log` and `h60-xml/`.
- H60 raw capture limitation is explicit: the saved log is abbreviated, while
  the actual tool result recorded exit 0 and the fresh XML is preserved. The
  stream was not reconstructed and H60 was not repeated solely for logging.
- H61 compile gate: exit 1 on the missing `otherFixture`; no tests ran, raw/evidence in `h61-academic-2026-09-19.log`.
- H63 Academic runtime: exit 1 with fresh six XML files in `h63-xml/`; complete direct streams are `h63-academic-stdout-2026-09-19.log` and `h63-academic-stderr-2026-09-19.log`, with exact start/end/exit in `h63-academic-context-2026-09-19.txt`.
- H63 Docker before/after inventories show no net retained containers; owned IDs and Ryuk removal evidence are recorded separately.
- H64 Academic runtime: exit 0 with fresh six XML files in `h64-xml/`; complete direct streams are `h64-academic-stdout-2026-09-19.log` and `h64-academic-stderr-2026-09-19.log`, with exact start/end/exit in `h64-academic-context-2026-09-19.txt`. The aggregate is 47/0/0/0.
- H64 Docker before/after inventories show no net retained containers; owned PostgreSQL/Ryuk IDs and post-run absence are recorded in `h64-owned-cleanup-2026-09-19.log`.
- H64 XML digest labels in `h64-checks.json` were corrected to the actual files;
  the raw H64 XML and direct streams were not changed.
- `git diff --check`: exit 0; only existing LF/CRLF conversion notices were emitted.

The pagination correction was run successfully under H64 with the exact six
selectors, process-local reuse disabled, fresh XML counts, and owned-resource
evidence. The null-element correction is source-only and awaits the later
targeted `SubjectAssignmentAuthorityIT` lease. Independent Sol review and the
final full-solution gate are still required.

The calendar fixture correction is source-only and limited to the two contract
tests `AcademicAssignmentGrpcContractTest` and
`SemesterAssignmentLockContractTest`. The gRPC fixture derives its active,
future, and expired assignment dates from `LocalDate.now(Europe/Moscow)` and
keeps a 30-day past/90-day future semester horizon. The semester lock fixture
derives its current semester from the service's `LocalDate.now()` clock and
keeps a 60-day future end. The historical-ID gRPC fixture remains static. This
removes the fixed September 2026 horizon while retaining identity, effective
end, date filtering, row-lock, and zero-save assertions. The exact six-selector
H54 command is prepared but was not run in this source-only slot; prior H54
PASS evidence remains historical evidence for the pre-correction source and
the changed fixtures need that targeted rerun.

## 9. Do not

Do not claim H63 as PASS, infer a product defect from the page-zero assertion,
rerun Gradle under this source-only stage, enlarge the page size to hide the
pagination, hardcode total counts, delete shared database rows, clean foreign
Docker resources, widen production scope, or close L5B/client/full-solution
work.

## Pending decisions and limits

The source correction is frozen at the `SubjectServiceIT` pagination traversal,
the null-element validation regression, and the calendar fixture correction in
the two contract tests; H64 is PASS for the prior six selectors. The targeted
null regression, calendar H70 unit rerun, fresh replacement Sol review, and
the final full-solution gate remain pending. L5B, client adaptation,
deployment, and main merge are outside this packet. Historical
H48/H49/H51/H56/H63 evidence is retained and must not be conflated with the
H64 fresh result.

Later targeted runtime command (prepared, not run in this source-only slot):

```
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

Calendar correction H54 command (prepared, not run in this source-only slot):

```text
.\gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.config.SubjectTypeUserTypeTest --tests ru.rutcampustrack.academic.assignment.AssignmentAuthorityContractTest --tests ru.rutcampustrack.academic.assignment.AssignmentClosureContractTest --tests ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcContractTest --tests ru.rutcampustrack.academic.semester.SemesterAssignmentLockContractTest --tests ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityContractTest --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

Calendar correction evidence is in `evidence/calendar-correction-checks.json`.
