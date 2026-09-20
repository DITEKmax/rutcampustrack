# Runtime and check evidence

Recorded 2026-09-08 at revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Python focused behavior

- Environment: Windows `win32`; Python `3.13.5` from
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/notification-bot/.venv/Scripts/python.exe`;
  pytest `9.0.3`; pytest-asyncio `1.3.0`.
- Command (exit code `0`):
  `python -m pytest -o addopts= tests\\test_headman_alerts.py tests\\test_attendance_request_grpc_client.py`
  from `services/notification-bot`.
- Output: `18 passed in 4.33s`.
- The fake bot/fake queue scenarios cover canonical projection, spoofed payload
  rejection, group mismatch, terminal request no-op, current headman filtering,
  lookup/membership/attachment failure propagation, invalid attachment
  descriptor failure before enqueue, gRPC metadata/finite resolve timeout and
  attachment failure behavior.

## Python mechanical check

- Command (exit code `0`):
  `python -m py_compile bot\\grpc_client\\attendance_client.py bot\\grpc_client\\attendance_pb2.py bot\\grpc_client\\attendance_pb2_grpc.py bot\\notifications\\headman_alerts.py tests\\test_attendance_request_grpc_client.py tests\\test_headman_alerts.py`.

## Java status

`AttendanceRequestBotGrpcServiceTest` includes exact generated Resolve/Fetch
method descriptors and public Student descriptor boundary assertions. These are
direct adapter/domain/interceptor mock tests; they do not start a Java gRPC
server. The initial authorized Gradle run failed at `compileTestJava` because
the new test used non-existent `GetStudentAttendanceSnapshot*` types; the
bounded correction and one authorized rerun then passed all 20 selected tests.
See `gradle-failure.md` and `gradle-success.md`.

## Runtime limits

No real Java gRPC server, Mongo, RabbitMQ, Academic service, Telegram API,
Docker/Testcontainers, deployment or production data was used. The two accepted
repair IT evidence artifacts remain byte-identical under
`imported-repair-evidence/` and are guarded by `import-guard.md`.
