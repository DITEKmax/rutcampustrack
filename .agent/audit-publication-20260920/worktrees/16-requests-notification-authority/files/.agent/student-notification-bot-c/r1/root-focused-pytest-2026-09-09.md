# Root focused pytest verification

Date: 2026-09-09. Revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

- Python executable: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/notification-bot/.venv/Scripts/python.exe`
- Working directory: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority/services/notification-bot`
- Command: `python -m pytest --override-ini=addopts= -q tests/test_attendance_request_grpc_client.py tests/test_excuse_decided.py tests/test_event_dispatcher.py::test_dispatch_handler_exception_propagates`
- Exit code: `0`
- Result: `13 passed in 15.00s`

The existing canonical project environment matches the pinned production dependency versions used by this service: aiogram 3.23.0, aio-pika 9.5.3, aiohttp 3.13.5, pydantic 2.9.2, pydantic-settings 2.6.1, grpcio 1.73.0, protobuf 6.31.0, redis 5.2.1. Pytest 9.0.3 and pytest-asyncio 1.3.0 satisfy the test requirement minimums.

`--override-ini=addopts=` disables the repository-wide coverage addopts only for this focused functional run. No coverage gate or live Rabbit/Telegram/gRPC service behavior is claimed. Product/test hashes remained unchanged after the run.