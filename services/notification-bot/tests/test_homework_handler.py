import asyncio
from dataclasses import replace
from datetime import date
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.grpc_client.academic_pb2 import UserResponse, UserRoleGrant
from bot.handlers.homework import cb_homework_page, cmd_homework_today, cmd_homework_week
from bot.services.bot_menu import BotGrant, MenuScope
from bot.services.homework_pager import HomeworkPager, homework_pages


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
    assert "Физика, пара №2" in text
    assert "Лабораторная работа" in text
    assert "Задачи 1-3" in text
    assert "https://example.com/hw.pdf" in text
    assert "✅ Физика, пара №1" in text
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


@pytest.mark.parametrize(
    "handler,end_date,period",
    [
        (cmd_homework_today, "2026-05-07", "сегодня"),
        (cmd_homework_week, "2026-05-13", "ближайшую неделю"),
    ],
)
async def test_headman_with_student_grant_reads_own_homework_for_requested_period(handler, end_date, period):
    message = _make_message()
    academic = MagicMock()
    academic.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(group_id=999))
    academic.get_user_by_id = AsyncMock(
        return_value=UserResponse(
            roles_version=1,
            id=77,
            telegram_id=12345,
            role="headman",
            group_id=999,
            roles=[
                UserRoleGrant(role="HEADMAN", status="ACTIVE", group_id=10),
                UserRoleGrant(role="STUDENT", status="ACTIVE", group_id=10),
            ],
        )
    )
    message.menu_client.menu_scope.return_value = MenuScope(
        77, 2, 1, (BotGrant("HEADMAN", 10), BotGrant("STUDENT", 10))
    )
    academic.get_active_semester = AsyncMock(return_value=_make_semester())
    message.homework_client.homeworks = AsyncMock(
        return_value=[
            _make_homework(1, title="Сегодня", lesson_date="2026-05-07"),
            _make_homework(2, title="Завтра", lesson_date="2026-05-08"),
        ]
    )
    await handler(
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
        date_to=end_date,
    )
    text = message.answer.await_args.args[0]
    assert f"Домашнее задание на {period}" in text and "Сегодня" in text
    assert ("Завтра" in text) == (handler is cmd_homework_week)


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


def _callback(data, *, sender=12345, chat_id=12345, chat_type="private", message_id=56):
    return SimpleNamespace(
        from_user=SimpleNamespace(id=sender),
        message=SimpleNamespace(
            chat=SimpleNamespace(id=chat_id, type=chat_type), message_id=message_id, edit_text=AsyncMock()
        ),
        data=data,
        answer=AsyncMock(),
    )


def _button(call, label):
    buttons = call.kwargs["reply_markup"].inline_keyboard[0]
    return next(button.callback_data for button in buttons if button.text == label)


async def test_direct_homework_pages_keep_long_unicode_task_and_several_open_snapshots():
    pager = HomeworkPager()
    msg = _make_message()
    msg.answer.return_value = SimpleNamespace(message_id=56, chat=msg.chat)
    academic = SimpleNamespace(get_active_semester=AsyncMock(return_value=_make_semester()))
    long_description = "🧪" * 3500 + "конец <описания>"
    msg.homework_client.homeworks.return_value = [
        _make_homework(1, description=long_description, link="https://example.com/д/з"),
        _make_homework(2, title="Второе задание", lesson_number=3),
    ]
    for command, receipt in [(cmd_homework_week, 56), (cmd_homework_today, 57)]:
        msg.answer.return_value = SimpleNamespace(message_id=receipt, chat=msg.chat)
        await command(msg, academic, date(2026, 5, 8), msg.homework_client, msg.menu_client, pager)

    week, today = msg.answer.await_args_list
    assert "ближайшую неделю" in week.args[0] and "на сегодня" in today.args[0]
    first = _button(week, "Далее")
    callback = _callback(first)
    pages = [week.args[0]]
    while True:
        await cb_homework_page(callback, msg.menu_client, pager)
        edit = callback.message.edit_text.await_args
        pages.append(edit.args[0])
        if not any(btn.text == "Далее" for btn in edit.kwargs["reply_markup"].inline_keyboard[0]):
            break
        callback = _callback(_button(edit, "Далее"))

    joined = "".join(page.rsplit("\n\nСтраница ", 1)[0] for page in pages)
    assert long_description in joined and "https://example.com/д/з" in joined and "Второе задание" in joined
    assert all(len(page.encode("utf-16-le")) // 2 <= 4096 for page in pages)
    previous = _callback(_button(edit, "Назад"))
    await cb_homework_page(previous, msg.menu_client, pager)
    assert previous.message.edit_text.await_args.args[0] == pages[-2]

    duplicate = _callback(first)
    await cb_homework_page(duplicate, msg.menu_client, pager)
    duplicate.message.edit_text.assert_not_awaited()
    today_callback = _callback(_button(today, "Далее"), message_id=57)
    await cb_homework_page(today_callback, msg.menu_client, pager)
    today_callback.message.edit_text.assert_awaited_once()


@pytest.mark.parametrize(
    "failure", ["wrong_actor", "wrong_chat", "wrong_message", "expired", "restart", "rebound", "role_revoked"]
)
async def test_homework_page_callbacks_deny_stale_or_foreign_snapshots(failure):
    now = [0]
    pager = HomeworkPager(clock=lambda: now[0], ttl_seconds=1)
    scope = MenuScope(77, 2, 1, (BotGrant("STUDENT", 10),))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    send = AsyncMock(return_value=SimpleNamespace(message_id=56, chat=SimpleNamespace(id=12345, type="private")))
    await pager.send(send, sender=12345, scope=scope, pages=("первая", "личное задание"))
    callback = _callback(_button(send.await_args, "Далее"))
    if failure == "wrong_actor":
        callback.from_user.id = callback.message.chat.id = 12346
    elif failure == "wrong_chat":
        callback.message.chat.type = "group"
    elif failure == "wrong_message":
        callback.message.message_id += 1
    elif failure == "expired":
        now[0] += 2
    elif failure == "restart":
        pager = HomeworkPager()
    elif failure == "rebound":
        menu.menu_scope.return_value = replace(scope, telegram_binding_version=3)
    elif failure == "role_revoked":
        menu.menu_scope.return_value = replace(scope, roles_version=2, grants=())

    await cb_homework_page(callback, menu, pager)
    callback.message.edit_text.assert_not_awaited()
    assert callback.answer.await_args.kwargs["show_alert"] is True
    assert "личное задание" not in callback.answer.await_args.args[0]


async def test_homework_concurrent_or_replayed_navigation_cannot_edit_twice():
    pager = HomeworkPager()
    scope = MenuScope(77, 2, 1, (BotGrant("STUDENT", 10),))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    send = AsyncMock(return_value=SimpleNamespace(message_id=56, chat=SimpleNamespace(id=12345, type="private")))
    await pager.send(send, sender=12345, scope=scope, pages=("первая", "вторая"))
    first = _button(send.await_args, "Далее")
    editing = asyncio.Event()
    release = asyncio.Event()

    async def edit(*_args, **_kwargs):
        editing.set()
        await release.wait()

    original = _callback(first)
    original.message.edit_text.side_effect = edit
    active = asyncio.create_task(cb_homework_page(original, menu, pager))
    await asyncio.wait_for(editing.wait(), timeout=1)
    try:
        duplicate = _callback(first)
        await cb_homework_page(duplicate, menu, pager)
        duplicate.message.edit_text.assert_not_awaited()
    finally:
        release.set()
        await active

    await cb_homework_page(_callback(_button(original.message.edit_text.await_args, "Назад")), menu, pager)
    replay = _callback(first)
    await cb_homework_page(replay, menu, pager)
    replay.message.edit_text.assert_not_awaited()


@pytest.mark.parametrize("provider_applied_edit", [False, True])
async def test_homework_navigation_recovers_after_missing_edit_receipt(provider_applied_edit):
    pager = HomeworkPager()
    scope = MenuScope(77, 2, 1, (BotGrant("STUDENT", 10),))
    menu = SimpleNamespace(menu_scope=AsyncMock(return_value=scope))
    send = AsyncMock(return_value=SimpleNamespace(message_id=56, chat=SimpleNamespace(id=12345, type="private")))
    await pager.send(send, sender=12345, scope=scope, pages=("первая", "вторая", "третья"))
    initial_data = _button(send.await_args, "Далее")
    initial_click = _callback(initial_data)
    initial_click.message.edit_text.side_effect = TimeoutError
    await cb_homework_page(initial_click, menu, pager)
    failed_edit = initial_click.message.edit_text.await_args
    assert initial_click.answer.await_args.kwargs["show_alert"] is True

    if provider_applied_edit:
        pending_data = _button(failed_edit, "Далее")
        foreign = _callback(pending_data, message_id=57)
        await cb_homework_page(foreign, menu, pager)
        foreign.message.edit_text.assert_not_awaited()
        retry = _callback(pending_data)
        expected = "третья"
    else:
        retry = _callback(initial_data)
        expected = "вторая"
    await cb_homework_page(retry, menu, pager)
    actual_edit = retry.message.edit_text.await_args
    assert actual_edit.args[0].startswith(expected)
    if not provider_applied_edit:
        assert actual_edit == failed_edit
    retry.answer.assert_awaited_once_with()

    replay = _callback(initial_data)
    await cb_homework_page(replay, menu, pager)
    replay.message.edit_text.assert_not_awaited()


def test_homework_pages_preserve_authored_layout_and_complete_url_at_page_break():
    link = "https://example.com/" + "x" * (2048 - len("https://example.com/"))
    description = "Решите задачи.\n  1. Первый шаг\n\n  2. Второй шаг\n" + "т" * 1780
    pages = homework_pages(
        "Домашнее задание",
        "08.05.2026",
        "",
        [{"title": "Письменная работа", "description": description, "link": link, "lesson_date": "2026-05-08"}],
    )
    rendered = "".join(pages)
    # The full link would cross the unadjusted message boundary.
    assert rendered.index(link) < 3900 < rendered.index(link) + len(link)
    assert len(pages) > 1 and description in rendered
    assert sum(link in page for page in pages) == 1
    assert all(len(page.encode("utf-16-le")) // 2 <= 3900 for page in pages)
