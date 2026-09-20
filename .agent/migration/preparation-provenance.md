# Provenance и protected baseline подготовки

Baseline transfer: `.agent/migration/pre-transfer-baseline.json`, revision
`87784165874e2da6fc261abc1c01584e24624289`.

| Path | Baseline SHA-256 | Current SHA-256 | Result |
|---|---|---|---|
| `docs/INDEX.md` | `084d57fc…f6e8d0f3` | prior exact body retained at `docs/archive/transfer-20260905/INDEX.md` with the same hash | adapted navigation only |
| `AGENTS.md` | `82e6ce5c…97acd759` | `d16c3bca…c21ed331` | dated owner-authorized append, recorded in manifest `governance_changes` |
| `docs/agent-workflow.md` | `daa588fc…dadfcb3d4` | `932072f7…abbf8156e` | dated owner-authorized append, recorded in manifest `governance_changes` |
| `frontends/AGENTS.md` | `4ff6effd…201a3b50` | same | unchanged |
| `services/AGENTS.md` | `dadad5fe…db50d476` | same | unchanged |
| `tests/AGENTS.md` | `58c78156…346d867` | same | unchanged |
| `DATABASES_OVERVIEW.md` | `985f14ca…183b035a` | same | unchanged |

`docs/sources/manifest.yaml` keeps the transfer inventory SHA-256
`433ed5f2…fc3bacd1` and adds post-transfer generated/artifact hashes separately.
The 06.09.2026 read-only recheck resolves 109 retained duplicates recursively and has
zero missing targets, source drift, or target hash failures after refresh; it is not a
replacement transfer.
