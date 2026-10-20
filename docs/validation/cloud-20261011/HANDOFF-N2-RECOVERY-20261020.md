# N2 and lifecycle correction handoff

Status: **COMBINED_INTERNAL_APPROVED; COMBINED_HOSTED_ACCEPTANCE_PENDING**. Designated external recheck remains PENDING. No merge/deploy.

Published current N2 source: [c2534aa2](https://github.com/DITEKmax/rutcampustrack/commit/c2534aa2e649094020a14881a9da07a84a5f82ec). The final typed403 and both late-response generation fences are published; current ordinary gates are running. A separate five-file lifecycle fixture/diagnostic correction is locally verified and independently internally approved for its five manifest hashes; integrated uncommitted. Baseline: [4c71f43d](https://github.com/DITEKmax/rutcampustrack/commit/4c71f43d3bb078c275656b2cba54b2d8b19610ed). Base/merge-base: [3908fad9](https://github.com/DITEKmax/rutcampustrack/commit/3908fad9624f97aec8891fc79338ceb6b0616bf3). Existing draft [PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), branch codex/server-validation-20261011. Exact final source/docs readback is recorded in the PR description and ignored checkpoint.

## User-visible correction

When reconnect joins an older refresh that rejects with a network error, the retained Student shell offers a working **«Перезагрузить»** action. It reads current admission with the existing bearer, without a second refresh POST anywhere in that admission chain. Read-only remains until current identity, permissions and context are confirmed.

Same-authority success and transient failures retain the owner/key and uncertain command/idempotency key. A real bearer401, typed409 SESSION_STATE_STALE, exact current-session gateway403 WRONG_ROLE or authority change follows the existing generation boundary and explicit re-entry/role selection. Old commands are neither replayed nor transferred into another identity. Bearer401 is not described as remote logout. Online events and successful Today data reads alone do not establish authority.

The correction also closes the queued-online and delayed background401 paths which could otherwise send another refresh after the uncertain failure. Normal reconnect, successful busy-bootstrap reconciliation and R2 settlement-before-online remain covered. Shared TMA behavior is preserved through the optional owner callback; no new auth scheme or UI redesign.

## Scope and custody

| File                                                                    | Change                                                                                       |
| ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| frontends/pwa-vue/src/App.vue                                           | Current-only recovery, generation-bound refresh policy, online queue behavior                |
| frontends/pwa-vue/src/auth.ts                                           | Optional refresh policy and current-session revalidation through existing authority handling |
| frontends/pwa-vue/src/auth-client.ts                                    | Exact current-session typed403 normalization; unrelated denials remain closed                |
| frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue     | Route retained offline Today retry to shell recovery; ordinary retry preserved               |
| frontends/pwa-vue/src/reconnect-recovery.test.ts                        | New deterministic production-flow regression and safeguards                                  |
| frontends/pwa-vue/src/bootstrap-policy.test.ts                          | Existing bootstrap fixtures follow the new options/queue behavior                            |
| frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts | Actual mounted-owner retry and uncertain-command preservation                                |
| tests/e2e/specs/mobile-real-server.spec.ts                              | Append active/suspended N2 browser scenarios; old D002 lines/assertions retained             |
| tests/e2e/playwright.real-mobile.config.ts                              | Include the two exact new titles in the existing selection                                   |
| .github/workflows/ci.yml                                                | Same selection in existing CLI grep; update only two required source-digest pins             |

No files deleted. The N2 source/test/config inventory above and the five-file lifecycle delta below form the combined package; workflow is shared and counted once. Exact final inventory is retained with the final source receipt. Task status/contract/matrix and this handoff are separate from executable inputs. Author manifest hashes were verified before and after byte-for-byte integration. The starting checkout was tracked-clean; all25 foreign untracked paths, worktrees, WIP, data and evidence remain preserved.

## Executed local proof

The same final test bytes run against immutable baseline production sources yield **7 FAIL / 4 PASS**; corrected production sources yield **11 PASS**. The baseline and final test digest is recorded in `final-baseline-custody.json`. Initial smaller RED/GREEN attempts and a corrected evidence-description version are retained rather than overwritten.

| Check                                       | Actual result and boundary                                                                                                                          |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| reconnect-recovery.test.ts                  | Final11 PASS,4915ms, peak393244KiB; real auth/StudentApi/createPwaFetcher and complete App functions with synthetic fetch/storage/reactive adapters |
| Exact baseline regression                   | Expected7 FAIL/4 PASS,5377ms, peak389956KiB; immutable4c production sources                                                                         |
| auth.test.ts                                | 46 PASS, including generation/logout/late-response behavior                                                                                         |
| bootstrap-policy.test.ts                    | 29 PASS                                                                                                                                             |
| Selected StudentFeatureOwner tests          | 6 PASS, peak448176KiB; real owner with mocked presentation/query boundaries; same uncertain check-in command/key, no automatic replay               |
| Focused auth/test TypeScript                | PASS; scoped config, not a full Vue typecheck                                                                                                       |
| real-mobile TypeScript                      | PASS                                                                                                                                                |
| Existing diagnostic/privacy source controls | 20 PASS; prior strict schema/reader behavior retained                                                                                               |
| Scoped precommit12 files                    | PASS, no autochanges; its pre-staging Gitleaks call alone did not establish new-source scanning                                                     |
| Actual committed-source Gitleaks            | PASS for the single baseline→source commit, redact enabled; cached scanner provenance retained; full hosted Security remains mandatory              |

Commands ran from the isolated frontend workspace with one CPU, group RSS≤512MiB, bounded output and fresh reserve checks:

```text
node <existing-node_modules>/vitest/vitest.mjs run pwa-vue/src/reconnect-recovery.test.ts --maxWorkers=1 --no-file-parallelism --no-cache
node <existing-node_modules>/vitest/vitest.mjs run pwa-vue/src/auth.test.ts --maxWorkers=1 --no-file-parallelism --no-cache
node <existing-node_modules>/vitest/vitest.mjs run pwa-vue/src/bootstrap-policy.test.ts --maxWorkers=1 --no-file-parallelism --no-cache
node <existing-node_modules>/vitest/vitest.mjs run mobile-core/src/shared/components/StudentFeatureOwner.test.ts -t "connection retry|uncertain check-in|lost ACK|real warm Vue Query|same owner reconnects|saved Today/HW" --maxWorkers=1 --no-file-parallelism --no-cache
node <existing-node_modules>/typescript/bin/tsc -p ../tests/e2e/tsconfig.real-mobile.json --noEmit
python3 -B tests/e2e/fixtures/http500-diagnostic-contract.py --source-root <assigned-worktree>
```

Exact absolute commands, environment limits, timestamps, source hashes and receipts are retained in ignored `current-wip/n2-recovery-20261020/author-proof`; original author evidence remains `/tmp/rct-n2-astra-proof-20261020`. No private logs, credentials, signed URLs or raw browser reports are published.

Combined Vitest and full local PWA typecheck exceeded the unchanged512MiB group cap and were stopped: **NOT_PASS**. Separate relevant tests passed. No repeated full launcher, browser sandbox bypass, Docker/DB runtime or rejected Mongo preparation. Initial lint found one test fixture declaration error, corrected before final source freeze; four unchanged baseline indentation warnings remain, so that initial lint is not claimed PASS.

## Hosted verification

All listed runs are ordinary attempt1 on source6585; none was canceled or rerun.

| Gate     | Run                                                                                | Result  |
| -------- | ---------------------------------------------------------------------------------- | ------- |
| Push CI  | [39412699625](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412699625) | FAILURE |
| PR CI    | [39412708921](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708921) | FAILURE |
| Coverage | [39412708929](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708929) | SUCCESS |
| Security | [39412708923](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708923) | SUCCESS |
| OpenAPI  | [39412708933](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708933) | SUCCESS |

Both push and PR Mobile Vue workspace typecheck and PWA/TMA builds have passed. Both active N2 cases passed; suspended cases failed at the new200/401-only assertion. The actual HTTP status was not retained and remains UNKNOWN. PR original D002 separately timed out after pre-online refresh rejection; its underlying network cause remains UNKNOWN. The two hosted cases use actual browser/owner/backend admission plus a controlled native-fetch rejection boundary. Their purpose is to verify active/suspended user outcomes, no extra refresh POST and no role mutation; they do not prove the historical network cause. SafeReporter serializes source locations rather than titles, so any case title derived from the source loop is labeled as derived.

## Review and remaining limits

Independent internal Astra review checked all nine source/test/workflow files and exact evidence custody: **no blocking findings**. Requested author/reviewer/check model gpt-6-astra/high; actual runtime metadata UNKNOWN. Root coordinated/integrated; no available API switched its runtime model. Designated external reviewer is separate and is contacted only by the main chat.

Known findings: previous F01–F09/N1 retain external FIXED; N2 author-local verification is complete, hosted and external acceptance pending. Historical v3 39/39,196/196 stays frozen and does not count as current N2 acceptance.

Historical network causality, SETUP Attendance PUT500, activity-card mismatch and specific gateway429 depletion remain UNKNOWN. N2 is independently source-confirmed; this correction does not identify those historical causes. B013/B014/B015, rejected Mongo preparation and sandbox restrictions remain frozen. Built-image scan SKIPPED is not PASS. No production/VPS/real secrets/data/provider/Telegram/manual/deploy or main merge.

## Reviewed test correction after source6585

The production contract can return409 with raw `extras.code=SESSION_STATE_STALE`: Auth reconciliation clears an unselectable active role and increments session_version, then compares the fresh authority with bearer claims. The existing App handler already invalidates the old generation and displays login. The new hosted test omitted this legitimate explicit re-entry outcome. This source-confirmed oracle defect does not establish the unrecorded status of the failed run.

Three-file correction: `mobile-real-server.spec.ts` accepts only that exact raw409 code with login/navigation0, retaining no extra POST refresh/no role mutation; unrelated409/403/503 remain failures. Existing numeric status diagnostics are attached on failure without payload. `reconnect-recovery.test.ts` adds the actual-App409-envelope case. `ci.yml` changes only two matching source-digest pins. Product bytes and original D002 are unchanged.

Correction checks:12 production-flow tests PASS (4787ms,398624KiB), seven extracted-oracle positive/negative controls PASS, hosted/focused TypeScript PASS,20 source-bound diagnostic controls PASS. A wrong-source diagnostic invocation first produced19PASS/1ERROR and is retained as NOT_PASS; the corrected invocation reads the actual assigned source. The previous exact11-case baseline RED/GREEN remains evidence for its original bytes; no claim of expanded12-case baseline identity. Internal independent review APPROVE on all three manifest hashes; hosted proof for this delta PENDING. Original author safe summary and review handoff are retained in the ignored evidence directory. No source6585 failures are relabeled green.

## Final typed403 product correction

Source46df pushCI39418122297 / PRCI39418134955 both observe suspended recovery GET `/api/auth/session`403; active N2 and original D002 pass in both. Push primary artifact12262968383, digest `c2ddccfc2ac7fcecfd7097eb665fab6f53ac5782ea5ef0fcc2fca03c7ccef3db`; PR primary12262603323, digest `10ee92a2a641fc26e51aa85b3ec0b03d7239e7a4bb784729eb0cb204b7f5a140`. Strict ZIP/digest/CRC/JSON checks passed. The raw body/code of these failed responses is unavailable; it is not asserted to have been observed.

The source-confirmed route is access bearer → Gateway internal admission → unselectable active grant → ROLE_NOT_SELECTABLE403 → top-level public WRONG_ROLE403 before forwarding the current-session query. The existing client read only nested extras, losing this typed denial. The final patch recognizes only current-session operation + HTTP403 + raw status403 + top-level WRONG_ROLE and derives a private marker. App uses that marker for existing explicit re-entry. Unknown/BFF403, wrong/missing/nested code and forged marker remain closed; no blanket403-to-logout mapping. Both current-session and refresh-candidate rejection paths assert the captured generation, preventing an old denial from resetting a newer authority.

Final source delta against46df is six files: App.vue, auth-client.ts, auth.ts, reconnect-recovery.test.ts, the new hosted N2 branch and two existing workflow digest pins. Old D002 prefix and all prior positive/negative gates remain unchanged. Independent internal review found one provisional late-candidate race; author reproduced and fixed it before publication, then reviewer approved the final six hashes. Actual external recheck remains PENDING.

| Final scoped proof                  | Result                                                                                              |
| ----------------------------------- | --------------------------------------------------------------------------------------------------- |
| Exact final21-case baseline on46df  | 1 FAIL /20 PASS; exact typed403 remained Student;7316ms,423708KiB                                   |
| Same final21 bytes on correction    | 21 PASS;13720ms,413224KiB                                                                           |
| Incremental late-candidate denial   | RED reproduced newer generation reset, fixed GREEN included above                                   |
| Existing auth-client/auth/bootstrap | 28 /46 /29 PASS                                                                                     |
| Extracted hosted oracle             | 13 PASS; typed403/409/401 obligations and strict wrong/missing/nested/status/forged/unknown refusal |
| Focused + hosted TypeScript         | PASS, peak413496KiB /407884KiB                                                                      |
| Existing diagnostic controls        | 20 PASS                                                                                             |

The first final-baseline sidecar run had2FAIL/19PASS because a copied auth-client and symlinked bootstrap-policy used different AuthRequestError classes. That extra failure is harness evidence, not a46df product regression. The corrected baseline copies immutable bootstrap-policy locally; exact source/module custody was independently checked. Both receipts remain. Full final commands/source hashes/15 attempts are in `403-finalsummary.safe.json`, SHA `fcd494f6a311de1882a5870057ca0441e1335bce87d6b566e1e7d836c28dfd48`, retained in ignored author-proof and original author directory. No raw private logs or bodies are published.

Currentc253 ordinary hosted results remain pending: pushCI39425035725, PRCI39425048537, Coverage39425048675, Security39425048592, OpenAPI39425048788. Security/OpenAPI and both full PWA/TMA typecheck/build passed at the recorded checkpoint. No source6585/46df failure is hidden or counted as final acceptance.

## Lifecycle correction and evidence limits

Owner added LC1/LC2 alongside N2. New assignments explicitly request `gpt-6.1-sol/ultra/forknone`; actual runtime model/effort UNKNOWN, global settings unchanged. Previous Astra work and evidence remain preserved; no new Astra task is active. Root integrates, `/root/n2_lifecycle_sol_author` owns the five-file correction, `/root/lifecycle_sol_review` is independent and read-only, and `/root/n2_sol_ci_observer` observes the original current runs.

Historical46df PR39418134955 failed at status-card count assertion2214 after ARCHIVE terminal completion, reasonNONE. Artifact12263724094 digest `5e28567a08cb4e71e5ab4df9d5540710064efa4e35dfe4a53b9681039423cd43` preserves terminal archive and separate successful cleanup restore, but not the count/DOM or the remaining main assertions. It is consistent with the controlled mechanism below; the historical trigger remains UNKNOWN and a connection to N2 is not established.

The deterministic control executes the actual fixture assertion block with controlled locator ordering: visibility is observed, then permitted completed UI settlement removes the transient card before the next await. The unchanged baseline rejects count0 instead of expected1. The correction removes only that transient count requirement and adds count1 for the stable exact own-semester card; final archived/inactive icon and correct enabled action remain mandatory. Existing narrow click timeout/disappearance handling remains, as do terminal operation identity, idempotent replay, downstream data checks, writer denials, main restore, inactive state and activation. Absence of a progress card alone is not PASS.

Barrier-only diagnostics now emit bounded `semester-effect.applicability.v1` NOT_APPLICABLE for closed known nonbarrier reasons after existing path/ownership/marker checks, before irrelevant seed/DB/process access. The three supported Schedule barrier reasons still execute the original strict reader/projection. UNKNOWN, malformed or unreadable input remains failure. Original lifecycle test failure is not changed.

Frozen author manifest SHA `831401a4a365e4b25052f40372281a6cb12ad9d6be1d20be301567f9c3f02ac0` covers exactly:

- Modified `.github/workflows/ci.yml`: run the small source-backed control; exact fixture digest update.
- Modified `tests/e2e/fixtures/semester-lifecycle.spec.ts`: transient/stable assertion correction.
- Modified `tests/e2e/fixtures/semester-effect-diagnostic.py`: explicit applicability receipt.
- Modified `tests/e2e/fixtures/semester-effect-diagnostic.control.py`: positive/negative applicability and owned-entrypoint controls.
- Created `tests/e2e/fixtures/semester-lifecycle-ui.control.mjs`: deterministic actual-source terminal/UI controls.

Local results: baseline RED count1/0; fixed terminal1accepted/15rejected and UI4accepted/8rejected. Diagnostic controls, existing source/privacy20, Python AST/YAML, changed-block strict types and diff whitespace pass. Whole strict fixture typecheck is NOT_PASS on baseline and correction with the same three diagnostics (two TS7006 and one TS2322); no unrelated source/compiler change. The first baseline harness parse failure and workflow insertion-sequence failure were corrected and preserved as failed attempts. This proof is not mounted production DOM or a full browser journey; the ordinary hosted lifecycle remains required.

Independent internal review APPROVE for exact5hashes is recorded in review SHA `567bcd3eae2590ba4c835574596f4a9f8d317c9e10246169dce623d37871fb20`; final combined-source gates and designated external recheck remain pending. Prior10 independent FIXED and frozen historical39/39,196/196 remain. LC1/LC2 are selected criteria, not extra external findings or percentage credit.

## Current c253 N2 trigger counterfailure

Push N2 active/suspended and old D002 PASS. PR old D002 and suspended PASS, but active N2 fails at the held-refresh poll6432 before offline/reconnect/current-only retry. Strict PR artifact12266941411 digest `611e60383d5bbc235314adf1009553822f9a5a744bb7febcbeaa6cac486e9107` retains that location; actual held value and HTTP are UNKNOWN. Push remaining5 artifact12267115918 separately proves all10PASS. Different outcomes do not establish infrastructure cause or a common defect.

Real useToday explicitly refetches on returning to Today; fresh cache alone is not the demonstrated explanation. Source allows an unfinished initial query with no data to retain its existing promise instead of dispatching a new GET through the later-installed native-fetch gate. The test login waits for navigation, not initial Today settlement. `/root/n2_sol_trigger_author` is checking this controlled order and the minimal settled-query precondition; no new product change is assumed. Historical causality remains UNKNOWN until sufficient evidence.

## Controlled N2 preparation mechanism

A bounded deterministic proof mounts actual useToday with real usePwaAuth, generation-bound StudentApi and createPwaFetcher. A deferred initial GET permits navigation away and back while refetch joins the pre-gate request: one Today GET, no refresh, heldfalse. Settling the initial request before navigation produces a new gated GET: two Today GETs, one refresh, heldtrue. The original held oracle is RED in the pending schedule; settled variants with staleTime0 andInfinity pass. This proves a permitted fixture-preparation failure mechanism, not the unrecorded exact historical schedule.

The proposed correction adds only a positive actualTodayScreen heading precondition before leaving Today. It cannot pass on an unmounted/pending/error-without-data view; accepted empty/active/ended views are checked. The original timeout, transport gate, held requirement, offline/online/rejection order, current-only retry, active/suspended oracle, no-extra-refresh/no-role-PUT and cleanup remain unchanged. Source and exact workflow pins have independent internal APPROVE for the exact six combined executable hashes, review SHA `5c41dabf981256eb423819327d6c8d14b22e9cdf14a79bf59cccc944704202ce`. Independent8controls and HTTP500/privacy20 pass. Reviewer initially missed its temporary node_modules link; that failed setup ran0tests and is preserved separately. No source correction or oracle change was needed.

The integrated package against c253 contains six executable files (five LC inventory entries plus N2 spec, workflow shared once) and five task documents. Both c253 full main lifecycle cases pass: push artifact12267208205 and PR artifact12266874137. The new fixture bytes still require new-source ordinary gates. Current observed failed PR N2 prerequisite remains in history; current runs are neither canceled nor rerun.
