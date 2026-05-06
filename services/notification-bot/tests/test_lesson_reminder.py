"""Tests for lesson.reminder Telegram fan-out."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_reminder import handle_lesson_reminder


def _make_student(user_id: int, telegram_id: int):
    student = MagicMock()
    student.user_id = user_id
    student.telegram_id = telegram_id
    return student


def _make_event(phase: str = "midpoint"):
    return {
        "event_type": "lesson.reminder",
        "payload": {
            "lesson_id": 101,
            "group_id": 5,
            "phase": phase,
        },
    }


@pytest.mark.asyncio
async def test_lesson_reminder_sends_to_unmarked_students_even_without_start_message_key():
    students = [
        _make_student(10, 1010),
        _make_student(20, 2020),
    ]
    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=777))
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    redis_client = MagicMock()
    redis_client.is_student_marked = AsyncMock(return_value=False)
    redis_client.add_message_id = AsyncMock()
    redis_client.get_message_ids = AsyncMock(return_value=[])

    await handle_lesson_reminder(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
    )

    assert len(captured_tasks) == 2
    redis_client.get_message_ids.assert_not_called()

    for task in captured_tasks:
        await task.coroutine_factory()

    assert bot.send_message.call_count == 2
    redis_client.add_message_id.assert_any_call(101, 10, 777)
    redis_client.add_message_id.assert_any_call(101, 20, 777)


@pytest.mark.asyncio
async def test_lesson_reminder_skips_marked_students():
    students = [
        _make_student(10, 1010),
        _make_student(20, 2020),
    ]
    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=888))
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    redis_client = MagicMock()
    redis_client.is_student_marked = AsyncMock(side_effect=[True, False])
    redis_client.add_message_id = AsyncMock()

    await handle_lesson_reminder(
        _make_event(phase="near_end"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
    )

    assert len(captured_tasks) == 1
    assert captured_tasks[0].user_id == 20

    await captured_tasks[0].coroutine_factory()

    bot.send_message.assert_awaited_once()
    assert "скоро закончится" in bot.send_message.await_args.kwargs["text"]
    redis_client.add_message_id.assert_called_once_with(101, 20, 888)


@pytest.mark.asyncio
async def test_lesson_reminder_returns_early_on_missing_required_fields():
    bot = MagicMock()
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=[])
    send_queue = MagicMock()
    send_queue.put = AsyncMock()
    redis_client = MagicMock()
    redis_client.is_student_marked = AsyncMock()

    await handle_lesson_reminder(
        {"event_type": "lesson.reminder", "payload": {"group_id": 5}},
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
    )

    send_queue.put.assert_not_called()
    redis_client.is_student_marked.assert_not_called()
