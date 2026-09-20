# r1 checks and evidence

Baseline revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
Frozen contract SHA-256:
`2E59CC3746F71965BF64ED1C54293012A64E5F4F8219BBE1E1B782D65EEEFABD`.
Environment: Windows PowerShell, nested worktree, bundled CPython 3.12.14.

| Criterion / check | Command | Exit | Evidence / result |
|---|---|---:|---|
| Owned Attendance + student-alert tests | `python -m pytest -q tests/test_attendance_request_grpc_client.py tests/test_excuse_decided.py` | 1 | System Python 3.12 reported `No module named pytest`; no tests collected. |
| Owned tests through uv | `uv run --offline pytest -q tests/test_attendance_request_grpc_client.py tests/test_excuse_decided.py` | 1 | `.venv` was created, but uv reported `Failed to spawn: pytest` / program not found. |
| Focused dispatcher propagation test | `uv run --offline pytest -q tests/test_event_dispatcher.py::test_dispatch_handler_exception_propagates` | 1 | Same missing pytest executable; no test execution. |
| Attempted offline dependency setup | `uv pip install --offline --python .venv\\Scripts\\python.exe -r requirements.txt -r requirements-test.txt` | 1 | uv cache access denied at `C:\\Users\\maksd\\AppData\\Local\\uv\\cache`; no repository dependency/config/lockfile change. External install is outside scope. |
| Python syntax | bundled CPython `-m py_compile bot/grpc_client/attendance_client.py tests/test_attendance_request_grpc_client.py bot/notifications/student_alerts.py tests/test_excuse_decided.py` | 0 | All four files compile. |
| Attendance timeout/static contract | bundled CPython AST assertions | 0 | Fetch call has `timeout=self._resolve_timeout_seconds` and metadata; focused tests assert timeout and propagation. |
| Academic propagation/static contract | bundled CPython AST assertion | 0 | `get_user_by_id` call is outside every `try` node; no catch remains at handler boundary. |
| Whitespace | `git diff --check -- <four assigned paths>` | 0 | Clean; Git emitted only LF→CRLF working-tree warnings. |
| Ruff | `Get-Command ruff -ErrorAction SilentlyContinue` | N/A | Ruff executable is not installed; contract says run if available. |
| Exact scope guard | `git status --short --untracked-files=all -- <four paths> .agent/student-notification-bot-c/r1` | 0 | Only four assigned paths plus owned r1 evidence appear in the path-limited result; unrelated pre-existing dirty state is untouched. |
| Wrong outer paths guard | `Test-Path <outer services/notification-bot/src/.../attendance_client.py>` and corresponding student-alert path | 0 | Both returned `False`; no outer primary files were created. |

Pytest checks are recorded as blocked by the runtime toolchain, not as PASS.
No Docker, Gradle, network, Rabbit, Telegram, Academic, Attendance, or other
external service was used.
