"""Tests for the lesson.closed notification handler (NOTIF-04).

Verifies that when a lesson closes, all stored reminder messages are deleted
from Telegram and Redis keys are cleared. In-memory reminder scheduler был
удалён вместе с миграцией midpoint-напоминаний на schedule-service
(LessonReminderJob), поэтому handler больше не принимает reminder_scheduler.
"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from aiogram.exceptions import TelegramBadRequest

from bot.notifications.lesson_closed import handle_lesson_closed


def _make_student(user_id: int, telegram_id: int, is_headman: bool = False):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    s.is_headman = is_headman
    return s


def _make_event(lesson_id: int = 101, group_id: int = 5):
    return {
        "event_type": "lesson.closed",
        "payload": {
            "lesson_id": lesson_id,
            "group_id": group_id,
        },
    }


def _make_handler_deps(students=None, message_ids_per_student=None):
    """Build mocked dependencies for handle_lesson_closed."""
    bot = MagicMock()
    bot.delete_message = AsyncMock()

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students or [])

    redis_client = MagicMock()
    if message_ids_per_student is not None:
        redis_client.get_message_ids = AsyncMock(side_effect=message_ids_per_student)
    else:
        redis_client.get_message_ids = AsyncMock(return_value=[])
    redis_client.delete_key = AsyncMock()

    return bot, academic_client, redis_client


@pytest.mark.asyncio
async def test_lesson_closed_deletes_all_message_ids_and_clears_redis():
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
    ]
    message_ids_per_student = [[10, 11], [20, 21]]
    bot, academic_client, redis_client = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(lesson_id=101, group_id=5),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    assert bot.delete_message.call_count == 4
    bot.delete_message.assert_any_call(chat_id=111, message_id=10)
    bot.delete_message.assert_any_call(chat_id=111, message_id=11)
    bot.delete_message.assert_any_call(chat_id=222, message_id=20)
    bot.delete_message.assert_any_call(chat_id=222, message_id=21)

    assert redis_client.delete_key.call_count == 2
    redis_client.delete_key.assert_any_call(101, 1)
    redis_client.delete_key.assert_any_call(101, 2)


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_without_telegram_id():
    students = [
        _make_student(user_id=1, telegram_id=0),
        _make_student(user_id=2, telegram_id=222),
    ]
    message_ids_per_student = [[10]]
    bot, academic_client, redis_client = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    assert bot.delete_message.call_count == 1
    assert redis_client.delete_key.call_count == 1


@pytest.mark.asyncio
async def test_lesson_closed_silently_ignores_telegram_bad_request():
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.delete_message = AsyncMock(
        side_effect=TelegramBadRequest(method=MagicMock(), message="Message to delete not found")
    )

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)

    redis_client = MagicMock()
    redis_client.get_message_ids = AsyncMock(return_value=[99])
    redis_client.delete_key = AsyncMock()

    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    redis_client.delete_key.assert_called_once_with(101, 1)


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_with_empty_message_ids():
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
    ]
    message_ids_per_student = [[], [55]]

    bot, academic_client, redis_client = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    assert bot.delete_message.call_count == 1
    bot.delete_message.assert_called_once_with(chat_id=222, message_id=55)
    assert redis_client.delete_key.call_count == 1


@pytest.mark.asyncio
async def test_lesson_closed_returns_early_on_missing_payload_fields():
    bot = MagicMock()
    bot.delete_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=[])
    redis_client = MagicMock()
    redis_client.delete_key = AsyncMock()

    bad_event = {"event_type": "lesson.closed", "payload": {"group_id": 5}}
    await handle_lesson_closed(
        bad_event,
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_not_called()
    redis_client.delete_key.assert_not_called()
