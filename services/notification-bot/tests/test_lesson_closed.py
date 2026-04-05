"""Tests for the lesson.closed notification handler (NOTIF-04).

Verifies that when a lesson closes, all stored reminder messages are deleted
from Telegram, Redis keys are cleared, and timer tasks are cancelled.
"""
from unittest.mock import AsyncMock, MagicMock, call

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
        # Side-effect: each call returns the next list
        redis_client.get_message_ids = AsyncMock(side_effect=message_ids_per_student)
    else:
        redis_client.get_message_ids = AsyncMock(return_value=[])
    redis_client.delete_key = AsyncMock()

    reminder_scheduler = MagicMock()
    reminder_scheduler.cancel_lesson = MagicMock()  # sync method

    return bot, academic_client, redis_client, reminder_scheduler


@pytest.mark.asyncio
async def test_lesson_closed_deletes_all_message_ids_and_clears_redis():
    """lesson.closed deletes every stored message_id per student and calls delete_key.

    Given 2 students each with 2 message_ids, delete_message is called 4 times
    and delete_key is called 2 times.
    """
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
    ]
    # Each student has 2 message_ids
    message_ids_per_student = [[10, 11], [20, 21]]
    bot, academic_client, redis_client, reminder_scheduler = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(lesson_id=101, group_id=5),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    # 4 total deletions (2 per student)
    assert bot.delete_message.call_count == 4
    bot.delete_message.assert_any_call(chat_id=111, message_id=10)
    bot.delete_message.assert_any_call(chat_id=111, message_id=11)
    bot.delete_message.assert_any_call(chat_id=222, message_id=20)
    bot.delete_message.assert_any_call(chat_id=222, message_id=21)

    # Redis key cleared for each student
    assert redis_client.delete_key.call_count == 2
    redis_client.delete_key.assert_any_call(101, 1)
    redis_client.delete_key.assert_any_call(101, 2)


@pytest.mark.asyncio
async def test_lesson_closed_cancels_timer_tasks_before_deletions():
    """lesson.closed calls reminder_scheduler.cancel_lesson(lesson_id) before processing."""
    students = [_make_student(user_id=1, telegram_id=111)]
    call_order = []

    bot = MagicMock()
    async def track_delete(**kwargs):
        call_order.append("delete_message")
    bot.delete_message = AsyncMock(side_effect=track_delete)

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)

    redis_client = MagicMock()
    redis_client.get_message_ids = AsyncMock(return_value=[42])
    redis_client.delete_key = AsyncMock()

    reminder_scheduler = MagicMock()
    def track_cancel(lesson_id):
        call_order.append("cancel_lesson")
    reminder_scheduler.cancel_lesson = MagicMock(side_effect=track_cancel)

    await handle_lesson_closed(
        _make_event(lesson_id=101),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    assert call_order[0] == "cancel_lesson", "cancel_lesson must be called before delete_message"
    reminder_scheduler.cancel_lesson.assert_called_once_with(101)


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_without_telegram_id():
    """Students with telegram_id=0 are skipped — no deletion attempted."""
    students = [
        _make_student(user_id=1, telegram_id=0),   # skipped
        _make_student(user_id=2, telegram_id=222),  # processed
    ]
    message_ids_per_student = [[10]]  # only one call (for student 2)
    bot, academic_client, redis_client, reminder_scheduler = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    # Only 1 deletion (student 1 was skipped)
    assert bot.delete_message.call_count == 1
    assert redis_client.delete_key.call_count == 1


@pytest.mark.asyncio
async def test_lesson_closed_silently_ignores_telegram_bad_request():
    """TelegramBadRequest (message already deleted) is silently caught — does not propagate."""
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

    reminder_scheduler = MagicMock()
    reminder_scheduler.cancel_lesson = MagicMock()

    # Must not raise
    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    # delete_key is still called even after TelegramBadRequest
    redis_client.delete_key.assert_called_once_with(101, 1)


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_with_empty_message_ids():
    """Students whose get_message_ids returns [] are skipped (already cleaned by NOTIF-05)."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
    ]
    # Student 1 already cleaned up, student 2 still has a message
    message_ids_per_student = [[], [55]]

    bot, academic_client, redis_client, reminder_scheduler = _make_handler_deps(
        students=students, message_ids_per_student=message_ids_per_student
    )

    await handle_lesson_closed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    # Only student 2 gets deletion (student 1 had no messages)
    assert bot.delete_message.call_count == 1
    bot.delete_message.assert_called_once_with(chat_id=222, message_id=55)
    assert redis_client.delete_key.call_count == 1


@pytest.mark.asyncio
async def test_lesson_closed_returns_early_on_missing_payload_fields():
    """Handler returns early without error when required payload fields are missing."""
    bot = MagicMock()
    bot.delete_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=[])
    redis_client = MagicMock()
    redis_client.delete_key = AsyncMock()
    reminder_scheduler = MagicMock()

    # Event missing lesson_id
    bad_event = {"event_type": "lesson.closed", "payload": {"group_id": 5}}
    await handle_lesson_closed(
        bad_event,
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
        reminder_scheduler=reminder_scheduler,
    )

    # Nothing should have been called
    bot.delete_message.assert_not_called()
    redis_client.delete_key.assert_not_called()
    reminder_scheduler.cancel_lesson.assert_not_called()
