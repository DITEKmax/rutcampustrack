from datetime import date
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.homework import cmd_homework_week
from bot.services.academic_http_client import TokenExpiredError


def _make_message(user_id: int = 12345) -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.answer = AsyncMock()
    return message


def _make_user_response(found: bool = True, group_id: int = 10) -> MagicMock:
    resp = MagicMock()
    resp.found = found
    resp.group_id = group_id
    return resp


def _make_subjects_response(*subjects: tuple[int, str]) -> MagicMock:
    resp = MagicMock()
    resp.subjects = [MagicMock(subject_id=subject_id, subject_name=name) for subject_id, name in subjects]
    return resp


@pytest.mark.asyncio
async def test_homework_week_requires_login():
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value=None)

    await cmd_homework_week(
        message,
        jwt_redis=jwt_redis,
        academic_client=MagicMock(),
        academic_http_client=MagicMock(),
        today=date(2026, 5, 7),
    )

    message.answer.assert_awaited_once()
    assert "/login" in message.answer.call_args.args[0]


@pytest.mark.asyncio
async def test_homework_week_formats_upcoming_homeworks():
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10))
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response((42, "Физика")))

    academic_http_client = MagicMock()
    academic_http_client.get_active_semester = AsyncMock(return_value={"id": 2, "active": True})
    academic_http_client.get_homeworks = AsyncMock(
        return_value=[
            {
                "id": 1,
                "subjectId": 42,
                "title": "Лабораторная работа",
                "description": "Задачи 1-3",
                "link": "https://example.com/hw.pdf",
                "lessonDate": "2026-05-08",
                "lessonNumber": 2,
                "completed": False,
            },
            {
                "id": 2,
                "subjectId": 42,
                "title": "Уже готово",
                "lessonDate": "2026-05-09",
                "lessonNumber": 1,
                "completed": True,
            },
            {
                "id": 3,
                "subjectId": 42,
                "title": "Дальнее ДЗ",
                "lessonDate": "2026-05-14",
                "lessonNumber": 1,
                "completed": False,
            },
        ]
    )

    await cmd_homework_week(
        message,
        jwt_redis=jwt_redis,
        academic_client=academic_client,
        academic_http_client=academic_http_client,
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
    academic_http_client.get_homeworks.assert_awaited_once_with("tok", group_id=10, semester_id=2)


@pytest.mark.asyncio
async def test_homework_week_empty_range_message():
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10))
    academic_client.get_subjects_by_ids = AsyncMock()

    academic_http_client = MagicMock()
    academic_http_client.get_active_semester = AsyncMock(return_value={"id": 2, "active": True})
    academic_http_client.get_homeworks = AsyncMock(return_value=[])

    await cmd_homework_week(
        message,
        jwt_redis=jwt_redis,
        academic_client=academic_client,
        academic_http_client=academic_http_client,
        today=date(2026, 5, 7),
    )

    text = message.answer.call_args.args[0]
    assert "На ближайшие 7 дней домашних заданий нет." in text
    academic_client.get_subjects_by_ids.assert_not_called()


@pytest.mark.asyncio
async def test_homework_week_token_expired_deletes_jwt():
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "old", "refresh_token": "ref"})
    jwt_redis.delete = AsyncMock()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10))

    academic_http_client = MagicMock()
    academic_http_client.get_active_semester = AsyncMock(side_effect=TokenExpiredError("expired"))

    await cmd_homework_week(
        message,
        jwt_redis=jwt_redis,
        academic_client=academic_client,
        academic_http_client=academic_http_client,
        today=date(2026, 5, 7),
    )

    jwt_redis.delete.assert_awaited_once_with(12345)
    assert "Сессия истекла" in message.answer.call_args.args[0]
