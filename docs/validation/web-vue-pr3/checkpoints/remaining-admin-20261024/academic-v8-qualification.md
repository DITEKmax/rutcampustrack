# Academic SOURCEv8 / r4 — accepted narrow result

Existing Goal PR3; own worktree `/workspace/rct-worktrees/group-final-delete`; base `10d8b908ae9801b22018881389f21ef5c0188583`. Source stays frozen as `review-source-v8/`: patch SHA256 `9984272a88728f18cd28f6cce34687e570dbc8072893c5524b1a0d6565292f8c`, manifest `b7fb9694e6651d4502cb892401664658375f5dec4fa80c39bd87b0d967c1c69b`.

Root authorized exactly eight previously failed invocations after independent v8 narrow recheck PASS. Reason for retry: explicit status path binding application defect and two fixture CHECK violations corrected. No source edits during or after run. Requested Sol6.1/high; actual served-model telemetry unavailable.

## Result

One selected Gradle invocation, `academic-it-v8-command.txt`, completed exit 0 in 104.17 seconds at 2026-10-24T17:47:19Z. Native XML proves 8 PASS / 0 FAIL / 0 SKIP. Changed Academic API/test compilation passed implicitly; unchanged dependencies were reused. No full suite/class/four-module compile or prior-PASS rerun. Compile log has existing deprecation/unchecked warnings only.

Eight passing scenarios: durable local outbox rollback/resume; password/preview final deletion preserving account/other role/foreign group/replay; lost negative Auth COMMIT reply; acceptance-wins resolver race; stale preview and original partial outcome recovery; logout while prepare delayed; resolver actor/group ownership; stale participant release preserving membership/authority.

The prior five native PASS remain accepted: cross-group threshold (v6-r2), resolve-wins tombstone plus delayed permission/credentials/expiry (v7-r3). Thus all 13 planned Academic/Auth scenarios are closed across the causally qualified native runs. This boundary uses real HTTP, directed Auth, own PostgreSQL/Redis/password/session logic and explicit Schedule/Attendance transport doubles. It does not claim actual Schedule/Attendance database protocols or a live four-service deletion.

## Evidence

- `academic-it-v8-r4-pre.json`: exact source hash, eight selectors, managed socket and cached image identities; fresh disk 3,834,101,760 B; conservative available memory 8,060,149,760 B.
- `academic-it-v8-r4.log`: BUILD SUCCESSFUL; SHA256 `5bfa28f496c41f4dd9bbce129f9ccb91596ca1e29c73aafc4a295c39e58d5af5`.
- `academic-it-v8-r4-native-results/`: immutable Gradle XML + binary outputs. Native case list and XML hash in `academic-it-v8-r4-cases.json`.
- `academic-it-v8-r4-result.json` / `academic-it-v8-r4-resource-samples.json`: before/after source identical, minimum free disk 2,707,173,376 B, guard not triggered, no cgroup OOM/max increments. Gradle HTML test report was not updated by this selected task; native results are preserved directly.
- `academic-it-v8-r4-docker-events.json` / `academic-it-v8-r4-cleanup.json` / before-and-after container snapshots: no image pulls, all own fresh fixture containers closed, no pre-existing container removed, no Gradle daemon/test-worker JVM remains. Fresh free disk at release 3,833,225,216 B.

HEAVY released 2026-10-24T17:48:01Z. No main/shared DB/queue/service changes, no global cleanup, no push/merge/deploy. CI/Coverage/Security/OpenAPI remain manual/PAUSED. Next: Root owns lease/review for selected Schedule protocol checks and bounded cached-Mongo startup adaptation/Attendance checks; none are authorized by the completed Academic GO.
