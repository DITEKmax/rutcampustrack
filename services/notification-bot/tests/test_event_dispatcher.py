"""Tests for EventDispatcher routing and error handling."""

import logging
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.config import Settings
from bot.consumers.event_dispatcher import EventDispatcher


def _make_dispatcher(handlers_override=None):
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    redis_client = MagicMock()
    config = MagicMock(spec=Settings)
    config.mini_app_url = "https://t.me/ruttrack_bot/ruttrack"
    config.mini_app_web_url = "https://ruttrack.site/mini-app/"
    otp_tracker = MagicMock()

    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
        otp_tracker=otp_tracker,
    )
    if handlers_override:
        dispatcher._handlers.update(handlers_override)
    return dispatcher


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_started():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.started": mock_handler})

    event = {"event_type": "lesson.started", "payload": {"lesson_id": 1}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_reminder():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.reminder": mock_handler})

    event = {"event_type": "lesson.reminder", "payload": {"lesson_id": 7, "group_id": 3, "phase": "midpoint"}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_cancelled():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.cancelled": mock_handler})

    event = {"event_type": "lesson.cancelled", "payload": {"lesson_id": 2}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_blocked():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.blocked": mock_handler})

    event = {"event_type": "lesson.blocked", "payload": {"lesson_id": 2}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_unknown_event_type_does_not_raise():
    dispatcher = _make_dispatcher()
    event = {"event_type": "totally.unknown.event", "payload": {}}
    await dispatcher.dispatch(event)


@pytest.mark.asyncio
async def test_dispatch_unknown_event_type_logs_debug(caplog):
    dispatcher = _make_dispatcher()
    event = {"event_type": "unknown.thing", "payload": {}}
    with caplog.at_level(logging.DEBUG, logger="bot.consumers.event_dispatcher"):
        await dispatcher.dispatch(event)
    assert any("unknown.thing" in record.message for record in caplog.records)


@pytest.mark.asyncio
async def test_dispatch_handler_exception_propagates():
    """M16 G2: handler exception propagates up to event_consumer for DLQ routing."""
    failing_handler = AsyncMock(side_effect=RuntimeError("boom"))
    dispatcher = _make_dispatcher(handlers_override={"lesson.started": failing_handler})

    event = {"event_type": "lesson.started", "payload": {}}
    with pytest.raises(RuntimeError, match="boom"):
        await dispatcher.dispatch(event)

    failing_handler.assert_called_once()


def test_config_mini_app_url_has_default():
    settings = Settings(_env_file=None)
    assert settings.mini_app_url == "https://t.me/ruttrack_bot/ruttrack"
    assert settings.mini_app_web_url == "https://ruttrack.site/mini-app/"


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_closed():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"lesson.closed": mock_handler})

    event = {"event_type": "lesson.closed", "payload": {"lesson_id": 101, "group_id": 5}}
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_attendance_marked():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"attendance.marked": mock_handler})

    event = {
        "event_type": "attendance.marked",
        "payload": {"lesson_id": 101, "user_id": 10, "group_id": 5, "status": "present"},
    }
    await dispatcher.dispatch(event)

    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatcher_has_all_event_types():
    """EventDispatcher._handlers contains all registered event types."""
    dispatcher = _make_dispatcher()
    expected_types = {
        "lesson.started",
        "lesson.reminder",
        "lesson.blocked",
        "lesson.cancelled",
        "homework.published",
        "homework.updated",
        "homework.weekly_digest",
        "homework.due_reminder",
        "excuse.requested",
        "excuse.decided",
        "late_checkin.requested",
        "late_checkin.decided",
        "lesson.closed",
        "attendance.marked",
        "group.renamed",
        "group.archived",
        "otp.requested",
        "otp.verified",
        "password.changed",
        "lesson.one_off.created",
        "lesson.one_off.cancelled",
        "alert.fired",
    }
    assert set(dispatcher._handlers.keys()) == expected_types


@pytest.mark.asyncio
async def test_cancelled_late_checkin_closes_actions_without_false_rejection():
    dispatcher = _make_dispatcher()
    tracker = MagicMock()
    tracker.get_all = AsyncMock(return_value=[{"chat_id": 10, "message_id": 20}])
    tracker.delete = AsyncMock()
    dispatcher._request_tracker = tracker
    dispatcher._bot.edit_message_reply_markup = AsyncMock()
    dispatcher._bot.send_message = AsyncMock()

    await dispatcher._handle_late_checkin_decided({
        "event_type": "late_checkin.decided",
        "payload": {
            "request_id": "req-1",
            "user_id": 100,
            "status": "cancelled",
            "resolution_reason": "geo_confirmed",
        },
    })

    dispatcher._bot.edit_message_reply_markup.assert_awaited_once_with(
        chat_id=10, message_id=20, reply_markup=None
    )
    dispatcher._bot.send_message.assert_awaited_once()
    assert "подтверждено по геолокации" in dispatcher._bot.send_message.await_args.kwargs["text"]
    assert "Отклонено" not in dispatcher._bot.send_message.await_args.kwargs["text"]
    tracker.delete.assert_awaited_once_with("late_checkin", "req-1")


@pytest.mark.asyncio
async def test_dispatch_routes_otp_verified():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"otp.verified": mock_handler})
    event = {"event_type": "otp.verified", "payload": {"telegram_id": 42}}
    await dispatcher.dispatch(event)
    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_group_renamed():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"group.renamed": mock_handler})
    event = {"event_type": "group.renamed", "payload": {"group_id": 42}}
    await dispatcher.dispatch(event)
    mock_handler.assert_called_once_with(event)


@pytest.mark.asyncio
async def test_dispatch_routes_group_archived():
    mock_handler = AsyncMock()
    dispatcher = _make_dispatcher(handlers_override={"group.archived": mock_handler})
    event = {"event_type": "group.archived", "payload": {"group_id": 77}}
    await dispatcher.dispatch(event)
    mock_handler.assert_called_once_with(event)
