# Materials transfer task packet

- Date: 2026-09-05.
- Base revision: `87784165874e2da6fc261abc1c01584e24624289`.
- Branch: `codex/materials-transfer`; it was created after the protected-file baseline while preserving the shared dirty checkout.
- Model and effort: `gpt-5.6-terra`, `high`, selected for S2 provenance-sensitive transfer of 2,230 inventory records. Runtime model metadata is not available in this environment.
- Scope: exact destinations listed by `.agent/migration/inventory.json`; `docs/INDEX.md`; `docs/sources/manifest.yaml`, address index and unresolved-reference report; transfer evidence in `.agent/migration/`.
- Forbidden: source roots, product code, AGENTS/.agents/.codex, `docs/agent-workflow.md`, `DATABASES_OVERVIEW.md`, Figma, API contracts, database and production operations.

## Acceptance criteria

1. Every safe `copy-*` and `duplicate` source has matching pre-copy SHA-256 and size.
2. Exact canonical/archive copies preserve SHA-256; duplicate targets resolve to equal content.
3. Old instructions are inactive archive files, never active project instructions.
4. Every inventory record has provenance, canonical/target address, status, owner/date and checksum data in the manifest.
5. The original index is exact in an archive before its dated navigation addition; protected files remain unchanged.
6. Product disputes remain explicitly blocked; documentation-only work has no application/service runtime to start.

## Evidence and limits

Preflight, post-transfer verification, protected baseline, checks and runnable PowerShell scripts are in this directory. The required independent review is pending in `review.md`. No source instructions or prompts were executed.
