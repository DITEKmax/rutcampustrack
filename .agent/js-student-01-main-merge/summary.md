# Preparation summary

Status: PASS_WITH_INSTRUCTION_REVIEW for the bounded S2 preparation.

Prepared:

- guarded-main-ff-merge.ps1: PowerShell 7 guarded dry-run/apply script. It
  requires explicit FinalSHA, ExpectedRootHEAD, and ExpectedBranch, checks
  stable Git preconditions, hashes exact owner leaves, writes an exclusive
  manifest before mutation, and verifies restoration plus final main SHA.
- contract.md: frozen nine-section contract and scope boundary.
- evidence.md: revisions, collision survey, dirty-state preservation, and
  instruction conflict evidence.
- checks.json: criteria, commands, exit codes, environment, and limitations.

Evidence:

- Root HEAD and local main are both
  87784165874e2da6fc261abc1c01584e24624289.
- Root branch is codex/materials-transfer; local master is absent.
- Active integration candidate observed at final prep is
  f383c10a6e0304c4f68aab794612d1bd89b509a8; packet survey SHA
  f85a4d27b9093bad6d60096552f306e8f3e2222b remains available.
- The latest dry-run exits 0 and finds 76 exact collisions: 72 identical and 4
  different. Tracked dirty incoming overlap is zero; staged changes are zero.
- Newer root AGENTS.md and docs/agent-workflow.md are preserved and recorded
  as instruction conflicts.

The actual main switch, fast-forward merge, owner moves, and restoration were
not executed. Root must rerun the dry-run with the final integration SHA, obtain
the independent Sol PASS, resolve or explicitly allow the instruction
conflicts, and invoke -Apply separately.

## Actual merge recorded by root

Apply result: PASS. Local `main` is now
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; all 76 owner files were restored
with 0 SHA-256 mismatches, and the pre-existing tracked dirty snapshot was
preserved. Evidence: [apply-result.json](./apply-result.json).
