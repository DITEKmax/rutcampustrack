# Notification bot request safety — r1 implementation packet

## Goal

Close the two notification-bot defects in the frozen contract: give both
Attendance request RPCs the validated finite deadline, and let an Academic
`get_user_by_id` failure escape the student alert handler so the existing
consumer boundary can reject/dead-letter the message.

## Context / evidence

The frozen contract is
`.agent/student-gateway-c/contracts/notification-bot-timeout-propagation-2026-09-08.md`
with SHA-256
`2E59CC3746F71965BF64ED1C54293012A64E5F4F8219BBE1E1B782D65EEEFABD`.
The current checkout initially contained an owner-resolved path mismatch: the
contract's baseline hashes and P1 files exist in the nested
`requests-notification-authority` worktree with the old `bot/...` layout, while
the initial task packet named nonexistent outer `src/...` paths. Root explicitly
corrected the scope before any product write; this packet records that delta.

## Relevant scope

Worktree: `C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-notification-authority`

Writable product/test paths, exactly:

- `services/notification-bot/bot/grpc_client/attendance_client.py`
- `services/notification-bot/tests/test_attendance_request_grpc_client.py`
- `services/notification-bot/bot/notifications/student_alerts.py`
- `services/notification-bot/tests/test_excuse_decided.py`

Evidence is limited to `.agent/student-notification-bot-c/r1/**` in this
worktree. Outer primary worktree and all other paths are preserved.

## Required behavior

- Reuse `AttendanceRequestGrpcClient._resolve_timeout_seconds` for both
  `ResolveRequestNotification` and `FetchExcuseAttachment` calls.
- Preserve identifier validation, secret metadata, and original gRPC errors.
- Propagate Academic lookup exceptions from `handle_student_alert` before any
  queue or Telegram work.
- Preserve missing user, `telegram_id=0`, malformed payload, and unsupported
  status/reason skips.

## Constraints

No new timeout setting, generic refactor, consumer topology/retry/DLQ change,
generated-file/config/dependency/lockfile change, external service call, or
outer-primary write. Existing P1 bytes in the nested worktree remain intact.

## Existing patterns

`resolve_request_notification` already passes the shared finite timeout and
lets `grpc.aio.AioRpcError` propagate. Student alert tests capture
`SendTask.coroutine_factory`; dispatcher tests cover handler exception
propagation at the consumer boundary.

## Acceptance criteria

Both Attendance RPCs receive the configured finite deadline and metadata;
original RPC exceptions remain observable. Academic lookup failure escapes with
zero queued/sent work. Intentional skip behavior remains unchanged. Stable diff
contains only the four assigned product/test files; evidence remains under the
owned r1 directory.

## Verification

Run the two owned test files, the focused dispatcher exception test, scoped
`py_compile`, Ruff if available, and exact scope/hash guards. Record command,
exit code, revision, environment, and evidence. Product runtime is not run:
the contract forbids real Rabbit/Telegram/Academic/Attendance calls and unit
tests are the applicable runtime boundary.

## Do not

Do not redesign paths, alter consumer acknowledgement semantics, swallow or
translate Academic exceptions, add a second SLA/configuration key, edit P1 or
unrelated dirty work, or claim live delivery/retry evidence.

## Owner correction / pre-write state

Root correction received 2026-09-08: use this nested worktree and old-layout
paths above. Before this packet write, no product/test path was modified by r1.
The nested worktree already had unrelated/P1 dirty state; target working-tree
hashes matched the contract baseline and are recorded in `before-hashes.txt`.
