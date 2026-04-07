"""
Tests for the throttled Telegram send queue (token bucket + retry logic).
"""

import asyncio
from unittest.mock import AsyncMock, patch

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
    queue = TelegramSendQueue()
    queue.start()

    await queue.put(SendTask(coroutine_factory=factory))
    await queue._queue.join()

    factory.assert_called_once()
    assert queue._total_sent == 1
    assert queue._total_failed == 0

    await queue.shutdown()


async def test_queue_processes_in_order():
    """Three SendTasks must be processed in FIFO order."""
    results: list[int] = []

    async def make_factory(n: int):
        async def factory():
            results.append(n)

        return factory

    queue = TelegramSendQueue()
    queue.start()

    for i in range(3):
        factory = await make_factory(i)
        await queue.put(SendTask(coroutine_factory=factory))

    await queue._queue.join()

    assert results == [0, 1, 2]

    await queue.shutdown()


# ---------------------------------------------------------------------------
# Rate-limit / token bucket
# ---------------------------------------------------------------------------


async def test_rate_limit_token_bucket():
    """Submit 35 messages at once. Token bucket caps burst at 30; remaining 5 need refill.
    All 35 must eventually be sent."""
    with patch("asyncio.sleep", new_callable=AsyncMock):
        queue = TelegramSendQueue()
        # Pre-fill bucket to maximum burst
        queue._tokens = queue._MAX_TOKENS

        factory = AsyncMock()
        queue.start()

        await asyncio.gather(*[queue.put(SendTask(coroutine_factory=factory)) for _ in range(35)])
        await queue._queue.join()

    assert queue._total_sent == 35
    assert queue._MAX_TOKENS == 30

    await queue.shutdown()


# ---------------------------------------------------------------------------
# Retry logic
# ---------------------------------------------------------------------------


async def test_429_retry_after():
    """Factory raises FakeRetryAfter on first call, succeeds on second.
    Factory must be called exactly twice."""
    call_count = 0

    async def factory():
        nonlocal call_count
        call_count += 1
        if call_count == 1:
            raise FakeRetryAfter(retry_after=1)

    with patch("asyncio.sleep", new_callable=AsyncMock):
        queue = TelegramSendQueue()
        queue.start()
        await queue.put(SendTask(coroutine_factory=factory))
        await queue._queue.join()

    assert call_count == 2
    assert queue._total_sent == 1
    assert queue._total_failed == 0

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
        queue = TelegramSendQueue()
        queue.start()
        await queue.put(SendTask(coroutine_factory=factory))
        await queue._queue.join()

    # initial + 3 retries = 4 calls total
    assert call_count == 4
    assert queue._total_failed == 1
    assert queue._total_sent == 0

    await queue.shutdown()


# ---------------------------------------------------------------------------
# Shutdown logging
# ---------------------------------------------------------------------------


async def test_shutdown_logs_totals(caplog):
    """After sending 2 messages, shutdown() should log 'sent=2' and 'failed=0'."""
    import logging

    factory = AsyncMock()

    with caplog.at_level(logging.INFO, logger="bot.services.send_queue"):
        queue = TelegramSendQueue()
        queue.start()

        await queue.put(SendTask(coroutine_factory=factory))
        await queue.put(SendTask(coroutine_factory=factory))
        await queue._queue.join()

        await queue.shutdown()

    assert "sent=2" in caplog.text
    assert "failed=0" in caplog.text
