# Task contract — routing instructions v2 (2026-09-06)

Packet revision: v2  
Risk: S2  
Owner: `/root/routing_coordinator/routing_writer` (sole writer for routing/docs/config artifacts)  
Root: `/root`; bounded coordinator: `/root/routing_coordinator` (read-only)  
Requested implementation model/effort: `gpt-5.6-luna` / `max`; no child agents.  
Baseline revision: `87784165874e2da6fc261abc1c01584e24624289` on `codex/materials-transfer`.  
Baseline observation: shared checkout is dirty with unrelated tracked/untracked work; existing v1 packet is `.agent/routing-20260906/` and remains evidence.

## 1. Goal

Replace the active routing description with the owner decision dated 2026-09-06:
Astra medium root → bounded read-only coordinators at Astra low/medium, all in
one coordinator layer → fresh Luna max leaves; Terra high is an evidence-backed
escalation;
fresh Sol high performs important independent review. Keep global rules canonical,
project documents procedure-focused, and stage protected global/local Codex targets
without claiming that they were applied.

## 2. Context/evidence

- `AGENTS.md`, `docs/agent-workflow.md`, the v1 routing packet, and local
  `.codex/{config.toml,agents/*.toml}` were read. Current local config has
  `gpt-6-astra`/`medium`, agents enabled, concurrency `3`, and default
  `gpt-5.6-luna`/`high`; v2 changes the default leaf effort to `max` in staged
  targets only.
- The global `C:/Users/maksd/.codex/AGENTS.md` and global role/config routing
  fields were provided as already inspected evidence; protected contents are not
  copied or printed here. Global application is pending root review/approval.
- Official schema/subagent pages were opened by root: the schema documents only
  supported config fields; standalone role files carry behavior while explicit
  spawn model/effort remains authoritative.
- Observed runtime metadata: four total available slots in this session; the
  config concurrency field is a child limit and does not prove total capacity or
  model selection. Explicitly requested Luna max is not independent runtime proof.
- v1 review identified incomplete global routing/handoff and unsafe apply concerns;
  v2 retains its `.inactive` archive and adds a fresh staged, guarded apply path.

## 3. Relevant scope

Writable active docs: root `AGENTS.md`, `docs/agent-workflow.md`, and the routing
section of `docs/implementation/parallel-development.md`; one routing manifest
record in `docs/sources/manifest.yaml`; new evidence/check artifacts under
`.agent/routing-20260906-v2/`. Protected targets are staged under that packet:
global `AGENTS.md`, global role TOMLs, and local `.codex` config/roles. No product
source, tests, Figma, API, deployment, or unrelated docs are in scope.

## 4. Required behavior

- Global `AGENTS.md` is the sole canonical common routing policy. It defines root
  Astra medium (Astra low only for an obvious S0/S1 plan; Astra high for critical
  architecture/product/risk), bounded read-only coordinators at Astra low/medium
  when independent scopes and slots make them useful, exactly one coordinator
  layer, and fresh leaf spawns with explicit model/effort and `fork_turns="none"`.
- Default implementation leaf is `gpt-5.6-luna`/`max`, including S3. Risk selects
  safeguards, evidence, checks, and review; it does not automatically select
  Terra. Root may route directly to a leaf when a coordinator would reduce useful
  parallelism.
- Coordinators may spawn their assigned fresh leaves, but never nested
  coordinators; leaves cannot create children. Root may route directly to a leaf
  when a coordinator would reduce useful parallelism.
- Terra high is allowed only after a recorded defect or complexity boundary has
  a request link/reference, reproduction, new evidence, correction, bounded
  scope, and root decision. Sol high is a fresh independent important review,
  not an automatic implementation fallback. S4 exceptions retain S3 safeguards.
- Project `AGENTS.md` and workflow docs keep project-specific procedure and link
  to the global canonical policy; they do not repeat the model/risk matrix.
  Persistent role files contain behavior only and do not pin model/effort or
  create coordinator configuration keys unsupported by the official schema.
- The staged PowerShell apply script must hash/freeze/parse all payloads before
  writes, validate exact live preconditions, reject symlink/reparse/out-of-root
  paths, preserve unrelated TOML semantics and line endings, use unique backup
  and same-directory atomic replacement, roll back on failure, and verify readback.

## 5. Constraints

Preserve all unrelated dirty/untracked work and current product decisions. Do not
read or print secrets or protected global contents, bypass ACLs, apply protected
targets, spawn agents, or claim runtime/model behavior from config alone. Do not
redesign the product, API, Figma, trust, security, or five JS findings. Root owns
the final review and any protected apply operation.

## 6. Existing patterns

Use `.agent/routing-20260906/` as immutable v1 evidence and its archive as the
pre-v1 `.inactive` record. Use JSON-compatible `docs/sources/manifest.yaml`,
`.agent/*/checks.json`, explicit SHA-256 evidence, and `apply_patch` for edits.
The existing config schema fields are limited to the observed root/agents keys;
the concurrency value remains `3` unless a supported owner decision changes it.

## 7. Acceptance criteria

1. Global staged policy contains one coherent owner route and complete S0–S4
   safeguards, with one coordinator layer, coordinator-to-leaf spawning, and
   the no-nested-coordinator/leaf-child boundary explicit.
2. Active local docs contain no conflicting or duplicated generic model/risk
   tables; local project procedure links the global canonical policy.
3. Staged role files are behavior-only; staged config uses root Astra medium and
   default Luna max without invented schema keys; unrelated values are preserved.
4. v1 routing artifacts remain archived `.inactive`, and the manifest records the
   dated v2 supersession without rewriting product records.
5. Apply script has exact target/stage hashes, preconditions, backup/rollback,
   atomic replacement, reparse guards, parse/readback checks, and is staged only.
6. Stable diff, syntax/link/text checks, hashes, and limitations are recorded;
   product runtime is explicitly not applicable.

## 8. Verification

Run TOML parsing for active/staged roles/config and JSON-compatible manifest
parsing; assert canonical-link, no-duplicate-table, S0–S4, coordinator, leaf,
Terra-gate, and review wording. Compile the apply script and exercise it only in
isolated temporary fixtures for success, stale/missing/malformed stage, changed
live target, pre-existing backup, reparse refusal, rollback, CRLF preservation,
and readback. Record command, exit code, environment, revision, hashes, diff
scope, and evidence in `.agent/routing-20260906-v2/checks.json`. No application or
service runtime is applicable to documentation/config-only changes.

## 9. Do not

Do not apply global/protected files, alter product/JS/Figma/API behavior, add
unsupported config keys, remove historical evidence, overwrite another agent's
work, use unreviewed runtime assumptions, or stop a dependent in-scope change
for a product decision that is outside this contract. Report any required delta
to root instead of redesigning it.
