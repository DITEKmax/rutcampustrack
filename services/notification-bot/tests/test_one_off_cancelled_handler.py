"""Tests for the lesson.one_off.cancelled notification handler (Phase 60-04)."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_one_off_cancelled import handle_lesson_one_off_cancelled


def _make_student(user_id: int, telegram_id: int):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    return s


def _make_subjects_response(subject_name: str = "Математика"):
    resp = MagicMock()
    resp.subjects = [MagicMock(subject_name=subject_name)]
    return resp


def _make_event(
    group_id: int = 5,
    subject_id: int = 42,
    date: str = "2026-04-20",
    lesson_number: int = 3,
):
    return {
        "event_type": "lesson.one_off.cancelled",
        "payload": {
            "group_id": group_id,
            "subject_id": subject_id,
            "date": date,
            "lesson_number": lesson_number,
        },
    }


@pytest.mark.asyncio
async def test_sends_cancellation_to_all():
    """Every group student (including headman per D-18) gets a cancellation notice."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Физика"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_one_off_cancelled(
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
async def test_message_contains_date_and_subject():
    """Cancellation text includes the date, subject name, and lesson number."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Дискретная математика"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_one_off_cancelled(
        _make_event(date="2026-05-11", lesson_number=2),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "2026-05-11" in text
    assert "Дискретная математика" in text
    assert "2" in text  # lesson_number


@pytest.mark.asyncio
async def test_fallback_subject_on_grpc_error():
    """Falls back to a generic label when subject lookup raises."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(side_effect=Exception("gRPC timeout"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_one_off_cancelled(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs.get("text", "")
    # fallback subject label 'Пара' used when grpc fails
    assert "Пара" in text


@pytest.mark.asyncio
async def test_skips_missing_payload_fields():
    """Handler returns early on missing required fields."""
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    incomplete = {"event_type": "lesson.one_off.cancelled", "payload": {"date": "2026-04-20"}}

    await handle_lesson_one_off_cancelled(
        incomplete,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    send_queue.put.assert_not_called()
