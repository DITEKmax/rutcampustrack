# CURRENT — PR2 N2 session recovery

Persisted Goal Q031/D037: **N2_HOSTED_ORACLE_CORRECTION_IN_PROGRESS**. [Active contract](TASK-N2-RECOVERY-20261020.md), [eleven findings](fix-findings-F01-F09-20261019.csv), [retained historical causal handoff](HANDOFF-CI-CAUSALITY-20261020.md).

Starting **4c71f43d3bb078c275656b2cba54b2d8b19610ed**

Published/executable **6585ca49f4ef4890797b77ed7e252865b57b4f3c**

Branch codex/server-validation-20261011; base3908fad9; PR2 open/draft/unmerged. Source6585 Coverage39412708929, Security39412708923 and OpenAPI39412708933 SUCCESS. Push CI39412699625 and PR CI39412708921 continue with known remaining-mobile failures; no cancellation/rerun. Both full PWA/TMA typecheck/build jobs PASS. Baseline4c green gates and historical failures remain separate receipts. Built-image scan SKIPPED. Historical v3 **39/39,196/196 FROZEN** is preserved; current known findings **10/11 independently FIXED**, N2 REQUEST_CHANGES/correction PENDING.

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

**Hosted counterfailure:** both source6585 active N2 cases PASS; suspended cases fail at the new test's200/401-only assertion. Actual response status was not retained: UNKNOWN. Source confirms suspended-grant reconciliation can return409 with raw `extras.code=SESSION_STATE_STALE`, which existing App correctly handles as login. A reviewed three-file test correction accepts only that exact409 and requires login/navigation0/no extra refresh or role mutation; arbitrary409/403/503 still fail. It also records bounded numeric failure status through the existing safe carrier. Product files and original D002 remain unchanged. PR D002 separately timed out after pre-online refresh rejection; underlying network cause UNKNOWN, not closed by the oracle correction.

**Progress:** original exact11-case baseline7FAIL/4PASS → fixed11PASS preserved. Expanded12 production-flow cases, seven strict admission-oracle controls, focused/hosted TypeScript and20 diagnostic controls PASS. Mounted-owner6, auth46/bootstrap29 prior results remain applicable. Both initial product and three-file correction have internal independent Astra APPROVE; designated external review and new-source hosted acceptance PENDING. Exact safe manifests/commands/results are under current-wip/n2-recovery-20261020. Combined local tests/full Vue typecheck hit unchanged512MiB cap, NOT_PASS; hosted full consumer builds/typecheck PASS.

**Ownership/model:** root sole Git/taskdocs integrator; sole author `/root/n2_astra_author`, `/tmp/rct-n2-astra-20261020`; independent read-only `/root/n2_astra_review`. Requested model gpt-6-astra/high/forknone for N2 research/code/check/review; actual runtime metadata UNKNOWN. Root model switch is not exposed; root performs coordination/integration. External designated reviewer is separate and new delta PENDING. Starting tracked clean, foreign25 paths hash preserved; no reset/clean, data/cache deletion or runs cancelled.

**Limits/UNKNOWN:** historical f4/178 network causality, PUT500, activity mismatch and gateway429 depletion remain separately UNKNOWN; source N2 does not prove historical network cause. B013/B014/B015, rejected Mongo preparation and browser sandbox bypass FROZEN. Local full stack NO_FIT_PROOF; CPU1/group RSS512MiB bounded checks only, full local privacy launcher not repeated. Current hosted privacy receipts remain accepted for unchanged diagnostic code. No production/VPS/secrets/real data/provider/manual acceptance or deferred scope.

**Next:** stage the exact reviewed three-file correction plus owned task status; run actual-staged precommit, commit, and wait for current source6585 gates to finish before normal publication. New ordinary CI verifies the specified suspended409 re-entry path and retains all existing scenarios. Then exact source/docs/evidence [handoff](HANDOFF-N2-RECOVERY-20261020.md) at READY_FOR_INDEPENDENT_RECHECK and stop. No merge/deploy/reviewer-chat contact; Goal not COMPLETE.
