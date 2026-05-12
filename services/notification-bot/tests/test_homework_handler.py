from datetime import date
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.homework import cmd_homework_week


def _make_message(user_id: int = 12345) -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.answer = AsyncMock()
    return message


def _make_user_response(found: bool = True, group_id: int = 10, user_id: int = 77) -> MagicMock:
    resp = MagicMock()
    resp.found = found
    resp.user_id = user_id
    resp.group_id = group_id
    return resp


def _make_semester(semester_id: int = 2) -> MagicMock:
    semester = MagicMock()
    semester.id = semester_id
    return semester


def _make_homework(
    homework_id: int,
    *,
    subject_id: int = 42,
    subject_name: str = "Физика",
    title: str = "Лабораторная работа",
    description: str = "",
    link: str = "",
    lesson_date: str = "2026-05-08",
    lesson_number: int = 2,
    completed: bool = False,
) -> MagicMock:
    homework = MagicMock()
    homework.homework_id = homework_id
    homework.subject_id = subject_id
    homework.subject_name = subject_name
    homework.title = title
    homework.description = description
    homework.link = link
    homework.lesson_date = lesson_date
    homework.lesson_number = lesson_number
    homework.completed = completed
    return homework


@pytest.mark.asyncio
async def test_homework_week_does_not_require_web_login():
    message = _make_message()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    academic_client.get_homeworks_for_week = AsyncMock(
        return_value=[_make_homework(1, title="Лабораторная работа", lesson_date="2026-05-08")]
    )

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        today=date(2026, 5, 7),
    )

    message.answer.assert_awaited_once()
    text = message.answer.call_args.args[0]
    assert "/login" not in text
    assert "Лабораторная работа" in text
    academic_client.get_homeworks_for_week.assert_awaited_once_with(
        group_id=10,
        semester_id=2,
        student_id=77,
        date_from="2026-05-07",
        date_to="2026-05-13",
    )


@pytest.mark.asyncio
async def test_homework_week_formats_upcoming_homeworks():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    academic_client.get_homeworks_for_week = AsyncMock(
        return_value=[
            _make_homework(
                1,
                title="Лабораторная работа",
                description="Задачи 1-3",
                link="https://example.com/hw.pdf",
                lesson_date="2026-05-08",
                lesson_number=2,
            ),
            _make_homework(2, title="Уже готово", lesson_date="2026-05-09", lesson_number=1, completed=True),
            _make_homework(3, title="Дальнее ДЗ", lesson_date="2026-05-14", lesson_number=1),
        ]
    )

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        today=date(2026, 5, 7),
    )

    text = message.answer.call_args.args[0]
    assert "07.05.2026 — 13.05.2026" in text
    assert "Физика · пара №2" in text
    assert "Лабораторная работа" in text
    assert "Задачи 1-3" in text
    assert "https://example.com/hw.pdf" in text
    assert "✅ Физика · пара №1" in text
    assert "Дальнее ДЗ" not in text
    academic_client.get_homeworks_for_week.assert_awaited_once()


@pytest.mark.asyncio
async def test_homework_week_empty_range_message():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    academic_client.get_homeworks_for_week = AsyncMock(return_value=[])

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        today=date(2026, 5, 7),
    )

    text = message.answer.call_args.args[0]
    assert "На ближайшие 7 дней домашних заданий нет." in text


@pytest.mark.asyncio
async def test_homework_week_no_active_semester_message():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_active_semester = AsyncMock(return_value=None)
    academic_client.get_homeworks_for_week = AsyncMock()

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        today=date(2026, 5, 7),
    )

    assert "нет активного семестра" in message.answer.call_args.args[0]
    academic_client.get_homeworks_for_week.assert_not_called()
