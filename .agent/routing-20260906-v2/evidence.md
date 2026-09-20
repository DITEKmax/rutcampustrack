# Evidence — routing instructions v2

## Baseline and ownership

- Packet revision: `v2`; risk `S2`; requested writer route: `gpt-5.6-luna/max`.
- Baseline: `87784165874e2da6fc261abc1c01584e24624289`, branch
  `codex/materials-transfer`, captured before v2 edits.
- Shared checkout was already dirty with unrelated tracked/untracked work.
  Existing `.agent/routing-20260906/`, design packet, migration packet and
  vertical lane were left unchanged. This packet is the only new routing writer
  scope.
- Active local routing files were copied to `archive/*.inactive` before edits;
  `archive/ARCHIVE-MANIFEST.md` records their hashes and non-active status.

## Source resolution

- The owner decision dated 2026-09-06 supersedes the earlier 2026-09-05 routing.
  The manifest record is `owner-decision:2026-09-06-agent-routing-v2`, with
  status `applied` and `supersedes` set to the prior record. No product/API/Figma
  decision was changed.
- Common model/risk/coordinator policy is staged for global
  `C:\Users\maksd\.codex\AGENTS.md`; project `AGENTS.md` and
  `docs/agent-workflow.md` now reference the global canonical policy and retain
  only project procedure. `parallel-development.md` retains product lane
  ownership without a duplicate model/risk table.
- A follow-up owner decision dated 2026-09-06 adds one project-only workflow
  paragraph: after API/shared-components freeze, independent screens may run as
  parallel fresh Luna max tasks; each role then receives a separate end-to-end
  task from entry to result with negative cases, an integrated Docker runtime is
  used when needed, and fresh independent Sol important review follows. There
  are no legacy users or data, so no compatibility layer is added; accepted new
  flows, authz, and data invariants remain checks. The current batch has five
  defects, TMA checks are owner-deferred, and root owns later main integration
  while preserving pre-dirty state. This is local workflow only; no global route
  or product/API/Figma decision changed.
- Official source references already opened by root:
  `https://learn.chatgpt.com/docs/agent-configuration/subagents` and
  `https://learn.chatgpt.com/docs/config-schema.json`. No unsupported config key
  was introduced; one coordinator layer is behavioral policy, not a new schema
  field.

## Routing facts

- Root is Astra medium; Astra low is limited to an obvious S0/S1 plan and Astra
  high is for critical architecture/product/risk.
- Coordinators are bounded read-only tasks at Astra low/medium. Their count is
  constrained by independent scopes and actual slots, with one coordinator layer;
  only a coordinator explicitly assigned that function may spawn assigned fresh
  Luna max leaves. Ordinary scouts do not spawn. Leaves cannot create children.
- Fresh implementation leaves default to Luna max, including S3. Terra high is
  not automatic and requires a recorded defect/complexity boundary, request or
  reference, reproduction, new evidence, correction, bounded scope and root
  decision. Important review is fresh independent Sol high; Sol xhigh is an S4
  review exception with explicit justification.
- Every spawn carries explicit model/effort, fresh compact packet and
  `fork_turns="none"`. `agents.max_concurrent_threads_per_session = 3` is a
  child limit and does not prove total runtime capacity or model selection.

## Runtime and protected state

- Session observation supplied to root: four total concurrent slots were visible
  during coordinator/leaf overlap. This is runtime evidence for observed
  parallelism only; it is not inferred from config.
- Local/global target hashes were checked without printing protected contents.
  The nine live preconditions and five staged payload hashes are in
  `staged/preconditions.json` and are embedded in the guarded apply script.
- Root applied the protected global/local `.codex` targets through the actual
  filesystem permission path. Command: `pwsh -NoProfile -File
  .agent/routing-20260906-v2/apply-protected-routing.ps1 -Scope All -Apply`;
  exit `0`; output confirmed `Protected routing apply PASS for 9 targets`.
  Backup: `C:\Users\maksd\.codex\backups\routing-20260906-v2-b9f66a2807d2466fa4c23ee8f9a495dd`.
  This evidence records application only; it does not imply a running session
  reloaded the config.

## Capacity observation after apply

- Earlier overlap showed four concurrent tasks. After TMA completed, root's
  attempt to start a fresh integrator while root, BE and PWA were running
  returned `agent thread limit reached`; the completed nested leaf still appeared
  in the active list, and interrupting it did not free a slot. Therefore a
  running-count snapshot below four is not proof that a spawn is available.
  Check open/finishing children and the actual spawn result; do not add config
  keys or bypass the limit.

## Verification limitation

- The v2 routing record was parsed successfully before the later shared-checkout
  edit. A fresh whole-file JSON parse now stops at
  `docs/sources/manifest.yaml:31196`, where the unrelated entry contains the
  malformed key fragment `"decision]cate"` without a colon. This writer did not
  repair that pre-existing deep manifest corruption or rewrite the large shared
  file; the exact failed check is recorded in `checks.json`.
