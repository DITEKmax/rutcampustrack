"""Tests for the homework notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.homework import handle_homework


def _make_student(user_id: int, telegram_id: int, is_headman: bool = False, display_name: str = "Student"):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    s.is_headman = is_headman
    s.display_name = display_name
    return s


def _make_subjects_response(subject_name: str = "Математика"):
    resp = MagicMock()
    resp.subjects = [MagicMock(subject_name=subject_name)]
    return resp


def _make_homework_published_event(
    homework_id: int = 1,
    group_id: int = 5,
    subject_id: int = 42,
    title: str = "Задание 1",
    lesson_id: int = None,
    has_link: bool = False,
):
    payload = {
        "homework_id": homework_id,
        "group_id": group_id,
        "subject_id": subject_id,
        "title": title,
        "has_link": has_link,
    }
    if lesson_id is not None:
        payload["lesson_id"] = lesson_id
    return {
        "event_type": "homework.published",
        "payload": payload,
    }


def _make_homework_updated_event(
    homework_id: int = 1,
    group_id: int = 5,
    title: str = "Задание 1 (обновлено)",
):
    return {
        "event_type": "homework.updated",
        "payload": {
            "homework_id": homework_id,
            "group_id": group_id,
            "title": title,
        },
    }


@pytest.mark.asyncio
async def test_homework_published_sends_to_students_with_telegram_id():
    """homework.published sends message to students with telegram_id; skips telegram_id=0."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # should be skipped
        _make_student(user_id=3, telegram_id=333, is_headman=True),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Алгебра"))

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_published_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # Only 2 students have telegram_id > 0
    assert len(captured_tasks) == 2


@pytest.mark.asyncio
async def test_homework_published_text_contains_subject_and_title():
    """homework.published message text includes the resolved subject name and title."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Физика"))

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_published_event(title="Лабораторная работа №3"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Физика" in text
    assert "Лабораторная работа №3" in text
    assert "Новое домашнее задание" in text


@pytest.mark.asyncio
async def test_homework_updated_sends_to_all_students_with_telegram_id():
    """homework.updated sends messages to all group students with telegram_id."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # skipped
        _make_student(user_id=3, telegram_id=333),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_updated_event(title="Обновлённое задание"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # 2 students with telegram_id > 0
    assert len(captured_tasks) == 2


@pytest.mark.asyncio
async def test_homework_updated_text_contains_updated_marker_and_title():
    """homework.updated message text contains 'обновлено' and the title."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    # get_subjects_by_ids is not called for homework.updated (no subject_id in payload)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_updated_event(title="Задание по физике"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "обновлено" in text.lower()
    assert "Задание по физике" in text


@pytest.mark.asyncio
async def test_homework_published_subject_fallback_on_grpc_error():
    """Falls back to 'Предмет' when get_subjects_by_ids raises an exception."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(side_effect=Exception("gRPC down"))

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_published_event(title="Задание"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Предмет" in text
