# Scoped diff — routing 2026-09-06

Git cannot produce a tracked diff for this scope because these repository files
were already untracked before the task. The pre-routing copies in `archive/` are
the baseline for the replaced active routing files.

## Local configuration and instructions

- `.codex/config.toml`: root effort `low → medium`; default subagent
  `gpt-5.6-terra/medium → gpt-5.6-luna/high`.
- `.codex/agents/explorer.toml`: compact evidence and scoped search requirement.
- `.codex/agents/developer.toml`: bounded contract and report-before-redesign.
- `.codex/agents/reviewer.toml`: original goal/contract/diff/checks, repair
  contract and fresh Astra architecture review for risk/uncertainty.
- `AGENTS.md`: root actively delegates bounded work; developer owns code and
  broad research/runtime.

## Active project documents

- `docs/agent-workflow.md`: dated 06.09 replacement of 05.09 routing, model
  matrix, S0–S4 paths, handoff, compact contract, repair and verification rules.
- `docs/implementation/parallel-development.md`: active table now has Root Astra
  medium, Luna max bounded FE/BE implementation, escalation and fresh Sol review.
- `docs/sources/manifest.yaml`: a dated active owner decision superseding 05.09.

## Global apply

Only these six config values changed: `model`, `model_reasoning_effort`, and
four `[agents]` routing keys. Four instruction files were added/updated:
`AGENTS.md` and `agents/{explorer,developer,reviewer}.toml`. No global config
contents beyond those field names are copied here. Backups contain the pre-apply
global config and AGENTS hashes recorded in `checks.json` evidence.
