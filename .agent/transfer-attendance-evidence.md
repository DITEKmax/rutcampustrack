# Attendance lesson-transfer implementation evidence

## Scope

Implemented only in the assigned Attendance worktree on `codex/transfer-attendance-20260929`.
The frozen source contract is `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/lesson-transfer-contract.md`;
the canonical `RULES.md` SHA-256 supplied by root is
`F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
The separately authorized dependency commit `7ecbad76` adds Schedule transfer-state
fields. This implementation does not edit `proto/schedule.proto`, `proto/attendance.proto`,
student projection, BFF, Academic, Schedule, or ranking-owned files.

## Acceptance criteria addressed

- Validate the v1 Schedule event envelope, snapshots, operation identity, batches, and
  BIGINT homework binding IDs. Any valid batch performs the same whole-pair Attendance
  remap once per operation/hash; later batches return from the durable receipt.
- Serialize Attendance writes, transfer remap, close, cancellation and delete handlers on
  Mongo lesson fences. Writes recheck Schedule transfer state after taking the fence;
  completed transfer sources reject stale writes and late close/cancel are no-ops.
- Move source attendance documents in place by lesson/date/number while retaining document
  ID, status, mark provenance, excuse metadata and timestamps. Reject occupied target marks
  without overwriting either side.
- Rebind only operational references: PENDING late-check-in lesson fields, SUBMITTED excuse
  `lesson_ids`, and attachment `request_id` access keys. Preserve request IDs/status/decision
  metadata, immutable `lesson_snapshots`, attachment descriptors, bytes and timestamps.
- Commit the receipt and one `lesson.transfer.participant.applied` outbox event together;
  conflict receipts carry only allowlisted error codes. Schedule reads touching pending/error
  transfers fail explicitly.
- Added a real Mongo replica-set integration test for batch replay, mark/request/attachment
  preservation, the late-close and stale-writer fences, BIGINT binding parsing, and durable
  target-conflict receipt/ack behavior.

## Checks and runtime evidence

| Check | Result |
|---|---|
| `git diff --check` | Exit 0. PowerShell startup printed its existing PSReadLine console warning. |
| `.\gradlew.bat :services:attendance-service:attendance-app:test --tests "ru.rutcampustrack.attendance.event.LessonTransferParticipantIT"` | Not run: awaiting root's heavy-command lease after the currently queued PG/Schedule/Academic checks. This test uses Testcontainers MongoDB 7 replica set. |
| Full Attendance suite / Docker | Not run; outside the requested targeted verification. |
| Independent Sol review | Pending root coordination after a stable diff. |

No runtime success is claimed. No behavior was changed in response to shell WARN output;
the warning is a PowerShell profile/terminal capability message and is not linked to a
product request or reproduced product defect.

## Diff inventory

Changed Attendance writers/handlers: `AttendanceWritePortImpl`, `EventConsumer`,
`LessonEventService`, `ExcuseService`, `LateCheckinService`, `MarkingService`,
`StudentRequestService`, `PairWriteCoordinator`, `AttendanceAttachmentService`, and
`ScheduleGrpcClient`.

Added the transfer event parser, receipt/fence/snapshot documents, transactional participant
service, and `LessonTransferParticipantIT` under the attendance app.

## Limits

The targeted integration has not run, so compilation, Mongo transaction behavior and the
Testcontainers environment remain unverified. Independent review and the combined actual
Schedule/Academic/Attendance runtime scenario remain root-coordinated. Foreign untracked
`.agent` notes in the worktree were left untouched.
