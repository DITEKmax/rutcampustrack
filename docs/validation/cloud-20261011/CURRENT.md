# CURRENT — PR2 N2 session recovery

Persisted Goal Q031/D037: **N2_TYPED_403_SOURCE_READY_HOSTED_PENDING**. [Active contract](TASK-N2-RECOVERY-20261020.md), [eleven findings](fix-findings-F01-F09-20261019.csv), [retained historical causal handoff](HANDOFF-CI-CAUSALITY-20261020.md).

Starting **4c71f43d3bb078c275656b2cba54b2d8b19610ed**

Published/executable **46df487d3c778f7d286ca736988e9d8d2d0d7375**

Branch codex/server-validation-20261011; base3908fad9; PR2 open/draft/unmerged. Current source46df ordinary runs: pushCI39418122297, PRCI39418134955, Coverage39418135012, Security39418134921, OpenAPI39418134957. Security/OpenAPI SUCCESS; both full PWA/TMA typecheck/build PASS; other runs continue, PR has a known suspended N2 failure. Prior source6585 push/PR CI FAILURE and other gates SUCCESS are retained separately. Built-image scan SKIPPED. Historical v3 **39/39,196/196 FROZEN** remains; known findings **10/11 independently FIXED**, N2 correction/external review PENDING.

| ID  | Preserved independent acceptance; prior fixes retained                                    |
| --- | ----------------------------------------------------------------------------------------- |
| F01 | FIXED: empty fences valid, malformed IDs strict                                           |
| F02 | FIXED: durable RELEASED tuple/race/replay/fences                                          |
| F03 | FIXED: authoritative eligibility/pending fence/profile locks                              |
| F04 | FIXED: FULL trusted ADMIN reset, public denial, expiry/revoke                             |
| F05 | FIXED: production principals/minrights/fresh restore                                      |
| F06 | FIXED:68 mandatory env/validator/Compose references                                       |
| F07 | FIXED: production source/downstream gRPC identity                                         |
| F08 | FIXED580/f4: one per-chat publication context                                             |
| F09 | FIXED: domain moved flag, frozen wire, four consumers                                     |
| N1  | FIXED580/f4: empty manifest emits no invalid event; nonempty effect/ACK/archive preserved |

**N2:** the published product offers retained Student «Перезагрузить» as current-only admission, without another POST refresh throughout the chain. Same-authority success/transient failure retain owner/key/uncertain commands. Confirmed401, typed409 SESSION_STATE_STALE or authority change follow the existing generation boundary and explicit re-entry; no remote logout or old-command replay.

**Hosted counterfailure:** both source46df suspended N2 cases observe recovery GET `/api/auth/session` →403 at SESSION_RELOAD; active N2 and original D002 PASS in both. Actual body/code remain UNKNOWN. Source confirms Gateway admission rejects an unselectable active grant before version comparison and maps it to top-level403 WRONG_ROLE. The previous client lost that marker, leaving the retained shell read-only.

**Reviewed correction:** integrated six-file manifest adds a private normalized marker only for current-session + HTTP403 + raw status403 + top-level WRONG_ROLE. App permits explicit re-entry for that exact authority denial. Arbitrary/BFF403, wrong/missing/nested codes and forged markers do not trigger this path. Rejected current-session and refresh-candidate responses check captured generation before propagation, so a late old denial cannot reset newer authority. No blind POST, permission restoration, remote logout or old-command replay. Original D002 remains byte-identical.

**Proof:** exact final21 test bytes against immutable46df production yield1FAIL/20PASS (exact typed403), fixed source21PASS. Internal review caught a late candidate-response race; its regression and fix are retained. An earlier baseline sidecar class-identity mismatch caused an extra failure; corrected source custody is independently verified and the failed harness receipt remains distinct. Auth-client28/auth46/bootstrap29, strict oracle13, diagnostic20 and focused/hosted TypeScript PASS. Prior mounted-owner/initial N2 proofs retain their stated applicability. Final six hashes have independent internal APPROVE; designated external and new-source hosted acceptance PENDING. Local512MiB limits remain unchanged; no forbidden runtime retry.

**Ownership/model:** root sole Git/taskdocs integrator; sole author `/root/n2_astra_author`, `/tmp/rct-n2-astra-20261020`; independent read-only `/root/n2_astra_review`. Requested model gpt-6-astra/high/forknone for N2 research/code/check/review; actual runtime metadata UNKNOWN. Root model switch is not exposed; root performs coordination/integration. External designated reviewer is separate and new delta PENDING. Starting tracked clean, foreign25 paths hash preserved; no reset/clean, data/cache deletion or runs cancelled.

**Limits/UNKNOWN:** historical f4/178 network causality, PUT500, activity mismatch and gateway429 depletion remain separately UNKNOWN; source N2 does not prove historical network cause. B013/B014/B015, rejected Mongo preparation and browser sandbox bypass FROZEN. Local full stack NO_FIT_PROOF; CPU1/group RSS512MiB bounded checks only, full local privacy launcher not repeated. Current hosted privacy receipts remain accepted for unchanged diagnostic code. No production/VPS/secrets/real data/provider/manual acceptance or deferred scope.

**Next:** actual-staged precommit, commit reviewed six-file correction plus owned status; let current46df runs finish before normal push. Verify final-source ordinary gates and strict browser primaries, then exact [handoff](HANDOFF-N2-RECOVERY-20261020.md) at READY_FOR_INDEPENDENT_RECHECK. No merge/deploy/reviewer-chat contact; Goal not COMPLETE.
