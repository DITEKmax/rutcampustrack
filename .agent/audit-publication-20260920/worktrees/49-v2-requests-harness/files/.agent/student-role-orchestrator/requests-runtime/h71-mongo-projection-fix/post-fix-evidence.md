# H71 post-fix targeted evidence

The targeted pure regression was run after the H71 guard/fixture correction.

Command: `pwsh -NoProfile -File .agent\student-role-orchestrator\requests-runtime\r8-mongo-projection-check.ps1`

Exit code: `0`

Output: `R8 Mongo projection check: PASS (persisted lowercase submitted Mongo state plus actual API camelCase, Mongo snake_case and outbox descriptor shapes are compared separately; aliases, expiry and content mutations rejected)`

The check extracts and executes the actual `Get-MongoSnapshot` definition, validates its emitted JavaScript query/projection, runs the real `probe.mjs --self-test` fixture, and feeds the resulting descriptors into the actual `Assert-I1MongoDelta` definition. It accepts only raw Mongo `submitted`; it rejects raw `approved` and uppercase `SUBMITTED`. The same pass verifies the public API `summary.status=PENDING` contract and `attachments[].state=ACTIVE` contract. No Docker or product runtime was started.

Independent direct emitted-JS-to-assert reproduction output (exit code `0`):

```json
{"helper":"Get-MongoSnapshot -> emitted JS -> Assert-I1MongoDelta","database":"attendance_db","rawStatus":"submitted","statusProjection":true,"assertion":"PASS","negativeStatesRejected":{"approved":true,"SUBMITTED":true},"apiPendingContract":true,"attachmentActiveContract":true}
```

The original H71 full runtime remains `FAIL` and is not reclassified as PASS. The saved H71 report did not contain the post-I1 raw snapshot; this targeted evidence proves the helper/assertion contract against the source-registered converter model and identifies the harness mismatch. A fresh full runtime is outside this bounded repair and remains root-owned.
