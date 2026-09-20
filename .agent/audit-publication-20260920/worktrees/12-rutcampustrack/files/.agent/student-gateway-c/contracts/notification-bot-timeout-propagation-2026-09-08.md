# Notification bot request safety — frozen four-file contract

Date: 2026-09-08. Risk: S3. This contract starts only after the accepted P1
attachment bundle released its sole writer. Revision baseline:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Goal

Close two notification-bot defects: `FetchExcuseAttachment` has no finite gRPC
deadline, and transient Academic lookup failures are swallowed so the Rabbit
message is acknowledged without a student notification.

## Context / evidence

`attendance_client.py:30-40` validates identifiers and sends the internal secret
but omits `timeout`; its existing test explicitly asserts this absence.
`student_alerts.py:90-99` catches every exception from `get_user_by_id`, logs and
returns. `event_dispatcher.py:210/236` directly awaits the handler, while
`event_consumer.py:71-84` declares a dead-letter exchange and processes messages
with `requeue=False`; an uncaught handler error therefore reaches the existing
DLQ path instead of being silently acknowledged.

## Relevant scope

Exactly four writable Python files in
`.agent/worktrees/requests-notification-authority`:

- `services/notification-bot/bot/grpc_client/attendance_client.py` — baseline
  SHA-256 `8BF8A0BB9BBDD7A4C122F7406592052BC0F6088CB7920E92884256648C04C37A`.
- `services/notification-bot/tests/test_attendance_request_grpc_client.py` —
  baseline SHA-256
  `B79A43C0C56631FB439C166BD20F008B9A4FDEC84466306D8E5B456337318573`.
- `services/notification-bot/bot/notifications/student_alerts.py` — baseline
  SHA-256 `E6DDE170C3D9E60EE826234CADC31C12AEB8984FB4897614965F2BE2BC8FF15C`.
- `services/notification-bot/tests/test_excuse_decided.py` — baseline SHA-256
  `F5AC10B6659FE4C3C88699BCA70E23351EA12BBE5F39EE82ED42338E935578C2`.

Evidence belongs below a new task-owned directory under
`.agent/student-requests-authority-c/bot-r1/`. Accepted P1 Java/test hashes and
all other files remain frozen.

## Required behavior

- Reuse the already validated positive finite `resolve_timeout_seconds` for both
  Attendance request RPCs, including a configured non-default value. A separate
  fetch SLA or configuration key is not needed.
- `fetch_excuse_attachment` must preserve identifier validation, authentication
  metadata and the original gRPC exception while supplying the finite timeout.
- Exceptions from Academic `get_user_by_id` must propagate through
  `handle_student_alert` so the consumer can reject/dead-letter the message.
  Nothing may be enqueued or sent after that failure.
- A missing user result or `telegram_id=0`, malformed payload, and unsupported
  status/reason remain intentional nonretryable skips.

## Constraints

Do not change consumer topology, retry/DLQ policy, dependency injection,
configuration, proto, generated files, Java, accepted P1 code, UI or BFF scope.
Do not contact real Telegram, Rabbit, Academic or Attendance services.

## Existing patterns

`resolve_request_notification` already passes the shared finite timeout and
propagates `grpc.aio.AioRpcError`. `test_event_dispatcher.py` already proves that
handler exceptions propagate to the consumer boundary. Existing async unit
tests use `AsyncMock` and inspect RPC keyword arguments and captured send tasks.

## Acceptance criteria

Both Attendance request RPCs use the configured finite deadline; original gRPC
errors and secret metadata remain visible. Academic lookup failure escapes the
student handler with zero queued/sent work. All intentional skip behavior stays
unchanged. The stable diff contains only the four files above.

## Verification

Run only the two owned Python test files plus the existing focused
`test_event_dispatcher.py::test_dispatch_handler_exception_propagates`, then
scoped `py_compile` and Ruff if available. Record commands, environment, exit
codes, counts, hashes and a scope guard. After stable freeze, obtain a fresh
independent Sol/high review. Live Rabbit/Telegram behavior remains an explicit
open runtime limit.

## Do not

Do not broaden into generic timeout refactoring, add a second timeout setting,
swallow or translate the Academic exception, change acknowledgement semantics,
edit generated clients, reuse the P1 writer before its release, or claim live
delivery/retry evidence from unit tests.
