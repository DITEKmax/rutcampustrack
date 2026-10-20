# CURRENT — PR2 fresh CI causal diagnosis

Persisted Goal Q031/D037: **IN_PROGRESS — FRESH_CI_BLOCKED**. Owner reports designated independent review APPROVE on source580/docsf4; all ten F01–F09/N1 FIXED. Fresh docs CI failure is a separate unresolved blocker, not waived by D009. [Active causal scope](TASK-CI-CAUSALITY-20261020.md). No external reviewer contact, merge or deploy. [Contract](TASK-FIX-F01-F09-20261019.md), [handoff](HANDOFF-F01-F09-20261019.md), [ten findings](fix-findings-F01-F09-20261019.csv).

The two full-reference headers below identify the accepted pre-diagnostic baseline. The new diagnostic delta is the current checkpoint commit; its exact published revision and live gate receipts are recorded in PR readback and ignored latest-checkpoint.

Starting **26d6e74e9d187105c8778c3e5d469f8f59879a1f**

Published/executable **580b1a69ee435dc5c1fd298e93799afe8a466715**

Branch codex/server-validation-20261011; main/base3908fad9; PR2 open/draft/unmerged. Prior F9 lineage7863→b3 and docs26 remains historical. Historical matrix v3 **39/39,196/196 FROZEN**, bytes/weights unchanged. Designated external SOURCE_ONLY recheck APPROVE on source580/docsf4: all ten findings FIXED. That accepted code snapshot remains historical; any new executable delta needs its own review applicability. Merge readiness remains BLOCKED by CI39347026363.

| ID | Author result | External acceptance / applicability |
| --- | --- | --- |
| F01 | PRESERVED | FIXED previous26/b3: persisted empty fences valid, malformed IDs strict; new N1 retains it |
| F02 | PRESERVED | FIXED previous snapshot: durable exact RELEASED tuple, race/replay and no stale fences; current Schedule8 methods PASS |
| F03 | PRESERVED | FIXED: authoritative eligibility/pending fence/profile locks; unchanged source and prior8IT proof |
| F04 | PRESERVED | FIXED: FULL trusted ADMIN reset/public denial/expiry/revoke; unchanged source and prior13IT proof |
| F05 | PRESERVED | FIXED: production principals/minrights/fresh restore; unchanged source and sufficient prior proof |
| F06 | PRESERVED | FIXED:68mandatory env/validator/Compose references; unchanged source |
| F07 | PRESERVED | FIXED: production source/downstream gRPC labels; unchanged source |
| F08 | AUTHOR_VERIFIED on580 | FIXED by designated external recheck on580/f4. Deterministic RED4FAIL/1PASS→144PASS; fresh fullbot push/PR/Coverage steps SUCCESS |
| F09 | PRESERVED | FIXED: domain moved flag/frozen wire/four consumers; unchanged source |
| N1 | AUTHOR_VERIFIED on580 | FIXED by designated external recheck on580/f4. Actual PG push+PR223PASS each, all8participant SOURCE_METHOD/currentSHA including both extended methods |

**Fresh docs gates:** CI39347026363 FAILURE/attempt1; Coverage39347026287, Security39347026278, OpenAPI39347026623 SUCCESS. Client118381983773 step23 D002 response deadline; lifecycle118381983942 step65 SETUP Attendance PUT500; step67 diagnostic failure and aggregate are consequences. No rerun/cancel. D002 safe row4221 proves pre-online native-call ambiguity; no HTTP status observed. MARK diagnostic refuses current source/signature and only prints its result, leaving backend cause UNKNOWN. Correction integrated:6harness/workflow files, no product code. Independent internal APPROVE_SCOPED_ONLY/findings0; external review of new delta PENDING. Next: one normal source publication/gate acceptance with new observables, not a rerun of unchanged source.

**Source gates:** pushCI39341753845, PRCI39341763221, Coverage39341763320, Security39341763225, OpenAPI39341763289 all SUCCESS/attempt1/exact580. Push/PR distinct; actual PR checkout carrier tree equals source580. Built-image scan SKIPPED, not PASS. Previous26CI naturally SUCCESS; no canceled/rerun jobs.

**N1:** guard only zero physical lessons before event publication. Actual empty COMMIT/RELEASE/replay produces no invalid outbox/effect and real archive READY/fence. Nonempty exact event/PENDING→actual consumer ACK twice→durable receipt/APPLIED→READY preserved. Artifacts12229222823(push)/12229756764(PR), checkout12229961309; exact digest/size/source/test SHA, all8SOURCE_METHOD PASS. Baseline runtime RED NOT_RUN; fixed real PG PASS. Schema minItems1, Attendance validation, ledger and barrier unchanged.

**F08:** one injected client serializes per-chat authority/invalidate/send/receipt across /start and startup. Different chats independent, weak lock registry/cancellation safe, bounded retries unchanged. Local Python3.12.14/aiohttp3.14.4 fixed144PASS, CPU1/RSS371828KiB; exact race, reverse order, errors/cancellation/isolation. Fresh jobs118364723416/118364754804 pytest+Ruff+format SUCCESS; CoveragePython118364754067 pytest SUCCESS. No CI case-count or skip-absence claim from job metadata.

**Diagnostic delta checks:** D002 actual reporter/callback RED loses both states → targeted GREEN retains/distinguishes pre/post phases; strict reporter typecheck/syntax and proper Prettier PASS, final formatting AST-identical. MARK baseline rejects current source/signature → coherent14controls PASS; ordinary4096B closed artifact/first-request/privacy/source/negative controls. Full local privacy launcher RESOURCE_BOUND at550121472B above512MiB group cap; not PASS, no limit increase, unchanged full gate deferred to normal CI. Existing10finding product behavior unchanged.

**Review/checks:** independent internal source APPROVE_SCOPED, format-only APPROVE and actual push/PR/bot evidence APPROVE_EVIDENCE, findings0. GJF1.25.2/AOSP and Ruff checks PASS; final tests whitespace/token/AST-equivalent to reviewed r1, product bytes unchanged. Final source/evidence/handoff review and scoped doc checks are delivery guards, recorded in ignored receipts. Prior eight FIXED outcomes preserved in this narrow delta.

**Ownership/resources:** Root sole Git/taskdocs/shared integrator; authors n1_empty_event(/tmp/rct-n1-20261020) and f08_publication_race(/tmp/rct-f08-20261020), detached base26, frozen r2 inputs integrated exactly. New helper route Sol6.1/high/forknone; actual model/effort metadata UNKNOWN. Existing WIP/worktrees/data preserved; foreign25 NUL hash e46eaa7a unchanged. Cleanup0.16GiB/4CPU; no local Docker/DB/pulls/Gradle/full native compile under reserve/NO_FIT_PROOF. Actual PG via ordinary isolated CI.

**Handoff:** source delta5product/test files + taskdocs; final documentation HEAD separately identified by PR readback/final response. Q009/D009 applies only if every other tracked input is byte-identical to tested580; final docs CI/Coverage must not be relabeled source PASS. Ignored evidence: .agent/evidence/cloud-20261011/current-wip/n1-f08-20261020/; combined-source-r2.safe.json and current-source-gates.safe.json. Raw private reports/bodies/logs and real credentials/PII not read or published.

**Limits/next:** B013/B014/B015 FROZEN. Q034/D039 and historical2cfD002 underlying causes UNKNOWN. Production/VPS/deploy/real migration/grants/secrets/rotation/backup/real Telegram/manual and all previous deferred scopes excluded. No missing owner decision. Current work is causal diagnosis of the two fresh failures, not reopening the accepted ten fixes. Source580→f4 only5taskdocs; identical executable inputs do not establish an infrastructure cause. Two assigned authors implemented diagnostic-only scopes in isolated detachedf4 worktrees; one RO helper compared actual runner inputs, and independent reviewer accepted the frozen6file delta as APPROVE_SCOPED_ONLY. Root owns Git/taskdocs/integration. Source580/f4 external APPROVE does not automatically cover new diagnostics. Read-only Cloud resource preflight: workspace about4.12GB, below local Docker4GiB floor; no heavy launches or cleanup. Main chat requests any new designated review separately. Never merge/deploy here.
