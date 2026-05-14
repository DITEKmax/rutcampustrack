"""Tests for the lesson.blocked notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_blocked import handle_lesson_blocked


def _make_student(user_id: int, telegram_id: int):
    student = MagicMock()
    student.user_id = user_id
    student.telegram_id = telegram_id
    return student


def _make_subjects_response(subject_name: str = "Математика"):
    resp = MagicMock()
    resp.subjects = [MagicMock(subject_name=subject_name)]
    return resp


def _make_event(
    group_id: int = 5,
    subject_id: int = 42,
    date: str = "2026-05-20",
    lesson_number: int = 3,
    room: str = "B-303",
):
    return {
        "event_type": "lesson.blocked",
        "payload": {
            "lesson_id": 501,
            "group_id": group_id,
            "subject_id": subject_id,
            "date": date,
            "lesson_number": lesson_number,
            "start_time": "12:20",
            "end_time": "13:50",
            "room": room,
        },
    }


@pytest.mark.asyncio
async def test_lesson_blocked_sends_to_students_with_telegram_id():
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),
        _make_student(user_id=3, telegram_id=333),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Физика"))

    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_lesson_blocked(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 2

    for task in captured_tasks:
        await task.coroutine_factory()

    assert bot.send_message.call_count == 2


@pytest.mark.asyncio
async def test_lesson_blocked_message_contains_subject_date_and_reason():
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Теормех"))

    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_lesson_blocked(
        _make_event(date="2026-05-21"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "Пара заблокирована старостой" in text
    assert "Теормех" in text
    assert "2026-05-21" in text
    assert "Самостоятельная отметка невозможна" in text


@pytest.mark.asyncio
async def test_lesson_blocked_skips_missing_payload_fields():
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    await handle_lesson_blocked(
        {"event_type": "lesson.blocked", "payload": {"date": "2026-05-20"}},
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    send_queue.put.assert_not_called()
