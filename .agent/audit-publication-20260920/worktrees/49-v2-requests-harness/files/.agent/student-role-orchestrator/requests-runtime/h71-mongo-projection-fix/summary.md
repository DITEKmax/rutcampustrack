# H71 scoped summary

Status: `TARGETED_PASS / RELEASE_TO_ROOT_REVIEW`.

## Scope and criteria

The scope was the H71 Mongo raw status projection/assertion and its existing R8 pure check. Acceptance required a source-backed first mismatch, strict model-correct raw storage, a passing actual helper regression, rejection of wrong states, preservation of API PENDING and attachment ACTIVE, and no product edits.

## Evidence

- Before edit: `diagnosis-before-edit.md` records emitted `Get-MongoSnapshot` output `requestTicketStatus="submitted"` and the unchanged assertion failure against `SUBMITTED`.
- After edit: `post-fix-evidence.md` records the actual helper/assertion PASS and negative rejections.
- Checks and exit codes: `checks.json`.
- Root originals were read-only: `.agent/orchestration-v2/evidence/h71-context.json`, `h71-report.json`, `h71-owned-cleanup.json`; none was overwritten.

## Diff

1. `runner.ps1`: changed one strict H71 raw Mongo guard literal from `SUBMITTED` to `submitted`, with the message explicitly naming lowercase Mongo storage and the API PENDING mapping.
2. `r8-mongo-projection-check.ps1`: model-corrected the ticket fixture and all valid after-state fixtures to raw `submitted`; retained strict descriptor field checks; added explicit API PENDING/attachment ACTIVE checks; added negative `approved` and uppercase `SUBMITTED` raw-state cases.
3. Added only this unique H71 evidence directory (`contract.md`, diagnosis, post-fix evidence, checks, summary).

Foreign dirty changes already present in `runner.ps1`, `r8-mongo-projection-check.ps1`, `probe.mjs`, and adjacent request-runtime files were preserved. No unrelated file was reverted or reformatted.

## Verification and runtime

Targeted parser, R8 generated-JS/query/assertion, probe syntax, and scoped diff checks all exited `0`. The pure helper evidence used the current emitted JS and production assertion definitions and exited `0`. Product Docker/runtime was not run; the original full H71 report remains `FAIL` and is not relabeled.

## Limitations / handoff

The original report saved the failure message but not the actual post-I1 raw snapshot, so the exact runtime value is source-derived and reproduced by the model-correct fixture rather than claimed as a saved full-runtime observation. No product persistence defect was established. Source freeze is released for root's final scoped independent review; no commit, push, deploy, Terra escalation, or full historical rerun was performed.
