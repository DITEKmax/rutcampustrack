"""Tests for EventDispatcher routing and error handling."""

import logging
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.config import Settings
from bot.consumers.event_dispatcher import EventDispatcher
from bot.services.send_queue import TelegramSendQueue
from tests.business_delivery import BusinessDelivery, assert_business_delivery, assert_terminal_cards, fact_event


def _make_dispatcher(handlers_override=None):
    bot = MagicMock()
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=SimpleNamespace(id=100, telegram_id=0))
    send_queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
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
async def test_dispatch_routes_lesson_started(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.started", expected=("Занятие началось",))


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_reminder(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.reminder", expected=("Напоминание",))


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_cancelled(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.cancelled", expected=("Занятие отменено",))


@pytest.mark.asyncio
async def test_dispatch_routes_lesson_blocked(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.blocked", expected=("Самостоятельная отметка закрыта",))


@pytest.mark.asyncio
async def test_dispatch_unknown_event_type_logs_debug(caplog):
    dispatcher = _make_dispatcher()
    event = {"event_type": "unknown.thing", "payload": {}}
    with caplog.at_level(logging.DEBUG, logger="bot.consumers.event_dispatcher"):
        await dispatcher.dispatch(event)
    assert any(record.getMessage() == "Unhandled event type: unknown" for record in caplog.records)
    assert all(event["event_type"] not in record.getMessage() for record in caplog.records)


@pytest.mark.asyncio
async def test_dispatch_handler_exception_propagates(monkeypatch):
    """M16 G2: handler exception propagates up to event_consumer for DLQ routing."""
    failing_handler = AsyncMock(side_effect=RuntimeError("boom"))
    monkeypatch.setattr("bot.notifications.audience.handle_business", failing_handler)
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
async def test_dispatch_routes_lesson_closed(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.closed", expected=("Занятие завершено",))


@pytest.mark.asyncio
async def test_dispatch_routes_attendance_marked(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", expected=("Отметка", "Автор: староста"))


@pytest.mark.asyncio
async def test_dispatcher_has_all_event_types(fake_redis):
    # Independent accepted meanings; private registry shape is not an oracle.
    meanings = [
        ("lesson.transferred", None, "Занятие перенесено"),
        ("lesson.restored", None, "Занятие восстановлено"),
        ("lesson.updated", None, "Занятие изменено"),
        ("homework.withdrawn", None, "снято с публикации"),
        ("group.membership.changed", None, "Участие в группе изменено"),
        ("headman.access.changed", None, "Права старосты изменены"),
        ("assistant.access.changed", None, "Права помощника изменены"),
        ("admin.access.changed", None, "Права администратора изменены"),
        ("semester.activated", None, "Семестр активирован"),
        ("semester.operation.completed", None, "Операция с семестром завершена"),
        ("group.promotion.completed", None, "партия перевода групп"),
        ("lesson.started", None, "Занятие началось"),
        ("lesson.closed", None, "Занятие завершено"),
        ("lesson.cancelled", None, "Занятие отменено"),
        ("lesson.reminder", None, "Напоминание об отметке"),
        ("lesson.blocked", None, "Самостоятельная отметка закрыта"),
        ("lesson.one_off.created", None, "Добавлено разовое занятие"),
        ("homework.published", None, "Новое домашнее задание"),
        ("homework.updated", None, "Домашнее задание изменено"),
        ("homework.weekly_digest", None, "Домашние задания на неделю"),
        ("homework.due_reminder", None, "Скоро срок домашнего задания"),
        ("excuse.requested", None, "Новая заявка на уважительную причину"),
        ("late_checkin.requested", None, "Новая заявка на отметку"),
        ("attendance.marked", None, "Отметка посещаемости изменена"),
        ("group.renamed", None, "Название группы изменено"),
        ("group.archived", None, "Группа архивирована"),
        ("excuse.decided", "approved", "Уважительная причина одобрена"),
        ("excuse.decided", "rejected", "Уважительная причина отклонена"),
        ("excuse.decided", "cancelled", "отменена"),
        ("late_checkin.decided", "approved", "Заявка на отметку одобрена"),
        ("late_checkin.decided", "rejected", "Заявка на отметку отклонена"),
        ("late_checkin.decided", "cancelled", "отменена"),
    ]
    scope = fact_event("lesson.cancelled")["payload"]
    for kind, status, fact in meanings:
        await fake_redis.flushdb()
        await assert_business_delivery(
            fake_redis,
            kind,
            status=status,
            expected=(fact,),
            extra={"actor_user_id": 99, "source": scope, "target": {**scope, "lesson_id": 12}},
        )


@pytest.mark.asyncio
async def test_cancelled_late_checkin_closes_actions_without_false_rejection(fake_redis):
    await assert_terminal_cards(fake_redis, cancelled=True)


@pytest.mark.asyncio
async def test_dispatch_routes_otp_verified(fake_redis):
    h = BusinessDelivery(fake_redis)
    await h.otp.store(101, 101, 11, 12)
    event = {"event_type": "otp.verified", "payload": {"telegram_id": 101}}
    assert await h.dispatcher.prepare(event) is None
    h.queue.start()
    try:
        batch = await h.dispatcher.dispatch(event)
        await batch.wait()
        assert set(h.deleted) == {(101, 11), (101, 12)}
        assert await h.otp.pop(101) is None
    finally:
        await h.queue.shutdown()


@pytest.mark.asyncio
async def test_dispatch_routes_group_renamed(fake_redis):
    await assert_business_delivery(fake_redis, "group.renamed", expected=("Название группы изменено",))


@pytest.mark.asyncio
async def test_dispatch_routes_group_archived(fake_redis):
    await assert_business_delivery(fake_redis, "group.archived", expected=("Группа архивирована",))
