# Lesson transfer delivery contract — Schedule and Academic

## Goal

Deliver the Schedule operation and Academic bound-homework participant for JS-HEADMAN21: transfer a future lesson to a free assigned slot while retaining attendance through the independent Attendance participant, moving only explicitly bound homework, and never using cancel-and-create.

## Context/evidence

The frozen S3 contract is `.agent/orchestration-v2/evidence/2026-09-27-delivery/lesson-transfer-contract.md`; it is authoritative for this implementation. Root approved the immutable `lesson.transfer.requested` v1 / `lesson.transfer.participant.applied` v1 wire shape, fixed batches of at most 64, an explicit empty Academic batch, status GET `/schedule/lesson-transfers/{operationId}`, and `LessonResponse` fields 26 and 27. Physical lesson IDs must change under V17/V20 constraints; no cross-service SQL+Mongo transaction is claimed.

## Relevant scope

This worktree owns Schedule transfer API/service/writer/recovery, transfer lifecycle and binding gates, Schedule V21, Academic bound-homework participant/publication behavior and V38, plus the Schedule proto response fields. Attendance event/remap/coordinator, `proto/attendance.proto`, student projection/ranking, BFF, and Academic SemesterService/repository are separately owned and excluded.

## Required behavior

Schedule persists replay identity, immutable lesson and binding snapshots, bounded fixed batch payloads, pending lifecycle fences, and its outbox transactionally. Exact retries return the same operation; changed payload conflicts. The source is future/planned at the expected revision, the assigned target is free, and terminal conflicts do not partially move local data. Transfer status is observable as PENDING/COMPLETED/ERROR; only COMPLETED is HTTP 200, and an error is not reported as success.

Academic applies only the listed bindings. Each batch has durable content identity, receipt, and acknowledgement; duplicate/reordered delivery cannot count twice. Empty bindings still produce one explicit empty-batch receipt. Existing bound homework keeps identity, content, publication identity/state, and attachments while its lesson slot changes; unrelated homework is untouched. Pending publication has a durable marker applied before materialization. Archived homework remains terminal. Transferred lessons are not cancellations, and late close events must not erase attendance.

## Constraints

No full distributed transaction, generic saga framework, compensating cancellation, frontend/BFF change, production migration, push, or deploy. Do not alter files owned by the Attendance or archive participants. Archive-semester enforcement remains a separate owner dependency; preserve the allowlisted `ARCHIVED_SEMESTER` result.

## Existing patterns

Use existing Schedule PostgreSQL outbox/replay/lifecycle fences and V17 binding schema. Academic reuses `HomeworkBindingArchiveCoordinator` binding locks, durable outbox patterns, and `HomeworkPublicationPersistence` materialization flow.

## Acceptance criteria

For a valid future transfer, Schedule retains source/target/history and exact retry identity; Academic moves only bound non-archived homework, preserving its state and publication/content identity. Exact repeated batches are idempotent, target conflicts roll back participant-local changes, and no operation completes before exact participant receipts arrive. Attendance preservation is an integration criterion and remains pending until the separate participant is integrated and real checks pass.

## Verification

At this checkpoint, the source diff has only received `git diff --check`; tests, builds, migrations, endpoint checks, and runtime scenarios are NOT RUN pending root's heavy-check queue. Planned targeted command after archive2PG is complete: `./gradlew :schedule-service:schedule-app:test :academic-service:academic-app:test` from the repository root, followed by the root-owned targeted PG/API acceptance scenario. Record actual command, exit code, revision, environment, and evidence in `checks.json` and `runtime-evidence.md`; never convert skipped checks to PASS.

## Do not

Do not call this entire cross-service feature DONE from this scoped commit. Do not claim Attendance preservation, archive-semester enforcement, or end-to-end runtime acceptance until those separate owners and checks are integrated.
