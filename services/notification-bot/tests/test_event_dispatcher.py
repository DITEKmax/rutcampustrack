"""Tests for EventDispatcher routing and error handling."""
import logging
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.consumers.event_dispatcher import EventDispatcher
from bot.config import Settings


def _make_dispatcher(handlers_override=None):
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    redis_client = MagicMock()
    config = MagicMock(spec=Settings)
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )
    if handlers_override:
        dispatcher._handlers.update(handlers_override)
    return dispatcher


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_started():
    """dispatch calls the handler registered for lesson.started."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.started": mock_handler})

    event = {"event_type": "lesson.started", "payload": {"lesson_id": 1}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_cancelled():
    """dispatch calls the handler registered for lesson.cancelled."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.cancelled": mock_handler})

    event = {"event_type": "lesson.cancelled", "payload": {"lesson_id": 2}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_unknown_event_type_does_not_raise():
    """dispatch silently ignores unknown event types — no exception raised."""
    dispatcher = _make_dispatcher()

    event = {"event_type": "totally.unknown.event", "payload": {}}
    # Must not raise
    await dispatcher.dispatch(event)


@pytest.mark.asyncio
async def test_dispatch_unknown_event_type_logs_debug(caplog):
    """dispatch logs unknown event_type at DEBUG level."""
    dispatcher = _make_dispatcher()

    event = {"event_type": "unknown.thing", "payload": {}}
    with caplog.at_level(logging.DEBUG, logger="bot.consumers.event_dispatcher"):
        await dispatcher.dispatch(event)

    assert any("unknown.thing" in record.message for record in caplog.records)


@pytest.mark.asyncio
async def test_dispatch_handler_exception_is_caught():
    """If handler raises, dispatch catches and does NOT re-raise (ack safety)."""
    failing_handler = AsyncMock(side_effect=RuntimeError("boom"))
    dispatcher = _make_dispatcher(handlers_override={"lesson.started": failing_handler})

    event = {"event_type": "lesson.started", "payload": {}}
    # Must not raise — RabbitMQ ack safety
    await dispatcher.dispatch(event)

    failing_handler.assert_called_once()


@pytest.mark.asyncio
async def test_dispatch_handler_exception_is_logged(caplog):
    """Handler exception is logged via logger.exception."""
    failing_handler = AsyncMock(side_effect=RuntimeError("handler error"))
    dispatcher = _make_dispatcher(handlers_override={"lesson.started": failing_handler})

    event = {"event_type": "lesson.started", "payload": {}}
    with caplog.at_level(logging.ERROR, logger="bot.consumers.event_dispatcher"):
        await dispatcher.dispatch(event)

    assert any("lesson.started" in record.message for record in caplog.records)


def test_config_mini_app_url_has_default():
    """Settings.mini_app_url has a sensible default value."""
    settings = Settings()
    assert settings.mini_app_url == "https://t.me/RutTrackBot/checkin"
