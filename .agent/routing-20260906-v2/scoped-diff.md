# Scoped diff — routing instructions v2

## Active writable project files

- `AGENTS.md` now keeps RutCampusTrack boundaries/procedure and points to the
  global canonical routing; generic model/risk tables and temporary staging
  wording were removed. Russian communication and project ownership rules remain.
- `docs/agent-workflow.md` now contains project procedure, coordinator handoff,
  one shared-checkout writer rule, escalation evidence gate and independent
  review handoff. The duplicated model/risk tables were removed.
- `docs/implementation/parallel-development.md` keeps the product lane decision
  and replaces its routing table with project ownership. It points to the global
  canonical route and states one coordinator layer, coordinator-to-leaf scope,
  one writer per shared checkout and independent worktrees for parallel writers.
- `docs/agent-workflow.md` adds the dated project-only future-batch workflow
  decision: parallel fresh Luna max screen tasks after API/shared-components
  freeze, separate role E2E tasks with negative cases, optional Docker runtime,
  and fresh independent Sol important review; no legacy compatibility layer.
- `docs/sources/manifest.yaml` marks the earlier 2026-09-06 routing record
  superseded and records the v2 owner decision as `applied`, with the protected
  global canonical path and application backup.

## Protected target staging and apply

`.agent/routing-20260906-v2/staged/global/AGENTS.md` is the common canonical
policy. Its three role TOMLs are behavior-only: explorer is a read-only scout or
explicitly assigned coordinator, developer is a fresh Luna max leaf writer, and
reviewer is fresh independent review. `staged/config-routing.toml` changes only
the six supported values, including default subagent effort `max`; there is no
coordinator schema key or max-depth claim.

`apply-protected-routing.ps1` maps nine exact targets: global `AGENTS.md`, global
config and three global roles, plus project `.codex` config and three project
roles. It requires `-Apply`, freezes and hashes staged inputs, checks exact live
preconditions, rejects reparse/out-of-root paths, renders only six config keys,
backs up originals under a unique path, uses same-directory atomic replacement,
rolls back changed targets on failure and verifies readback. Root applied all
nine protected targets after review through the permitted filesystem path; the
backup location and PASS output are recorded in `evidence.md` and `checks.json`.

## Archive and evidence

The pre-v2 active local files are retained as `.inactive` copies under
`archive/`, with hashes in `archive/ARCHIVE-MANIFEST.md`. v1 routing artifacts
under `.agent/routing-20260906/` remain unchanged evidence. `task-contract-v2.md`
contains the nine contract sections; `evidence.md`, `checks.json` and
`staged/preconditions.json` record source precedence, hashes, checks and limits.

Git reports this repository's transfer docs and agent artifacts as untracked or
pre-existing dirty material, so a tracked baseline diff is unavailable. The
archive copies are the stable before-state for this bounded routing diff; no
unrelated files were reverted or staged.
