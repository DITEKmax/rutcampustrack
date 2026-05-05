"""Tests for the attendance.marked notification handler (NOTIF-05).

Verifies that when a student checks in (status=present), their reminder
messages are deleted from Telegram and Redis keys are cleared.
For status!=present, no deletion occurs.
"""

from unittest.mock import AsyncMock, MagicMock

import pytest
from aiogram.exceptions import TelegramBadRequest

from bot.notifications.attendance_marked import handle_attendance_marked


def _make_student(user_id: int, telegram_id: int, is_headman: bool = False):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    s.is_headman = is_headman
    return s


def _make_event(
    lesson_id: int = 101,
    user_id: int = 10,
    group_id: int = 5,
    status: str = "present",
    marked_by: str = "student_geo",
):
    return {
        "event_type": "attendance.marked",
        "payload": {
            "lesson_id": lesson_id,
            "user_id": user_id,
            "group_id": group_id,
            "status": status,
            "marked_by": marked_by,
        },
    }


def _make_deps(students=None, message_ids=None):
    """Build mocked dependencies for handle_attendance_marked."""
    bot = MagicMock()
    bot.send_message = AsyncMock()
    bot.delete_message = AsyncMock()

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students or [])

    redis_client = MagicMock()
    redis_client.get_message_ids = AsyncMock(return_value=message_ids or [])
    redis_client.delete_key = AsyncMock()

    return bot, academic_client, redis_client


@pytest.mark.asyncio
async def test_attendance_marked_headman_present_uses_plus_status_label():
    """Headman manual present mark is shown as присутствует (+), not присутствует (б)."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[])
    event = _make_event(status="present", marked_by="headman")

    await handle_attendance_marked(
        event,
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.send_message.assert_awaited_once()
    sent_text = bot.send_message.await_args.kwargs["text"]
    assert sent_text.splitlines()[0] == "Староста проставил вам статус: присутствует (+)"
    assert "(б)" not in sent_text


@pytest.mark.asyncio
async def test_attendance_marked_present_deletes_messages_and_clears_redis():
    """status=present: all stored message_ids are deleted and Redis key is cleared."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    message_ids = [50, 51, 52]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=message_ids)

    await handle_attendance_marked(
        _make_event(lesson_id=101, user_id=10, group_id=5, status="present"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    assert bot.delete_message.call_count == 3
    bot.delete_message.assert_any_call(chat_id=1010, message_id=50)
    bot.delete_message.assert_any_call(chat_id=1010, message_id=51)
    bot.delete_message.assert_any_call(chat_id=1010, message_id=52)
    redis_client.delete_key.assert_called_once_with(101, 10)


@pytest.mark.asyncio
async def test_attendance_marked_absent_deletes_messages():
    """status=absent: student is already marked, so active reminders are cleared."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[50])

    await handle_attendance_marked(
        _make_event(status="absent"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_called_once_with(chat_id=1010, message_id=50)
    redis_client.delete_key.assert_called_once_with(101, 10)


@pytest.mark.asyncio
async def test_attendance_marked_excused_deletes_messages():
    """status=excused: reminder'ы тоже чистим — уважительная означает, что
    студента не ждут, и напоминание-«отметься» становится бесполезным."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[50])

    await handle_attendance_marked(
        _make_event(status="excused"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_called_once_with(chat_id=1010, message_id=50)
    redis_client.delete_key.assert_called_once_with(101, 10)


@pytest.mark.asyncio
async def test_attendance_marked_free_attendance_deletes_messages():
    """status=free_attendance: свободное посещение — напоминание тоже
    становится бесполезным, чистим."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[50])

    await handle_attendance_marked(
        _make_event(status="free_attendance"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_called_once_with(chat_id=1010, message_id=50)
    redis_client.delete_key.assert_called_once_with(101, 10)


@pytest.mark.asyncio
async def test_attendance_marked_present_silently_ignores_telegram_bad_request():
    """TelegramBadRequest during delete is silently caught (idempotent delete)."""
    students = [_make_student(user_id=10, telegram_id=1010)]

    bot = MagicMock()
    bot.delete_message = AsyncMock(
        side_effect=TelegramBadRequest(method=MagicMock(), message="Message to delete not found")
    )

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)

    redis_client = MagicMock()
    redis_client.get_message_ids = AsyncMock(return_value=[77])
    redis_client.delete_key = AsyncMock()

    # Must not raise
    await handle_attendance_marked(
        _make_event(status="present"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    # delete_key still called even after TelegramBadRequest
    redis_client.delete_key.assert_called_once_with(101, 10)


@pytest.mark.asyncio
async def test_attendance_marked_present_no_messages_is_noop():
    """status=present but get_message_ids returns [] — no deletions, no Redis clear."""
    students = [_make_student(user_id=10, telegram_id=1010)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[])

    await handle_attendance_marked(
        _make_event(status="present"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_not_called()
    redis_client.delete_key.assert_not_called()


@pytest.mark.asyncio
async def test_attendance_marked_missing_user_id_returns_early():
    """Handler returns early without error when user_id is missing from payload."""
    bot = MagicMock()
    bot.delete_message = AsyncMock()
    academic_client = MagicMock()
    redis_client = MagicMock()
    redis_client.delete_key = AsyncMock()

    bad_event = {
        "event_type": "attendance.marked",
        "payload": {"lesson_id": 101, "group_id": 5, "status": "present"},
    }
    await handle_attendance_marked(
        bad_event,
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_not_called()
    redis_client.delete_key.assert_not_called()


@pytest.mark.asyncio
async def test_attendance_marked_student_not_in_group_members_is_noop():
    """If academic_client returns no match for user_id, handler is a no-op."""
    # Group has student 99, but event is for student 10
    students = [_make_student(user_id=99, telegram_id=9999)]
    bot, academic_client, redis_client = _make_deps(students=students, message_ids=[50])

    await handle_attendance_marked(
        _make_event(user_id=10, status="present"),
        bot=bot,
        academic_client=academic_client,
        redis_client=redis_client,
    )

    bot.delete_message.assert_not_called()
    redis_client.delete_key.assert_not_called()
