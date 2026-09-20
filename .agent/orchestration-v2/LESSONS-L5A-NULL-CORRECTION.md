# L5A full-review correction — 2026-09-19
## Goal
S2 correct confirmed null-element HTTP validation defect and H64 XML index labels; preserve L5A scope.
## Context/evidence
Fresh full46 Sol FAIL: CreateSubjectRequest initialAssignments List<@Valid ...> accepts null; SubjectService createSubject maps semesterId and throws NPE. Root read both originals. H64 actual47PASS remains valid; two XML digest labels swapped, not source hashes.
## Relevant scope
Same assigned Luna author sole writer v2-assignment-authority: CreateSubjectRequest.java, focused SubjectAssignmentAuthorityIT regression, own evidence/h64-checks.json and fresh review packet/manifest. Preserve all46 original source paths, no unrelated edits.
## Required behavior
List<@NotNull @Valid InitialAssignmentRequest>; real authorized HTTP initialAssignments:[null] returns400 and subject/lesson-type/assignment row snapshots unchanged. Correct only two H64 XML index labels against actual files; immutable raw logs/XML untouched.
## Constraints
RULES C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Read CURRENT and canonical LESSONS-L5.md v3. Luna max, no children, Terra NEVER, not alone preserve others. No Docker/Gradle until separate root lease, no integration/build/deploy/push.
## Existing patterns
Existing MockMvc authorized headman/real PostgreSQL and zero-write snapshots; Jakarta container element constraints.
## Acceptance criteria
Invalid element400 with no persisted changes; prior valid cases preserved; exact H64 XML label correction. Source frozen and author RELEASE before replacement fresh full-task Sol high.
## Verification
Author prepare source/check command first. Root assigns single heavy queue targeted SubjectAssignmentAuthorityIT. Save command/revision/actual exit/full raw/current XML and exact owned cleanup. Previous unaffected gates remain valid. Full46 replacement review required, not patch-only.
## Do not
Do not weaken domain rules, activate closure, change product lifecycle, modify H64 XML/raw, or claim full product ready.

## Calendar fixture correction after full recheck
Root personally read confirmed MED: teacherSubjectsReturnsOnlyCurrentEffectiveAssignmentsWithConcreteEnd uses future2026-09-20 against actual Moscow current date; referencedSemesterCannotChangeDatesAfterRowLock uses completed2026-09-30 boundary against current service date. Same author sole writer TESTS ONLY AcademicAssignmentGrpcContractTest and SemesterAssignmentLockContractTest plus own evidence/manifest. Build fixture dates around appropriate actual service clock with generous margins; retain exact identity/effective-end/current-vs-future-vs-expired and referenced-date/lock assertions. Historical-ID static-date test is not time-dependent and need not change. No production Clock/API/dependency changes. Prepare sourcefreeze, then root exclusive H70 six H54 unit selectors; preserve previous PASS dated evidence and explain fixture horizon. Full task replacement Sol after checks/release remains required; no extra author/child or scope growth.
