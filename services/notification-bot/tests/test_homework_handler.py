from datetime import date
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.grpc_client.academic_pb2 import UserResponse, UserRoleGrant
from bot.handlers.homework import cmd_homework_today, cmd_homework_week
from bot.services.bot_menu import BotGrant, MenuScope


def _make_message(user_id: int = 12345) -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.chat.type = "private"
    message.chat.id = user_id
    message.answer = AsyncMock()
    message.homework_client = SimpleNamespace(homeworks=AsyncMock())
    message.menu_client = SimpleNamespace(
        menu_scope=AsyncMock(return_value=MenuScope(77, 2, 1, (BotGrant("STUDENT", 10),)))
    )
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
    academic_client.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            roles=[UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10)],
        )
    )
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    message.homework_client.homeworks = AsyncMock(
        return_value=[_make_homework(1, title="Лабораторная работа", lesson_date="2026-05-08")]
    )

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
        today=date(2026, 5, 7),
    )

    message.answer.assert_awaited_once()
    text = message.answer.call_args.args[0]
    assert "/login" not in text
    assert "Лабораторная работа" in text
    message.homework_client.homeworks.assert_awaited_once_with(
        telegram_sender_id=12345,
        expected_user_id=77,
        expected_roles_version=1,
        expected_telegram_binding_version=2,
        date_from="2026-05-07",
        date_to="2026-05-13",
    )


@pytest.mark.asyncio
async def test_homework_week_formats_upcoming_homeworks():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            roles=[UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10)],
        )
    )
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    message.homework_client.homeworks = AsyncMock(
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
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
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
    message.homework_client.homeworks.assert_awaited_once()


@pytest.mark.asyncio
async def test_homework_week_empty_range_message():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            roles=[UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10)],
        )
    )
    academic_client.get_active_semester = AsyncMock(return_value=_make_semester(2))
    message.homework_client.homeworks = AsyncMock(return_value=[])

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
        today=date(2026, 5, 7),
    )

    text = message.answer.call_args.args[0]
    assert "На ближайшие 7 дней домашних заданий нет." in text


@pytest.mark.asyncio
async def test_homework_week_no_active_semester_message():
    message = _make_message()

    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=10, user_id=77))
    academic_client.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            roles=[UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10)],
        )
    )
    academic_client.get_active_semester = AsyncMock(return_value=None)
    message.homework_client.homeworks = AsyncMock()

    await cmd_homework_week(
        message,
        academic_client=academic_client,
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
        today=date(2026, 5, 7),
    )

    assert "нет активного семестра" in message.answer.call_args.args[0]
    message.homework_client.homeworks.assert_not_called()


@pytest.mark.parametrize("handler", [cmd_homework_today, cmd_homework_week])
@pytest.mark.parametrize("status", ["SUSPENDED", "ARCHIVED", "EXPELLED", "GRADUATED", "DISMISSED", "UNKNOWN"])
async def test_direct_homework_never_reads_for_inactive_student_even_with_active_teacher(handler, status):
    message = _make_message()
    academic = MagicMock()
    academic.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response())
    academic.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            role="student",
            group_id=10,
            roles=[
                UserRoleGrant(role="STUDENT", status=status, group_id=10),
                UserRoleGrant(role="TEACHER", status="ACTIVE"),
            ],
        )
    )
    message.menu_client.menu_scope.return_value = MenuScope(
        77, 2, 1, (BotGrant("STUDENT", 10, status), BotGrant("TEACHER"))
    )
    message.homework_client.homeworks = AsyncMock()
    academic.get_active_semester = AsyncMock()
    await handler(
        message,
        academic,
        today=date(2026, 5, 7),
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
    )
    message.homework_client.homeworks.assert_not_awaited()
    academic.get_active_semester.assert_not_awaited()
    assert "ДЗ недоступно" in message.answer.await_args.args[0]


@pytest.mark.parametrize("handler", [cmd_homework_today, cmd_homework_week])
async def test_direct_homework_rejects_non_private_chat_before_lookup(handler):
    message = _make_message()
    message.chat.type = "group"
    academic = MagicMock(get_user_by_telegram_id=AsyncMock())
    await handler(message, academic)
    academic.get_user_by_telegram_id.assert_not_awaited()


async def test_today_uses_current_student_grant_group_and_only_today_for_multirole_user():
    message = _make_message()
    academic = MagicMock()
    academic.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=999))
    academic.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            role="teacher",
            group_id=999,
            roles=[
                UserRoleGrant(role="TEACHER", status="ACTIVE"),
                UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10),
            ],
        )
    )
    academic.get_active_semester = AsyncMock(return_value=_make_semester())
    message.homework_client.homeworks = AsyncMock(
        return_value=[
            _make_homework(1, title="Сегодня", lesson_date="2026-05-07"),
            _make_homework(2, title="Завтра", lesson_date="2026-05-08"),
        ]
    )
    await cmd_homework_today(
        message,
        academic,
        today=date(2026, 5, 7),
        bot_homework_client=message.homework_client,
        bot_menu_client=message.menu_client,
    )
    message.homework_client.homeworks.assert_awaited_once_with(
        telegram_sender_id=12345,
        expected_user_id=77,
        expected_roles_version=1,
        expected_telegram_binding_version=2,
        date_from="2026-05-07",
        date_to="2026-05-07",
    )
    text = message.answer.await_args.args[0]
    assert "Домашнее задание на сегодня" in text and "Сегодня" in text and "Завтра" not in text


@pytest.mark.parametrize("handler", [cmd_homework_today, cmd_homework_week])
async def test_missing_protected_capability_never_falls_back_to_legacy_student_rpc(handler):
    message = _make_message()
    academic = MagicMock(
        get_user_by_telegram_id=AsyncMock(return_value=_make_user_response()),
        get_user_by_id=AsyncMock(
            return_value=UserResponse(
                id=77,
                telegram_id=12345,
                roles_version=1,
                roles=[UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10)],
            )
        ),
        get_active_semester=AsyncMock(return_value=_make_semester()),
        get_homeworks_for_week=AsyncMock(),
    )
    await handler(message, academic, today=date(2026, 5, 7), bot_menu_client=message.menu_client)
    academic.get_homeworks_for_week.assert_not_awaited()
    assert "Сервис временно недоступен" in message.answer.await_args.args[0]
