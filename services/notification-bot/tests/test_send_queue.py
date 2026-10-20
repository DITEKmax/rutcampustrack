"""
Tests for the throttled Telegram send queue (token bucket + retry logic).
"""

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from prometheus_client import CollectorRegistry, generate_latest

from bot.observability import SendQueueCollector
from bot.services.send_queue import SendTask, TelegramSendQueue


class FakeRetryAfter(Exception):
    """Mock for aiogram.exceptions.TelegramRetryAfter."""

    def __init__(self, retry_after: int = 1) -> None:
        self.retry_after = retry_after


# ---------------------------------------------------------------------------
# Basic send tests
# ---------------------------------------------------------------------------


async def test_single_message_sent():
    """A single SendTask should cause coroutine_factory to be called exactly once."""
    factory = AsyncMock()
    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()

    await queue.put(
        SendTask(
            coroutine_factory=factory,
            user_id=10,
            chat_id=555,
            category="reminders",
            notification_type="LESSON_REMINDER",
        )
    )
    await queue._queue.join()

    factory.assert_called_once()
    assert queue._total_sent == 1
    assert queue._total_failed == 0
    registry = CollectorRegistry()
    registry.register(SendQueueCollector(queue))
    samples = generate_latest(registry).decode()
    assert "notification_bot_send_tasks_completed_total 1.0" in samples
    assert "notification_bot_send_tasks_exhausted_total 0.0" in samples
    assert "notification_bot_send_queue_depth 0.0" in samples
    assert "notification_bot_send_workers_alive 2.0" in samples
    assert "user_id=" not in samples and "chat_id=" not in samples

    await queue.shutdown()


async def test_queue_processes_in_order():
    """Three SendTasks must be processed in FIFO order."""
    results: list[int] = []

    async def make_factory(n: int):
        async def factory():
            results.append(n)

        return factory

    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()

    for i in range(3):
        factory = await make_factory(i)
        await queue.put(
            SendTask(
                coroutine_factory=factory,
                user_id=10,
                chat_id=555,
                category="reminders",
                notification_type="LESSON_REMINDER",
            )
        )

    await queue._queue.join()

    assert results == [0, 1, 2]

    await queue.shutdown()


# ---------------------------------------------------------------------------
# Rate-limit / token bucket
# ---------------------------------------------------------------------------


class ControlledTime:
    def __init__(self, monkeypatch):
        import bot.services.send_queue as module

        self.now = 0.0
        self.pending = []
        self.waiting = asyncio.Event()
        proxy = SimpleNamespace(**vars(asyncio))
        proxy.sleep = self.sleep
        monkeypatch.setattr(module, "asyncio", proxy)
        monkeypatch.setattr(module, "time", SimpleNamespace(monotonic=lambda: self.now))

    async def sleep(self, delay):
        future = asyncio.get_running_loop().create_future()
        self.pending.append((self.now + delay, future))
        self.waiting.set()
        await future

    async def advance(self, seconds):
        self.now += seconds
        for deadline, future in self.pending:
            if deadline <= self.now and not future.done():
                future.set_result(None)
        for _ in range(20):
            await asyncio.sleep(0)


async def test_rate_limit_token_bucket(monkeypatch):
    clock = ControlledTime(monkeypatch)
    attempts = []

    async def sender():
        attempts.append(clock.now)

    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            for _ in range(35):
                await queue.put(
                    SendTask(coroutine_factory=sender, user_id=10, chat_id=555, notification_type="LESSON_REMINDER")
                )
        await asyncio.wait_for(clock.waiting.wait(), 1)
        assert attempts == [0.0] * 30
        await clock.advance(0.02)
        assert attempts == [0.0] * 30
        for _ in range(5):
            await clock.advance(0.04)
        await asyncio.wait_for(batch.wait(), 1)
        assert len(attempts) == 35
        assert all(t >= 1 / 30 for t in attempts[30:])
    finally:
        await queue.shutdown()


# ---------------------------------------------------------------------------
# Retry logic
# ---------------------------------------------------------------------------


async def test_429_retry_after(monkeypatch):
    clock = ControlledTime(monkeypatch)
    attempts = []

    async def sender():
        attempts.append(clock.now)
        if len(attempts) == 1:
            raise FakeRetryAfter(retry_after=7)

    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await queue.put(
                SendTask(coroutine_factory=sender, user_id=10, chat_id=555, notification_type="LESSON_REMINDER")
            )
        await asyncio.wait_for(clock.waiting.wait(), 1)
        assert attempts == [0]
        await clock.advance(6.99)
        assert attempts == [0]
        await clock.advance(0.01)
        await asyncio.wait_for(batch.wait(), 1)
        assert attempts == [0, 7]
    finally:
        await queue.shutdown()


async def test_max_retries_skip():
    """Factory always raises a generic Exception.
    Factory called 4 times (initial + 3 retries). total_failed incremented. No crash."""
    call_count = 0

    async def factory():
        nonlocal call_count
        call_count += 1
        raise RuntimeError("permanent failure")

    with patch("asyncio.sleep", new_callable=AsyncMock):
        queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
        queue.start()
        await queue.put(
            SendTask(
                coroutine_factory=factory,
                user_id=10,
                chat_id=555,
                category="reminders",
                notification_type="LESSON_REMINDER",
            )
        )
        await queue._queue.join()

    # initial + 3 retries = 4 calls total
    assert call_count == 4
    assert queue._total_failed == 1
    assert queue._total_sent == 0
    registry = CollectorRegistry()
    registry.register(SendQueueCollector(queue))
    samples = generate_latest(registry).decode()
    assert "notification_bot_send_tasks_completed_total 0.0" in samples
    assert "notification_bot_send_tasks_exhausted_total 1.0" in samples

    await queue.shutdown()


async def test_failed_send_logs_exception_summary(monkeypatch):
    """Observe the production JSON formatter, including privacy of embedded stdlib fields."""
    import io
    import json
    import logging
    from uuid import uuid4

    from bot.observability import setup_observability

    marker = uuid4().hex
    root = logging.getLogger()
    previous_handlers, previous_level = root.handlers[:], root.level
    monkeypatch.setenv("OTEL_SDK_DISABLED", "true")
    setup_observability()
    stream = io.StringIO()
    root.handlers[0].setStream(stream)
    clock = ControlledTime(monkeypatch)

    async def factory():
        raise RuntimeError(f"token={marker} https://api.telegram.org/bot{marker}/sendMessage")

    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await queue.put(
                SendTask(
                    coroutine_factory=factory,
                    user_id=12345678,
                    chat_id=87654321,
                    category="reminders",
                    notification_type="LESSON_REMINDER",
                )
            )
        # Advance only the queue's retry sleeper; other asyncio tasks retain real scheduling.
        for _ in range(100):
            await clock.advance(100)
            if batch.futures and all(f.done() for f in batch.futures):
                break
        import pytest

        from bot.services.send_queue import TelegramDeliveryFailed

        with pytest.raises(TelegramDeliveryFailed):
            await asyncio.wait_for(batch.wait(), 1)
        registry = CollectorRegistry()
        registry.register(SendQueueCollector(queue))
        assert "notification_bot_send_tasks_exhausted_total 1.0" in generate_latest(registry).decode()
        rendered = stream.getvalue()
        logs = [json.loads(line) for line in rendered.splitlines()]
        exhausted = [item for item in logs if item.get("exception_type") == "RuntimeError"]
        assert exhausted
        assert "reminders" in exhausted[-1]["msg"]
        assert marker not in rendered
        assert "12345678" not in rendered and "87654321" not in rendered
    finally:
        await queue.shutdown()
        root.handlers = previous_handlers
        root.setLevel(previous_level)


# ---------------------------------------------------------------------------
# Shutdown logging
# ---------------------------------------------------------------------------


async def test_shutdown_logs_totals(caplog):
    """After sending 2 messages, shutdown() should log 'sent=2' and 'failed=0'."""
    import logging

    factory = AsyncMock()

    with caplog.at_level(logging.INFO, logger="bot.services.send_queue"):
        queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
        queue.start()

        await queue.put(
            SendTask(
                coroutine_factory=factory,
                user_id=10,
                chat_id=555,
                category="reminders",
                notification_type="LESSON_REMINDER",
            )
        )
        await queue.put(
            SendTask(
                coroutine_factory=factory,
                user_id=10,
                chat_id=555,
                category="reminders",
                notification_type="LESSON_REMINDER",
            )
        )
        await queue._queue.join()

        await queue.shutdown()

    assert "sent=2" in caplog.text
    assert "failed=0" in caplog.text
