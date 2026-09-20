# I3 source freeze

Date: 2026-09-15  
Target: `codex/v2-integration-a2-l3` at base `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`

The accepted I2 34-row union was preserved and expanded with the changed
production Compose path, then rehashed after the I3 source correction. The
exact 35 path/hash rows are in `i3-union-manifest.sha256`. They are sorted by
path, encoded as UTF-8, and their canonical LF-joined, no-trailing-LF
representation has SHA256
`604CD4D36A70710B7C1790805DA70721B97741AB7CA0924E46BAF3850F614C6A`.
The row count is 35 and every recorded file hash matches the target.

The five I3 live-input paths are:

- `.env.prod.example`
- `docker-compose.prod.yml`
- `docker-compose.e2e.yml`
- `tests/e2e/.env.ci` (only the four nonsecret network keys and their comment)
- `scripts/validate-env-prod.sh`

Relative to the read-only R5 sibling, the two Compose files each add only the
explicit `gateway` and `ip_range` IPAM substitutions. The two input files add
the gateway/range values (`172.30.0.1` and `172.30.0.128/25`) and explanatory
comments. The validator adds two required variables and strict containment,
usable-host, distinctness, and outside-range checks. No other current caller
of the four variables was found.

The source manifest used for the preceding R5 transfer remains
`ED35EDC347D30D610F7530DA767C90674A6F1853E4C76331535D8098FE3992F6`; the
pre-I1 union remains `EB831B86E3E61559B4B9B3755AD36F69F582039CB6D460F88B003544E210DC85`,
and the prior I2 union was `32031090D593BBC8B53869590F14D90386A97B1AD4786CDC7B6C16586C8CC266`.
The final manifest adds the production Compose row with hash
`F601260775C6BECDE42C5CEBD157D7F6AD0BD62CBC7B1D0A204C8963C9A41CA3`.
The accepted OpenAPI snapshot (`D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0`),
generated TypeScript (`5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E`),
and composed `OpenApiSnapshotIT` (`D0D715C22A77CBADFE61177454428B606A5F0476359C2B8FD879573A4A221C25`)
are unchanged.

The source sibling worktree was read-only and remained unchanged. No source
instructions, secrets, build/cache output, snapshot, generated types, Docker
state, or R3 runtime evidence was copied.
