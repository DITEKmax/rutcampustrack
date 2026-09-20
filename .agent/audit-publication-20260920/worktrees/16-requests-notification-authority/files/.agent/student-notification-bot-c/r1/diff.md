# Stable r1 product diff

The stable product scope is exactly the four paths in `packet.md`.

`bot/grpc_client/attendance_client.py`: `fetch_excuse_attachment` now passes
`timeout=self._resolve_timeout_seconds` alongside the existing secret metadata.
Identifier validation, request construction, and exception identity are
unchanged.

`tests/test_attendance_request_grpc_client.py`: the attachment failure test now
uses configured timeout `4.25` and asserts both metadata and timeout while
asserting the original `NOT_FOUND` gRPC error remains visible. The existing
resolve test already covers the shared non-default timeout `2.5`.

`bot/notifications/student_alerts.py`: the Academic lookup is awaited directly;
the broad catch/log/return block was removed so the original exception reaches
the dispatcher/consumer boundary. Existing malformed payload, unsupported
status/reason, missing user, and `telegram_id=0` returns remain. The late
checkin reason/status hunks visible against HEAD were pre-existing P1 bytes and
were preserved unchanged by r1.

`tests/test_excuse_decided.py`: added a focused async test that raises a
sentinel `RuntimeError` from Academic, asserts the same exception object
propagates, and verifies zero queued tasks and zero Telegram sends.

Untracked Attendance client/test files are the released P1 bundle in this
worktree; their pre-r1 hashes are recorded in `before-hashes.txt`. No generated
or configuration file was edited.
