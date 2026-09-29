# JS-HEADMAN21 lesson transfer - scoped source checkpoint

## Scope

Implements the Schedule operation and Academic bound-homework participant from the frozen S3 contract. Schedule validates and records the future source/assigned free target, exact replay key and payload, fixed binding batches, lifecycle/history and local outbox in its SQL transaction. Academic applies only those binding snapshots with local durable batch receipts and acknowledgements. The operation exposes pending/completed/error status and does not use cancel-and-create.

The Attendance participant, archive-semester guard, and combined API/runtime acceptance are separately owned or sequenced by root and are not claimed complete here.

## Product diff inventory

- Schedule REST contract/controller/service/writer/recovery, event producer/consumer, lifecycle and binding/read gates, TRANSFERRED status, Schedule gRPC response gates, and V21__durable_lesson_transfer.sql.
- Academic transfer batch/event consumer/coordinator, publication materialization marker, homework slot-preserving update, dedicated Rabbit queue/DLQ, and V38__durable_lesson_transfer_participant.sql.
- Proto was isolated in commit a6124b389a134a53f8d2bcd2dc573060c04f3d01: LessonResponse.transfer_operation_id=26, transfer_state=27.
- Foreign .agent/evidence/headman-trend-export-20260926/{contract,result}.md edits are preserved and excluded.

## Acceptance/evidence state

Implemented source supports exact replay, source revision and target fences, batches of at most 64 plus an explicit empty Academic batch, durable acknowledgements, ERROR status for terminal participant conflicts, and Academic updates that retain homework identity/content/publication state. A transferred source is distinct from cancellation. The frozen cross-service contract and atomicity implementation record are in contract.md and atomicity-options.md.

## Checks

- git status --short; git branch --show-current; git rev-parse HEAD - exit 0 before scoped commit; assigned branch and proto commit verified. Git emitted a global-ignore permission warning, recorded in checks.json.
- git diff --cached --check - pending immediately before source-ready commit; record the exact exit code in checks.json.
- Planned targeted command: ./gradlew :schedule-service:schedule-app:test :academic-service:academic-app:test. Not run; root sequences it after archive2PG.

## Runtime and limits

No build, tests, migrations, endpoint calls, or service scenario have run from this checkpoint. See runtime-evidence.md. Root must run targeted checks and combined API/Attendance acceptance; do not label the cross-service feature verified until those results and independent review are available.
