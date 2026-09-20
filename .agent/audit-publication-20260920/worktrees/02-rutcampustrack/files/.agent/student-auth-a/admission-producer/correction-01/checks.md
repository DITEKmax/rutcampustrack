# Correction-01 checks

Environment: Windows PowerShell, checkout
`C:\Users\maksd\.codex\worktrees\1456\rutcampustrack`, revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. No Gradle/Docker/runtime command
was run in this correction.

| Check | Command/evidence | Exit | Result |
|---|---|---:|---|
| Source baseline guard | `Get-FileHash`/byte comparison for the 17 rows in `../source-hashes.json` before editing | 0 | PASS, 17/17 |
| Source-17 post-correction guard | Same manifest: two named target drifts match the correction manifest; the other 13 existing rows match; two deleted rows remain absent | 0 | PASS |
| Exact two-row manifest | `Get-FileHash -Algorithm SHA256` and `Get-Item.Length` for both owned fixtures | 0 | PASS |
| Fixture semantic patterns | Scoped PowerShell assertions for both live issue times, `plusSeconds(60)`, default `group_id="10"`, terminal `group_id=null`, status/readOnly assertions | 0 | PASS |
| Scoped whitespace | PowerShell trailing-space/tab scan over the two owned fixtures | 0 | PASS |
| Scoped Git/status review | `git status --short` and path-scoped status/diff inspection; no owned product/shared/Auth13 path changed | 0 | PASS |
| Gradle/Docker/Testcontainers/runtime | Explicitly outside this correction packet and prohibited by the request | N/A | NOT RUN; root owns later runtime |

The earlier runtime failure command is retained as provenance only in
`../runtime-failure-01/`; this leaf does not relabel that failed run as a new
PASS. A fresh focused runtime rerun, if required by root, is a separate gate.
