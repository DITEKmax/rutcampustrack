# H74 test-only regression independent review
## Goal
S2 assess the new actual REST/PostgreSQL concurrent assignment regression for safe retention. No product defect/fix acceptance.
## Context/evidence
Read RULES C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A; CURRENT latest update; LESSONS-L5A-LOCK-ORDER-FIX.md H74 addendum. H72 failed mocking abstract callRealMethod, not deadlock. H74 corrected delegate test PASS1/0/exit0 actual unchanged f59; SQL FOR NO KEY UPDATE disproves earlier static FOR UPDATE FK-cycle inference. No product fix authorized. H73 cancelled.
## Relevant scope
Read-only worktree C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-assignment-authority. Base f59b3b9951c971bde42265ae67df8754dc594a58; only tracked test diff services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java SHA B3F4A942F3EAFBF27FB56B7E95EA5B9A5EF0D8CE6EA8B25406F235D007B2E10E. Product SubjectService377BF9B1E2F6E2B88529DD362177F0C47318D7A6C31561C4E516FA89491AFFA3 unchanged. Open related fixtures/repos/transaction callers as needed. Own evidence .agent/v2-assignment-authority; root evidence h74-context/stdout/stderr/AssignmentAuthorityIT.xml/owned-cleanup.json.
## Required behavior
Review complete added test diff, actual repository proxy delegation, transaction lifetime, latch interleaving, request failures, cleanup/executor termination, cross-test stubbing/reset behavior, row/assertion meaning. Confirm it tests two real callers/PG writes and does not impose fake locks or falsely claim negative deadlock reproduction. Check actual XML/source match and distinguish old incorrect static claim from current actual evidence. Report concrete findings only.
## Constraints
Fresh Sol high/forknone read-only, no children/Terra/product/docs/evidence writes/runtime/Gradle/Docker. Preserve other agents' scopes. Max3 project leaves/root heavy queue. No push/deploy/merge.
## Existing patterns
Existing class real MockMvc routes, PostgreSQL fixtures and MockitoSpyBean Spring Test JDK proxy delegate. Public MockCreationSettings.defaultAnswer preserves original repository query. New helper surfaces completed HTTP errors before latch timeout.
## Acceptance criteria
PASS or actionable finding severity/file:line/evidence/repro/impact/correction for full test-only scope. If broader class execution needed to validate changed spy wiring/shared fixture, name exact bounded selector and why. Do not request whole product retest without concrete risk.
## Verification
Review only; use root H74 actual exit/XML/cleanup. Runtime execution by root only after review. No claim other tests rerun on current test annotation changes.
## Do not
No production lock-order rewrite, artificial forced negative test, old static inference treated as fact, retry/mocking expectation tricks or broad code cleanup. Reviewer's role ends with verdict/RELEASE, never implement corrections.

## Fresh independent test review FAIL1 MEDIUM / bounded correction
Reviewer h74_assignment_test_review RELEASED. At AssignmentAuthorityIT253–260 finally calls shutdown + one20s await/assert only; unfinished requests survive a timeout, can retain DB transactions/locks and contaminate next tests; cleanup assertion masks primary error. Correct test only: release latch, cancel unfinished futures, interrupt executor via shutdownNow when necessary, bounded join and preserve primary exception with cleanup failure added/suppressed. Do not claim interruption guarantees killing arbitrary JDBC; use applicable bounded query/transaction safeguards if needed and explain evidence. No production changes or artificial negative locks. Same author may update own correction evidence. Root reserves H75 whole AssignmentAuthorityIT (7 tests, includes changed method) once after freeze; this validates class-wide spies/reset as requested by reviewer, without redundant immediately repeated method run. Fresh FULL test-only review after freeze/checks. Preserve H74 actual result/withdrawal. Original H73 remains cancelled.
