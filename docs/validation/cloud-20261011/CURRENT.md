# CURRENT — PR2 N1/F08 correction

Persisted Goal Q031/D037: **READY_FOR_INDEPENDENT_RECHECK**. Author verification2/2 complete; external new-correction recheck PENDING. Final delivery guard: exact documentation-only commit, source-byte identity and local/remote/PR readback before the final response. No external reviewer contact, merge or deploy. [Contract](TASK-FIX-F01-F09-20261019.md), [handoff](HANDOFF-F01-F09-20261019.md), [ten findings](fix-findings-F01-F09-20261019.csv).

Starting **26d6e74e9d187105c8778c3e5d469f8f59879a1f**

Published/executable **580b1a69ee435dc5c1fd298e93799afe8a466715**

Branch codex/server-validation-20261011; main/base3908fad9; PR2 open/draft/unmerged. Prior F9 lineage7863→b3 and docs26 remains historical. Historical matrix v3 **39/39,196/196 FROZEN**, bytes/weights unchanged. External previous snapshot: F01–F07/F09 FIXED; F08 NOT_FIXED plus new N1 P2 blocker. The two current corrections are author-verified, not externally APPROVED; REQUEST_CHANGES remains until designated recheck.

| ID | Author result | External acceptance / applicability |
| --- | --- | --- |
| F01 | PRESERVED | FIXED previous26/b3: persisted empty fences valid, malformed IDs strict; new N1 retains it |
| F02 | PRESERVED | FIXED previous snapshot: durable exact RELEASED tuple, race/replay and no stale fences; current Schedule8 methods PASS |
| F03 | PRESERVED | FIXED: authoritative eligibility/pending fence/profile locks; unchanged source and prior8IT proof |
| F04 | PRESERVED | FIXED: FULL trusted ADMIN reset/public denial/expiry/revoke; unchanged source and prior13IT proof |
| F05 | PRESERVED | FIXED: production principals/minrights/fresh restore; unchanged source and sufficient prior proof |
| F06 | PRESERVED | FIXED:68mandatory env/validator/Compose references; unchanged source |
| F07 | PRESERVED | FIXED: production source/downstream gRPC labels; unchanged source |
| F08 | AUTHOR_VERIFIED on580 | Previous NOT_FIXED; current correction PENDING external recheck. Deterministic RED4FAIL/1PASS→144PASS; fresh fullbot push/PR/Coverage steps SUCCESS |
| F09 | PRESERVED | FIXED: domain moved flag/frozen wire/four consumers; unchanged source |
| N1 | AUTHOR_VERIFIED on580 | New correction PENDING external recheck. Actual PG push+PR223PASS each, all8participant SOURCE_METHOD/currentSHA including both extended methods |

**Source gates:** pushCI39341753845, PRCI39341763221, Coverage39341763320, Security39341763225, OpenAPI39341763289 all SUCCESS/attempt1/exact580. Push/PR distinct; actual PR checkout carrier tree equals source580. Built-image scan SKIPPED, not PASS. Previous26CI naturally SUCCESS; no canceled/rerun jobs.

**N1:** guard only zero physical lessons before event publication. Actual empty COMMIT/RELEASE/replay produces no invalid outbox/effect and real archive READY/fence. Nonempty exact event/PENDING→actual consumer ACK twice→durable receipt/APPLIED→READY preserved. Artifacts12229222823(push)/12229756764(PR), checkout12229961309; exact digest/size/source/test SHA, all8SOURCE_METHOD PASS. Baseline runtime RED NOT_RUN; fixed real PG PASS. Schema minItems1, Attendance validation, ledger and barrier unchanged.

**F08:** one injected client serializes per-chat authority/invalidate/send/receipt across /start and startup. Different chats independent, weak lock registry/cancellation safe, bounded retries unchanged. Local Python3.12.14/aiohttp3.14.4 fixed144PASS, CPU1/RSS371828KiB; exact race, reverse order, errors/cancellation/isolation. Fresh jobs118364723416/118364754804 pytest+Ruff+format SUCCESS; CoveragePython118364754067 pytest SUCCESS. No CI case-count or skip-absence claim from job metadata.

**Review/checks:** independent internal source APPROVE_SCOPED, format-only APPROVE and actual push/PR/bot evidence APPROVE_EVIDENCE, findings0. GJF1.25.2/AOSP and Ruff checks PASS; final tests whitespace/token/AST-equivalent to reviewed r1, product bytes unchanged. Final source/evidence/handoff review and scoped doc checks are delivery guards, recorded in ignored receipts. Prior eight FIXED outcomes preserved in this narrow delta.

**Ownership/resources:** Root sole Git/taskdocs/shared integrator; authors n1_empty_event(/tmp/rct-n1-20261020) and f08_publication_race(/tmp/rct-f08-20261020), detached base26, frozen r2 inputs integrated exactly. New helper route Sol6.1/high/forknone; actual model/effort metadata UNKNOWN. Existing WIP/worktrees/data preserved; foreign25 NUL hash e46eaa7a unchanged. Cleanup0.16GiB/4CPU; no local Docker/DB/pulls/Gradle/full native compile under reserve/NO_FIT_PROOF. Actual PG via ordinary isolated CI.

**Handoff:** source delta5product/test files + taskdocs; final documentation HEAD separately identified by PR readback/final response. Q009/D009 applies only if every other tracked input is byte-identical to tested580; final docs CI/Coverage must not be relabeled source PASS. Ignored evidence: .agent/evidence/cloud-20261011/current-wip/n1-f08-20261020/; combined-source-r2.safe.json and current-source-gates.safe.json. Raw private reports/bodies/logs and real credentials/PII not read or published.

**Limits/next:** B013/B014/B015 FROZEN. Q034/D039 and historical2cfD002 underlying causes UNKNOWN. Production/VPS/deploy/real migration/grants/secrets/rotation/backup/real Telegram/manual and all previous deferred scopes excluded. No missing owner decision. After final delivery readback, stop READY_FOR_INDEPENDENT_RECHECK; main chat requests designated external review separately. Never merge/deploy here.
