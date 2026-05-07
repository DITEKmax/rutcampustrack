"""Tests for /status command handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.status import cmd_status
from bot.services.attendance_http_client import TokenExpiredError


def _make_message(user_id: int = 12345) -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.answer = AsyncMock()
    return message


def _make_lesson(
    lesson_id: int = 101,
    subject_id: int = 55,
    room: str = "А-101",
    start_time: str = "09:00",
    end_time: str = "10:30",
) -> MagicMock:
    lesson = MagicMock()
    lesson.id = lesson_id
    lesson.subject_id = subject_id
    lesson.room = room
    lesson.start_time = start_time
    lesson.end_time = end_time
    return lesson


def _make_user_response(found: bool = True, group_id: int = 10) -> MagicMock:
    resp = MagicMock()
    resp.found = found
    resp.group_id = group_id
    return resp


def _make_subjects_response(subject_name: str = "Математика") -> MagicMock:
    resp = MagicMock()
    subj = MagicMock()
    subj.subject_name = subject_name
    resp.subjects = [subj]
    return resp


@pytest.mark.asyncio
async def test_status_no_jwt():
    """User without stored JWT is prompted to /login."""
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value=None)
    schedule_client = MagicMock()
    attendance_client = MagicMock()
    academic_client = MagicMock()

    await cmd_status(
        message,
        jwt_redis=jwt_redis,
        schedule_client=schedule_client,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Сначала получите код через /login" in text


@pytest.mark.asyncio
async def test_status_no_active_lesson():
    """No active lesson returns 'Нет активной пары'."""
    message = _make_message()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(found=True, group_id=10))
    schedule_client = MagicMock()
    schedule_client.get_active_lesson = AsyncMock(return_value=None)
    attendance_client = MagicMock()

    await cmd_status(
        message,
        jwt_redis=jwt_redis,
        schedule_client=schedule_client,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "нет активной пары" in text


@pytest.mark.asyncio
async def test_status_with_lesson_present():
    """Active lesson with matching 'present' record shows Присутствует."""
    message = _make_message()
    lesson = _make_lesson(lesson_id=101, subject_id=55)
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(found=True, group_id=10))
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Математика"))
    schedule_client = MagicMock()
    schedule_client.get_active_lesson = AsyncMock(return_value=lesson)
    attendance_client = MagicMock()
    attendance_client.get_student_records = AsyncMock(
        return_value=[
            {"lessonId": 101, "status": "present"},
        ]
    )

    await cmd_status(
        message,
        jwt_redis=jwt_redis,
        schedule_client=schedule_client,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Присутствует" in text
    assert "Математика" in text
    assert "А-101" in text


@pytest.mark.asyncio
async def test_status_not_marked():
    """Active lesson with no matching attendance record shows 'Не отмечен'."""
    message = _make_message()
    lesson = _make_lesson(lesson_id=101, subject_id=55)
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(found=True, group_id=10))
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Физика"))
    schedule_client = MagicMock()
    schedule_client.get_active_lesson = AsyncMock(return_value=lesson)
    attendance_client = MagicMock()
    attendance_client.get_student_records = AsyncMock(
        return_value=[
            {"lessonId": 999, "status": "present"},  # different lesson
        ]
    )

    await cmd_status(
        message,
        jwt_redis=jwt_redis,
        schedule_client=schedule_client,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Не отмечен" in text


@pytest.mark.asyncio
async def test_status_token_expired():
    """TokenExpiredError causes JWT deletion and prompts re-login."""
    message = _make_message()
    lesson = _make_lesson(lesson_id=101)
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "old_tok", "refresh_token": "ref"})
    jwt_redis.delete = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(found=True, group_id=10))
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Информатика"))
    schedule_client = MagicMock()
    schedule_client.get_active_lesson = AsyncMock(return_value=lesson)
    attendance_client = MagicMock()
    attendance_client.get_student_records = AsyncMock(side_effect=TokenExpiredError("JWT expired"))

    await cmd_status(
        message,
        jwt_redis=jwt_redis,
        schedule_client=schedule_client,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    jwt_redis.delete.assert_called_once_with(12345)
    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Сессия истекла" in text
    assert "/login" in text
