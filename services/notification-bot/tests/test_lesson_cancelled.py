"""Tests for the lesson.cancelled notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_cancelled import handle_lesson_cancelled


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
    date: str = "2025-04-10",
    cancel_reason: str | None = None,
):
    payload = {
        "group_id": group_id,
        "subject_id": subject_id,
        "date": date,
    }
    if cancel_reason is not None:
        payload["cancel_reason"] = cancel_reason
    return {"event_type": "lesson.cancelled", "payload": payload}


@pytest.mark.asyncio
async def test_lesson_cancelled_sends_to_students_with_telegram_id():
    """Only students with telegram_id > 0 receive a message; telegram_id=0 is skipped."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # skipped
        _make_student(user_id=3, telegram_id=333),
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

    await handle_lesson_cancelled(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # 2 students have telegram_id > 0
    assert len(captured_tasks) == 2

    # Execute coroutine factories to trigger bot.send_message
    for task in captured_tasks:
        await task.coroutine_factory()

    assert bot.send_message.call_count == 2


@pytest.mark.asyncio
async def test_lesson_cancelled_message_contains_subject_name_and_date():
    """Message text includes resolved subject name and lesson date."""
    students = [_make_student(user_id=1, telegram_id=111)]

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

    await handle_lesson_cancelled(
        _make_event(date="2025-05-01"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Теормех" in text
    assert "2025-05-01" in text


@pytest.mark.asyncio
async def test_lesson_cancelled_with_cancel_reason():
    """cancel_reason is included in the message text when provided."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_lesson_cancelled(
        _make_event(cancel_reason="Преподаватель заболел"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Преподаватель заболел" in text


@pytest.mark.asyncio
async def test_lesson_cancelled_without_cancel_reason():
    """Handler works correctly when cancel_reason is absent."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Химия"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    # Event without cancel_reason key
    await handle_lesson_cancelled(
        _make_event(),  # no cancel_reason
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # Must not raise and must enqueue 1 task
    assert len(captured_tasks) == 1

    await captured_tasks[0].coroutine_factory()
    assert bot.send_message.call_count == 1

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Причина" not in text


@pytest.mark.asyncio
async def test_lesson_cancelled_resolves_subject_name():
    """Subject name from gRPC is used in the cancellation message."""
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

    await handle_lesson_cancelled(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    assert "Дискретная математика" in call_kwargs.get("text", "")


@pytest.mark.asyncio
async def test_lesson_cancelled_fallback_subject_name_on_grpc_error():
    """Falls back to 'Пара' when get_subjects_by_ids raises an exception."""
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

    await handle_lesson_cancelled(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    assert "Пара" in call_kwargs.get("text", "")


@pytest.mark.asyncio
async def test_lesson_cancelled_skips_missing_payload_fields():
    """Handler logs a warning and returns early when required payload fields are missing."""
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    incomplete_event = {"event_type": "lesson.cancelled", "payload": {"date": "2025-04-10"}}

    # Should not raise
    await handle_lesson_cancelled(
        incomplete_event,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    send_queue.put.assert_not_called()
