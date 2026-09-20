# Runtime-final evidence

Status: `HEAVY RELEASE`. Baseline revision is
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. The frozen source guard has the
original 17-entry SHA256 manifest and the post-correction classification of
three expected drifts, twelve unchanged existing rows and two deleted rows
absent. Correction-01 and correction-02 manifests, plus the prior four-failure
runtime evidence, are referenced by hash in `manifest.json`.

The shared focused command started at `2026-09-10T18:37:03Z` and exited 0
(`BUILD SUCCESSFUL in 59s`, 16 tests, 0 failures). The first auth command is
retained as archived failure evidence (exit 1, 25 tests, four fixture
failures). The corrected retry started at `2026-09-10T19:02:50Z` and exited 0
(`BUILD SUCCESSFUL in 1m15s`, 25/0/0/0). The PostgreSQL admission IT started at
`2026-09-10T19:04:24Z` and exited 0 (`BUILD SUCCESSFUL in 1m1s`, 5/0/0/0).

The integration run used fresh `postgres:16` with reuse disabled, container
`ac2c415009ee07cf53513227ea55979b4e32462e02f980c881aca7f86ea33eff`, port
55775, PostgreSQL 16.13, and Flyway validation/application through V24. The
postguard recorded `bad=0`, Java/Gradle processes 0, reserved ports free, and
an escalated read-only Docker query exit 0 with zero containers.

Seven current JUnit XML files were copied byte-for-byte to `junit/`; the copy
check passed 7/7. Exact suite hashes, byte counts and 8+8+10+5+7+3+5 test
counts are in `manifest.json`. No source, test or prior evidence file was
changed in this capture; this artifact records runtime evidence only.
