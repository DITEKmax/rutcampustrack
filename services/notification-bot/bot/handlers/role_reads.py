"""Private read-only role actions with fresh grants and expiring own-lesson choices."""

import html
import secrets
import time
from collections import Counter
from dataclasses import dataclass

from aiogram import F, Router
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, InputRichMessage, Message

from bot.handlers.prefs import HEADMAN_LABEL, TEACHER_TODAY_LABEL
from bot.handlers.start import private_sender
from bot.services.bot_menu import (
    BotLesson,
    BotReadDenied,
    BotReadUnavailable,
    MenuScope,
    educational_now,
    resolve_menu_scope,
)
from bot.services.homework_pager import split_message

role_reads_router = Router()
_CHOICE_PREFIX = "group-read:"
_UNAVAILABLE = "Сервис временно недоступен. Попробуйте ещё раз позже."
_STALE = "Этот выбор устарел. Откройте «Учёт группы» ещё раз."
_STATUS_LABELS = {
    "PRESENT": "Присутствует",
    "EXCUSED": "Уважительная причина",
    "FREE_ATTENDANCE": "Свободное посещение",
    "ABSENT": "Отсутствует",
    "CANCELLED": "Отменено",
    "UNMARKED": "Не отмечен",
    "UNKNOWN": "Статус неизвестен",
}
_SUMMARY_LABELS = {
    "PRESENT": "Присутствуют",
    "EXCUSED": "Уважительная причина",
    "FREE_ATTENDANCE": "Свободное посещение",
    "ABSENT": "Отсутствуют",
    "UNMARKED": "Не отмечены",
    "CANCELLED": "Отменено",
    "UNKNOWN": "Статус неизвестен",
}
_LESSON_STATES = {"PLANNED": "Запланирована", "ACTIVE": "Идёт", "CLOSED": "Завершена", "CANCELLED": "Отменена"}
_LESSON_TYPES = {
    "LECTURE": "Лекция",
    "PRACTICE": "Практика",
    "LAB": "Лабораторная",
    "LABORATORY": "Лабораторная",
}


@dataclass(frozen=True)
class LessonChoice:
    sender: int
    scope: MenuScope
    lesson_ids: frozenset[int]
    message_id: int
    expires: float


class LessonChoices:
    """Process-local bounded UI state: restart/expiry safely invalidates old callbacks."""

    def __init__(self, *, ttl_seconds: float = 120, max_entries: int = 1024, clock=time.monotonic):
        if ttl_seconds <= 0 or max_entries <= 0:
            raise ValueError("Positive selection limits required")
        self._entries: dict[str, LessonChoice] = {}
        self._ttl = ttl_seconds
        self._limit = max_entries
        self._clock = clock

    def new_token(self) -> str:
        now = self._clock()
        self._entries = {token: value for token, value in self._entries.items() if value.expires > now}
        while len(self._entries) >= self._limit:
            self._entries.pop(next(iter(self._entries)))
        return secrets.token_urlsafe(12)

    def register(self, token: str, sender: int, scope: MenuScope, lessons: tuple[BotLesson, ...], message_id: int):
        if type(message_id) is not int or message_id <= 0:
            raise BotReadUnavailable("Lesson selection receipt unavailable")
        while len(self._entries) >= self._limit:
            self._entries.pop(next(iter(self._entries)))
        self._entries[token] = LessonChoice(
            sender, scope, frozenset(lesson.lesson_id for lesson in lessons), message_id, self._clock() + self._ttl
        )

    def take(self, token: str, sender: int, message_id: int, lesson_id: int) -> LessonChoice | None:
        choice = self._entries.get(token)
        if choice is None or choice.expires <= self._clock():
            self._entries.pop(token, None)
            return None
        if choice.sender != sender or choice.message_id != message_id or lesson_id not in choice.lesson_ids:
            return None
        self._entries.pop(token, None)
        return choice


def _compact(value: str) -> str:
    return " ".join(str(value or "").split())


def _lesson_lines(lesson: BotLesson) -> list[str]:
    flags = [label for enabled, label in ((lesson.moved, "перенесена"), (lesson.one_off, "разовая")) if enabled]
    status = "Отменена" if lesson.cancelled else _LESSON_STATES.get(lesson.status.upper(), "Статус неизвестен")
    lesson_type = _compact(lesson.lesson_type)
    lesson_type = _LESSON_TYPES.get(lesson_type.upper(), lesson_type or "Вид пары не указан")
    return [
        _compact(lesson.subject_name),
        f"Вид пары: {lesson_type}",
        f"Пара №{lesson.lesson_number}, {_compact(lesson.start_time)}–{_compact(lesson.end_time)} МСК",
        f"Группа: {_compact(lesson.group_name)}",
        f"Аудитория: {_compact(lesson.room) or 'не указана'}",
        f"Состояние: {status}" + (f" ({', '.join(flags)})" if flags else ""),
    ]


async def _answer_text(message, text: str, client, sender: int, scope: MenuScope):
    for index, chunk in enumerate(split_message(text)):
        if index:
            await _unchanged(client, sender, scope)
        await message.answer(chunk, parse_mode=None)


async def _unchanged(client, sender: int, scope: MenuScope):
    if await resolve_menu_scope(client, sender) != scope:
        raise BotReadDenied("Own authority changed")


@role_reads_router.message(F.text == TEACHER_TODAY_LABEL)
async def cmd_teacher_today(message: Message, bot_menu_client=None):
    sender = private_sender(message)
    if sender is None:
        await message.answer("Откройте личный чат с ботом, чтобы посмотреть свои пары.")
        return
    scope = await resolve_menu_scope(bot_menu_client, sender)
    if scope is None or not scope.teacher_enabled:
        await message.answer("Мои пары недоступны. Нужна действующая роль преподавателя.")
        return
    try:
        result = await bot_menu_client.teacher_today(sender, scope)
        now = educational_now(result.server_now, result.educational_date)
        if any(
            lesson.lesson_date != result.educational_date or type(lesson.lesson_id) is not int or lesson.lesson_id <= 0
            for lesson in result.lessons
        ):
            raise BotReadUnavailable("Invalid teacher day snapshot")
        await _unchanged(bot_menu_client, sender, scope)
        header = f"📅 Мои пары сегодня\nДата: {now:%d.%m.%Y}\nДанные на {now:%H:%M:%S} МСК"
        lessons = ["\n".join(_lesson_lines(lesson)) for lesson in result.lessons]
        await _answer_text(
            message, "\n\n".join([header, *(lessons or ["Сегодня у вас нет пар."])]), bot_menu_client, sender, scope
        )
    except Exception:
        await message.answer(_UNAVAILABLE)


@role_reads_router.message(F.text == HEADMAN_LABEL)
async def cmd_headman_current(message: Message, bot_menu_client=None, bot_lesson_choices=None, bot=None):
    sender = private_sender(message)
    if sender is None:
        await message.answer("Откройте личный чат с ботом, чтобы посмотреть учёт своей группы.")
        return
    scope = await resolve_menu_scope(bot_menu_client, sender)
    if scope is None or not scope.headman_enabled:
        await message.answer("Учёт группы недоступен. Нужна действующая роль старосты своей группы.")
        return
    try:
        await _show_headman(message, bot_menu_client, bot_lesson_choices, sender, scope, bot=bot)
    except Exception:
        await message.answer(_UNAVAILABLE)


async def _show_headman(message, client, choices, sender: int, scope: MenuScope, selected_lesson_id=0, *, bot=None):
    result = await client.headman_current(sender, scope, selected_lesson_id)
    now = educational_now(result.server_now, result.educational_date)
    await _unchanged(client, sender, scope)
    heading = ["📋 Учёт группы", f"Дата: {now:%d.%m.%Y}", f"Данные на {now:%H:%M:%S} МСК"]
    if result.state == "NO_CURRENT":
        await message.answer("\n".join([*heading, "", "Сейчас нет текущей пары."]))
        return
    if result.state == "CHOOSE":
        if choices is None or selected_lesson_id or len(result.choices) < 2:
            raise BotReadUnavailable("Lesson selection unavailable")
        if len({lesson.lesson_id for lesson in result.choices}) != len(result.choices):
            raise BotReadUnavailable("Ambiguous lesson selection")
        for lesson in result.choices:
            _validate_current_lesson(lesson, result.educational_date, scope)
        token = choices.new_token()
        buttons = [
            [
                InlineKeyboardButton(
                    text=(
                        f"{_compact(lesson.group_name)}, пара №{lesson.lesson_number}, {_compact(lesson.subject_name)}"
                    )[:100],
                    callback_data=f"{_CHOICE_PREFIX}{token}:{lesson.lesson_id}",
                )
            ]
            for lesson in result.choices
        ]
        sent = await message.answer(
            "Сейчас доступны несколько пар ваших групп. Выберите пару.",
            reply_markup=InlineKeyboardMarkup(inline_keyboard=buttons),
        )
        choices.register(token, sender, scope, result.choices, sent.message_id)
        return
    if result.state != "READY" or result.lesson is None:
        raise BotReadUnavailable("Unknown lesson snapshot")
    lesson = result.lesson
    _validate_current_lesson(lesson, result.educational_date, scope)
    if selected_lesson_id and selected_lesson_id != lesson.lesson_id:
        raise BotReadDenied("Selected lesson changed")
    if len({row.student_id for row in result.rows}) != len(result.rows) or any(
        type(row.student_id) is not int or row.student_id <= 0 or type(row.record_present) is not bool
        for row in result.rows
    ):
        raise BotReadUnavailable("Invalid attendance roster")
    heading.extend(["", *_lesson_lines(lesson)])
    if not result.rows:
        await _answer_text(message, "\n".join([*heading, "", "В составе группы нет студентов."]), client, sender, scope)
        return

    counts = Counter()
    roster = []
    for row in result.rows:
        status = row.status.upper()
        if not row.record_present:
            status = "UNMARKED"
        elif status not in _STATUS_LABELS or status == "UNMARKED":
            status = "UNKNOWN"
        counts[status] += 1
        roster.append((_compact(row.display_name), _STATUS_LABELS[status]))
    summary = [f"Всего студентов: {len(roster)}"]
    for statuses in (("PRESENT", "EXCUSED"), ("ABSENT", "UNMARKED"), ("FREE_ATTENDANCE", "CANCELLED", "UNKNOWN")):
        totals = [f"{_SUMMARY_LABELS[status]}: {counts[status]}" for status in statuses if counts[status]]
        if totals:
            summary.append(", ".join(totals))
    if not await _try_rich_roster(bot, sender, roster, heading, summary):
        await _unchanged(client, sender, scope)
        rows = [f"{number}. {name} — {status}" for number, (name, status) in enumerate(roster, 1)]
        await _answer_text(
            message, "\n".join([*heading, "", "Посещаемость", *rows, "", *summary]), client, sender, scope
        )


async def _try_rich_roster(bot, sender, rows, heading, footer):
    """Pinned aiogram supports Bot API rich HTML tables; provider rejection falls back to text."""
    if bot is None or not callable(getattr(bot, "send_rich_message", None)):
        return False
    rich = "".join(f"<p>{html.escape(line)}</p>" for line in heading if line)
    rich += "<table><tr><th>Студент</th><th>Посещаемость</th></tr>"
    for name, status in rows:
        rich += f"<tr><td>{html.escape(name)}</td><td>{html.escape(status)}</td></tr>"
    rich += "</table>"
    rich += "".join(f"<p>{html.escape(line)}</p>" for line in footer if line)
    # The plain renderer handles large rosters without relying on provider-specific rich limits.
    if len(rich.encode("utf-16-le")) // 2 > 3900:
        return False
    try:
        await bot.send_rich_message(
            chat_id=sender, rich_message=InputRichMessage(html=rich, skip_entity_detection=True), request_timeout=5
        )
        return True
    except Exception:
        return False


def _validate_current_lesson(lesson, educational_date, scope):
    if (
        type(lesson.lesson_id) is not int
        or lesson.lesson_id <= 0
        or lesson.lesson_date != educational_date
        or lesson.cancelled
        or lesson.status.upper() != "ACTIVE"
        or lesson.group_id not in scope.groups_for("HEADMAN")
    ):
        raise BotReadDenied("Lesson is no longer current")


@role_reads_router.callback_query(F.data.startswith(_CHOICE_PREFIX))
async def cb_headman_lesson(callback: CallbackQuery, bot_menu_client=None, bot_lesson_choices=None, bot=None):
    message = getattr(callback, "message", None)
    sender = getattr(getattr(callback, "from_user", None), "id", None)
    chat = getattr(message, "chat", None)
    if (
        type(sender) is not int
        or sender <= 0
        or getattr(chat, "type", None) != "private"
        or getattr(chat, "id", None) != sender
    ):
        await callback.answer("Откройте личный чат с ботом.", show_alert=True)
        return
    try:
        prefix, token, raw_id = callback.data.split(":")
        lesson_id = int(raw_id)
        if prefix != "group-read" or lesson_id <= 0 or bot_lesson_choices is None:
            raise ValueError()
        choice = bot_lesson_choices.take(token, sender, getattr(message, "message_id", None), lesson_id)
        if choice is None:
            raise ValueError()
        scope = await resolve_menu_scope(bot_menu_client, sender)
        if scope != choice.scope or not scope.headman_enabled:
            raise ValueError()
    except (AttributeError, TypeError, ValueError):
        await callback.answer(_STALE, show_alert=True)
        return
    await callback.answer()
    try:
        await message.edit_reply_markup(reply_markup=None)
        await _show_headman(message, bot_menu_client, bot_lesson_choices, sender, scope, lesson_id, bot=bot)
    except Exception:
        await message.answer(_UNAVAILABLE)
