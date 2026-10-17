# Current731 receipt reconciliation

READ_ONLY bounded reconciliation complete; upstream CI Chromium boot still pending at snapshot. No source/runtime/test mutation. Requested Sol6.1/high; actual UNKNOWN. Root published head `731d45e5c890e62976a69192dce62e9b5f534c6c`, all four actual run events `pull_request`, attempt 1. Producer head differs from actual PR merge checkout `ba64e94c498866b9b833de38baff4c3888869a09`. Draft PR2 remains open, target main3908, unmerged.

| Workflow | Run | Status | Conclusion |
| --- | --- | --- | --- |
| CI | 38205391312 | in_progress | pending; Python Ruff/Auth IT/Academic IT failed |
| Coverage | 38205391066 | completed | failure; Auth/Academic |
| Security | 38205391059 | completed | success; built-images conditional skipped |
| OpenAPI | 38205391045 | completed | success |

Actual checkout verified independently for all17 completed CI jobs, all8 executed Coverage jobs, three executed Security jobs and OpenAPI job. Coverage diff report skipped, Security built-image job skipped: no executed checkout. Chromium still on Boot isolated Vue auth stack; no current browser test/result receipt and checkout UNKNOWN until available.

Native collector results: Academic unit465PASS/0FAIL/0ERROR/0SKIP(46XML); Auth unit140PASS/0FAIL/0ERROR/0SKIP(25XML); Auth IT167PASS/2FAIL/0ERROR/0SKIP(24XML); Academic IT153PASS/292FAIL/0ERROR/0SKIP(65XML). Collector acceptance NOT_ASSESSED; finite counts do not close the required matrix. Coverage logs independently show same Auth169total/2fail and Academic445total/292fail; Coverage does not publish native collectors for these failed jobs.

Academic292/292 private XML failures are IllegalStateException at AcademicFixtureResetListener.beforeTestMethod:65 with exact inventory guard; SQLState/CausedBy absent. Current V57 creates bot_credential_messages absent from TABLES. Failure is before fixture TRUNCATE/seed/test, so no product-assertion failure inferred. Auth both AssertionFailedError expected1/actual4 at AuthOtpFlowIT304/360 payload.size(), requiring source correction consistent with accepted v2/T002, preserving nonsecret payload checks. Ruff fourI001 imports plus threeE501 lengths124/132/121 versus120; exact paths/codes in ruff-diagnostics.json.

Safe artifacts downloaded byte-for-byte and digest matched GitHub: Academic unit11727540712 /467596fe..., Auth unit11726799162 /eacc40c7..., Auth IT11727257083 /e1af0f1f..., Academic IT11727905148 /ff342d99.... Full IDs/digests and run/job status evidence in metadata-final.json. Native XML identities/digests and current source identities are retained in the safe collector JSON. Raw reports11727306919(Auth)/11727690761(Academic) are PRIVATE600, excluded from the safe archive; safe extracts retain types/stack source/numeric assertions only. No credentials, request values, stdout or sensitive properties are published.

Next: finish sole-author source corrections/review; reconcile Chromium terminal receipt and new current producer CI gates after source changes. This checkpoint is not product DONE or all-three-green.

Snapshot: 2026-10-12T05:03:59.525283+00:00
