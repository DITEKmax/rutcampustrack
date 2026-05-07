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
    description: str = None,
    link: str = None,
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
    if description is not None:
        payload["description"] = description
    if link is not None:
        payload["link"] = link
    if lesson_id is not None:
        payload["lesson_id"] = lesson_id
    return {
        "event_type": "homework.published",
        "payload": payload,
    }


def _make_homework_updated_event(
    homework_id: int = 1,
    group_id: int = 5,
    subject_id: int = 42,
    title: str = "Задание 1 (обновлено)",
    description: str = None,
    link: str = None,
    lesson_date: str = "2026-05-01",
    lesson_number: int = 2,
):
    return {
        "event_type": "homework.updated",
        "payload": {
            "homework_id": homework_id,
            "group_id": group_id,
            "subject_id": subject_id,
            "title": title,
            "description": description,
            "link": link,
            "lesson_date": lesson_date,
            "lesson_number": lesson_number,
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
        _make_homework_published_event(
            title="Лабораторная работа №3",
            description="Решить задачи 1-5",
            link="https://example.com/homework.pdf",
        ),
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
    assert "Решить задачи 1-5" in text
    assert "https://example.com/homework.pdf" in text
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
async def test_homework_updated_text_contains_subject_and_title():
    """Phase 61 / D-07: homework.updated resolves subject_name и формирует
    читаемый текст «ДЗ изменено: {subject} — {title}» + строку с парой/датой."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Математика"))

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        _make_homework_updated_event(
            title="Задача 3",
            description="Добавлен второй вариант",
            link="https://example.com/updated",
            lesson_date="2026-05-01",
            lesson_number=2,
        ),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Домашнее задание изменено" in text
    assert "Математика" in text
    assert "Задача 3" in text
    assert "Добавлен второй вариант" in text
    assert "https://example.com/updated" in text
    assert "Пара: №2" in text
    assert "2026-05-01" in text
    # Ensure we did try to resolve the subject (D-07 требует lookup)
    academic_client.get_subjects_by_ids.assert_awaited_once_with([42])


@pytest.mark.asyncio
async def test_homework_updated_subject_fallback_on_grpc_error():
    """homework.updated — падение gRPC → fallback к «Предмет», сообщение всё равно уходит."""
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
        _make_homework_updated_event(title="Упражнение"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "Домашнее задание изменено" in text
    assert "Предмет" in text
    assert "Упражнение" in text


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


@pytest.mark.asyncio
async def test_homework_due_reminder_sends_direct_to_target_user():
    """homework.due_reminder is user-scoped and does not fan out through group members."""
    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_student(user_id=7, telegram_id=777))
    academic_client.get_group_members = AsyncMock()

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        {
            "event_type": "homework.due_reminder",
            "payload": {
                "user_id": 7,
                "group_id": 5,
                "semester_id": 1,
                "days_before_due": 2,
                "due_date": "2026-05-05",
                "homework": {
                    "homework_id": 100,
                    "subject_name": "Math",
                    "title": "Essay",
                    "lesson_date": "2026-05-05",
                    "lesson_number": 2,
                },
            },
        },
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    academic_client.get_user_by_id.assert_awaited_once_with(7)
    academic_client.get_group_members.assert_not_called()
    assert len(captured_tasks) == 1
    assert captured_tasks[0].user_id == 7
    assert captured_tasks[0].chat_id == 777
    assert captured_tasks[0].category == "homework"

    await captured_tasks[0].coroutine_factory()
    call_kwargs = bot.send_message.call_args.kwargs
    assert call_kwargs["chat_id"] == 777
    text = call_kwargs.get("text", "")
    assert "Math" in text
    assert "Essay" in text
    assert "05.05.2026" in text


@pytest.mark.asyncio
async def test_homework_weekly_digest_formats_grouped_items():
    """homework.weekly_digest groups the per-user item list by deadline date."""
    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_student(user_id=8, telegram_id=888))

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_homework(
        {
            "event_type": "homework.weekly_digest",
            "payload": {
                "user_id": 8,
                "group_id": 5,
                "semester_id": 1,
                "week_start": "2026-05-04",
                "week_end": "2026-05-10",
                "total_count": 2,
                "items": [
                    {
                        "homework_id": 100,
                        "subject_name": "Math",
                        "title": "Essay",
                        "lesson_date": "2026-05-05",
                        "lesson_number": 2,
                    },
                    {
                        "homework_id": 200,
                        "subject_name": "History",
                        "title": "Read",
                        "lesson_date": "2026-05-04",
                        "lesson_number": 1,
                    },
                ],
            },
        },
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "04.05.2026" in text
    assert "05.05.2026" in text
    assert text.index("04.05.2026") < text.index("05.05.2026")
    assert "History" in text
    assert "Read" in text
    assert "Math" in text
    assert "Essay" in text
