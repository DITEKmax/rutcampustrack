# H71 Mongo projection diagnosis and conditional harness repair

Packet owner: bounded implementation leaf `h71-mongo-projection-fix` (Luna max, no children; Terra NEVER).
Sole writer scope: this directory and, only after the diagnosis gate, the H71-owned harness projection/assertion scope in `runner.ps1`/its targeted regression check.
Base harness revision: `73fd5f27ceb429ad0692b073189f8a527c550830`.
Accepted runner source hash: `05C76B696BF987370C0597C3AF76C9DB94FB588AA3AC3087D6907110EBBB9716`.
Rules: [C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md), SHA-256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Current: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md` (OWNER GO checkpoint; H71 open).

## 1. Goal

Identify the first exact persisted Mongo status value/schema mismatch in H71 and repair only an unequivocal harness serialization/projection defect. Preserve strict raw-storage semantics and the API/domain distinction.

## 2. Context / evidence

- Root originals (read-only, immutable): `.agent/orchestration-v2/evidence/h71-context.json`, `h71-report.json`, and `h71-owned-cleanup.json`.
- Failed run: `20260920-085506935-kjaskyqr`; union revision `426a15b6b42e816deaa3ca5c50437e0964aaf85e`; runner hash `05C76B...BB9716`; probe hash `1093B...8731D6`; build manifest hash `61E73E...009F4F`.
- H71 report records Node I1 exit 0 and exact failure: `I1 persisted request ticket must retain the SUBMITTED database state (API maps it to PENDING)`; it does not save the post-I1 snapshot value.
- Source model opened by root: `ExcuseTicket.status` is `ExcuseTicketStatus` with default `SUBMITTED`; registered `MongoConvertersConfig.ExcuseTicketStatusWriter` serializes `source.name().toLowerCase()` and the reader uppercases on read.
- Harness source: `Get-MongoSnapshot` projects raw `x.status`; `Assert-I1MongoDelta` uses strict `-ceq 'SUBMITTED'`.

## 3. Relevant scope

Assigned worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`.
Relevant files: `.agent/student-role-orchestrator/requests-runtime/runner.ps1`, `r8-mongo-projection-check.ps1`, `probe.mjs`, and this unique evidence directory. Product source under `v2-runtime-build-r2` is read-only context and out of scope.

## 4. Required behavior

Reproduce the actual emitted Mongo JavaScript and production assertion with a model-correct fixture that applies the registered writer (`SUBMITTED` -> `submitted`). Record the first exact observed raw value and schema. Repair only if the harness expected value is unequivocally wrong for persisted storage, with a narrow regression that keeps raw Mongo and public API representations separate.

## 5. Constraints

- No product edits, no assertion weakening, no case-insensitive comparison, no arbitrary normalization, no broad hardening.
- Do not overwrite root H71 evidence, accepted baselines, or other agents' changes.
- No Docker, Gradle, network, ports, heavy queue, secrets, commits, pushes, deploys, or new children.
- WARN/ERROR changes code only after request linkage and reproduction.
- Batch only closely related projected fields against real persistence mappings in the same targeted pass.

## 6. Existing patterns

Use the existing `Get-MongoSnapshot`, `Assert-I1MongoDelta`, `r8-mongo-projection-check.ps1`, and strict `Get-ExactObjectProperty`/canonical descriptor paths. The raw Mongo `status` representation must remain distinct from API `PENDING` and domain `SUBMITTED` naming.

## 7. Acceptance criteria

1. First exact value/schema diagnosis distinguishes runtime-observed data from source-derived expectation.
2. Meaningful emitted-JS/model-correct regression fails before correction and passes after correction.
3. A negative wrong persisted status still fails.
4. Relevant enum converters are checked without applying global lowercasing to unrelated fields.
5. Unique evidence is immutable and includes source/runtime distinction, diff, limitations, and cleanup state.

## 8. Verification

Use targeted parser/pure checks only; record revision, exact command, exit code, environment, and evidence in `checks.json`. Runtime is N/A for a harness-only repair unless the targeted helper requires it; do not rerun the full H71 Docker harness. Perform a final scoped diff/status check and release source freeze before root's independent Sol review.

## 9. Do not

Do not claim H71 runtime PASS, rewrite product converters, change API contracts, weaken strict guards, overwrite `h71-report.json`, or escalate to Terra. If evidence shows a product persistence defect instead of a harness expectation defect, stop the affected source and report the root proposal without editing it.
