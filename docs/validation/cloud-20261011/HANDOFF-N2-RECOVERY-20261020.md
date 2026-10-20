# N2 correction handoff

Status: **SOURCE_READY; HOSTED_ACCEPTANCE_PENDING**. Designated external recheck remains PENDING. No merge/deploy.

Source: [6585ca49](https://github.com/DITEKmax/rutcampustrack/commit/6585ca49f4ef4890797b77ed7e252865b57b4f3c). Baseline: [4c71f43d](https://github.com/DITEKmax/rutcampustrack/commit/4c71f43d3bb078c275656b2cba54b2d8b19610ed). Base/merge-base: [3908fad9](https://github.com/DITEKmax/rutcampustrack/commit/3908fad9624f97aec8891fc79338ceb6b0616bf3). Existing draft [PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), branch codex/server-validation-20261011. Exact final source/docs readback is recorded in the PR description and ignored checkpoint.

## User-visible correction

When reconnect joins an older refresh that rejects with a network error, the retained Student shell offers a working **«Перезагрузить»** action. It reads current admission with the existing bearer, without a second refresh POST anywhere in that admission chain. Read-only remains until current identity, permissions and context are confirmed.

Same-authority success and transient failures retain the owner/key and uncertain command/idempotency key. A real bearer401, typed409 SESSION_STATE_STALE or authority change follows the existing generation boundary and explicit re-entry/role selection. Old commands are neither replayed nor transferred into another identity. Bearer401 is not described as remote logout. Online events and successful Today data reads alone do not establish authority.

The correction also closes the queued-online and delayed background401 paths which could otherwise send another refresh after the uncertain failure. Normal reconnect, successful busy-bootstrap reconciliation and R2 settlement-before-online remain covered. Shared TMA behavior is preserved through the optional owner callback; no new auth scheme or UI redesign.

## Scope and custody

| File                                                                    | Change                                                                                       |
| ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| frontends/pwa-vue/src/App.vue                                           | Current-only recovery, generation-bound refresh policy, online queue behavior                |
| frontends/pwa-vue/src/auth.ts                                           | Optional refresh policy and current-session revalidation through existing authority handling |
| frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue     | Route retained offline Today retry to shell recovery; ordinary retry preserved               |
| frontends/pwa-vue/src/reconnect-recovery.test.ts                        | New deterministic production-flow regression and safeguards                                  |
| frontends/pwa-vue/src/bootstrap-policy.test.ts                          | Existing bootstrap fixtures follow the new options/queue behavior                            |
| frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts | Actual mounted-owner retry and uncertain-command preservation                                |
| tests/e2e/specs/mobile-real-server.spec.ts                              | Append active/suspended N2 browser scenarios; old D002 lines/assertions retained             |
| tests/e2e/playwright.real-mobile.config.ts                              | Include the two exact new titles in the existing selection                                   |
| .github/workflows/ci.yml                                                | Same selection in existing CLI grep; update only two required source-digest pins             |

No files deleted. Nine source/test/config files plus the task status/contract/matrix and this handoff comprise the correction package. Author manifest hashes were verified before and after byte-for-byte integration. The starting checkout was tracked-clean; all25 foreign untracked paths, worktrees, WIP, data and evidence remain preserved.

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

| Gate     | Run                                                                                | Result                         |
| -------- | ---------------------------------------------------------------------------------- | ------------------------------ |
| Push CI  | [39412699625](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412699625) | IN_PROGRESS_WITH_KNOWN_FAILURE |
| PR CI    | [39412708921](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708921) | IN_PROGRESS_WITH_KNOWN_FAILURE |
| Coverage | [39412708929](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708929) | SUCCESS                        |
| Security | [39412708923](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708923) | SUCCESS                        |
| OpenAPI  | [39412708933](https://github.com/DITEKmax/rutcampustrack/actions/runs/39412708933) | SUCCESS                        |

Both push and PR Mobile Vue workspace typecheck and PWA/TMA builds have passed. Both active N2 cases passed; suspended cases failed at the new200/401-only assertion. The actual HTTP status was not retained and remains UNKNOWN. PR original D002 separately timed out after pre-online refresh rejection; its underlying network cause remains UNKNOWN. The two hosted cases use actual browser/owner/backend admission plus a controlled native-fetch rejection boundary. Their purpose is to verify active/suspended user outcomes, no extra refresh POST and no role mutation; they do not prove the historical network cause. SafeReporter serializes source locations rather than titles, so any case title derived from the source loop is labeled as derived.

## Review and remaining limits

Independent internal Astra review checked all nine source/test/workflow files and exact evidence custody: **no blocking findings**. Requested author/reviewer/check model gpt-6-astra/high; actual runtime metadata UNKNOWN. Root coordinated/integrated; no available API switched its runtime model. Designated external reviewer is separate and is contacted only by the main chat.

Known findings: previous F01–F09/N1 retain external FIXED; N2 author-local verification is complete, hosted and external acceptance pending. Historical v3 39/39,196/196 stays frozen and does not count as current N2 acceptance.

Historical network causality, SETUP Attendance PUT500, activity-card mismatch and specific gateway429 depletion remain UNKNOWN. N2 is independently source-confirmed; this correction does not identify those historical causes. B013/B014/B015, rejected Mongo preparation and sandbox restrictions remain frozen. Built-image scan SKIPPED is not PASS. No production/VPS/real secrets/data/provider/Telegram/manual/deploy or main merge.

## Reviewed test correction after source6585

The production contract can return409 with raw `extras.code=SESSION_STATE_STALE`: Auth reconciliation clears an unselectable active role and increments session_version, then compares the fresh authority with bearer claims. The existing App handler already invalidates the old generation and displays login. The new hosted test omitted this legitimate explicit re-entry outcome. This source-confirmed oracle defect does not establish the unrecorded status of the failed run.

Three-file correction: `mobile-real-server.spec.ts` accepts only that exact raw409 code with login/navigation0, retaining no extra POST refresh/no role mutation; unrelated409/403/503 remain failures. Existing numeric status diagnostics are attached on failure without payload. `reconnect-recovery.test.ts` adds the actual-App409-envelope case. `ci.yml` changes only two matching source-digest pins. Product bytes and original D002 are unchanged.

Correction checks:12 production-flow tests PASS (4787ms,398624KiB), seven extracted-oracle positive/negative controls PASS, hosted/focused TypeScript PASS,20 source-bound diagnostic controls PASS. A wrong-source diagnostic invocation first produced19PASS/1ERROR and is retained as NOT_PASS; the corrected invocation reads the actual assigned source. The previous exact11-case baseline RED/GREEN remains evidence for its original bytes; no claim of expanded12-case baseline identity. Internal independent review APPROVE on all three manifest hashes; hosted proof for this delta PENDING. Original author safe summary and review handoff are retained in the ignored evidence directory. No source6585 failures are relabeled green.
