from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.handlers.prefs import (
    CREDENTIALS_LABEL,
    HEADMAN_LABEL,
    HOMEWORK_TODAY_LABEL,
    HOMEWORK_WEEK_LABEL,
    LINKS_LABEL,
    LOGIN_LABEL,
    SETTINGS_LABEL,
    TEACHER_TODAY_LABEL,
    main_keyboard,
)
from bot.handlers.role_reads import LessonChoices, cb_headman_lesson, cmd_headman_current, cmd_teacher_today
from bot.services.bot_menu import (
    BotAttendanceRow,
    BotGrant,
    BotLesson,
    HeadmanCurrent,
    MenuScope,
    TeacherToday,
    educational_now,
    resolve_menu_scope,
)

pytestmark = pytest.mark.asyncio


def scope(*roles, version=4):
    return MenuScope(
        7, 2, version, tuple(BotGrant(role, 10 if role in {"STUDENT", "HEADMAN"} else 0) for role in roles)
    )


def lesson(lesson_id=11, **changes):
    return replace(
        BotLesson(
            lesson_id,
            "Группа 1",
            "Математика",
            "Лекция",
            "2026-10-18",
            1,
            "00:00",
            "01:30",
            "101",
            "ACTIVE",
            group_id=10,
            subject_id=42,
            semester_id=2,
        ),
        **changes,
    )


def client(authority, **responses):
    return SimpleNamespace(
        menu_scope=AsyncMock(return_value=authority),
        teacher_today=AsyncMock(return_value=responses.get("teacher")),
        headman_current=AsyncMock(return_value=responses.get("headman")),
    )


def message(sender=123, *, chat_type="private", chat_id=123):
    return SimpleNamespace(
        from_user=SimpleNamespace(id=sender),
        chat=SimpleNamespace(type=chat_type, id=chat_id),
        message_id=55,
        answer=AsyncMock(return_value=SimpleNamespace(message_id=56)),
        edit_reply_markup=AsyncMock(),
    )


def ready(rows=(), **changes):
    return replace(HeadmanCurrent("2026-10-17T21:30:00Z", "2026-10-18", "READY", lesson=lesson(), rows=rows), **changes)


def text(msg):
    return "\n".join(call.args[0] for call in msg.answer.await_args_list)


@pytest.mark.parametrize(
    "roles,extras",
    [
        ((), ()),
        (("STUDENT",), (HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL)),
        (("HEADMAN",), (HEADMAN_LABEL,)),
        (("TEACHER",), (TEACHER_TODAY_LABEL,)),
        (("ADMIN",), ()),
        (
            ("HEADMAN", "TEACHER", "ADMIN", "STUDENT"),
            (HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, HEADMAN_LABEL, TEACHER_TODAY_LABEL),
        ),
        (("STUDENT", "STUDENT", "HEADMAN"), (HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, HEADMAN_LABEL)),
    ],
)
async def test_menu_is_union_of_current_grants_with_common_four_first_once(roles, extras):
    resolved = await resolve_menu_scope(client(scope(*roles)), 123)
    labels = [button.text for row in main_keyboard(**resolved.keyboard_options).keyboard for button in row]
    assert labels == [CREDENTIALS_LABEL, LINKS_LABEL, LOGIN_LABEL, SETTINGS_LABEL, *extras]
    assert len(labels) == len(set(labels))


async def test_missing_unknown_suspended_and_ambiguous_student_grants_do_not_create_authority():
    authority = MenuScope(
        7,
        2,
        4,
        (
            BotGrant("TEACHER", status="SUSPENDED"),
            BotGrant("SUPERADMIN"),
            BotGrant("STUDENT", 10),
            BotGrant("STUDENT", 12),
        ),
    )
    resolved = await resolve_menu_scope(client(authority), 123)
    assert not resolved.homework_enabled and not resolved.teacher_enabled and not resolved.headman_enabled
    assert await resolve_menu_scope(client(None), 123) is None
    for invalid in (
        replace(authority, user_id=0),
        replace(authority, roles_version=0),
        replace(authority, telegram_binding_version=0),
    ):
        assert await resolve_menu_scope(client(invalid), 123) is None
    unavailable = client(authority)
    unavailable.menu_scope.side_effect = RuntimeError("PRIVATE_MARKER")
    assert await resolve_menu_scope(unavailable, 123) is None


@pytest.mark.parametrize("handler", [cmd_headman_current, cmd_teacher_today])
@pytest.mark.parametrize(
    "fields",
    [dict(chat_type="group"), dict(chat_type="supergroup"), dict(chat_id=999), dict(sender=True), dict(sender=-1)],
)
async def test_private_guard_precedes_all_authority_and_data_reads(handler, fields):
    msg = message(**fields)
    adapter = client(scope("HEADMAN", "TEACHER"))
    await handler(msg, bot_menu_client=adapter)
    adapter.menu_scope.assert_not_awaited()
    adapter.teacher_today.assert_not_awaited()
    adapter.headman_current.assert_not_awaited()


async def test_headman_reads_all_rows_directly_and_distinguishes_unmarked_from_absent():
    rows = tuple(
        BotAttendanceRow(
            number, f"Студент {number} " + "Имя " * 12, "ABSENT" if number <= 2 else "PRESENT", number != 1
        )
        for number in range(1, 141)
    )
    msg = message()
    adapter = client(scope("HEADMAN"), headman=ready(rows))
    await cmd_headman_current(msg, adapter)
    rendered = text(msg)
    assert "140. Студент 140" in rendered and "1. Студент 1" in rendered
    assert "Отсутствуют: 1 · Не отмечены: 1" in rendered
    assert "00:30:00 МСК" in rendered and "18.10.2026" in rendered
    assert len(msg.answer.await_args_list) > 1
    assert all(len(call.args[0]) <= 3900 for call in msg.answer.await_args_list)
    assert all(not call.kwargs.get("reply_markup") for call in msg.answer.await_args_list)
    assert adapter.headman_current.await_args.args[:2] == (123, scope("HEADMAN"))


async def test_no_current_lesson_is_explicit_and_unknown_state_fails_closed():
    msg = message()
    adapter = client(scope("HEADMAN"), headman=ready(state="NO_CURRENT", lesson=None))
    await cmd_headman_current(msg, adapter)
    assert "нет текущей пары" in text(msg)
    msg = message()
    adapter.headman_current.return_value = ready(state="UNKNOWN")
    await cmd_headman_current(msg, adapter)
    assert "временно недоступен" in text(msg)


async def test_teacher_today_uses_server_moscow_day_without_web_login_or_extra_buttons():
    msg = message()
    adapter = client(scope("TEACHER"), teacher=TeacherToday("2026-10-17T21:30:00Z", "2026-10-18", (lesson(),)))
    await cmd_teacher_today(msg, adapter)
    assert "18.10.2026" in text(msg) and "Математика" in text(msg) and "00:30:00 МСК" in text(msg)
    assert all(not call.kwargs.get("reply_markup") for call in msg.answer.await_args_list)
    assert adapter.teacher_today.await_args.args == (123, scope("TEACHER"))


@pytest.mark.parametrize("failure", ["revoked", "rebound", "dependency", "wrongdate"])
async def test_teacher_read_never_renders_data_after_revocation_rebinding_or_invalid_day(failure):
    authority = scope("TEACHER")
    adapter = client(authority, teacher=TeacherToday("2026-10-17T21:30:00Z", "2026-10-18", (lesson(),)))
    if failure == "revoked":
        adapter.menu_scope.side_effect = [authority, scope("ADMIN", version=5)]
    elif failure == "rebound":
        adapter.menu_scope.side_effect = [authority, replace(authority, telegram_binding_version=3)]
    elif failure == "dependency":
        adapter.teacher_today.side_effect = RuntimeError("PRIVATE_MARKER")
    else:
        adapter.teacher_today.return_value = TeacherToday("2026-10-17T21:30:00Z", "2026-10-17", (lesson(),))
    msg = message()
    await cmd_teacher_today(msg, adapter)
    assert "Математика" not in text(msg) and "PRIVATE_MARKER" not in text(msg)
    assert "недоступен" in text(msg)


async def test_multiple_current_lessons_require_expiring_own_message_choice_and_recheck():
    authority = scope("HEADMAN")
    choices = LessonChoices()
    adapter = client(authority, headman=ready(state="CHOOSE", lesson=None, choices=(lesson(11), lesson(12))))
    msg = message()
    await cmd_headman_current(msg, adapter, choices)
    buttons = msg.answer.await_args.kwargs["reply_markup"].inline_keyboard
    callback_data = buttons[1][0].callback_data
    assert len(callback_data.encode()) <= 64
    assert "Студент" not in text(msg)
    callback_message = message()
    callback_message.message_id = 56
    callback = SimpleNamespace(
        from_user=SimpleNamespace(id=123), message=callback_message, data=callback_data, answer=AsyncMock()
    )
    adapter.headman_current.return_value = ready(
        (BotAttendanceRow(1, "Свой студент", "PRESENT", True),), lesson=lesson(12)
    )
    await cb_headman_lesson(callback, adapter, choices)
    assert "Свой студент" in text(callback_message)
    assert adapter.headman_current.await_args.args == (123, authority, 12)
    await cb_headman_lesson(callback, adapter, choices)
    assert "устарел" in callback.answer.await_args.args[0]
    assert adapter.headman_current.await_count == 2


@pytest.mark.parametrize(
    "defect", ["other_sender", "other_chat", "other_message", "other_lesson", "expired", "revoked", "rebound"]
)
async def test_stale_tampered_and_foreign_callbacks_never_read_roster(defect):
    now = [1.0]
    choices = LessonChoices(clock=lambda: now[0])
    authority = scope("HEADMAN")
    token = choices.new_token()
    choices.register(token, 123, authority, (lesson(), lesson(12)), 56)
    msg = message()
    msg.message_id = 56
    sender, selected = 123, 11
    adapter = client(authority)
    if defect == "other_sender":
        sender = 456
        msg.chat.id = sender
    elif defect == "other_chat":
        msg.chat.type = "group"
    elif defect == "other_message":
        msg.message_id = 57
    elif defect == "other_lesson":
        selected = 99
    elif defect == "expired":
        now[0] += 121
    elif defect == "revoked":
        adapter.menu_scope.return_value = scope("ADMIN", version=5)
    elif defect == "rebound":
        adapter.menu_scope.return_value = replace(authority, telegram_binding_version=3)
    callback = SimpleNamespace(
        from_user=SimpleNamespace(id=sender), message=msg, data=f"group-read:{token}:{selected}", answer=AsyncMock()
    )
    await cb_headman_lesson(callback, adapter, choices)
    adapter.headman_current.assert_not_awaited()
    assert not msg.answer.called
    callback.answer.assert_awaited_once()


async def test_domain_changing_selected_lesson_does_not_disclose_rows():
    choices = LessonChoices()
    authority = scope("HEADMAN")
    token = choices.new_token()
    choices.register(token, 123, authority, (lesson(11), lesson(12)), 56)
    msg = message()
    msg.message_id = 56
    adapter = client(
        authority, headman=ready((BotAttendanceRow(1, "PRIVATE_MARKER", "PRESENT", True),), lesson=lesson(99))
    )
    callback = SimpleNamespace(
        from_user=SimpleNamespace(id=123), message=msg, data=f"group-read:{token}:11", answer=AsyncMock()
    )
    await cb_headman_lesson(callback, adapter, choices)
    assert "PRIVATE_MARKER" not in text(msg)


@pytest.mark.parametrize(
    "server,date",
    [("2026-10-18T00:00:00", "2026-10-18"), ("bad", "2026-10-18"), ("2026-10-17T21:30:00Z", "2026-10-17")],
)
async def test_timezone_snapshot_requires_offset_and_consistent_moscow_date(server, date):
    with pytest.raises(RuntimeError):
        educational_now(server, date)


@pytest.mark.parametrize("supported", [True, False])
async def test_pinned_rich_table_support_escapes_data_and_provider_failure_falls_back_to_readable_text(supported):
    rows = (BotAttendanceRow(1, "Студент <script>", "ABSENT", False), BotAttendanceRow(2, "Студент 2", "ABSENT", True))
    adapter = client(scope("HEADMAN"), headman=ready(rows))
    msg = message()
    bot = SimpleNamespace(send_rich_message=AsyncMock())
    if not supported:
        bot.send_rich_message.side_effect = RuntimeError("Unsupported rich method")
    await cmd_headman_current(msg, adapter, bot=bot)
    request = bot.send_rich_message.await_args.kwargs
    assert request["chat_id"] == 123
    rich = request["rich_message"].html
    assert "<table>" in rich and "&lt;script&gt;" in rich and "<script>" not in rich
    assert "Не отмечен" in rich and "Отсутствуют: 1" in rich and "Не отмечены: 1" in rich
    if supported:
        msg.answer.assert_not_awaited()
    else:
        assert "Студент <script> | Не отмечен" in text(msg)
        assert msg.answer.await_args.kwargs["parse_mode"] is None


async def test_native_aiogram_dispatcher_delivers_role_button_with_pinned_rich_method():
    from aiogram import Bot, Dispatcher, F, Router
    from aiogram.client.session.base import BaseSession
    from aiogram.methods import SendRichMessage
    from aiogram.types import Message, Update

    class SyntheticSession(BaseSession):
        def __init__(self):
            super().__init__()
            self.calls = []

        async def close(self):
            pass

        async def make_request(self, bot, method, timeout=None):
            self.calls.append(method)
            return Message.model_validate(
                {
                    "message_id": 80,
                    "date": 1792272600,
                    "chat": {"id": 123, "type": "private"},
                    "text": "synthetic receipt",
                },
                context={"bot": bot},
            )

        async def stream_content(self, url, **kwargs):
            yield b""

    session = SyntheticSession()
    bot = Bot("123456:synthetic-token", session=session)
    dispatcher = Dispatcher()
    router = Router()
    router.message.register(cmd_headman_current, F.text == HEADMAN_LABEL)
    dispatcher.include_router(router)
    adapter = client(scope("HEADMAN"), headman=ready((BotAttendanceRow(1, "Свой студент", "PRESENT", True),)))
    update = Update.model_validate(
        {
            "update_id": 1,
            "message": {
                "message_id": 55,
                "date": 1792272600,
                "chat": {"id": 123, "type": "private"},
                "from": {"id": 123, "is_bot": False, "first_name": "Test"},
                "text": HEADMAN_LABEL,
            },
        }
    )
    try:
        await dispatcher.feed_update(bot, update, bot_menu_client=adapter, bot_lesson_choices=LessonChoices())
        assert len(session.calls) == 1 and isinstance(session.calls[0], SendRichMessage)
        assert session.calls[0].chat_id == 123
        assert "Свой студент" in session.calls[0].rich_message.html
    finally:
        await dispatcher.storage.close()
        await bot.session.close()
