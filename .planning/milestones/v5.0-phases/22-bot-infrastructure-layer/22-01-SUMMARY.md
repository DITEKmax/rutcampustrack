---
plan: "01"
phase: "22-bot-infrastructure-layer"
status: complete
completed: "2026-04-05"
tasks_completed: 2
tests_added: 6
---

# Phase 22 Plan 01: Test infrastructure and consumer watchdog Summary

**One-liner:** pytest scaffold with fakeredis fixture + aio-pika watchdog restart loop and prefetch_count=10 for the notification-bot.

## What Was Built

### Task 1: Test infrastructure scaffold

**Created:**
- `services/notification-bot/pytest.ini` — `asyncio_mode = auto` so all async tests run without per-test decorator
- `services/notification-bot/requirements-test.txt` — pytest>=8.0, pytest-asyncio>=1.1.0, fakeredis[aioredis]>=2.34.0
- `services/notification-bot/tests/__init__.py` — empty package marker
- `services/notification-bot/tests/conftest.py` — `fake_redis` async fixture using `fakeredis.aioredis.FakeRedis(decode_responses=True)`, yielded and closed via `@pytest_asyncio.fixture`

### Task 2: Watchdog wrapper + prefetch_count + health adjustment

**Modified:**
- `services/notification-bot/bot/consumers/event_consumer.py` — added `await channel.set_qos(prefetch_count=10)` immediately after `channel = await connection.channel()` and before any exchange/queue declarations
- `services/notification-bot/bot/__main__.py` — replaced `run_consumer` + `_connection` global with `run_with_watchdog(rabbitmq_url)`:
  - `while True` loop wraps `start_consumer`
  - `asyncio.CancelledError` is re-raised (clean shutdown)
  - `Exception` logs WARNING with traceback, sleeps 5s, retries
  - Normal return logs WARNING (silent consumer death), sleeps 5s, retries
  - Health handler checks only `_consumer_task.done()`, returns `watchdog_dead` reason

**Created:**
- `services/notification-bot/tests/test_consumer_watchdog.py` — 6 unit tests (all pass)

## Test Results

```
collected 6 items

tests/test_consumer_watchdog.py::test_watchdog_restarts_on_consumer_failure PASSED
tests/test_consumer_watchdog.py::test_watchdog_propagates_cancelled_error    PASSED
tests/test_consumer_watchdog.py::test_watchdog_restarts_on_normal_exit       PASSED
tests/test_consumer_watchdog.py::test_health_up_during_reconnect              PASSED
tests/test_consumer_watchdog.py::test_health_down_when_watchdog_dead          PASSED
tests/test_consumer_watchdog.py::test_prefetch_count_set                      PASSED

6 passed in 10.45s
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing bot/ source files in git worktree**
- **Found during:** Task 1 — test collection would fail without importable bot package
- **Issue:** The worktree had only `.env.example` and `requirements.txt`; the `bot/` source tree was absent
- **Fix:** Created all bot source files (`__init__.py`, `config.py`, `consumers/`, `grpc_client/`, `handlers/`, `services/`) in the worktree matching the main branch content
- **Files modified:** 7 new files under `services/notification-bot/bot/`
- **Commit:** cdb5de9

**2. [Rule 3 - Blocking] aiohttp version conflict in requirements.txt**
- **Found during:** pip install — `aiogram==3.15.0` requires `aiohttp<3.11`, requirements.txt pins `aiohttp==3.11.11`
- **Issue:** Conflicting dependency prevented full requirements install
- **Fix:** Installed `aiohttp>=3.9` (resolved to 3.10.x) for tests only; aiogram not needed for this plan's tests. The requirements.txt conflict is a pre-existing issue deferred to a future plan.
- **Commit:** No code change — install workaround only

**3. [Rule 1 - Bug] queue.iterator() mock type error in test_prefetch_count_set**
- **Found during:** First test run (test 6)
- **Issue:** `mock_queue.iterator` was an `AsyncMock` returning a coroutine, but `async with queue.iterator()` requires a synchronous context manager return
- **Fix:** Changed to `MagicMock(return_value=mock_queue_iter_cm)` where `mock_queue_iter_cm` has `__aenter__` / `__aexit__` as `AsyncMock`
- **Files modified:** `tests/test_consumer_watchdog.py`
- **Commit:** cdb5de9 (fixed before final commit)

## Known Stubs

None — no UI-facing data, no placeholder text in created files.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundary changes introduced.

## Self-Check: PASSED

- `services/notification-bot/pytest.ini` — FOUND
- `services/notification-bot/requirements-test.txt` — FOUND
- `services/notification-bot/tests/__init__.py` — FOUND
- `services/notification-bot/tests/conftest.py` — FOUND
- `services/notification-bot/bot/consumers/event_consumer.py` — FOUND
- `services/notification-bot/bot/__main__.py` — FOUND
- `services/notification-bot/tests/test_consumer_watchdog.py` — FOUND
- commit cdb5de9 — FOUND (git log verified)
