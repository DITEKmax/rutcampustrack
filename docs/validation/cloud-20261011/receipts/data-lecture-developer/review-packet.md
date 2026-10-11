# Data/lecture review packet — stable c115f61996fd415d20286a0787f4985b80a59a24

Writer: data_lecture_developer; requested Sol6.1/high, actual UNKNOWN. Independent WT /workspace/rct-worktrees/data-lecture, branch codex/rct-validation-data-lecture-20261011, frozen e093eb988785ed48f26c4f3a561f168ed4d96401, global baseline3908fad9624f97aec8891fc79338ceb6b0616bf3. Old delivery0e717 unchanged. Root alone owns shared docs/integration, no push/children by this author.

## Confirmed P2: T001 publication consistency

Accepted source: docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md requires current ACTIVE assignment. Root decisions.md T001 resolves implementation publication consistency without introducing a new owner rule. Existing AssignmentRepository#findPublishedActiveBySubjectIdAndGroupId excludes ADOPT_UNASSIGNED target while operation PREPARED/APPLIED; SubjectAssembler uses it for ordinary response. Actual initial-1 signed HTTP request: own headman received empty ordinary assignments/teacherIds and exact pending target; teacher list nevertheless returned the subject before dependency commit succeeded. New test failed on absence assertion, rather than infrastructure.

Only modified product symbol: SubjectRepository#findAssignedToTeacher (lines32–39), adds ACTIVE and correlated NOT EXISTS with the same uncompleted-adoption discriminator/states. Existing teacher/semester/date/valid-until, outer page/order/group semantics unchanged. Export author separately adds findLessonTypeCodes in another frozen worktree; root must preserve both non-overlapping hunks. No API/proto/schema/config/migration changes.

Initial-1 old query is relevant red sensitivity, session17719 exit1; exact command, source hashes, log, academic XML copied under .agent/evidence/data-lecture-validation/initial-1. Green-1 session22176 terminalexit0/1m38s, selected PASS1: checks same signed HTTP path, preserved own pending handle, teacher subject absent before commit, exact target resume, then teacher subject present and durable single assignment/COMMITTED operation. Schedule participant is a controlled seam, not a live Schedule database or full identity service.

## Distinct meaningful tests

Created SubjectListDataIT.java (191 lines). Page method actual PASS initial-1: 51 own rows plus foreign-group control, legal page sizes1/10/50, 59 HTTP requests; exact IDs/order/no duplicates/page totals/group/types/published assignment fields. Hibernate prepareStatementCount first pages4/22/102 and latency recorded as diagnostics. These counts exclude independent JDBC/admission; no accepted latency budget, no performance defect or optimization. This method has no dependence on the changed teacher-only predicate and is not repeated merely for a PASS count.

Created ScheduleGrpcBatchTransferIT.java (120 lines). Actual production client over owned real loopback Netty RPC; Schedule persistence is a controlled seam. Normal batch has exact two scoped lessons. Handler starts pending transfer after compact snapshot, before per-item validation; typed whole-batch failure occurs, then recovery returns exact IDs/group/semester, and completed transfer/foreign scope mutable admission rejects with ConflictException. Initial-1 PASS measured1batch+2per-item RPCs; green-1 restored method PASS. No Mongo durable-no-write/full service-token/TLS or distributed transfer-atomicity claim.

L06 sensitivity control-1: temporary TEST-ONLY counterfeit replaces only the pending fault assertion subject with the same actual loopback blocking batch RPC, deliberately omitting per-item checks. Main production client unchanged. Actual session2126 exit1/40s: AssertionError "Expecting code to raise a throwable" at the same negative oracle. This is a relevant behavioral violation, not syntax/type/infra failure. Full counterfeit test source and SHA preserved in l06-counterfeit-test.java.txt and command manifest. Final test restored byte-for-byte from l06-normal-test.java.txt; green-1 then reruns normal production client method. Counterfeit source is not committed.

## Reused actual L07/L08 checks

Existing-1 session41957 exit0/2m10s, frozen product unchanged. Selected four meaningful cases PASS/no skips. Nine automatically included architecture assertions PASS separately, not acceptance credit.

L07 SemesterArchiveStateIT#archiveAndActivation_areSerializedAndKeepAtMostOneActiveSemester: real disposable PG + production service commands concurrent via bounded barrier/futures; exactly activation target active, source inactive ARCHIVING/write-blocked and exact source archive event. This verifies the selected durable concurrency invariant, not HTTP optimistic-conflict mapping or all restore interleavings.

L08 JdbcSessionAuthorityIT: real owned PG loads current original Academic migration chain. Held grant-writer lock returns wholly-new versions/status/active-role tuple. Failures after password event and after session revoke return AUTHORITY_UNAVAILABLE and roll back credential flags/hash, all session revokes and password event; both sessions remain live. These are existing controlled local transaction failures, not full password HTTP/client acceptance (separate security package). Schema ownership in schema-ownership.md: Academic sole production Flyway; Auth JDBC consumer/writer shares Academic database, Auth production Flyway disabled. No DB split/Saga/2PC proposed.

## Exact evidence and safety

run-manifest.json contains actual argv/cwd/runtime HEAD/source SHA256/launcher SHA/session/exit and owned PID chains for each invocation; proposed-run.json remains explicitly PROPOSED_NOT_EXECUTED and differs from actual selected runs. Per-run command.json captured before launch. Logs and copied XML under WT .agent/evidence/data-lecture-validation/{initial-1,existing-1,control-1,green-1}. Safe result summaries in scratch; safe JWT marker scan0 so far. MockMvcPrint.NONE prevents request-token/body printing; synthetic IDs/data only, no environment/credentials dump.

Initial and existing launcher/daemon/worker chains terminal; their recorded exact PostgreSQL/Ryuk IDs absent via Docker inspect, no foreign resources touched. Loopback server/channel bounded shutdown asserted in test AfterEach. Final DATA3 RELEASED. Stable clean commit c115f61996fd415d20286a0787f4985b80a59a24; final ownPG/Ryuk IDs absent and PIDs gone. Green-1 selected restored productionclient method PASS1, no skips/errors.

Review requested: T001 query matching publication protocol, meaningful red/green and L06 sensitivity, fixture cleanup, exact evidence qualifiers, preservation of export-author non-overlapping addition at root integration. Root supplies a fresh independent reviewer; this author retains corrections.

## Initial page-test provenance qualification
Initial1b058 snapshot/exact old qualification comment not preserved. Author description of comment-only change cannot independently byte-prove source equivalence to committed33a928. Historical pagePASS remains observed at recorded oldhash; exact committed-source page reuse equivalence UNKNOWN. Green-1 teacher/Netty sourcehashes match commit. See subject-test-provenance.json/.md; no runtime/product changes.
