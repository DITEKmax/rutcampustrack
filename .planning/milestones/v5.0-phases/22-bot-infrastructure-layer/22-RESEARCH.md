# Phase 22: Bot Infrastructure Layer - Research

**Researched:** 2026-04-05
**Domain:** Python asyncio — aio-pika consumer watchdog, grpc.aio persistent channel, redis.asyncio list operations, asyncio.Queue token bucket
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Watchdog Strategy**
- D-01: Wrapper coroutine pattern — watchdog wraps consumer task. On exit (exception or silent death), log error, sleep 5s, restart consumer. `__main__.py` loop: `while True: run consumer, on failure: log + sleep 5s + retry`.
- D-02: Retry forever — no max retry limit. Each attempt logged at WARNING level.
- D-03: Health endpoint stays UP during reconnection attempts — DOWN only if watchdog coroutine itself crashes.
- D-04: `prefetch_count=10` on aio-pika channel.
- D-05: INFO-level logging for every received event (log event_type).

**Throttled Send Queue**
- D-06: `asyncio.Queue` + token bucket pattern. Single worker coroutine drains queue. Callers `await queue.put(message)`. Worker paces and sends.
- D-07: Single global queue — one queue, one worker coroutine shared across all event handlers.
- D-08: Error handling: retry 3x with backoff (1s/2s/4s). On 429: respect `Retry-After`. After 3 failures: log error with user_id/chat_id and skip. No DLQ.
- D-09: Simple counters logged periodically — queue depth every 60s if non-empty, total sent/failed on shutdown.

**gRPC Client**
- D-10: Persistent `grpc.aio` channel created once at startup, reused for all calls. grpc.aio handles reconnection automatically.
- D-11: In-memory cache for `GetGroupMembers` results with 5-minute TTL. Dict keyed by `group_id`. Invalidated on `group.updated` RabbitMQ event.
- D-12: grpcio pinned at 1.73.0 (carried from Phase 20 — protobuf 5.x compatible).

**Redis Client**
- D-13: Use `redis.asyncio` (redis-py) with `redis[hiredis]` for C parser speedup.
- D-14: Default connection pool via `redis.asyncio.from_url()` (max 10 connections).
- D-15: Error handling: log and continue. If Redis is down, bot continues sending Telegram messages. No retry, no circuit breaker.

### Claude's Discretion
- Token bucket implementation details (stdlib vs third-party)
- Exact gRPC client wrapper class structure
- Redis helper class design (thin wrapper vs direct calls)
- Test structure and fixtures
- `__main__.py` signal handling and graceful shutdown

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BINFRA-01 | Bot uses gRPC client to resolve group members with telegram_ids for broadcasting | grpc.aio persistent channel + in-memory cache; GetGroupMembers returns GroupMembersResponse with repeated StudentInfo (telegram_id field confirmed in proto) |
| BINFRA-02 | Bot uses throttled send queue to respect Telegram rate limits (30 msg/sec) | asyncio.Queue + token bucket worker coroutine; Telegram 30 msg/sec global limit confirmed |
| BINFRA-03 | Bot uses aio-pika connect_robust with watchdog for reliable RabbitMQ consumption | aio-pika 9.x known issue: queue.iterator can silently die after RabbitMQ restart; wrapper coroutine restart pattern is the correct mitigation |
</phase_requirements>

---

## Summary

Phase 22 implements three infrastructure clients for the Telegram notification bot: an aio-pika consumer with external watchdog, a grpc.aio channel wrapper for Academic Service, and a redis.asyncio client for reminder message_id storage. All outgoing Telegram messages are routed through a single global asyncio.Queue with a token bucket worker coroutine.

The bot skeleton from Phase 20 is already solid: `connect_robust` is used, health server runs on aiohttp, config is Pydantic Settings. This phase enhances `event_consumer.py` (add `set_qos(prefetch_count=10)`, integrate watchdog), creates `bot/grpc_client/academic_client.py` (persistent channel + in-memory cache), creates `bot/services/redis_client.py` (thin async wrapper) and `bot/services/send_queue.py` (token bucket worker).

A critical pitfall is that aio-pika's `RobustConnection` does NOT automatically restart the `queue.iterator` after a RabbitMQ restart. Prefetched messages become stale ("Writer is None" RuntimeError). The fix confirmed in aio-pika 9.2.3+ is to wrap the iterator loop in a try/except and restart it — exactly the watchdog pattern decided in D-01. The current pinned version (9.5.3) handles this correctly when the watchdog restart loop is in place.

**Primary recommendation:** Implement watchdog as a bare `while True` wrapper with `asyncio.sleep(5)` on failure; use stdlib token bucket (no third-party library needed for 30 tokens/sec); pin `redis[hiredis]` at 5.x (latest redis-py is 7.4.0 — add to requirements.txt alongside existing deps).

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies to Phase 22? |
|-----------|----------------------|
| Contract-first Java modules | No — Python bot, not applicable |
| No Lombok in contract modules | No — Python bot |
| Enum UPPER_CASE / lowercase PG | No — no database in this phase |
| Flyway migrations, ddl-auto: validate | No — no Spring service |
| HATEOAS Level 3 REST | No — bot is Telegram, not REST |
| RFC 7807 Problem Details | No |
| Soft delete, TIMESTAMPTZ | No |
| Package naming ru.rutcampustrack.{service} | No — Python `bot.*` package used |
| `JAVA_HOME = C:\Users\maksd\.jdks\ms-21.0.9` | Irrelevant — Python project |
| `.\gradlew.bat build` | Irrelevant — Python project |
| After phase completion: update docs/phase-N-report.md | YES — create `docs/phase-22-report.md` |
| Read docs/phases-plan.md before starting | YES — done as part of existing context |

---

## Standard Stack

### Core (all versions confirmed against PyPI [VERIFIED: pypi.org])

| Library | Version (pinned) | Purpose | Why |
|---------|-----------------|---------|-----|
| aio-pika | 9.5.3 | RabbitMQ consumer (already in requirements.txt) | Already pinned, 9.2.3+ fixes queue.iterator restart issue |
| grpcio | 1.73.0 | gRPC async client (already in requirements.txt) | Pinned in D-12; 1.80.x requires protobuf 6.x, breaking |
| grpcio-tools | 1.73.0 | Proto code generation (already in requirements.txt) | Must match grpcio version |
| protobuf | 6.30.2 | Proto runtime (already in requirements.txt) | Compatible with grpcio 1.73.0 |
| redis | 5.x (add `redis[hiredis]`) | Async Redis client — per D-13 | redis-py 4.2+ has asyncio built-in; hiredis = C parser speedup |
| aiogram | 3.15.0 | Telegram API (already pinned) | Already in use; phase adds send queue around it |

**Note on redis version:** Latest redis-py is 7.4.0 [VERIFIED: pypi.org]. However, pinning to a major version is preferable. D-13 says `redis[hiredis]` — use `redis[hiredis]==5.2.1` to stay stable. The `[hiredis]` extra installs the C parser; the async API lives in `redis.asyncio` subpackage. [ASSUMED: 5.2.1 is the latest 5.x — should confirm with `pip install "redis[hiredis]" --dry-run`.]

### Test Dependencies (add to dev requirements or requirements-test.txt)

| Library | Version | Purpose |
|---------|---------|---------|
| pytest | 8.x | Test runner |
| pytest-asyncio | 1.1.0 | Async test support [VERIFIED: pypi.org] |
| fakeredis | 2.34.1 | In-process Redis mock with async support [VERIFIED: pypi.org] |
| unittest.mock | stdlib | Mock grpc stub and aiogram bot |

**Installation (additions to requirements.txt):**
```bash
# Add to requirements.txt:
redis[hiredis]==5.2.1   # or latest 5.x — verify with: pip show redis
```

**Test requirements file (new file):**
```bash
# requirements-test.txt
pytest>=8.0
pytest-asyncio==1.1.0
fakeredis[aioredis]>=2.34.0
```

**Version verification:**
```bash
# Run inside Docker container or virtualenv:
pip show redis grpcio aio-pika aiogram
```

---

## Architecture Patterns

### Recommended File Layout After Phase 22

```
services/notification-bot/
├── bot/
│   ├── __init__.py
│   ├── __main__.py           # Enhanced: watchdog loop wrapping start_consumer
│   ├── config.py             # No changes needed (redis_host/port, grpc_host/port already there)
│   ├── consumers/
│   │   └── event_consumer.py # Enhanced: add set_qos(prefetch_count=10)
│   ├── grpc_client/
│   │   ├── __init__.py
│   │   └── academic_client.py  # NEW: AcademicGrpcClient with channel + in-memory cache
│   ├── services/
│   │   ├── __init__.py
│   │   ├── redis_client.py     # NEW: ReminderRedisClient (thin async wrapper)
│   │   └── send_queue.py       # NEW: TelegramSendQueue (asyncio.Queue + token bucket)
│   └── handlers/
│       └── __init__.py         # Untouched in this phase
├── tests/
│   ├── __init__.py
│   ├── conftest.py             # NEW: shared fixtures
│   ├── test_consumer_watchdog.py  # NEW: watchdog retry logic
│   ├── test_academic_client.py    # NEW: gRPC client + cache
│   ├── test_redis_client.py       # NEW: RPUSH/LRANGE/EXPIRE with fakeredis
│   └── test_send_queue.py         # NEW: token bucket throughput test
├── requirements.txt            # Add redis[hiredis]
├── requirements-test.txt       # NEW: pytest + pytest-asyncio + fakeredis
├── pytest.ini                  # NEW: asyncio_mode = auto
└── Dockerfile
```

### Pattern 1: Watchdog Wrapper in `__main__.py`

**What:** `while True` loop that starts the consumer task. On any exception: log at WARNING, sleep 5s, retry. Never exits unless the watchdog itself crashes.

**When to use:** Long-running daemon that must self-heal after RabbitMQ restart.

```python
# Source: D-01, D-02 + aio-pika issue #563 fix pattern
# [CITED: github.com/mosquito/aio-pika/issues/563]

async def run_with_watchdog(rabbitmq_url: str) -> None:
    """Wrapper coroutine that restarts consumer on failure. Never exits normally."""
    global _connection
    attempt = 0
    while True:
        attempt += 1
        try:
            logger.info("Starting consumer (attempt %d)", attempt)
            _connection = await start_consumer(rabbitmq_url)
            # start_consumer only returns if consumer exits cleanly (should not happen)
            logger.warning("Consumer exited normally — restarting (attempt %d)", attempt + 1)
        except asyncio.CancelledError:
            logger.info("Watchdog cancelled — shutting down")
            raise  # propagate clean shutdown
        except Exception:
            logger.warning("Consumer failed (attempt %d) — retrying in 5s", attempt, exc_info=True)
        await asyncio.sleep(5)

async def main() -> None:
    global _consumer_task
    await run_health_server()
    _consumer_task = asyncio.create_task(run_with_watchdog(config.rabbitmq_url))
    logger.info("notification-bot started")
    try:
        await _consumer_task
    except asyncio.CancelledError:
        logger.info("Main task cancelled, shutting down")
```

**Health check adjustment (D-03):**

```python
async def health_handler(request: web.Request) -> web.Response:
    # Consumer task RUNNING = UP (even if currently reconnecting)
    # Consumer task DONE = DOWN (watchdog itself crashed)
    if _consumer_task is None or _consumer_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"watchdog_dead"}')
    return web.Response(text='{"status":"UP"}', content_type="application/json")
```

### Pattern 2: aio-pika set_qos in `event_consumer.py`

```python
# Source: official aio-pika docs + D-04
# [CITED: github.com/mosquito/aio-pika — Quick start example]

channel = await connection.channel()
await channel.set_qos(prefetch_count=10)  # D-04: limits in-flight messages
```

**Critical pitfall:** `set_qos` must be called BEFORE `queue.iterator()`. The RobustChannel stores the QoS and restores it automatically after reconnect.

### Pattern 3: gRPC Async Client with In-Memory Cache

```python
# Source: grpc.aio official docs [CITED: grpc.github.io/grpc/python/grpc_asyncio.html]
# D-10, D-11, D-12

import time
import grpc
import grpc.aio
from bot.grpc_client import academic_pb2, academic_pb2_grpc

class AcademicGrpcClient:
    _CACHE_TTL_SECONDS = 300  # D-11: 5-minute TTL

    def __init__(self, host: str, port: int) -> None:
        target = f"{host}:{port}"
        self._channel = grpc.aio.insecure_channel(target)  # D-10: persistent channel
        self._stub = academic_pb2_grpc.AcademicGrpcServiceStub(self._channel)
        # D-11: cache: {group_id: (timestamp_float, list[StudentInfo])}
        self._cache: dict[int, tuple[float, list]] = {}

    async def get_group_members(self, group_id: int) -> list:
        """Returns list of StudentInfo with telegram_id. Cached 5 min."""
        now = time.monotonic()
        if group_id in self._cache:
            ts, members = self._cache[group_id]
            if now - ts < self._CACHE_TTL_SECONDS:
                return members

        request = academic_pb2.GroupMembersRequest(group_id=group_id)
        response = await self._stub.GetGroupMembers(request)
        members = list(response.students)
        self._cache[group_id] = (now, members)
        return members

    def invalidate(self, group_id: int) -> None:
        """Called on group.updated event."""
        self._cache.pop(group_id, None)

    async def close(self) -> None:
        await self._channel.close()
```

**Important:** Do NOT use `async with grpc.aio.insecure_channel(...)` for a persistent client — that closes the channel on exit. Create channel once at startup, close on graceful shutdown.

### Pattern 4: Redis Reminder Client

```python
# Source: redis-py asyncio docs [CITED: redis-py 7.4.0 asyncio examples]
# D-13, D-14, D-15

import logging
import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

class ReminderRedisClient:
    def __init__(self, host: str, port: int, key_template: str, ttl: int) -> None:
        url = f"redis://{host}:{port}"
        # D-14: default connection pool, max_connections=10
        self._redis = aioredis.from_url(url, max_connections=10, decode_responses=True)
        self._key_template = key_template  # "reminder:msgs:{lesson_id}:{user_id}"
        self._ttl = ttl                    # 86400

    def _key(self, lesson_id: int, user_id: int) -> str:
        return self._key_template.format(lesson_id=lesson_id, user_id=user_id)

    async def add_message_id(self, lesson_id: int, user_id: int, message_id: int) -> None:
        """RPUSH + EXPIRE. D-15: log and continue on error."""
        key = self._key(lesson_id, user_id)
        try:
            await self._redis.rpush(key, message_id)
            await self._redis.expire(key, self._ttl)
        except Exception:
            logger.exception("Redis error storing reminder message_id for lesson=%d user=%d", lesson_id, user_id)

    async def get_message_ids(self, lesson_id: int, user_id: int) -> list[int]:
        """LRANGE 0 -1. Returns empty list on error (D-15)."""
        key = self._key(lesson_id, user_id)
        try:
            raw = await self._redis.lrange(key, 0, -1)
            return [int(v) for v in raw]
        except Exception:
            logger.exception("Redis error reading reminder message_ids for lesson=%d user=%d", lesson_id, user_id)
            return []

    async def delete_key(self, lesson_id: int, user_id: int) -> None:
        key = self._key(lesson_id, user_id)
        try:
            await self._redis.delete(key)
        except Exception:
            logger.exception("Redis error deleting reminder key for lesson=%d user=%d", lesson_id, user_id)

    async def close(self) -> None:
        await self._redis.aclose()
```

### Pattern 5: Token Bucket Send Queue

```python
# Source: D-06, D-07, D-08, D-09
# Token bucket: stdlib only, no external library needed

import asyncio
import logging
import time
from dataclasses import dataclass
from typing import Any, Awaitable, Callable

logger = logging.getLogger(__name__)

@dataclass
class SendTask:
    coroutine_factory: Callable[[], Awaitable[Any]]
    user_id: int | None = None

class TelegramSendQueue:
    _RATE = 30          # tokens per second (Telegram global limit)
    _MAX_TOKENS = 30    # burst ceiling
    _RETRY_DELAYS = [1, 2, 4]   # D-08

    def __init__(self) -> None:
        self._queue: asyncio.Queue[SendTask] = asyncio.Queue()
        self._tokens: float = self._MAX_TOKENS
        self._last_refill: float = time.monotonic()
        self._worker_task: asyncio.Task | None = None
        self._total_sent = 0
        self._total_failed = 0

    def start(self) -> None:
        """Start the worker coroutine. Call once at bot startup."""
        self._worker_task = asyncio.create_task(self._worker())

    async def put(self, task: SendTask) -> None:
        """Enqueue a send task. Callers await this."""
        await self._queue.put(task)

    async def _worker(self) -> None:
        while True:
            task = await self._queue.get()
            await self._consume_token()
            await self._send_with_retry(task)
            self._queue.task_done()
            # D-09: log queue depth periodically
            if not self._queue.empty():
                qsize = self._queue.qsize()
                if qsize % 10 == 0:  # rough approximation for 60s log
                    logger.info("Send queue depth: %d", qsize)

    async def _consume_token(self) -> None:
        """Block until a token is available (token bucket)."""
        while True:
            now = time.monotonic()
            elapsed = now - self._last_refill
            self._tokens = min(self._MAX_TOKENS, self._tokens + elapsed * self._RATE)
            self._last_refill = now
            if self._tokens >= 1.0:
                self._tokens -= 1.0
                return
            # Sleep for time to next token
            sleep_time = (1.0 - self._tokens) / self._RATE
            await asyncio.sleep(sleep_time)

    async def _send_with_retry(self, task: SendTask) -> None:
        for attempt, delay in enumerate(self._RETRY_DELAYS + [None], start=1):
            try:
                await task.coroutine_factory()
                self._total_sent += 1
                return
            except TelegramRetryAfter as e:  # aiogram exception
                logger.warning("Telegram 429 — retry after %ds", e.retry_after)
                await asyncio.sleep(e.retry_after)
            except Exception:
                if delay is None:
                    logger.error("Send failed after 3 attempts user_id=%s — skipping", task.user_id)
                    self._total_failed += 1
                    return
                logger.warning("Send attempt %d failed — retrying in %ds", attempt, delay)
                await asyncio.sleep(delay)

    async def shutdown(self) -> None:
        """D-09: log totals on shutdown."""
        if self._worker_task:
            self._worker_task.cancel()
        logger.info("SendQueue shutdown: sent=%d failed=%d", self._total_sent, self._total_failed)
```

**Note:** `TelegramRetryAfter` is `aiogram.exceptions.TelegramRetryAfter` in aiogram 3.x. [ASSUMED — verify import path in aiogram 3.15.]

### Anti-Patterns to Avoid

- **Never `asyncio.get_event_loop()` in tests**: Use `pytest-asyncio` with `asyncio_mode = auto`. The 1.x release removed the `event_loop` fixture entirely — do not attempt to override it.
- **Never rely on connect_robust alone for consumer restart**: `RobustConnection` reconnects the TCP connection and re-subscribes. However, prefetched messages from a dead channel become stale and cause `RuntimeError: Writer is None`. The watchdog loop discards the entire connection and creates a fresh one — safer than trying to recover.
- **Never use `async with channel:` for persistent gRPC**: The async context manager closes the channel on `__aexit__`. For a long-running bot, create the channel at startup and only close it during graceful shutdown.
- **Never call `await channel.set_qos()` after binding the iterator**: QoS must be set before consuming.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token bucket rate limiter | Custom sleep-loop without accounting for burst | Stdlib token bucket pattern (float tokens + monotonic time) | Any simpler sleep(1/30) approach can accumulate drift and allow bursts; token bucket handles burst capacity correctly |
| Redis list operations | String-based concatenation | `redis.asyncio` RPUSH/LRANGE | Atomic, TTL-safe, order-preserved |
| gRPC reconnection | Manual TCP polling | grpc.aio channel (C-core handles backoff) | gRPC C-core implements exponential backoff and IDLE/CONNECTING/READY state machine |
| Async test mocks | Real Redis in tests | `fakeredis[aioredis]` | Pure Python, no daemon required, supports full redis.asyncio API |
| gRPC stub mock | Real Academic Service in tests | `unittest.mock.AsyncMock` on stub | Simpler, no proto compilation in test environment |

---

## Common Pitfalls

### Pitfall 1: Silent Consumer Death After RabbitMQ Restart
**What goes wrong:** After RabbitMQ restarts, `connect_robust` restores the TCP connection and re-declares bindings. But `queue.iterator()` is a Python async generator still pointing at the old channel object. It silently stops yielding messages — no exception is raised in some versions.

**Why it happens:** aio-pika's RobustChannel re-opens channels but does not signal the running iterator to restart. Prefetched messages from the dead channel raise `RuntimeError: Writer is None` on ack.

**How to avoid:** The watchdog `while True` in `run_with_watchdog()` catches ALL exceptions including `RuntimeError`. When the iterator dies, the `start_consumer()` function raises (or the outer loop detects the silent exit), sleep 5s, and restart `start_consumer()` — creating a new connection, new channel, new iterator.

**Warning signs:** Consumer logs stop, queue in RabbitMQ Management UI shows 0 consumers, messages accumulate in queue.

**Fix confirmed in:** aio-pika 9.2.3+ [CITED: github.com/mosquito/aio-pika/issues/563] — still requires external watchdog; current pinned version 9.5.3 is safe.

### Pitfall 2: pytest-asyncio 1.x Breaking Changes
**What goes wrong:** If `asyncio_mode` is not configured in `pytest.ini`, pytest-asyncio 1.x throws `PytestUnraisableExceptionWarning` or fixture errors. The `event_loop` fixture is removed.

**Why it happens:** Version 1.0.0 (May 2025) removed the deprecated `event_loop` fixture entirely.

**How to avoid:** Add `pytest.ini` with:
```ini
[pytest]
asyncio_mode = auto
```
And use `@pytest_asyncio.fixture` (not `@pytest.fixture`) for async fixtures.

### Pitfall 3: grpc.aio Requires asyncio Event Loop at Import Time
**What goes wrong:** Importing `grpc.aio` or calling `grpc.aio.insecure_channel()` outside an async context in tests raises `RuntimeError: no running event loop`.

**Why it happens:** grpc.aio initializes its internal loop reference on first use.

**How to avoid:** Create `AcademicGrpcClient` instances inside `async def` functions or async fixtures — never at module level in test files.

### Pitfall 4: `decode_responses=True` Breaks Integer Message IDs
**What goes wrong:** If `redis.asyncio.from_url(..., decode_responses=True)` is used, LRANGE returns strings (`["123", "456"]`). If the consumer expects `int`, it will fail silently.

**How to avoid:** Explicitly cast: `[int(v) for v in raw]` in `get_message_ids()`. This is already accounted for in the Pattern 4 example above.

### Pitfall 5: Token Bucket Timer Drift on Slow Event Loop
**What goes wrong:** If the event loop is blocked (CPU-bound handler, slow network), `time.monotonic()` shows large `elapsed` which refills tokens to max burst. Next 30 messages go out instantly — violating the rate limit.

**How to avoid:** Cap refill: `self._tokens = min(self._MAX_TOKENS, ...)`. D-06 uses `MAX_TOKENS = 30` which is the burst ceiling.

### Pitfall 6: `requirements-test.txt` Not in Dockerfile
**What goes wrong:** Tests run in developer environment but fail in CI or Docker because test deps are not installed.

**How to avoid:** Either add test deps to a separate stage in Dockerfile, or install `requirements-test.txt` in a dedicated test step. Do NOT add pytest/fakeredis to the production `requirements.txt`.

---

## Code Examples

### Verified: aio-pika set_qos with prefetch_count=10
```python
# Source: aio-pika official docs Quick Start
# [CITED: github.com/mosquito/aio-pika — Quick start guide]
channel = await connection.channel()
await channel.set_qos(prefetch_count=10)
queue = await channel.declare_queue(QUEUE_NAME, durable=True, arguments={...})
async with queue.iterator() as queue_iter:
    async for message in queue_iter:
        async with message.process():
            # handle message
```

### Verified: redis.asyncio RPUSH / LRANGE / EXPIRE
```python
# Source: redis-py asyncio examples
# [CITED: redis.readthedocs.io/en/stable/examples/asyncio_examples.html]
import redis.asyncio as aioredis

r = aioredis.from_url("redis://localhost", decode_responses=True)
await r.rpush("mylist", "value1")
await r.expire("mylist", 86400)
items = await r.lrange("mylist", 0, -1)  # returns ["value1"]
await r.aclose()
```

### Verified: grpc.aio persistent channel pattern
```python
# Source: gRPC Python AsyncIO API docs
# [CITED: grpc.github.io/grpc/python/grpc_asyncio.html]
import grpc.aio

channel = grpc.aio.insecure_channel("host:port")
stub = MyServiceStub(channel)
response = await stub.MyMethod(request)
# ... use throughout lifetime of process ...
await channel.close()
```

### Verified: fakeredis async in tests
```python
# Source: fakeredis docs
# [CITED: pypi.org/project/fakeredis/ 2.34.1]
import fakeredis.aioredis

@pytest_asyncio.fixture
async def fake_redis():
    r = fakeredis.aioredis.FakeRedis(decode_responses=True)
    yield r
    await r.aclose()
```

### Verified: pytest-asyncio 1.x configuration
```ini
# pytest.ini — required for pytest-asyncio 1.x
[pytest]
asyncio_mode = auto
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `aioredis` (separate package) | `redis.asyncio` (built into redis-py 4.2+) | redis-py 4.2.0 (2022) | Use `import redis.asyncio as aioredis` — no separate aioredis install |
| `pytest-asyncio` with `event_loop` fixture | `pytest-asyncio` 1.x `asyncio_mode = auto` | May 2025 (v1.0.0) | `event_loop` fixture removed; use `loop_scope` parameter instead |
| grpcio-generated sync stubs | `grpc.aio` stubs (async/await) | grpcio 1.32+ | Generated stubs are the same; use `grpc.aio.insecure_channel()` instead of `grpc.insecure_channel()` |

**Deprecated/outdated:**
- `aioredis` (the standalone package): Replaced by `redis[asyncio]` / `redis.asyncio` since 2022. Do not install separately.
- `event_loop` fixture in pytest-asyncio: Removed in v1.0.0. Projects using it must migrate to `@pytest.mark.asyncio(loop_scope=...)`.

---

## Runtime State Inventory

> Phase 22 is NOT a rename/refactor phase. This section is not applicable.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Container testing | ✓ | 28.5.2 | — |
| Docker Compose | Infrastructure startup | ✓ | v2.40.3 | — |
| Python (host) | Running tests locally | ✗ | — | Run tests inside Docker (`docker compose run bot pytest`) |
| pip (host) | Installing test deps | ✗ | — | Use Docker |
| RabbitMQ container | Consumer integration test | ✓ (via docker compose) | See docker-compose.yml | — |
| Redis container | Redis integration test | ✓ (via docker compose) | See docker-compose.yml | fakeredis for unit tests |
| Academic Service gRPC | gRPC integration test | ✗ (not in scope) | — | `AsyncMock` stub in unit tests |

**Missing dependencies with no fallback:**
- None that block execution. All unit tests use fakes/mocks.

**Missing dependencies with fallback:**
- Python not installed on host: all test execution can happen inside Docker container.
- Academic Service not running: gRPC client unit tests use `AsyncMock` — no live service needed.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | pytest + pytest-asyncio 1.1.0 |
| Config file | `services/notification-bot/pytest.ini` (Wave 0 gap) |
| Quick run command | `docker compose run --rm notification-bot python -m pytest tests/ -x -q` |
| Full suite command | `docker compose run --rm notification-bot python -m pytest tests/ -v` |

**Alternative (if Python available on host):**
```bash
cd services/notification-bot
pip install -r requirements-test.txt
pytest tests/ -v
```

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BINFRA-03 | Watchdog restarts consumer after failure | unit | `pytest tests/test_consumer_watchdog.py -x` | Wave 0 gap |
| BINFRA-03 | Watchdog logs WARNING on retry | unit | `pytest tests/test_consumer_watchdog.py::test_watchdog_logs_warning -x` | Wave 0 gap |
| BINFRA-01 | GetGroupMembers returns telegram_ids | unit | `pytest tests/test_academic_client.py -x` | Wave 0 gap |
| BINFRA-01 | 5-min cache avoids repeated gRPC calls | unit | `pytest tests/test_academic_client.py::test_cache_hit -x` | Wave 0 gap |
| BINFRA-01 | Cache invalidated on group.updated | unit | `pytest tests/test_academic_client.py::test_cache_invalidate -x` | Wave 0 gap |
| BINFRA-02 | 50 simultaneous sends <= 30/sec | unit | `pytest tests/test_send_queue.py::test_throughput -x` | Wave 0 gap |
| BINFRA-02 | 429 response respects Retry-After | unit | `pytest tests/test_send_queue.py::test_429_retry_after -x` | Wave 0 gap |
| BINFRA-02 | After 3 failures: skips, logs error | unit | `pytest tests/test_send_queue.py::test_max_retries -x` | Wave 0 gap |
| Phase SC-3 | RPUSH then LRANGE returns all ids in order | unit | `pytest tests/test_redis_client.py::test_add_and_get_order -x` | Wave 0 gap |

### Sampling Rate
- **Per task commit:** `pytest tests/test_{module_under_change}.py -x -q`
- **Per wave merge:** `pytest tests/ -v`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `services/notification-bot/pytest.ini` — asyncio_mode = auto
- [ ] `services/notification-bot/requirements-test.txt` — pytest, pytest-asyncio, fakeredis
- [ ] `services/notification-bot/tests/__init__.py`
- [ ] `services/notification-bot/tests/conftest.py` — shared fixtures (fake_redis, mock_grpc_stub, mock_bot)
- [ ] `services/notification-bot/tests/test_consumer_watchdog.py`
- [ ] `services/notification-bot/tests/test_academic_client.py`
- [ ] `services/notification-bot/tests/test_redis_client.py`
- [ ] `services/notification-bot/tests/test_send_queue.py`

---

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No — bot-to-service calls only | — |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Minimal — event payload JSON decode | json.loads + key presence check (already in event_consumer.py) |
| V6 Cryptography | No — Redis and gRPC on internal Docker network (no TLS) | — |

**Known threat patterns for this stack:**

| Pattern | STRIDE | Mitigation |
|---------|--------|-----------|
| Malformed RabbitMQ event payload | Tampering | `try/except json.JSONDecodeError` already in event_consumer.py; nack to DLQ |
| Redis key collision | Spoofing | Key namespace `reminder:msgs:{lesson_id}:{user_id}` is specific; TTL=86400 limits blast radius |
| gRPC call with unchecked group_id | Tampering | gRPC stub raises `grpc.RpcError` on invalid id — catch and log in client |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `redis[hiredis]==5.2.1` is the latest stable 5.x release | Standard Stack | Pin to wrong minor version; can resolve with `pip install "redis[hiredis]>=5,<6"` |
| A2 | `aiogram.exceptions.TelegramRetryAfter` is the correct import path in aiogram 3.15.0 | Code Examples (send_queue) | `ImportError` at runtime; fix: `from aiogram.exceptions import TelegramRetryAfter` — verify in aiogram 3.15 changelog |
| A3 | `fakeredis.aioredis.FakeRedis` is the correct import path in fakeredis 2.34.x | Code Examples (tests) | Import error; alternative: `import fakeredis; fakeredis.FakeRedis(server=fakeredis.FakeServer(), version=(7,), decode_responses=True)` |

---

## Open Questions

1. **Proto-generated Python stubs location**
   - What we know: `proto/academic.proto` is the source. grpcio-tools is in requirements.txt.
   - What's unclear: Are `academic_pb2.py` and `academic_pb2_grpc.py` already generated in `bot/grpc_client/`? Phase 20 skeleton only has an empty `__init__.py`.
   - Recommendation: Wave 0 task should run `python -m grpc_tools.protoc` to generate stubs into `bot/grpc_client/`. Document the exact command in PLAN.

2. **Throughput test timing accuracy**
   - What we know: Testing that 50 messages at token bucket ≤ 30/sec requires ~1.67 seconds to complete.
   - What's unclear: asyncio timing in unit tests can be imprecise. A wall-clock assertion `1.5s < elapsed < 3s` is fragile in CI.
   - Recommendation: Test that the token bucket never allows more than 30 tokens consumed within any 1-second window (check `_tokens` state), rather than testing wall-clock elapsed time.

3. **Send queue integration with aiogram Bot object**
   - What we know: D-06 says callers `await queue.put(message)`. But aiogram `Bot.send_message()` is an awaitable coroutine, not a pre-built message.
   - What's unclear: Should `SendTask.coroutine_factory` be a `Callable[[], Coroutine]` (lambda wrapping `bot.send_message(...)`)? Or should the queue accept `(chat_id, text)` pairs?
   - Recommendation: Use `coroutine_factory: Callable[[], Awaitable[Any]]` — callers pass `lambda: bot.send_message(chat_id=..., text=...)`. This keeps the queue decoupled from aiogram internals.

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: pypi.org/project/aio-pika] — confirmed version 9.5.3 already in requirements.txt; 9.6.2 is latest but we keep current pin
- [VERIFIED: pypi.org/project/grpcio] — latest is 1.80.0; pinned at 1.73.0 per D-12
- [VERIFIED: pypi.org/project/redis] — latest is 7.4.0; `redis.asyncio` subpackage available since 4.2+
- [VERIFIED: pypi.org/project/pytest-asyncio] — latest is 1.1.0
- [VERIFIED: pypi.org/project/fakeredis] — latest is 2.34.1, asyncio support confirmed
- [CITED: grpc.github.io/grpc/python/grpc_asyncio.html] — gRPC AsyncIO API: persistent channel, stub creation, await pattern
- [CITED: github.com/mosquito/aio-pika/issues/563] — Consumer doesn't resume after connection stuck; fix confirmed in 9.2.3+
- [CITED: proto/academic.proto] — `GetGroupMembers` returns `GroupMembersResponse { repeated StudentInfo students }` where `StudentInfo.telegram_id` is int64 field 4 [VERIFIED: read file]

### Secondary (MEDIUM confidence)
- [WebSearch verified] — Telegram 30 msg/sec global rate limit is widely documented, consistent across sources
- [WebSearch verified] — aio-pika `set_qos(prefetch_count=N)` pattern from official Quick Start confirmed

### Tertiary (LOW confidence)
- Exact import path for `aiogram.exceptions.TelegramRetryAfter` — not verified in session (marked A2)
- `fakeredis.aioredis.FakeRedis` exact import path in 2.34.x — not verified in session (marked A3)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions verified against PyPI registries
- Architecture: HIGH — patterns derived from locked decisions (CONTEXT.md) + verified library APIs
- Pitfalls: HIGH for aio-pika (verified via GitHub issues); MEDIUM for pytest-asyncio (single source, official docs confirm)

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable libraries; grpcio pin is a project-level decision, not library lifecycle)
