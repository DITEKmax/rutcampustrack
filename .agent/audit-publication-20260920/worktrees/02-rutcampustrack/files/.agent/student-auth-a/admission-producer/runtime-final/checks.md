# Runtime-final checks

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Original source
guard: `../source-hashes.json`, SHA256
`DF8896D704B015E75C7266BCC0E6AAB1F56BDCC98D03276E75878C1342DCDA98`, 5613
bytes, 17 entries.

| Check | Evidence | Exit/status |
|---|---|---:|
| Shared focused tests | 2026-09-10T18:37:03Z; exact command and 16/0/0/0 aggregate in `manifest.json`; BUILD SUCCESSFUL in 59s | 0 |
| Earlier auth focused failure | Archived `../runtime-failure-01/manifest.json`, exact command; 25 tests, 4 failures, 0 errors, 0 skipped | 1 (archived) |
| Auth focused retry | 2026-09-10T19:02:50Z; exact command and 25/0/0/0 aggregate in `manifest.json`; BUILD SUCCESSFUL in 1m15s | 0 |
| PostgreSQL admission IT | 2026-09-10T19:04:24Z; exact command and 5/0/0/0 aggregate in `manifest.json`; BUILD SUCCESSFUL in 1m1s | 0 |
| Source postguard | Three expected drifts (correction-01 two plus correction-02 IT), twelve other existing rows unchanged, two deleted rows absent, `bad=0` | 0 |
| XML copy equality | Seven current build XMLs copied to `junit/`; source/destination SHA256 and byte lengths equal | 0, 7/7 |
| Runtime guard | Java/Gradle `0`, reserved ports free; PostgreSQL container `ac2c415009ee07cf53513227ea55979b4e32462e02f980c881aca7f86ea33eff`, port 55775, PostgreSQL 16.13, image `postgres:16`, reuse false; Flyway validated/applied through V24 | PASS |
| Docker cleanup query | Escalated read-only `docker ps` | 0, containers 0 |

All seven captured XML suites have zero failures, errors and skipped tests.
The earlier failed command remains failure evidence and is not relabeled as a
pass; the successful retry is the accepted focused auth result.
