# r1 manifest and scope guard

## Product/test paths

- `services/notification-bot/bot/grpc_client/attendance_client.py`
- `services/notification-bot/tests/test_attendance_request_grpc_client.py`
- `services/notification-bot/bot/notifications/student_alerts.py`
- `services/notification-bot/tests/test_excuse_decided.py`

## Evidence paths

- `.agent/student-notification-bot-c/r1/packet.md`
- `.agent/student-notification-bot-c/r1/before-hashes.txt`
- `.agent/student-notification-bot-c/r1/after-hashes.txt`
- `.agent/student-notification-bot-c/r1/checks.md`
- `.agent/student-notification-bot-c/r1/diff.md`
- `.agent/student-notification-bot-c/r1/manifest.md`
- `.agent/student-notification-bot-c/r1/runtime-evidence.md`
- `.agent/student-notification-bot-c/r1/summary.md`

The outer primary checkout and nonexistent `src/rutcampus_notification_bot`
paths are outside scope and were not created. Existing dirty files from other
roles, including P1 modifications in this nested worktree, were not reverted,
staged, committed, or reformatted.
