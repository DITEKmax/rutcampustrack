# Implementation summary — routing 2026-09-06

## Changed

- Local root config now selects Astra medium and Luna high fallback.
- Role instructions now require compact scout evidence, bounded implementation
  without silent redesign, and a repair contract with independent recheck on FAIL.
- The workflow replaces the 05.09 routing with the owner decision from 06.09;
  the active parallel-development table uses the same root, implementer, and
  reviewer choices.
- `docs/sources/manifest.yaml` records the dated supersession.
- Global routing was applied to five allowlisted global targets. The guarded script
  allowlisted global targets after `--apply`, freezes and parses the staged
  payload, verifies expected SHA-256 states, preserves unrelated TOML
  keys/sections and CRLF text, uses atomic replacement with rollback, and writes
  unique backups only under the global Codex directory.

## Runtime evidence

The global script ran successfully against a temporary fixture and then against
the reviewed global targets. It retained
an unrelated top-level key and unrelated TOML section, preserved CRLF text,
created all three staged role files, verified readback and backed up the fixture
config. Changed/missing/malformed stage inputs, live target changes, a preexisting
backup and injected mid-commit failure were refused or rolled back. Running
without `--apply` exited 1 as intended. No application or service is applicable
to this documentation/config change.

## Result and limitation

Fresh Sol recheck approved the repaired bundle before the explicit, escalated
global apply. The readback verified all six routing values, unchanged unrelated
parsed TOML semantics, staged byte equality and backups. Real symlink creation
remained unavailable in the fixture (WinError 1314), so that branch used a guard
boundary simulation; the applied targets themselves were regular paths.
