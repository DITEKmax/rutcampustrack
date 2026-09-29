# Frozen transfer atomicity decision and implementation record

Status: FROZEN by root 2026-09-29; Schedule and Academic source implemented, runtime pending. Canonical decision: .agent/orchestration-v2/evidence/2026-09-27-delivery/lesson-transfer-contract.md.

## Decision and authority

The physical lesson ID changes because V17 preserves physical snapshots and V20 protects occurrence pointer changes. Attendance uses Mongo and Academic uses separate SQL, so no cross-service transaction is claimed. Schedule owns operation/replay, source and target snapshots, lifecycle history, binding batches, pending gates, and outbox in one local SQL transaction. Academic applies explicit binding batches and writes receipts plus acknowledgement outbox locally. The separately owned Attendance participant remaps marks and attachments in its Mongo transaction, preserving document identity and provenance. Schedule reports COMPLETED only after exact acknowledgements from Attendance and every fixed Academic batch.

## Immutable payload and identity

- lesson.transfer.requested v1 carries operation/request IDs, actor and scope IDs, occurrence and expected revision, payload hash, source/target snapshots, fixed batch index/count, and at most 64 binding snapshots per batch. binding_id and homework_id use database BIGINT identity; homework_id is nullable. Each binding snapshot includes state and revision. Empty bindings produce one explicit empty Academic batch.
- Batch count, membership, and content hash are fixed in the Schedule transaction. Duplicate/reordered messages must not expand or double-count progress.
- lesson.transfer.participant.applied v1 carries exact operation/hash/source/target IDs, allowlisted participant/result, batch index, retryability, and optional error enum. Attendance uses batch_index=-1; Academic acknowledges its exact batch index. No exception strings are exposed.
- Error codes: TARGET_DATA_CONFLICT, SOURCE_STATE_CONFLICT, SCOPE_MISMATCH, INVALID_SNAPSHOT, DEPENDENCY_UNAVAILABLE, ARCHIVED_SEMESTER. Retryable dependency failure remains PENDING without a terminal receipt/ack; terminal conflict writes ERROR receipt/ack.

## HTTP contract and participant behavior

POST accepts a new or exact replayed request; changed payload on the same request key conflicts. PENDING is HTTP 202, COMPLETED is HTTP 200, and ERROR is surfaced by GET /schedule/lesson-transfers/{operationId}, not a false successful completion. Proto LessonResponse adds transfer_operation_id=26 and transfer_state=27.

Academic changes only the addressed active/non-archived bound homework slot, retaining ID, contents, attachments, publication identity/hash, and state. Unbound homework is untouched. A durable marker carries the slot change through pending materialization before publication. Archive-semester authorization remains a separate owner dependency; this implementation preserves ARCHIVED_SEMESTER.

## Constraints and current evidence

Use existing Schedule PostgreSQL replay/outbox/lifecycle patterns and Academic binding locks/receipt/outbox. No generic saga platform, compensation, production migration, or cross-database atomicity claim. No tests, build, migrations, endpoint checks, or runtime transfer scenarios have run from this source checkpoint. See checks.json and runtime-evidence.md; root sequences target checks after archive2PG.
