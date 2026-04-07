"""
Tests for the consumer watchdog and prefetch_count configuration.
"""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

import bot.__main__ as main_module
from bot.__main__ import health_handler, run_with_watchdog
from bot.consumers.event_consumer import start_consumer

# ---------------------------------------------------------------------------
# Watchdog behaviour tests
# ---------------------------------------------------------------------------


async def test_watchdog_restarts_on_consumer_failure():
    """Watchdog restarts start_consumer after it raises an exception."""
    call_count = 0
    second_call_event = asyncio.Event()

    async def mock_start_consumer(url: str, dispatcher=None):
        nonlocal call_count
        call_count += 1
        if call_count == 1:
            raise RuntimeError("simulated RabbitMQ connection error")
        # Second call — signal and block until cancelled
        second_call_event.set()
        await asyncio.sleep(9999)

    with patch("bot.__main__.start_consumer", side_effect=mock_start_consumer):
        task = asyncio.create_task(run_with_watchdog("amqp://test/"))
        # Wait until the second call is in progress (restart happened)
        await asyncio.wait_for(second_call_event.wait(), timeout=10)
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    assert call_count >= 2, "start_consumer should have been called at least twice"


async def test_watchdog_propagates_cancelled_error():
    """CancelledError from start_consumer is re-raised, not swallowed."""

    async def mock_start_consumer(url: str, dispatcher=None):
        raise asyncio.CancelledError()

    with patch("bot.__main__.start_consumer", side_effect=mock_start_consumer):
        with pytest.raises(asyncio.CancelledError):
            await run_with_watchdog("amqp://test/")


async def test_watchdog_restarts_on_normal_exit():
    """When start_consumer returns normally (silent death), watchdog restarts it."""
    call_count = 0

    async def mock_start_consumer(url: str, dispatcher=None):
        nonlocal call_count
        call_count += 1
        if call_count == 1:
            return  # Normal exit — silent death
        # Second call — raise CancelledError to stop the watchdog cleanly
        raise asyncio.CancelledError()

    with patch("bot.__main__.start_consumer", side_effect=mock_start_consumer):
        with pytest.raises(asyncio.CancelledError):
            await run_with_watchdog("amqp://test/")

    assert call_count == 2, "start_consumer should have been restarted after normal exit"


# ---------------------------------------------------------------------------
# Health handler tests
# ---------------------------------------------------------------------------


async def test_health_up_during_reconnect():
    """Health returns 200 while watchdog task is running (even if consumer is failing)."""
    started = asyncio.Event()

    async def mock_start_consumer(url: str, dispatcher=None):
        started.set()
        await asyncio.sleep(9999)

    original_consumer_task = main_module._consumer_task
    original_bot_task = main_module._bot_task
    try:
        with patch("bot.__main__.start_consumer", side_effect=mock_start_consumer):
            main_module._consumer_task = asyncio.create_task(run_with_watchdog("amqp://test/"))
            # Mock _bot_task as a live task so health_handler sees both tasks alive
            main_module._bot_task = asyncio.create_task(asyncio.sleep(9999))
            await asyncio.wait_for(started.wait(), timeout=5)

            request = MagicMock()
            response = await health_handler(request)

        assert response.status == 200
        assert b"UP" in response.body
    finally:
        for task in [main_module._consumer_task, main_module._bot_task]:
            if task and not task.done():
                task.cancel()
                try:
                    await task
                except (asyncio.CancelledError, Exception):
                    pass
        main_module._consumer_task = original_consumer_task
        main_module._bot_task = original_bot_task


async def test_health_down_when_watchdog_dead():
    """Health returns 503 when watchdog task has completed (done() == True)."""
    from aiohttp.web_exceptions import HTTPServiceUnavailable

    original_task = main_module._consumer_task
    try:
        # Create a task that completes immediately
        async def noop():
            return

        main_module._consumer_task = asyncio.create_task(noop())
        # Wait for the task to complete
        await asyncio.sleep(0)
        await asyncio.sleep(0)

        assert main_module._consumer_task.done(), "Task should be done"

        request = MagicMock()
        with pytest.raises(HTTPServiceUnavailable) as exc_info:
            await health_handler(request)

        assert exc_info.value.status == 503
        assert b"watchdog_dead" in exc_info.value.body
    finally:
        main_module._consumer_task = original_task


# ---------------------------------------------------------------------------
# Prefetch count test
# ---------------------------------------------------------------------------


async def test_prefetch_count_set():
    """start_consumer calls channel.set_qos(prefetch_count=10) before queue declarations."""
    mock_connection = AsyncMock()
    mock_channel = AsyncMock()
    mock_connection.channel.return_value = mock_channel

    # Mock exchange, queue declarations to avoid further errors
    mock_exchange = AsyncMock()
    mock_dlq_exchange = AsyncMock()
    mock_dlq_queue = AsyncMock()
    mock_queue = AsyncMock()

    mock_channel.declare_exchange.side_effect = [mock_exchange, mock_dlq_exchange]
    mock_channel.declare_queue.side_effect = [mock_dlq_queue, mock_queue]

    # Mock queue.iterator() to raise CancelledError so start_consumer exits
    mock_queue_iter_cm = MagicMock()
    mock_queue_iter_cm.__aenter__ = AsyncMock(side_effect=asyncio.CancelledError())
    mock_queue_iter_cm.__aexit__ = AsyncMock(return_value=False)
    mock_queue.iterator = MagicMock(return_value=mock_queue_iter_cm)

    with patch("bot.consumers.event_consumer.aio_pika.connect_robust", return_value=mock_connection):
        with pytest.raises(asyncio.CancelledError):
            await start_consumer("amqp://test/")

    mock_channel.set_qos.assert_called_once_with(prefetch_count=10)
