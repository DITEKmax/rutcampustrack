"""Tests for the lesson.one_off.created notification handler (Phase 60-04)."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_one_off_created import handle_lesson_one_off_created


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
    one_off_lesson_id: int = 501,
    group_id: int = 5,
    subject_id: int = 42,
    date: str = "2026-04-20",
    lesson_number: int = 3,
    classroom: str | None = "C-303",
):
    payload = {
        "one_off_lesson_id": one_off_lesson_id,
        "group_id": group_id,
        "subject_id": subject_id,
        "date": date,
        "lesson_number": lesson_number,
    }
    if classroom is not None:
        payload["classroom"] = classroom
    return {"event_type": "lesson.one_off.created", "payload": payload}


@pytest.mark.asyncio
async def test_sends_to_all_group_students():
    """Every group student with telegram_id gets a send task (D-18 — including headman)."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=222),
        _make_student(user_id=3, telegram_id=333),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Теормех"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_one_off_created(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # All 3 students receive a task — headman not filtered (D-18)
    assert len(captured_tasks) == 3

    for task in captured_tasks:
        await task.coroutine_factory()
    assert bot.send_message.call_count == 3

    # Text includes date, lesson number, subject, classroom
    sent_text = bot.send_message.call_args.kwargs.get("text", "")
    assert "2026-04-20" in sent_text
    assert "3" in sent_text  # lesson number
    assert "Теормех" in sent_text
    assert "C-303" in sent_text


@pytest.mark.asyncio
async def test_empty_group_no_error():
    """Empty students list does not raise and does not enqueue tasks."""
    bot = MagicMock()
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=[])
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    # Must not raise
    await handle_lesson_one_off_created(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    send_queue.put.assert_not_called()


@pytest.mark.asyncio
async def test_skips_students_without_telegram_id():
    """Students with telegram_id=0 are skipped."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # skipped
    ]

    bot = MagicMock()
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_one_off_created(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1


@pytest.mark.asyncio
async def test_skips_missing_payload_fields():
    """Handler returns early when required payload fields are missing."""
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    incomplete = {"event_type": "lesson.one_off.created", "payload": {"date": "2026-04-20"}}

    await handle_lesson_one_off_created(
        incomplete,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    send_queue.put.assert_not_called()
