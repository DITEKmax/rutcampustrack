# Task contract — routing 2026-09-06

## Goal

Apply the owner-approved global and project routing: evidence → compact contract
→ bounded implementation → independent verification.

## Context/evidence

The owner correction dated 2026-09-06 supersedes the 05.09 routing. Official
configuration evidence confirms the `[agents]` fields and standalone role files;
the runtime supports `gpt-5.6-luna` with `max` effort. Existing 05.09 routing is
archived under `archive/` before this task.

## Relevant scope

`AGENTS.md`, `.codex/config.toml`, `.codex/agents/{explorer,developer,reviewer}.toml`,
`docs/agent-workflow.md`, the active routing table in
`docs/implementation/parallel-development.md`, one manifest record, and this
directory. Global files are staged only.

## Required behavior

Root defaults to Astra medium; fallback is Luna high. Each spawn has explicit
model/effort and a fresh compact packet. Luna max performs bounded implementation;
Terra medium is for complex links and Terra high/Sol high are risk escalations.
Astra low is only for an obvious plan; Astra medium/high handles architecture,
product, uncertainty and risk. The S0–S4 matrix applies globally: S0 is Luna
lookup/tiny bounded edit and check; S1 is compact contract and Luna max; S2 adds
Luna high scout, Astra contract and fresh Sol high review; S3 adds senior contract,
applicable checks/runtime and fresh Sol high review. Sol xhigh is only justified
S4 and retains all S3 safeguards. Important review is fresh Sol high. The staged
handoff requires critical originals to be opened by the planner
and senior reviewer, not only summarized by a scout. FAIL produces a repair
contract and independent recheck without an identical retry lacking new evidence.

## Constraints

One writer in the shared checkout; preserve unrelated dirty/untracked work. Do
not change product code, Figma, API contracts, deploys, or global files in this
task. Role TOML files do not pin model/effort.

## Existing patterns

Project config already uses the `[agents]` schema. Role TOML files carry role
instructions. `.agent/routing-20260906/archive/` is the immutable pre-change
evidence. The manifest is JSON-compatible YAML.

## Acceptance criteria

1. Local root is Astra medium and agents default to Luna high.
2. Workflow and active parallel document contain the 06.09 routing without an
   active conflicting Astra low/Terra default rule.
3. Role instructions express bounded scope, compact evidence and repair review.
4. A self-contained global S0–S4 policy is staged with no global mutation.
5. Global apply freezes and parses staged payloads before a write; it permits only
   six routing-value semantic changes, rejects reparse paths and stale targets,
   uses unique backup, atomic replacement, rollback and readback verification.
6. TOML/YAML/Markdown and apply-script checks record exit codes and evidence.

## Verification

Parse TOML and manifest YAML; inspect routing and handoff assertions; compile the
staged Python script; run it only in a temporary fixture, never against global
config. Cover changed/missing/malformed stage, stale live target, backup, rollback,
readback, CRLF preservation and reparse refusal.

## Do not

Do not apply global files, inspect or print global config contents, modify
application code, rewrite historical reports, or spawn agents.
