"""Tests for EventDispatcher routing and error handling."""

import logging
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.config import Settings
from bot.consumers.event_dispatcher import EventDispatcher


def _make_dispatcher(handlers_override=None, reminder_scheduler=None):
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    redis_client = MagicMock()
    config = MagicMock(spec=Settings)
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"
    otp_tracker = MagicMock()

    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
        otp_tracker=otp_tracker,
        reminder_scheduler=reminder_scheduler,
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


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_closed():
    """dispatch calls the handler registered for lesson.closed."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.closed": mock_handler})

    event = {"event_type": "lesson.closed", "payload": {"lesson_id": 101, "group_id": 5}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_attendance_marked():
    """dispatch calls the handler registered for attendance.marked."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"attendance.marked": mock_handler})

    event = {
        "event_type": "attendance.marked",
        "payload": {"lesson_id": 101, "user_id": 10, "group_id": 5, "status": "present"},
    }
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatcher_has_eight_event_types():
    """EventDispatcher._handlers contains all registered event types."""
    dispatcher = _make_dispatcher()
    expected_types = {
        "lesson.started",
        "lesson.cancelled",
        "homework.published",
        "homework.updated",
        "excuse.requested",
        # 59-06 / D-28: student notification on excuse decision
        "excuse.decided",
        "late_checkin.requested",
        "late_checkin.decided",
        "lesson.closed",
        "attendance.marked",
        # 58-07 / BUG-006-6
        "group.renamed",
        "group.archived",
        # OTP login flow cleanup
        "otp.verified",
        # 60-04: headman one-off lesson add/remove
        "lesson.one_off.created",
        "lesson.one_off.cancelled",
    }
    assert set(dispatcher._handlers.keys()) == expected_types


@pytest.mark.asyncio
async def test_dispatch_routes_otp_verified():
    """dispatch invokes the handler registered for otp.verified."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"otp.verified": mock_handler})

    event = {"event_type": "otp.verified", "payload": {"telegram_id": 42}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_group_renamed():
    """dispatch calls the handler registered for group.renamed."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"group.renamed": mock_handler})

    event = {"event_type": "group.renamed", "payload": {"group_id": 42}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_group_archived():
    """dispatch calls the handler registered for group.archived."""
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"group.archived": mock_handler})

    event = {"event_type": "group.archived", "payload": {"group_id": 77}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatcher_passes_reminder_scheduler_to_lesson_closed(monkeypatch):
    """lesson.closed handler receives reminder_scheduler from EventDispatcher."""
    captured_kwargs = {}

    async def fake_handle_lesson_closed(event, bot, academic_client, redis_client, reminder_scheduler):
        captured_kwargs["reminder_scheduler"] = reminder_scheduler

    monkeypatch.setattr(
        "bot.notifications.lesson_closed.handle_lesson_closed",
        fake_handle_lesson_closed,
        raising=False,
    )

    scheduler = MagicMock()
    dispatcher = _make_dispatcher(reminder_scheduler=scheduler)
    # Replace the handler directly to use our fake
    dispatcher._handlers["lesson.closed"] = lambda event: fake_handle_lesson_closed(
        event,
        bot=dispatcher._bot,
        academic_client=dispatcher._academic_client,
        redis_client=dispatcher._redis_client,
        reminder_scheduler=dispatcher._reminder_scheduler,
    )

    event = {"event_type": "lesson.closed", "payload": {"lesson_id": 101, "group_id": 5}}
    await dispatcher.dispatch(event)

    assert captured_kwargs["reminder_scheduler"] is scheduler


@pytest.mark.asyncio
async def test_lesson_started_calls_schedule_reminders_when_scheduler_set():
    """lesson.started dispatch calls reminder_scheduler.schedule_reminders after sending messages."""
    scheduler = MagicMock()
    scheduler.schedule_reminders = MagicMock()

    dispatcher = _make_dispatcher(reminder_scheduler=scheduler)

    # Patch the actual lesson_started handler to a no-op so we only test scheduling
    async def noop_lesson_started(*args, **kwargs):
        pass

    import bot.notifications.lesson_started as ls_module

    original = ls_module.handle_lesson_started
    ls_module.handle_lesson_started = noop_lesson_started

    try:
        event = {
            "event_type": "lesson.started",
            "payload": {
                "lesson_id": 101,
                "group_id": 5,
                "subject_id": 42,
                "start_time": "09:00",
                "end_time": "10:30",
            },
        }
        await dispatcher.dispatch(event)
    finally:
        ls_module.handle_lesson_started = original

    scheduler.schedule_reminders.assert_called_once_with(101, 5, "09:00", "10:30")


@pytest.mark.asyncio
async def test_lesson_started_does_not_call_schedule_reminders_when_no_scheduler():
    """lesson.started dispatch does NOT call schedule_reminders when reminder_scheduler is None."""
    dispatcher = _make_dispatcher(reminder_scheduler=None)

    # Patch lesson_started handler to a no-op
    async def noop_lesson_started(*args, **kwargs):
        pass

    import bot.notifications.lesson_started as ls_module

    original = ls_module.handle_lesson_started
    ls_module.handle_lesson_started = noop_lesson_started

    try:
        event = {
            "event_type": "lesson.started",
            "payload": {
                "lesson_id": 101,
                "group_id": 5,
                "subject_id": 42,
                "start_time": "09:00",
                "end_time": "10:30",
            },
        }
        # Should not raise even with no scheduler
        await dispatcher.dispatch(event)
    finally:
        ls_module.handle_lesson_started = original
