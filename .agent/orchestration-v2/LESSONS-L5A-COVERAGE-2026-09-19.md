# L5A remaining verification scope — 2026-09-19

## Goal
S3 complete the remaining source-stage L5A verification requirements before fresh full review. This is not L5B, client integration, or whole-product readiness.

## Context/evidence
Canonical LESSONS-L5.md v3 and LESSONS-L5A-RESUME-2026-09-19.md remain authority. Root reread both. H54 six unit classes passed15 tests. H55 reached real PostgreSQL and returned201 for subject creation; remaining failures were incorrect test field assignmentIds versus actual SubjectResponse.createdAssignmentIds. H56 verifies the bounded test correction first. Lead read-only coverage audit found legacy SubjectServiceIT/AcademicGrpcIT still using TSG and missing real negative scenarios. Mapping corrections and dependency-scope amendments are documented in SLOTS and mapping-proposal.md; preserve these and failed-run evidence.

## Relevant scope
Existing sole Luna writer in C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-assignment-authority at base b8220ac92125a8afa37598b270aa4fab7aa1f470 plus assigned WIP. After H56 result/resource release, tests-only completion in four new IT classes, AbstractAssignmentAuthorityIT, existing subject/SubjectServiceIT and integration/AcademicGrpcIT, own evidence. No writes during active checks. Production changes require a separately confirmed defect and root decision. Attendance consumer tests are a separate bounded follow-up after current read-only scout, not an automatic production expansion.

## Required behavior
Adapt only obsolete legacy assignment fixtures/assertions: explicit immutable assignments/canonical types/current valid semester/active teacher grants replace TSG authority; nonempty legacyteacherIds now must reject atomically; closure routes must return typed409; preserve unrelated valid existing assertions.
Add actual route denial checks for foreign group and non-headman without writes. Historical GetAssignmentsByIds must reject nonpositive IDs and mixed missing IDs atomically; empty batch may return empty, do not invent rejection. Verify fully unreferenced subject deletion, referenced subject rejection and Schedule dependency failure fail-closed using existing external Schedule mock boundary only. Test completed-semester name-only denial and representative invalid assignment date bounds through real endpoints. Verify deterministic assignment summaries for same-teacher/multiple-types and exact IDs. Existing headman producer/teacher/gRPC/race/typed closure tests remain intact; full snapshots must prove no unauthorized writes.

## Constraints
Read absolute RULES.md in orchestration-v2 SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A, CURRENT and this packet. Existing Luna max sole writer, no children/Terra, not alone preserve others. No new coordinator or parallel writer. No source/schema/migration/proto/dependency changes under this test-only packet. Do not weaken retention, auth, compiler checks, dates, or expected statuses just to pass. No E/build/PWA edits, push/deploy/main merge or global configuration.

## Existing patterns
Use current AbstractAcademicIntegrationTest PostgreSQL/Flyway/MockMvc/in-process gRPC and existing tests; real authority/repository/transaction paths. External Schedule availability may be mocked to test fail-closed deletion. Process-local TESTCONTAINERS_REUSE_ENABLE=false, exact elevated context, owned-container cleanup are proven run setup; no shared database resets.

## Acceptance criteria
Existing relevant tests reflect accepted v3 rather than legacy TSG, new negative scenarios are exercised, exact current-source XML counts are nonzero with no failures/errors/skips. Full scope inventory identifies implemented/verified/open requirements. L5B, clients, integrated headman runtime and downstream release remain explicitly open. Full independent Sol review receives all original WIP plus current corrections, not only latest diff.

## Verification
First return exact source scope and selector plan after authoring freeze. No Gradle/Docker under this packet without a numbered root heavy lease. Proposed Academic integration selectors are the four new IT classes plus ru.rutcampustrack.academic.subject.SubjectServiceIT and ru.rutcampustrack.academic.integration.AcademicGrpcIT. Preserve raw commands/context/exits/XML and verify cleanup. Do not rerun passed unrelated suites without new changes or identified regression risk.

## Do not
No broad test rewrite, TSG dual writes, ADMIN authority expansion, activated closure, missing-ID partial success, force-delete history, invented client requirements, or premature L5A DONE.

## Downstream consumer test amendment
Root read ReportService.authorizeHeadmanOrTeacher454–479 and current ReportServiceTest336–353 after read-only scout release. Same sole writer may extend only services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportServiceTest.java after H56 passes/releases: current authoritative teacher row allows requested subject/group and exact teacher/active semester RPC arguments; empty response denies; mismatched subject/group denies. Existing mocked Academic client is the consumer boundary, not real cross-service proof. Future/expired exclusion remains Academic producer responsibility, exercised in its real tests. Do not add a second date authorization policy or Attendance production edits. Targeted ReportServiceTest unit command requires later heavy lease; no ReportIT rerun required absent new risk.
