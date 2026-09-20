# Correction-02 checks

Environment: Windows PowerShell, checkout
`C:\Users\maksd\.codex\worktrees\1456\rutcampustrack`, revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. This correction did not run
Gradle, Docker, Testcontainers, PostgreSQL or a service process.

| Check | Command/evidence | Exit | Result |
|---|---|---:|---|
| Source-17 post-correction guard | SHA/byte guard from `../source-hashes.json`: correction-01 two target hashes plus this IT after hash; twelve other existing rows unchanged; two deleted rows absent | 0 | PASS |
| Exact manifest | `Get-FileHash -Algorithm SHA256` and `Get-Item.Length` against the one owned IT row in `manifest.json` | 0 | PASS |
| Spring wiring assertions | Scoped source assertions for singleton registration, class registration, refresh, bean retrieval, instance identity, try-with-resources, controller MockMvc path, and preserved denial helper | 0 | PASS |
| Constructor-path guard | Scoped assertion that the positive block has no `admissionService(now)` call and the four-argument helper remains only outside it | 0 | PASS |
| Java shape/diff guard | Balanced-brace check plus exact required route/assertion markers in the owned file | 0 | PASS |
| Trailing whitespace | PowerShell scan over the owned IT | 0 | PASS |
| Git scope/status | Path-scoped status and ownership review; no product/Auth13/shared/other test path touched | 0 | PASS |
| Runtime/Gradle/Docker | Explicitly outside this correction packet | N/A | NOT RUN; root owns runtime gate |

Any Git global-ignore permission warning is an environment read warning from
the status command, not a source defect; it caused no code change.
